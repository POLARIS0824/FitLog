# Markdown 文件夹批量导入训练日志

## Context

用户需要从外部文件夹批量导入 Markdown 格式的训练日志。`MarkdownParser.preprocess()` 已完成字符串层清洗，现在需要补齐：
1. 通过 Android SAF（Storage Access Framework）访问用户选择的文件夹
2. 扫描其中所有 `.md` 文件，从文件名提取日期
3. 读取内容并调用预处理，存入 Room 数据库
4. 提供 UI 入口展示已导入的日志列表

## 方案

### 1. 数据层 — Markdown 文件扫描

**新增文件**：`app/src/main/java/com/example/myfitness/data/file/MarkdownFileScanner.kt`

- 封装 SAF `DocumentFile` 遍历逻辑
- 方法签名示例：`fun scanFolder(contentResolver: ContentResolver, treeUri: Uri): List<ScannedMarkdown>`
- 过滤 `.md` / `.markdown` 后缀（不区分大小写）
- 使用 `contentResolver.openInputStream()` 读取文件内容
- 调用已有 `MarkdownParser.preprocess()` 清洗文本
- 文件名日期解析：移除后缀后尝试 `LocalDate.parse()`（ISO-8601，如 `2024-01-15.md`）
- 解析失败则跳过该文件，返回结果中标注失败项

**新增 Domain 模型**（如需要，可放在 `domain/model` 或 data 层内部）：
- `ScannedMarkdown(val fileName: String, val date: LocalDate, val content: String)`

### 2. Domain / Repository 层

**修改文件**：
- `app/src/main/java/com/example/myfitness/domain/repository/WorkoutRepository.kt`
- `app/src/main/java/com/example/myfitness/data/repository/WorkoutRepositoryImpl.kt`

在 `importFromMarkdown` 签名中增加 `sourceFileName: String?` 参数，并更新 `WorkoutRepositoryImpl`：
- 将文件名写入 `WorkoutEntity.sourceFileName`
- 保持其他逻辑不变

### 3. UI 层 — 训练日志列表页面

**新增文件**：
- `app/src/main/java/com/example/myfitness/feature/traininglog/TrainingLogScreen.kt`
- `app/src/main/java/com/example/myfitness/feature/traininglog/TrainingLogViewModel.kt`

**`TrainingLogViewModel`**：
- `@HiltViewModel`，注入 `WorkoutRepository` 和 `@ApplicationContext Context`
- 状态：`TrainingLogUiState`（日志列表、导入中、导入结果/错误）
- 方法 `importFromFolder(treeUri: Uri)`：
  1. 调用 `contentResolver.takePersistableUriPermission()` 持久化权限
  2. 调用 `MarkdownFileScanner.scanFolder()` 获取文件列表
  3. 循环调用 `workoutRepository.importFromMarkdown(content, date, fileName)`
  4. 刷新列表状态

**`TrainingLogScreen`**：
- 顶部 `TopAppBar`：标题"训练日志"
- `LazyColumn` 展示已导入日志：日期 + 来源文件名
- FAB 或按钮"导入文件夹"：
  - 使用 `rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree())` 唤起 SAF
  - 回调中将 `treeUri` 交给 ViewModel
- 导入过程显示 `CircularProgressIndicator` 或 Snackbar 提示

### 4. Activity 入口

**修改文件**：`app/src/main/java/com/example/myfitness/MainActivity.kt`

当前 `MainActivity` 直接硬编码 `AITestScreen()`。为了不改动 AI 相关文件，同时让新页面可被访问：
- 在 `MainActivity` 中添加一个底部导航栏（`NavigationBar` / `BottomAppBar`），包含两个 Tab：
  - "AI 测试" → `AITestScreen()`
  - "训练日志" → `TrainingLogScreen()`
- 使用 `rememberSaveable { mutableIntStateOf(0) }` 管理当前选中页
- **不引入 Navigation 3 路由**，保持改动最小，避免学习成本和额外风险

> 注：build.gradle 中已声明 Navigation 3 依赖，但项目目前未使用。本次 plan 采用纯 Compose 状态切换，不激活 Navigation 3，以最小化修改范围。

## 关键文件清单

| 操作 | 路径 |
|------|------|
| 新增 | `app/src/main/java/com/example/myfitness/data/file/MarkdownFileScanner.kt` |
| 新增 | `app/src/main/java/com/example/myfitness/feature/traininglog/TrainingLogViewModel.kt` |
| 新增 | `app/src/main/java/com/example/myfitness/feature/traininglog/TrainingLogScreen.kt` |
| 修改 | `app/src/main/java/com/example/myfitness/domain/repository/WorkoutRepository.kt` |
| 修改 | `app/src/main/java/com/example/myfitness/data/repository/WorkoutRepositoryImpl.kt` |
| 修改 | `app/src/main/java/com/example/myfitness/MainActivity.kt` |

## 不修改的文件（AI 相关）

以下文件保持不变：
- `feature/ai/*`
- `di/AIModule.kt`
- `data/remote/AIApi.kt`
- `data/repository/AIChatRepositoryImpl.kt`
- `data/repository/AIProviderConfigRepositoryImpl.kt`
- 其他 AI 配置、DTO、Entity 文件

## 验证计划

1. **编译**：`./gradlew :app:assembleDebug` 成功
2. **功能验证**：
   - 打开 App，切换到"训练日志"Tab
   - 点击"导入文件夹"，在模拟器/真机上选择一个包含如下文件的文件夹：
     - `2024-01-15.md`（内容：任意训练日志文本）
     - `2024-03-10.md`
     - `bad-name.md`（日期解析失败，应被跳过并提示）
   - 确认列表中正确显示 `2024-01-15` 和 `2024-03-10` 两条记录，来源文件名正确
   - 使用 App Inspection / Database Inspector 确认 Room 中 `workouts` 表的 `sourceFileName` 和 `rawContent` 字段已写入
3. **回归验证**：切换到"AI 测试"Tab，确认原有 AI 功能未受影响
