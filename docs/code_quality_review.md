# FitLog 代码质量审查报告

> 审查日期：2026-07-29 | 审查范围：全部 130 个 Kotlin 源文件

## 1. 总体评价

项目整体代码质量较高，架构清晰、注释充分、命名规范。主要优点：

- Javadoc 风格注释覆盖所有公开类和关键方法
- Flow 响应式编程模式一致，`WhileSubscribed(5000)` 最佳实践贯穿
- 错误处理策略统一（`CancellationException` 向上传播 + `Result` 包裹）
- 关注点分离良好：ViewModel 只做装配，业务计算委托纯函数 Builder
- 安全设计到位：API Key 加密存储、Release 日志脱敏

## 2. Bug 与潜在缺陷

### 2.1 高严重度

| # | 位置 | 问题 | 影响 |
|---|------|------|------|
| 1 | `AIProviderConfigMapper.toModel()` | `ProviderType.valueOf(type)` 无 try-catch | 数据库中 type 字段被篡改或新增枚举值向后不兼容时直接崩溃 |
| 2 | `ExerciseConverters.toMuscleList()` | `Muscle.valueOf(it)` 无容错 | 种子数据或未来版本引入新肌群枚举时，旧版本 App 读取崩溃 |
| 3 | `ExerciseConverters.toBodyPart()` | `BodyPart.valueOf(value)` 无容错 | 同上，非法枚举值直接 `IllegalArgumentException` |
| 4 | `DatabaseModule.provideDatabase()` | `fallbackToDestructiveMigration()` | 版本升级时用户所有数据被清空（训练记录、配置等） |

### 2.2 中严重度

| # | 位置 | 问题 | 影响 |
|---|------|------|------|
| 5 | `ChatViewModel.send()` | 对话历史无长度限制 | 长对话超出模型 context window → API 返回错误 |
| 6 | `TodayViewModel` | `today = LocalDate.now()` 在构造时固定 | 长时间挂起（跨零点）后日期不准，本周进度错误 |
| 7 | `MarkdownFileScanner.parseDateFromFileName()` | 手动枚举后缀大小写变体 | `.MD`/`.Md`/`.mD` 覆盖了，但 `.Md` 等组合可能遗漏；应使用 `substringBeforeLast(".")` |
| 8 | `WorkoutRepository.insert()` | `OnConflictStrategy.IGNORE` 返回 -1 时仍调用 `insertChildren` | 冲突时 workoutId = -1，子行外键指向不存在的行（Room 不校验外键完整性时会写入脏数据） |
| 9 | `AISettingsViewModel.onFetchModels()` | 未保存配置时直接 `insert(tempConfig)` | 用户仅想拉取模型列表，但副作用是写入了完整配置（含可能不完整的 baseUrl） |
| 10 | `CoachInsightRepository` | DataStore 缓存仅存一条 | 多用户/多配置场景下缓存互相覆盖（当前单用户可接受） |

### 2.3 低严重度

| # | 位置 | 问题 | 影响 |
|---|------|------|------|
| 11 | `WorkoutEntity.toModel()` | 创建 `exercises = emptyList()` 的 Workout | 若误用此扩展（而非 `WorkoutWithExerciseLogs.toModel()`），下游拿到空动作列表 |
| 12 | `ExerciseDao.getByMuscle()` | LIKE 匹配逗号字符串 | "PECTORALIS" 会匹配到 "PECTORALIS_MAJOR"（子串包含） |
| 13 | `WorkoutPlanDao` | `getAllPlansWithDetails()` 和 `getAllPlansWithDetailsFlow()` 重复 | 维护成本，无功能影响 |
| 14 | `SystemPrompt.SYSTEM_PROMPT` | 内容仅 "You are a professional fitness coach" | Chat 页 AI 缺乏用户上下文，回复质量有限 |

## 3. 内存泄漏与性能风险

### 3.1 内存相关

