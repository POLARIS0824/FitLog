# Plan: 实现 AI API 请求层（Retrofit + UseCase）

## Context

项目已搭建好 AI 配置管理的基础层（`AIProviderConfig` 领域模型、Room 实体/DAO、加密存储 Repository、Hilt 绑定），但**网络请求层完全空白**：

- `data/remote/AIApi.kt` 是空接口
- `di/AIModule.kt` 是空文件
- `domain/usecase/` 目录不存在
- 没有 DTO 定义

本 plan 补齐从 DTO 到 UseCase 的完整链路，实现 OpenAI 兼容的 Chat Completions API 调用。

## Design Decisions

### Retrofit 动态 baseUrl

因用户可切换 API provider，`baseUrl` 和 `apiKey` 均动态变化。采用 **`@Url` + `@Header` 方案**：

- Retrofit 实例保持单例（共享 OkHttp 连接池和 kotlinx.serialization 转换器），baseUrl 用 placeholder
- 接口方法通过 `@Url` 传入完整 endpoint（如 `https://api.openai.com/v1/chat/completions`）
- `Authorization: Bearer <apiKey>` 通过 `@Header` 动态传入

优点：无需重建 Retrofit、无需拦截器 hack、代码直观。

### 分层设计

遵循项目已有的 MVVM + Repository 模式：

```
UI/ViewModel
    └── domain/usecase/SendChatMessageUseCase
            ├── domain/repository/AIChatRepository (接口)
            ├── domain/repository/AIProviderConfigRepository (读取当前配置)
            └── data/repository/AIChatRepositoryImpl
                    └── data/remote/AIApi (Retrofit)
```

## Files to Create / Modify

### 1. 新建 DTO（`data/remote/dto/`）

- `ChatCompletionRequest.kt` — 请求体（`model`, `messages` 等）
- `ChatCompletionResponse.kt` — 响应体（`choices`, `usage` 等）
- `MessageDto.kt` — 消息对象（`role`, `content`）

使用 `@Serializable` + `kotlinx.serialization`，与 `build.gradle.kts` 中已有的 `retrofit-kotlinx-serialization-converter` 保持一致。

### 2. 改造 `data/remote/AIApi.kt`

从空接口改为：

```kotlin
interface AIApi {
    @POST
    suspend fun chatCompletions(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest,
    ): ChatCompletionResponse
}
```

### 3. 新建 Repository 层

- `domain/repository/AIChatRepository.kt` — 接口，定义 `suspend fun sendMessage(messages: List<Message>): String`
- `data/repository/AIChatRepositoryImpl.kt` — 实现，内部调用 `AIApi`

### 4. 新建 UseCase

- `domain/usecase/SendChatMessageUseCase.kt` — 业务用例：
  1. 读取 `AIProviderConfigRepository.getActiveId()` 和 `getById()` 获取当前配置
  2. 构造 `ChatCompletionRequest`
  3. 调用 `AIChatRepository.sendMessage()`
  4. 返回 AI 回复文本，或抛出领域异常

### 5. 改造 `di/AIModule.kt`

从空文件改为 Hilt Module，提供：

- `@Singleton @Named("ai") fun provideAIRetrofit(...): Retrofit` — 单例 Retrofit，placeholder baseUrl
- `@Singleton fun provideAIApi(@Named("ai") retrofit: Retrofit): AIApi`
- `@Binds fun bindAIChatRepository(impl: AIChatRepositoryImpl): AIChatRepository`

### 6. 验证清单

- [ ] 编译通过（`./gradlew :app:compileDebugKotlin`）
- [ ] `SendChatMessageUseCase` 可被 Hilt 注入到 ViewModel
- [ ] 各层依赖方向正确：UseCase → Repository → AIApi，无反向依赖
