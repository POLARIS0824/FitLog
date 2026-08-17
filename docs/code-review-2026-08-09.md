# FitLog 全面代码审查报告（2026-08-09）

> 审查范围：data 层（Room/Repository/Retrofit/文件导入）、feature 与 ui 层（Compose/ViewModel/Navigation3）、构建配置与安全。
> 所有路径基于 `app/src/main/java/com/example/fitlog/`，行号为审查时快照。

---

## 一、高严重级 Bug（建议立即修复）

### 1. 数据库升级即清空全部用户数据
- `di/DatabaseModule.kt:39` 使用 `fallbackToDestructiveMigration()`；`AppDatabase.kt` 已迭代到 version=6 且 `exportSchema=false`，**无任何 Migration**。
- 影响：老用户每次 schema 变更，workouts / set_logs / plans / profile 全部静默清空。对以本地数据为核心的健身 App 是致命隐患。
- 修复：打开 `exportSchema=true`，为 6→7 起编写 Migration，逐步移除 destructive fallback。

### 2. `OnConflictStrategy.REPLACE` + 外键 CASCADE = 静默清数据
- `WorkoutPlanDao.kt:25-26` 的 `insertPlan` 用 REPLACE，而 `PlannedSessionEntity.kt:30-35` 的 `planId` 外键是 `onDelete = CASCADE`。
- SQLite 的 REPLACE = DELETE + INSERT：重存已有计划时先级联删除全部 `planned_sessions`（**含用户积累的 `completedWorkoutId` 完成记录**），再插回调用方传入的数据——不带完成标记即永久丢失。`WorkoutPlanSeeder` 版本升级重灌必然踩中。
- 修复：改为先 UPDATE、不存在再 INSERT；或保存计划时显式 diff 子表。

### 3. Release 构建无任何保护
- `app/build.gradle.kts:28-33`：`release { optimization { enable = false } }`——关闭 R8 混淆与资源压缩；**不存在 `proguard-rules.pro`，也无 `signingConfigs`**，release 包无法正式签名。
- 影响：反编译可直接看到全部网络协议与 AI 提示词逻辑；无法上架。
- 修复：开启 minify + 补 ProGuard 规则（Room/Retrofit/序列化 keep 规则）+ 配置签名。

### 4. 个人资料重复插入
- `ui/settings/profile/ProfileViewModel.kt:79-83`：首次保存走 `insert`（id=0 自增），但 `existingId` 未回填新 id；再次保存又 `insert` 一行，`user_profiles` 累积重复记录，`getFirst()` 只读第一行，用户后续修改"看似丢失"。
- 修复：insert 后回填 `existingId = newId`，或改用 upsert。

---

## 二、中严重级 Bug

| # | 位置 | 问题 |
|---|------|------|
| 5 | `WorkoutPlanDao.kt` `savePlanWithSessions` | 只 REPLACE 插入，从不删除已移除的 session；`deleteSessionsByPlanId` 定义了但**全工程无人调用**。编辑计划删减训练日后旧 session 残留，进度统计失真 |
| 6 | `relation/WorkoutWithExerciseLogs.kt:16-31` | `@Relation` 加载无排序保证（不支持 ORDER BY），`WorkoutMapper` 也未按 `sortOrder`/`setNumber` 重排；DAO 里的 ORDER BY 在 relation 路径完全失效，动作/组顺序不稳定 |
| 7 | `PlanConverters.kt:24` | JSON 反序列化无 try/catch，一行脏数据让整个 `getAllPlansWithDetailsFlow` 崩流（对比 `ExerciseConverters.kt:68-72` 有兜底，风格不一致） |
| 8 | `AISettingsViewModel.kt:47-53` | init 回填竞态：挂起等 `activeProvider.first()` 后整体覆写表单；DataStore 首读有延迟，用户打开页面即输入 API Key 会被回填清空 |
| 9 | `AISettingsViewModel.kt:165-256` | 拉取模型/测试连接结果跨 provider 串台：慢请求返回后直接写全局 `modelState`，等待期间用户切换 provider，A 服务商的模型列表会显示在 B 的表单里 |
| 10 | `MainActivity.kt:102` + `WorkoutScreen.kt:42-73` | Workout 页无顶栏返回按钮，与其余 7 个页面不一致，只能靠系统手势返回 |
| 11 | `hs_err_pid1192.log` | 2.1MB JVM 崩溃日志被提交进 git；内容是 Gradle daemon native OOM（本机 `-Xmx8192m` + Kotlin daemon 4GB 配置激进）。应从 git 移除并加入 .gitignore |
| 12 | `gradle/libs.versions.toml` + `app/build.gradle.kts:71` | 声明 Compose BOM 却又显式钉 `ui/runtime/foundation` 版本，且 `androidx-ui` 与 BOM 管理的 artifact 重复声明，BOM 实际被架空 |
| 13 | `app/build.gradle.kts:72,87-91` | 4 个零 import 的未使用依赖：`material:1.14.0`、`coil-compose`、`coil-gif`、两个 `google-adk` 库（adk-processor 即使要用也应走 ksp） |
| 14 | Reminder 功能 | WorkManager 从未接入（AGENTS.md TODO），`ReminderScreen.kt:250` 自承"提醒调度将在 WorkManager 接入后生效"——UI 已上线但功能无效 |