| # | 风险点 | 分析 | 结论 |
|---|--------|------|------|
| 1 | ViewModel 中的 Flow 链 | 全部使用 `viewModelScope`，ViewModel 销毁时自动取消 | ✅ 安全 |
| 2 | `shareIn(replay=1)` | 共享流持有最近一次发射值的引用 | ✅ 正常，单条数据量小 |
| 3 | `allWorkouts` 全量加载 | `getWorkouts()` 返回全部训练记录 | ⚠️ 数据量大时内存压力 |
| 4 | `rawContent` 字段 | 每条训练存储完整 Markdown 原文 | ⚠️ 长期积累占用存储 |
| 5 | Compose 重组 | `TodayScreen` 1257 行单文件 | ⚠️ 大 composable 增加重组范围 |

### 3.2 性能相关

| # | 风险点 | 分析 | 建议 |
|---|--------|------|------|
| 1 | `WeekProgressCalculator` 每次 combine 都计算所有模式 | 3 种模式 × 全量数据遍历 | 数据量小时可接受；大时考虑缓存 |
| 2 | `CoachInsightPrompt.summarizeWorkout()` 每次构建 catalog Map | `associateBy` 在循环外已做，OK | ✅ |
| 3 | Room 全量查询无分页 | `getAllWithDetails()` 加载全部 | 引入 Paging3 |
| 4 | 种子数据每次启动检查 | `getCount()` 查询极快 | ✅ 可接受 |

## 4. 异常处理审查

### 4.1 做得好的地方

- **CancellationException 统一向上传播**：所有 Repository 的 catch 块都先判断 CancellationException
- **AI 解析容错**：`parseCoachInsight` 容忍 code fence、多余文字、空字段
- **种子 fail-open**：`SeedOrchestrator` 的 finally 块确保即使种子失败也放行 UI
- **DataStore 读取兜底**：`MainViewModel.appearance` 的 `onCompletion` 确保异常时也放行

### 4.2 需要改进的地方

| # | 位置 | 问题 | 建议 |
|---|------|------|------|
| 1 | `ExerciseConverters` | 枚举 valueOf 无保护 | 添加 `entries.firstOrNull { it.name == value } ?: DEFAULT` |
| 2 | `AIProviderConfigMapper` | `ProviderType.valueOf(type)` 无保护 | 同上，降级为 `CUSTOM` |
| 3 | `WorkoutRepository.insert()` | 未检查返回的 workoutId 是否有效 | `if (workoutId == -1L) return@withTransaction -1L` |
| 4 | `MarkdownFileScanner` | `contentResolver.query()` 返回 null 时静默跳过 | 应记录日志或返回失败 |
| 5 | `ProviderType.buildUrl()` | 抛 `IllegalArgumentException` | 调用方（AIChatRepository）会 catch，但错误信息对用户不友好 |

## 5. 代码风格与规范

### 5.1 一致性评价

| 维度 | 评分 | 说明 |
|------|------|------|
| 命名规范 | ⭐⭐⭐⭐⭐ | 包名/类名/函数名语义清晰，遵循 Kotlin 惯例 |
| 注释覆盖 | ⭐⭐⭐⭐⭐ | 所有公开类/方法有 KDoc，关键设计有决策说明 |
| 包结构 | ⭐⭐⭐⭐ | 按层划分清晰，feature 内聚良好 |
| 状态管理 | ⭐⭐⭐⭐⭐ | Flow 模式统一，StateFlow 暴露一致 |
| 错误处理 | ⭐⭐⭐⭐ | 网络层统一，TypeConverter 层有遗漏 |
| 测试覆盖 | ⭐⭐⭐ | 有 DAO 测试和 UI 测试，但 Repository/ViewModel 测试不足 |

### 5.2 代码异味

