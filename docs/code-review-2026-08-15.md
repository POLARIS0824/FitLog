# FitLog 全面代码审查报告（2026-08-15）

> 审查方式：主代理逐文件通读 + 5 个子代理并行深挖（Room 数据层 / Repository·网络·种子 / ViewModel·Feature / Compose UI / 测试·构建），
> 并对关键结论做了**实测验证**（`compileDebugAndroidTestKotlin` 构建、`git ls-files`、grep 全库核实）。
> 行号基于当前工作区快照（含未提交改动）。

---

## 〇、最紧急：androidTest 编译失败（实测确认）

```
> Task :app:compileDebugAndroidTestKotlin FAILED
e: WorkoutPlanDaoTest.kt:134,135,146,147,148,161: Unresolved reference 'insertPlan'
```

- **原因**：工作区未提交的 `WorkoutPlanDao` 重构把 `insertPlan` 改名为 `insertPlanIgnore`（`WorkoutPlanDao.kt:33`），但 `app/src/androidTest/.../WorkoutPlanDaoTest.kt` 仍有 6 处调用旧名。
- **影响**：`connectedDebugAndroidTest` / `assembleAndroidTest` 全线失败，7 个 androidTest 类全部无法运行（含 WorkoutPlanDaoTest 本身的 savePlanWithSessions 级联用例）。
- **修复**：6 处改为 `insertPlanIgnore`（或按测试意图改用 `savePlanWithSessions`）；注意该测试 `insertPlan_duplicateId_replaces` 的"REPLACE 替换"断言与新语义（IGNORE）已不再成立，需同步调整。

**同时**：工作区有 5 个文件未提交（ProfileViewModel 回填 id、WorkoutPlanDao update-or-insert、UserProfileDao/Repository 返回主键、.gitignore 加 .workbuddy/）——这些是**正确的修复**，建议先提交再改测试，避免与编译修复混在一起。

---

## 一、高危 Bug

| # | 位置 | 问题 |
|---|------|------|
| 1 | `AppDatabase.kt:43-44` + `DatabaseModule.kt:39` | `version=6` 无任何 Migration + `fallbackToDestructiveMigration()`：任何 schema 变更静默清空全部本地数据；`ExerciseSeeder` 已专门为清库写了重灌逻辑，说明清库已是现实 |
| 2 | `AISettingsViewModel.kt:47-53,108-130` | init 回填竞态仍在：DataStore+Room 首读期间表单可交互（Screen 只有顶部进度条），用户输入会被回填清空；等待期间手动切 provider 会被 init 强切回去；onProviderSelected 快速连点 A→B 也会被旧协程覆写 |
| 3 | `AISettingsViewModel.kt:165-210,224-256` | 拉取模型/测试连接完成回调不校验 `selectedTypeState`：慢请求期间切换 provider，A 的模型列表/测试结果渲染进 B 表单，可能把 A 的模型保存到 B |
| 4 | `ChatScreen.kt:122-150` | 消息列表无 key（`ChatMessage` 无稳定 id）且**无自动滚动到底**：向上翻过历史后 AI 回复与"AI 正在思考…"都渲染在屏幕外，核心对话体验缺陷 |
| 5 | `MainActivity.kt:89` | 根页（Today）系统返回时 `removeLastOrNull()` 清空唯一 entry → 回退栈为空、白屏且 Activity 不退出；应 `if (removeLastOrNull() == null) finish()` |
| 6 | `SeedOrchestrator.kt:40-52` + `MainViewModel.kt:46-50` | 种子异常在 finally 之后继续上抛，`viewModelScope.launch` 无 catch → 应用启动即崩溃（fail-open 只放行了 completed 标记，没放异常） |
| 7 | `WorkoutPlanSeeder.kt:43-48` | 缺 ExerciseSeeder 那种"版本已最新但表为空"的重灌保护：destructive 清库后 workout_plans 永久缺失且永不重灌，`active_plan_id` 变悬空 |

## 二、中危 Bug