## 三、低严重级 Bug（批量顺手修）

- `WorkoutRepository.kt:37-41`：注释承诺冲突 IGNORE 返回 -1，实际仍用 -1 调 `insertChildren` 必抛 FK 异常——注释与实现矛盾。
- `AIProviderConfigRepository.kt:182-184`：`activeProvider` 只对 DataStore ID 做 map，Room 侧一次性 `getById`，编辑激活配置不触发重发（非真正响应式）；`CoachInsightRepository.kt:42` 同理。
- `WorkoutDao.kt:55` 按 `sourceFileName` 查询但 `WorkoutEntity.kt:26` 只索引了 `date`（导入去重逐文件调用，数据量大后变慢）。
- `ExerciseDao.kt:83` `LIKE '%'||:muscle||'%'` 依赖枚举名互不为子串，未来加 `BACK` 会误配 `UPPER_BACK`。
- `AIProviderConfigRepository.kt:100`：delete 前无谓地执行 API key 加密（`toEntity()`），加密失败会阻断删除。
- `MarkdownFileScanner.kt:82-89`：`getColumnIndexOrThrow` 与 `fileName.endsWith`（display name 可为 null）在 try 块外，异常炸掉整个扫描而非记入 failures。
- `ChatScreen.kt:123` / `WorkoutScreen.kt:61`：LazyColumn `items` 无 key；`ChatMessage` 连 id 字段都没有，将来支持删除/流式更新同一位置必错乱。
- `DataImportScreen.kt:250-267`：扫描结果在 `verticalScroll` Column 里 forEach 全量组合，大文件夹掉帧，应换 LazyColumn。
- `ReminderScreen.kt:90`、`AISettingsScreen.kt:164,722`：sheet/对话框用 `remember` 而非 `rememberSaveable`，旋转后状态丢失。
- `WorkoutViewModel.kt:16-71`：约 55 行注释掉的死代码；`insertWorkout/deleteWorkout` 无 try/catch 失败静默；`onDeleteWorkout` 回调无任何触发点，成摆设。

## 四、架构异味

1. **[中] Screen 组装领域对象**：`AISettingsScreen.kt:353-367` 在 onClick 里 new `AIProviderConfig`（含 trim/判空）传给 ViewModel，违反 AGENTS.md「表单状态在 ViewModel」约定；`onFetchModels(baseUrl, customEndpoint)` 同样由 Screen 回传 ViewModel 本已持有的值。
2. **[中] AISettingsViewModel 状态分散**：6 个 `MutableStateFlow` + 4 段 combine 链，首段构造完整 UiState 填占位值再被后段覆盖，多余分配且难读。建议合并为单一 `data class` + `update {}`。
3. **[中] 数据完整性约束下沉到 UI**：`AIProviderConfigRepository.kt:96-98` 自承"是否允许删除预设由 UI 控制"，任何新调用方都可能误删预设配置。
4. **[低] Repository 纯透传**：`ExerciseRepository`、`UserProfileRepository` 全部方法一行透传 DAO，无 Flow/错误处理/业务逻辑，未形成单源事实层。
5. **[低] Repository 链式依赖**：`AIChatRepository → AIProviderConfigRepository`、`CoachInsightRepository → AIChatRepository`，需警惕层级渐混。
6. **[低] Chat 无流式无取消**：`AIChatRepository.chat` 是一次性 suspend Result，请求 DTO 无 `stream` 字段；`ChatViewModel.send()` 的 Job 未保存，发送中无法取消。
7. **[低] 缺 CI**：无 .github/workflows，测试只在本地跑。