| # | 位置 | 异味 | 建议 |
|---|------|------|------|
| 1 | `WorkoutViewModel` | 大段注释掉的旧代码（~50 行） | 删除，依赖 VCS 历史 |
| 2 | `TodayScreen.kt` | 1257 行单文件 | 拆分为多个子 composable 文件 |
| 3 | `AISettingsScreen.kt` | 868 行单文件 | 同上 |
| 4 | `MetricCard.kt` | 787 行单组件 | 拆分为独立的卡片变体 |
| 5 | `TodayViewModel` | 378 行，含 3 个 private data class | 可提取为独立文件 |
| 6 | `Prompt.kt` | `SystemPrompt` 和 `CoachInsightPrompt` 混在一个文件 | 拆分为独立文件 |

## 6. 安全审查

| # | 检查项 | 状态 | 说明 |
|---|--------|------|------|
| 1 | API Key 存储 | ✅ | AES-GCM + Android Keystore，密文存 Room |
| 2 | 日志脱敏 | ✅ | Release 关闭 HttpLoggingInterceptor |
| 3 | 网络安全 | ✅ | HTTPS（由 baseUrl 保证） |
| 4 | 输入校验 | ⚠️ | 体重有范围校验；API Key/URL 无格式校验 |
| 5 | SQL 注入 | ✅ | Room 参数化查询，无原始 SQL 拼接 |
| 6 | 导出安全 | ✅ | `exportSchema = false` 不暴露 schema（但影响迁移） |
| 7 | 权限最小化 | ✅ | 仅 SAF（用户主动授权），无危险权限 |

## 7. 架构改进建议（按优先级）

### P0（发布前必须）

1. **移除 `fallbackToDestructiveMigration()`**：编写 v5→v6 等迁移脚本，或至少改为 `fallbackToDestructiveMigrationOnDowngrade()`
2. **TypeConverter 枚举容错**：所有 `valueOf()` 调用添加降级逻辑
3. **WorkoutRepository.insert() 冲突处理**：检查返回值 -1，避免脏数据

### P1（近期迭代）

4. **Chat 对话历史管理**：添加 token 计数或滑动窗口截断
5. **网络重试机制**：OkHttp Interceptor 指数退避（429/5xx）
6. **HTTP 错误码细分**：401→Key 过期、429→限流、5xx→服务不可用
7. **分页加载**：训练历史引入 Paging3

### P2（中期优化）

8. **流式 AI 响应**：Chat 页 SSE 流式输出，改善长回复体验
9. **跨零点刷新**：监听日期变化或 WorkManager 定时重建 ViewModel
10. **训练-计划自动联动**：记录训练时自动匹配并标记计划课次
11. **大文件拆分**：TodayScreen / AISettingsScreen / MetricCard 拆分子组件

### P3（长期演进）

12. **引入 UseCase 层**：跨 Repository 协调逻辑（训练完成→计划标记→统计更新）
13. **模块化**：feature 包升级为独立 Gradle module，编译隔离
14. **SystemPrompt 增强**：注入用户资料/训练上下文，提升 Chat 质量
15. **导入去重增强**：文件名 + content hash 双重判断

## 8. 测试覆盖分析

| 测试类型 | 现有 | 缺失 |
|----------|------|------|
| DAO 单元测试 | ✅ 5 个 DAO 测试 | BodyMetricDao、ExerciseLogDao、SetLogDao |
| UI 测试 | ✅ ChatScreen、SettingsComponents | TodayScreen、StatsScreen |
| Repository 测试 | ❌ | 全部 9 个 Repository |
| ViewModel 测试 | ❌ | 全部 ViewModel |
| Mapper 测试 | ❌ | 枚举容错、加密解密 |
| 集成测试 | ✅ MockStatsHistorySeeder | 导入链路、AI 链路 |

**建议优先补充**：
- `AIProviderConfigMapper` 的枚举容错测试
- `WorkoutRepository.insert()` 冲突场景测试
- `CoachInsightRepository` 缓存命中/未命中测试
- `TodayViewModel` 的多流组合正确性测试