| # | 位置 | 问题 |
|---|------|------|
| 8 | `relation/*.kt` + `model/Workout.kt:24-37` | @Relation 无 orderBy + 领域模型丢弃 `sortOrder`/`setNumber` → 动作/组顺序未定义；`WorkoutRepository.update` 按列表位置重写编号会漂移 |
| 9 | `PlanConverters.kt:20-25` | JSON decode 无 try/catch，一行脏数据崩整条 `getAllPlansWithDetailsFlow`（与 ExerciseConverters 风格不一致） |
| 10 | `ExerciseDao.kt:24-33` | `@Insert(REPLACE)` 触发 `exercise_logs.exerciseKey` 外键 SET_NULL（ExerciseLogEntity.kt:27-32）：种子重灌时历史日志动作关联被静默断开——补 Migration 后立即显现 |
| 11 | `WorkoutRepository.kt:39-43` | `workoutDao.insert` 为 IGNORE 冲突返回 -1，但注释承诺 -1 后仍 `insertChildren(-1)` → 子行 FK 异常崩溃，契约与实现矛盾 |
| 12 | `ProfileViewModel.kt:61-93` | onSave 无 isSaving 入口守卫：Main.immediate 下同帧双击 → 双 insert（回填修复挡不住双击），user_profiles 重复行 |
| 13 | `WorkoutViewModel.kt:16-71,105-115` + `WorkoutScreen.kt` | 56 行注释死代码；insert/delete 无 try/catch（踩中 #11 即崩）；`onDeleteWorkout` 无任何触发点 |
| 14 | `TodayViewModel.kt:123,159-236` | 主链无整体 catch：任一 Room 流异常 → 永久卡 loading；全量 `allWorkouts` 常驻 + 主线程 4 模式全量重算，数据量大掉帧 |
| 15 | `AIProviderConfigRepository.kt:182-184` | `activeProvider` 仅对 DataStore ID 响应式，Room 侧改动不可见（KDoc 自称应 flatMapLatest 到 Room Flow 却没做）；与 WorkoutPlanRepository.activePlan 正确写法成反例 |
| 16 | `AISettingsViewModel.kt:192-199` | onFetchModels 在配置从未保存时"拉取即落库"（含表单 apiKey/model 的 tempConfig），与保存按钮语义冲突 |
| 17 | `WorkoutScreen.kt:43-73` | 无 Scaffold/TopAppBar/insets：edge-to-edge 下列表顶到状态栏；无返回入口；仅"日期+感受"占位实现 |
| 18 | `DataImportScreen.kt:253-266` | 扫描结果在 verticalScroll Column 里 forEach 全量组合，大文件夹首帧卡顿 |
| 19 | `ReminderScreen.kt:90`、`AISettingsScreen.kt:164,722` | 弹窗/时间选择态用 remember 而非 rememberSaveable，旋转即丢 |
| 20 | `AISettingsScreen.kt:353-368` | onClick 里组装 `AIProviderConfig` 领域对象（含 trim/判空/isPreset），违反 CLAUDE.md「表单状态在 ViewModel」 |
| 21 | `AISettingsScreen.kt:739-740` | `menuAnchor(PrimaryNotEditable)` 与注释"可手动输入模型名"矛盾，自定义模型名输入疑似失效 |
| 22 | `AIChatRepository.kt:126-129` | 网络错误未映射：HttpException/SocketTimeout/SerializationException 原文直接展示给用户 |
| 23 | `AIProviderConfigRepository.kt:99-100` | delete 前无谓执行 `toEntity()` 加密，Keystore 异常会阻断删除 |
| 24 | `MarkdownFileScanner.kt:82-83` | `getColumnIndexOrThrow` 在逐文件 try 之外，一个异常炸掉整个扫描 |
| 25 | `MockStatsHistorySeederTest.kt`（androidTest） | 零断言、向**正式库**灌一年假数据、`runBlocking`、依赖手动双 APK——混在 androidTest 里会被 CI 批量误跑污染真实数据 |
| 26 | 网络序列化层零测试 | FakeAIApi 直接实现接口，绕过 Retrofit+kotlinx.serialization+DTO 全链路，线上格式漂移时测试全绿生产挂 |
| 27 | `DataImportViewModel`/`MarkdownFileScanner` 无测试 | 导入去重状态机（数据入口关键路径）零覆盖 |
| 28 | `app/build.gradle.kts:72,87-91` | **5 个未使用依赖**：google-adk-core-android、google-adk-processor、coil-compose、coil-gif、material（grep 全 src 无 import） |
| 29 | `app/build.gradle.kts:54-56,66,71` | Compose BOM 被显式钉版架空；`androidx-compose-ui`（BOM）与 `androidx-ui`（显式 1.11.1）是同一 artifact 声明两次 |
| 30 | `ui/theme/Type.kt` + res/font | google_sans 三个 ttf 从未被引用（死资源）；Typography 用默认字体，字号靠各组件 `.copy(fontSize)` 散落覆盖 |
| 31 | `res/values/strings.xml` | 仅 app_name 一条；全部页面顶栏标题硬编码英文 + 正文中文，同页中英混排，无法本地化 |

## 三、低危 / 打磨项