## 五、确认健康（排查后排除）

- **KeystoreManager 加密实现正确**：随机 IV、KeyStore 持钥、无 IV 重用；仅 catch Exception 过宽是小瑕疵。
- 全局无裸 `collectAsState()`，均为 `collectAsStateWithLifecycle`；无 ViewModel 持 Activity Context。
- Navigation3 回退栈操作正确，NavKey 均 @Serializable，无 ViewModel 泄漏。
- Hilt 作用域正确，昂贵对象均 @Singleton；release 已关闭 BODY 日志。
- Manifest 干净（仅 INTERNET、无明文流量、备份规则正确排除加密文件）。
- 无硬编码真实密钥；`local.properties` 未被 git 跟踪。
- 单测 50+ 个文件，覆盖 Keystore/Repository/ViewModel/Mapper，不缺单测。

---

## 六、后续开发 Roadmap（建议按此顺序写）

### Phase 0 — 止血（1~2 天，纯修 bug）
1. Room Migration 体系建设：`exportSchema=true` + 6→7 Migration + 移除 destructive fallback（问题 1）
2. 修 REPLACE+CASCADE 吞完成记录（问题 2）与孤儿 session 清理（问题 5）
3. 修 ProfileViewModel 重复 insert（问题 4）
4. Release 构建：minify + proguard-rules.pro + signingConfigs（问题 3）

### Phase 1 — 数据层加固
5. Relation 加载后按 `sortOrder`/`setNumber` 重排；补 `sourceFileName` 索引；修 LIKE 肌肉匹配
6. PlanConverters 容错；统一 Converters 风格
7. Repository 层整改：补错误处理约定（Result/Flow 封装）、预设配置删除保护下沉到数据层
8. `activeProvider` 改真正响应式（Room Flow + DataStore flatMapLatest）

### Phase 2 — 兑现已上线但无效的功能
9. **接入 WorkManager**：训练提醒调度（ReminderScreen 已上线待激活）——这是 AGENTS.md 里挂着的最大 TODO
10. **AI 聊天流式输出**：DTO 加 `stream`、SSE/分块解析、ChatViewModel 保存 Job 支持取消；给 `ChatMessage` 加 id 并补 LazyColumn key
11. AI 设置页重构：表单状态全部收进 ViewModel（修竞态问题 8、串台问题 9、异味 1/2）

### Phase 3 — 体验与工程化
12. Workout 页补返回按钮；`onDeleteWorkout` 要么接上 UI 要么删除；清理 55 行死代码
13. DataImport 结果改 LazyColumn；rememberSaveable 批量补齐
14. 构建清理：删 4 个未用依赖、统一 Compose BOM、移除崩溃日志并加 .gitignore
15. 加 GitHub Actions CI：assemble + unit test + lint
16. 训练记录编辑/删除流程补全（当前 WorkoutScreen 只有只读列表）

### Phase 4 — 新功能候选（按价值排序）
17. 训练统计页：基于已有 workouts/exercise_logs/set_logs 三级数据做容量/PR/趋势图表（数据已齐，纯 UI 工作，portfolio 展示效果最好）
18. 训练计划执行引导：激活计划 → 当日 session → 边练边记（打通 plan 与 workout 两套层级，是产品差异化核心）
19. AI 教练洞察落地：`CoachInsightRepository` 已有雏形，可做成训练后的自动总结卡片
20. 数据导出/备份（Markdown 导出，与已有导入对称）
