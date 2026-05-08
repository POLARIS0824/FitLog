# AI API 多来源切换测试页面完善计划

## Context

项目已具备完整的 AI 多提供商后端架构（Room + DataStore + Retrofit + ProviderType 枚举），但现有的 `AITestScreen` 页面缺少关键的 UI 能力：
- 无法选择 `ProviderType`（所有新增配置默认是 `CUSTOM`）
- 缺少 `customEndpoint` 和 `apiVersion` 字段输入
- 配置卡片不显示类型信息

这导致用户无法真正测试不同协议（OpenAI、Azure、DeepSeek 等）的切换功能。

## Goal

完善 `AITestScreen` 和 `AITestViewModel`，使其完整支持 6 种 `ProviderType` 的配置添加和切换测试。

## Implementation

### 1. AITestViewModel — 支持完整字段

文件：`app/src/main/java/com/example/myfitness/feature/ai/AITestViewModel.kt`

- 修改 `addConfig()` 签名，增加 `type: ProviderType`、`customEndpoint: String?`、`apiVersion: String?` 参数
- 在创建 `AIProviderConfig` 时传入这些字段
- 新增 `addPresetConfigs()`：一键插入 5 个常用预设（OpenAI、Moonshot、DeepSeek、SiliconFlow、Azure），baseUrl 和 model 留空或填示例值，方便用户直接填入自己的 API Key 即可测试

### 2. AITestScreen — 添加 ProviderType 选择器和动态字段

文件：`app/src/main/java/com/example/myfitness/feature/ai/AITestScreen.kt`

- **ProviderType 下拉菜单**：使用 `ExposedDropdownMenuBox`，展示 6 个选项的中文标签：
  - OPENAI → "OpenAI"
  - MOONSHOT → "Moonshot"
  - DEEPSEEK → "DeepSeek"
  - SILICONFLOW → "SiliconFlow"
  - AZURE → "Azure OpenAI"
  - CUSTOM → "自定义"
- **动态字段显示**：
  - 选择 `AZURE` 时显示 `apiVersion` 输入框（默认 `"2024-02-01"`）
  - 选择 `CUSTOM` 时显示 `customEndpoint` 输入框（如 `chat/completions`）
- **默认值联动**：选择不同 type 时自动填充推荐的 baseUrl（可编辑）：
  - OPENAI: `https://api.openai.com`
  - MOONSHOT: `https://api.moonshot.cn`
  - DEEPSEEK: `https://api.deepseek.com`
  - SILICONFLOW: `https://api.siliconflow.cn`
  - AZURE: `https://{your-resource}.openai.azure.com`
  - CUSTOM: `https://your-api.com`
- **ConfigCard 增强**：在卡片中显示 `ProviderType` 标签（彩色 Badge 或文本）
- **一键添加预设按钮**：新增 "添加常用预设" 按钮，调用 `viewModel.addPresetConfigs()`

### 3. 无需修改的文件

- `ProviderType.kt`：后端逻辑已完善
- `AIProviderConfig.kt` / `AIProviderConfigEntity`：字段已支持
- `AIChatRepositoryImpl.kt` / `AIApi.kt`：网络层无需改动
- `MainActivity.kt`：继续直接显示 `AITestScreen`

## Verification

1. 编译通过：`./gradlew :app:compileDebugKotlin`
2. 启动应用后进入 AI 链路测试页面
3. 从下拉菜单选择 "Moonshot"，baseUrl 自动填充 `https://api.moonshot.cn`
4. 填写 API Key 和 Model，保存配置
5. 选择不同 ProviderType 添加多个配置
6. 通过 RadioButton 切换激活配置，点击测试按钮，验证不同 provider 的请求 URL 和 Headers 正确构造
7. 验证 AZURE 类型时若未填 apiVersion 会抛出异常提示
8. 验证 CUSTOM 类型时若未填 customEndpoint 会抛出异常提示

## Critical Files

- `app/src/main/java/com/example/myfitness/feature/ai/AITestScreen.kt`
- `app/src/main/java/com/example/myfitness/feature/ai/AITestViewModel.kt`
