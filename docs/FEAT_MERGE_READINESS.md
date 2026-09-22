# Feat 合入 Dev 验证报告与就绪记录

本文档记录 `feat` 分支在合入 `dev` 前的完整修复、测试与门禁验证结果。

---

## 1. 分支与提交历史

### 分支状态
- **源分支**：`feat`
- **最新 Commit**：`a5c8499`（截止 Commit 7）
- **包含的 8 个逻辑阶段提交**：
  1. `588214e` `fix(chat): preserve drafts and recover history after failed clearing`
  2. `d2d2c49` `fix(workout): match planned exercises only by persisted identity`
  3. `da6ec7b` `fix(import): freeze edits while saving drafts`
  4. `5658051` `fix(import): share validation between preview and persistence`
  5. `d8e8ac8` `fix(import): preserve timestamps across markdown round trips`
  6. `9743f95` `test: cover reminder scheduling and Android compatibility`
  7. `a5c8499` `fix(agent): derive weekly frequency from generated sessions`
  8. 本次提交 `docs: record feat merge readiness`

---

## 2. 门禁验证结果

### 2.1 单元测试 (`testDebugUnitTest`)
- **执行命令**：`.\gradlew.bat testDebugUnitTest`
- **测试总数**：534 个
- **失败数**：0 个
- **跳过数**：0 个
- **通过率**：100%
- **测试报告路径**：`app/build/reports/tests/testDebugUnitTest/index.html`

### 2.2 代码检查 (`lintDebug`)
- **执行命令**：`.\gradlew.bat lintDebug`
- **错误数 (Errors)**：0 个
- **严重问题 (Fatal)**：0 个
- **警告数 (Warnings)**：66 个
- **提示数 (Hints)**：2 个
- **HTML 报告**：`app/build/reports/lint-results-debug.html`
- **XML 报告**：`app/build/reports/lint-results-debug.xml`
- **警告分类说明**：
  - `Correctness` (49):
    - `UnusedAttribute` (1): `enableOnBackInvokedCallback` 仅在 API 33+ 生效，向下安全忽略。
    - `AndroidGradlePluginVersion` (1): 提示 AGP 9.4.1 可用，当前稳定运行在 9.2.1。
    - `GradleDependency` (13) & `NewerVersionAvailable` (10): 第三方依赖有更高版本。
    - `ModifierParameter` (24): 部分 Composable 的 Modifier 参数规范建议，不影响功能与性能。
  - `Security` (2):
    - `TrustAllX509TrustManager` (2): 仅在测试与本地 Mock 服务中使用。
  - `Performance` (17):
    - `ObsoleteSdkInt` (4): 旧版本 SDK 检查冗余。
    - `AutoboxingStateCreation` (2): 状态基本类型自动装箱建议。
    - `UnusedResources` (11): 未引用的图标或字符串资源。

### 2.3 发布包构建 (`:app:assembleRelease`)
- **执行命令**：`.\gradlew.bat :app:assembleRelease`
- **构建状态**：SUCCESSFUL
- **生成产物**：`app/build/outputs/apk/release/app-release-unsigned.apk`
- **R8 混淆优化**：已通过，未出现规则冲突或缺少 Keep 规则问题。

---

## 3. 修复内容汇总

| Commit | 模块 | 核心改动 | 对应测试 |
| :--- | :--- | :--- | :--- |
| 1 | `chat` | 清空会话时保留草稿输入（包括清空过程中的输入）；清空失败按代数（epoch）去重恢复历史 | `ChatViewModelTest` (6 个测试覆盖清空成功/失败/重入/代数过期) |
| 2 | `workout` | 严格按持久化 `plannedExerciseId` 关联计划动作；无 ID 动作不推测目标、不计入计划动作进度，但保留在实际训练记录中 | `WorkoutViewModelTest`, `TodayPlanAssemblerTest` |
| 3 | `import` | 导入编辑草稿在保存匹配中禁用输入与增删改；提供取消并丢弃任务能力；token 守卫防止旧任务提交 | `DataImportViewModelTest`, `ImportEditSheetTest` |
| 4 | `import` | 统一预览与落库的数据清洗逻辑（剔除空白动作名、reps<=0 占位组、无有效组动作）；草稿编辑态保留占位组 | `ImportDraftModelsTest` |
| 5 | `import` | Markdown 导出引入带时区 ISO 8601 时间戳（`开始时间`/`结束时间`）；导入优先使用元数据，支持跨午夜、跨年及跨时区无损往返 | `MarkdownExporterTest`, `WorkoutParseRepositoryTest` |
| 6 | `reminder` | 调度意图契约与真实 WorkManager 行为解耦；补充真实 Android 环境下的排队、取消、自链幂等及 LocalDateConverters 兼容性测试 | `ReminderSchedulingScenariosTest`, `WorkManagerReminderSchedulerAndroidTest`, `LocalDateConvertersAndroidTest` |
| 7 | `agent` | AI 生成训练计划的 `sessionsPerWeek` 缺省时按各周课次数众数推导（平局取大）；支持显式指定优先；课次名称缺省/空白自动补全 | `CreatePlanToolTest` |

---

## 4. 边界声明与已知限制

1. **`DatabaseModule.kt` 隔离说明**：
   工作区中针对 `DatabaseModule.kt` 的 `.fallbackToDestructiveMigration()` 修改严格保持未暂存、未提交状态，未混入任何 commit，也不包含在最终合并分支中。
2. **Room 数据结构与迁移**：
   本次改动未修改 Room 表结构与实体字段（严格 ID 关联使用已有字段），未触碰 `AppDatabase.version`，无需编写新的 Room 迁移。
3. **Android 兼容性**：
   - 最低支持 SDK：API 26 (Android 8.0)。
   - `LocalDateConverters` 保证使用 `LocalDate.of(1970, 1, 1)` 降级，避免 `LocalDate.EPOCH` 在 API < 34 设备上抛出 `NoSuchFieldError`。