- `ChatScreen.kt:153-162`：输入 Row 无 navigationBarsPadding/imePadding，edge-to-edge 下可能被手势条遮挡。
- `StackedSnackbarHost.kt:173`：硬编码"关闭"；7 个设置页复制粘贴同一套 ~60 行"双标题滚动/吸附"脚手架，无公共组件。
- `MetricCard.kt:134-143`：水波 infiniteTransition 无可见性门控，卡片滚出屏幕仍逐帧动画。
- `TodayScreen.kt:1111` `todayDateLine()`、`MetricCard.kt:597-666` `MetricDashboardSection`：死代码。
- `ReminderScreen.kt:250`：WorkManager 未接入，开关/时间仅持久化无实际效果（UI 误导）。
- `WeekProgressCalculator.kt:158-167`：totalWorkingSets 在目录解析前累加，解析失败动作计入"有效组"，口径不一致。
- `CoachInsightBuilder.kt:57-58`：`days <= 0L` 对未来日期误判"今天已练"。
- `WorkoutDao.kt`：同日多训练 `ORDER BY date DESC` 无 id tiebreak；`getBySourceFileName` 无索引（导入去重全表扫描）。
- `ExerciseDao.kt:83`：`LIKE '%'||:muscle||'%'` 子串匹配，当前 19 枚举无实际误配，未来新增枚举即踩。
- `LocalDateConverters.kt`：`LocalDate.parse` 无容错。
- `@Update` 返回值不统一（多数 Unit，仅 updatePlan 返回 Int）；`UserProfileDao` REPLACE+autoGenerate 组合 + `getFirst` 无 ORDER BY。
- `ExerciseEntity.kt:31` 主键默认 `""`；`ExerciseSeedMapper` primaryMuscles+secondary 未去重可能虚高 isCompound。
- `KeystoreManager.kt:35-52`：首次建钥非线程安全（并发 generateKey）；encrypt 无异常边界。
- `AIModule.kt:39-45`：DEBUG 下 BODY 日志会打印 `Authorization` 头（含 API key），建议 `redactHeader`。
- `ProviderType.kt:55-56`：CUSTOM 相对路径含 query 时被整体编码；不强制 HTTPS。
- `StatsViewModel.kt:114-123,139-148`：体重弹层预填竞态（同 H1 型）；catch(Exception) 吞 CancellationException（与 ProfileViewModel 风格不一致）。
- `TodayViewModel.kt:159-178`：displayMode 双接线冗余；`ChatUiState.isLoading`、`TodayUiState.errorMessage` 死通道。
- `gradle.properties:28,36`：`configureondemand`（已弃用）、`org.gradle.tooling.parallel`（非标准属性）。
- `KeyGenParameterSpec.kt` stub：Builder 方法空实现，未来 Keystore 用新 API 时单测 NoSuchMethodError 且编译期不暴露。
- `TodayViewModelTest`/`StatsViewModelTest` 用 `LocalDate.now()` 造夹具，结果随运行日期漂移（建议注入 Clock）。

## 四、架构异味

1. **[高] AISettingsViewModel 状态碎片化**：6 个 MutableStateFlow + 4 段 combine 链，首段构造占位完整 UiState 再被后段覆盖；与 H2/H3 竞态互为因果。建议合并单一 data class + `update {}`。
2. **[高] 表单领域对象在 UI 组装**：AISettingsScreen onClick 里 new AIProviderConfig；`onFetchModels(baseUrl, customEndpoint)` 回传 VM 本已持有的值——双状态源，trim 逻辑散落两处。
3. **[中] 一次性事件用 StateFlow + onXxxShown 手动清除**：successMessage/fetchResult/lastResult/savedTick 靠 LaunchedEffect 配合，进程重建/多收集者时易丢，是 v1 可接受取舍但边界要明确。
4. **[中] Repository 纯透传**：Exercise/UserProfile/BodyMetric 全部一行透传 DAO，无 Flow/错误处理/业务逻辑，未形成单源事实层。
5. **[中] 跨仓库链式依赖**：AIChatRepository → AIProviderConfigRepository、CoachInsightRepository → 两者，层级渐混；AIChatRepository 是具体类不可 mock。
6. **[中] 设置系 7 页复制粘贴脚手架**：双标题滚动/吸附/顶栏渐变 ~60 行重复 7 份，Snackbar 也未统一封装。
7. **[低] 数据完整性约束下沉 UI**：`isPreset` 是否可删由 UI 控制（AIProviderConfigRepository 自承）；L6 的"拉取即落库 isPreset=true"正是该下沉的实例。
8. **[低] 文案与模型耦合**：BodyPart 中文 displayName 在 WeekProgressCalculator / Prompt / 多处重复定义。
9. **[低] 无分页 / 跨零点不刷新**：`getAllWithDetails()` 全量加载；today 在 VM 创建时固定（文档已标注为 v1 取舍）。
10. **[低] 无 CI**：48 个单测类 + 7 个 androidTest 只在本机跑过；无 release 签名，`assembleRelease` 产物不可安装。

