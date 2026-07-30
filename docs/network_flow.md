# FitLog 网络流设计文档

> 网络层：Retrofit2 + OkHttp + kotlinx.serialization | 协议：OpenAI 兼容 Chat Completions API

## 1. 架构总览

```
┌──────────────────────────────────────────────────────────────┐
│  ViewModel / Repository                                      │
│  (AIChatRepository / CoachInsightRepository / AISettingsVM)  │
├──────────────────────────────────────────────────────────────┤
│  AIChatRepository                                            │
│  - 获取激活配置 (AIProviderConfigRepository)                  │
│  - 组装 URL + Headers + Body                                 │
│  - 调用 AIApi                                                │
│  - DTO → Model 转换                                          │
│  - Result<T> 包裹返回                                        │
├──────────────────────────────────────────────────────────────┤
│  AIApi (Retrofit Interface)                                  │
│  - @POST chatCompletions(@Url, @HeaderMap, @Body)            │
│  - @GET  models(@Url, @HeaderMap)                            │
├──────────────────────────────────────────────────────────────┤
│  OkHttp Client                                               │
│  - HttpLoggingInterceptor (DEBUG: BODY / RELEASE: NONE)      │
│  - connectTimeout: 15s                                       │
│  - readTimeout: 60s (LLM 非流式响应需要)                      │
│  - writeTimeout: 30s                                         │
├──────────────────────────────────────────────────────────────┤
│  Retrofit                                                    │
│  - baseUrl: "https://placeholder.invalid/" (占位)            │
│  - Converter: kotlinx.serialization (application/json)       │
│  - 实际 URL 由 @Url 参数动态覆盖                              │
└──────────────────────────────────────────────────────────────┘
```

## 2. 多提供商适配

### 2.1 ProviderType 枚举

| 类型 | Chat URL 路径 | Models URL 路径 | 认证方式 |
|------|--------------|-----------------|----------|
| OPENAI | `/v1/chat/completions` | `/v1/models` | Bearer Token |
| MOONSHOT | `/v1/chat/completions` | `/v1/models` | Bearer Token |
| SILICONFLOW | `/v1/chat/completions` | `/v1/models` | Bearer Token |
| DEEPSEEK | `/chat/completions` | `/models` | Bearer Token |
| AZURE | `/openai/deployments/{model}/chat/completions?api-version=` | 不支持 | `api-key` Header |
| CUSTOM | `customEndpoint`（绝对/相对） | `/v1/models` | Bearer Token |

### 2.2 URL 构建（ProviderType.buildUrl）

```kotlin
// 使用 OkHttp HttpUrl builder，避免字符串拼接问题
val base = config.baseUrl.toHttpUrlOrNull()
    ?: throw IllegalArgumentException("Invalid baseUrl")
val builder = base.newBuilder()
when (this) {
    OPENAI, MOONSHOT, SILICONFLOW -> builder.addPathSegments("v1/chat/completions")
    DEEPSEEK -> builder.addPathSegments("chat/completions")
    AZURE -> {
        builder.addPathSegments("openai/deployments/${config.model}/chat/completions")
        builder.addQueryParameter("api-version", apiVersion)
    }
    CUSTOM -> { /* 绝对 URL 直接返回，相对路径追加到 baseUrl */ }
}
```

### 2.3 Headers 构建（ProviderType.buildHeaders）

```kotlin
// 大多数提供商：标准 Bearer Token
mapOf("Authorization" to "Bearer $apiKey")
// Azure：自定义 header
mapOf("api-key" to apiKey)
```

## 3. 请求/响应 DTO

### 3.1 ChatCompletionRequestDto

```json
{
  "model": "deepseek-chat",
  "messages": [
    {"role": "system", "content": "..."},
    {"role": "user", "content": "..."}
  ],
  "temperature": 0.7,
  "max_tokens": 300,
  "response_format": {"type": "json_object"}
}
```

- `temperature` / `max_tokens` / `response_format` 为 null 时不编码（kotlinx.serialization 默认行为）
- 向下兼容不支持这些字段的服务商

### 3.2 ChatCompletionResponseDto

```json
{
  "choices": [
    {
      "message": {"role": "assistant", "content": "..."},
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 100,
    "completion_tokens": 50,
    "total_tokens": 150
  }
}
```

### 3.3 ModelsResponseDto

```json
{
  "data": [
    {"id": "gpt-4o", "object": "model"},
    {"id": "gpt-4o-mini", "object": "model"}
  ]
}
```

## 4. 核心网络流

### 4.1 AI 对话（Chat 页）

```
ChatViewModel.send()
  → AIChatRepository.chat(messages)
    → providerConfigRepo.activeProvider.first()  // 获取激活配置
    → config.type.buildUrl(config)               // 构建 URL
    → config.type.buildHeaders(config.apiKey)    // 构建 Headers
    → ChatCompletionRequestDto(...)              // 组装 Body
    → aiApi.chatCompletions(url, headers, body)  // 发送请求
    → response.choices.firstOrNull()             // 提取回复
    → choice.message.toModel()                   // DTO → Model
  → Result.success(reply) / Result.failure(e)
  → _uiState.update { ... }
```

### 4.2 Coach Insight（Today 页 AI 增强）

```
TodayViewModel.aiPhaseFlow
  → sharedMaterials.map { toCoachInsightContext() }
  → .distinctUntilChangedBy { fingerprint() }    // 指纹去重
  → .flatMapLatest { context ->
      coachInsightRepository.getAiInsight(context)
        → readCache(fingerprint)                 // DataStore 缓存
        → CoachInsightPrompt.buildMessages()     // 组装 Prompt
        → aiChatRepository.chat(                 // 请求 AI
            messages, temperature=0.7,
            maxTokens=300, jsonMode=true
          )
        → parseCoachInsight(reply.content)       // 容错 JSON 解析
        → writeCache(fingerprint, raw)           // 写入缓存
    }
```

### 4.3 连通性测试（AI 设置页）

```
AISettingsViewModel.onTestConnection()
  → 用表单数据构造临时 AIProviderConfig（不需要已保存）
  → aiChatRepository.testConnection(config)
    → chat(config, listOf(ChatMessage("user", "Hi")))
  → 更新 TestState（成功/失败）
```

### 4.4 模型列表拉取

```
AISettingsViewModel.onFetchModels(baseUrl, customEndpoint)
  → 构造临时 config
  → aiChatRepository.fetchModels(config)
    → config.type.buildModelsUrl(config)
    → aiApi.models(url, headers)
    → response.data.map { it.id }
  → 成功：更新 ModelState + 持久化到 Room (cachedModels)
  → 失败：显示错误，不阻塞手动输入
```

## 5. 错误处理策略

### 5.1 分层错误处理

| 层次 | 策略 |
|------|------|
| OkHttp | 超时（connect 15s / read 60s / write 30s） |
| Retrofit | HTTP 错误码 → `HttpException`（kotlinx.serialization 解析） |
| AIChatRepository | `try-catch` 包裹，`CancellationException` 向上传播，其余 → `Result.failure` |
| CoachInsightRepository | 同上 + 缓存读写失败静默降级 |
| ViewModel | `Result.onFailure` → 更新 UI 错误状态 / 静默回退规则版 |

### 5.2 CancellationException 处理

```kotlin
// 所有网络仓库统一模式：取消不是"失败"，必须向上传播
catch (e: CancellationException) { throw e }
catch (e: Exception) { Result.failure(e) }
```

### 5.3 AI 回复解析容错

```kotlin
// parseCoachInsight：容忍 code fence、多余文字
val start = raw.indexOf('{')
val end = raw.lastIndexOf('}')
// 截取 JSON 子串 → kotlinx.serialization 解析
// observation/recommendation 为空 → 返回 null → 规则版兜底
```

## 6. 安全设计

| 措施 | 实现 |
|------|------|
| API Key 加密存储 | `KeystoreManager.encrypt()` → AES-GCM 密文存 Room |
| API Key 解密使用 | `KeystoreManager.decrypt()` 在 Mapper 层自动完成 |
| 日志脱敏 | Release 构建 `HttpLoggingInterceptor.Level.NONE` |
| 动态 URL | 避免 baseUrl 硬编码任何真实地址 |

## 7. 问题与改进建议

| # | 问题 | 严重度 | 建议 |
|---|------|--------|------|
| 1 | 无重试机制 | 中 | 添加 OkHttp `RetryInterceptor` 或 Retrofit 层指数退避 |
| 2 | 无流式响应（SSE） | 中 | Chat 页长回复体验差；考虑 OkHttp SSE + Flow 流式输出 |
| 3 | 无请求取消传播 | 低 | ViewModel 销毁时 `viewModelScope` 自动取消，已覆盖 |
| 4 | 无 HTTP 错误码细分 | 低 | 401/403/429 应给用户更精确的提示（Key 过期/限流） |
| 5 | `testConnection` 发真实消息 | 低 | 消耗 token；可考虑用 models 接口做连通性验证 |
| 6 | 无请求体大小限制 | 低 | 对话历史无限增长可能超出模型 context window |
| 7 | Azure `buildModelsUrl` 抛异常 | 低 | UI 层需 catch `UnsupportedOperationException`，当前未处理 |
| 8 | 占位 baseUrl 无实际意义 | — | 设计如此（@Url 覆盖），不影响功能 |

## 8. 超时配置说明

```kotlin
OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)   // TCP 握手
    .readTimeout(60, TimeUnit.SECONDS)      // LLM 生成耗时（非流式）
    .writeTimeout(30, TimeUnit.SECONDS)     // 请求体上传
```

- 60s readTimeout 是针对 LLM 非流式响应的刻意设置
- 默认 OkHttp 10s readTimeout 会误杀正常的长回复请求
- 未来引入 SSE 流式后，readTimeout 语义变化（每帧间隔），需重新评估