## 五、已确认健康（排查后排除）

- **KeystoreManager AES-GCM 用法正确**：随机 IV 无复用、tag 128 位、IV 前置、decryptOrNull 容错降级设计良好；stub 常量与真实 API 一致。
- **Room 关系设计**：无 N+1（@Relation 批量 IN 查询）、@Transaction 全覆盖、外键/索引（CASCADE/SET_NULL）配置正确。
- **AI 增强链**：`distinctUntilChangedBy(fingerprint)` + `flatMapLatest` 取消在途请求正确；CancellationException 在 AIChat/CoachInsight 仓库正确上抛；指纹含 today 跨天必刷新。
- **Stats 档位竞态**：flatMapLatest + PeriodWorkouts 原子对，快速连点末次胜出，无错帧。
- **纯逻辑层**：WeekProgressCalculator / Stats*Builder / TrainingLevelCalculator / TodayPlanAssembler 无除零、周界、时区 bug，全部 JVM 可测。
- **生命周期**：全 UI `collectAsStateWithLifecycle`；ViewModel 无 Activity 泄漏（DataImportViewModel 持 @ApplicationContext 合法）；MainViewModel Eagerly+onCompletion 防卡 Splash 正确。
- **测试质量整体高于一般项目**：映射器全字段往返、DAO/仓库用内存 Room + 真实临时 DataStore、ViewModel 测试锁死竞态与原子发射契约（ChatViewModelTest 并发发送去重、TodayViewModelTest 首帧门控、StatsViewModelTest 末次胜出）；PresetPlansTest 数据完整性断言是亮点。
- **工作区修复（未提交，建议尽快提交）**：① `WorkoutPlanDao.savePlanWithSessions` REPLACE→update-or-insert + session diff + completedWorkoutId 合并（WorkoutPlanDao.kt:147-167，修复了 REPLACE 触发 CASCADE 清空训练日的致命问题）；② ProfileViewModel insert 回填 existingId（修复重复记录）。

## 六、优先修复路线图

### P0 — 立即（当天）
1. 提交工作区 5 个文件的修复（data 层两个高危修复别丢）。
2. 修 `WorkoutPlanDaoTest` 6 处 `insertPlan` → 恢复 androidTest 编译（顺手调整"REPLACE 替换"断言为新语义）。

### P1 — 止血（1~2 天）
3. Room Migration 体系：`exportSchema=true` + 6→7 Migration + 移除 destructive fallback（#1）。
4. AISettingsViewModel 竞态：init 回填加 dirty 标志 / onProviderSelected 返回后校验 selectedType、拉取/测试回调校验 `selectedTypeState`（#2 #3）。
5. ChatScreen 滚动到底 + 消息 id + LazyColumn key（#4）。
6. MainActivity 根返回空栈守卫 `finish()`（#5）。
7. SeedOrchestrator 吞异常 + WorkoutPlanSeeder 清库重灌保护（#6 #7）。

### P2 — 数据层加固（本周）
8. @Relation orderBy 或领域模型补 sortOrder/setNumber + Mapper 重排（#8）。
9. PlanConverters/ExerciseConverters/LocalDateConverters 统一容错；ExerciseDao 重灌改 IGNORE/UPDATE（#9 #10）；WorkoutRepository -1 短路（#11）。
10. 网络错误统一映射（#22）；activeProvider 改 flatMapLatest + Room Flow（#15）；deleteById 直删（#23）；Scanner 列索引入 try（#24）。

### P3 — 工程化
11. 构建清理：删 5 个未用依赖、修 BOM 重复声明、`git rm --cached hs_err_pid1192.log` + `.gitignore *.log`、清理 `gradle.properties` 弃用项。
12. CI：unitTest + lint + connectedDebugAndroidTest（先修 P0 才能绿）；release 签名 + 开启 R8（补 keepRules）。
13. 字符串资源化 + 字体接入或删除；设置页脚手架抽公共组件。
14. 移出/标注 MockStatsHistorySeederTest；补 DTO 反序列化夹具测试与 DataImportViewModel 测试。

### P4 — 功能兑现
15. WorkManager 训练提醒（ReminderScreen 已上线待激活）。
16. Chat 流式输出 + 可取消；WorkoutScreen 补全（顶栏/insets/删除接线/死代码清理）。
17. 注入 Clock 修日期漂移测试；大列表 key 补齐（Workout/Chat/DataImport）。
