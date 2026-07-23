package com.example.fitlog.model.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ProviderType] 的单元测试。
 *
 * 验证各 AI 服务商的请求 URL 构造、模型列表 URL 构造和认证 Header 构造逻辑，
 * 包括正常路径与各类错误输入（非法 baseUrl、缺少必需参数）。
 */
class ProviderTypeTest {

    /**
     * 构造测试用配置的辅助方法，只设置与 URL 构造相关的字段。
     */
    private fun config(
        type: ProviderType,
        baseUrl: String,
        model: String = "test-model",
        apiKey: String = "sk-test",
        customEndpoint: String? = null,
        apiVersion: String? = null,
    ) = AIProviderConfig(
        id = type.name,
        name = type.name,
        type = type,
        baseUrl = baseUrl,
        apiKey = apiKey,
        model = model,
        customEndpoint = customEndpoint,
        apiVersion = apiVersion,
        isPreset = true,
    )

    // ──────────────────────────────────────
    // buildUrl — Chat Completions 地址
    // ──────────────────────────────────────

    /**
     * 测试 OpenAI 兼容类型（OPENAI/MOONSHOT/SILICONFLOW）拼接 v1/chat/completions。
     */
    @Test
    fun testBuildUrl_openAiCompatible_appendsV1ChatCompletions() {
        val openai = config(ProviderType.OPENAI, "https://api.openai.com")
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            ProviderType.OPENAI.buildUrl(openai),
        )

        val moonshot = config(ProviderType.MOONSHOT, "https://api.moonshot.cn")
        assertEquals(
            "https://api.moonshot.cn/v1/chat/completions",
            ProviderType.MOONSHOT.buildUrl(moonshot),
        )

        val siliconflow = config(ProviderType.SILICONFLOW, "https://api.siliconflow.cn")
        assertEquals(
            "https://api.siliconflow.cn/v1/chat/completions",
            ProviderType.SILICONFLOW.buildUrl(siliconflow),
        )
    }

    /**
     * 测试 baseUrl 末尾带斜杠时不产生双斜杠。
     */
    @Test
    fun testBuildUrl_baseUrlWithTrailingSlash_noDoubleSlash() {
        val cfg = config(ProviderType.OPENAI, "https://api.openai.com/")
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            ProviderType.OPENAI.buildUrl(cfg),
        )
    }

    /**
     * 测试 DeepSeek 拼接 chat/completions（无 v1 前缀）。
     */
    @Test
    fun testBuildUrl_deepseek_appendsChatCompletions() {
        val cfg = config(ProviderType.DEEPSEEK, "https://api.deepseek.com")
        assertEquals(
            "https://api.deepseek.com/chat/completions",
            ProviderType.DEEPSEEK.buildUrl(cfg),
        )
    }

    /**
     * 测试 baseUrl 自带路径时，端点路径在其后继续拼接。
     */
    @Test
    fun testBuildUrl_baseUrlWithPath_appendsAfterExistingPath() {
        val cfg = config(ProviderType.DEEPSEEK, "https://api.deepseek.com/v1")
        assertEquals(
            "https://api.deepseek.com/v1/chat/completions",
            ProviderType.DEEPSEEK.buildUrl(cfg),
        )
    }

    /**
     * 测试 Azure URL：包含 deployments/{model} 路径和 api-version 查询参数。
     */
    @Test
    fun testBuildUrl_azure_containsDeploymentAndApiVersion() {
        val cfg = config(
            ProviderType.AZURE,
            baseUrl = "https://my-resource.openai.azure.com",
            model = "gpt-4o",
            apiVersion = "2024-02-01",
        )
        assertEquals(
            "https://my-resource.openai.azure.com/openai/deployments/gpt-4o/chat/completions?api-version=2024-02-01",
            ProviderType.AZURE.buildUrl(cfg),
        )
    }

    /**
     * 测试 Azure 缺少 apiVersion 时抛出 IllegalArgumentException。
     */
    @Test
    fun testBuildUrl_azureMissingApiVersion_throws() {
        val cfg = config(ProviderType.AZURE, "https://my-resource.openai.azure.com", apiVersion = null)
        assertThrows(IllegalArgumentException::class.java) {
            ProviderType.AZURE.buildUrl(cfg)
        }
    }

    /**
     * 测试 CUSTOM 的 customEndpoint 为完整 URL 时直接返回该 URL（忽略 baseUrl）。
     */
    @Test
    fun testBuildUrl_customFullUrlEndpoint_returnedAsIs() {
        val cfg = config(
            ProviderType.CUSTOM,
            baseUrl = "https://ignored.example.com",
            customEndpoint = "https://openrouter.ai/api/v1/chat/completions",
        )
        assertEquals(
            "https://openrouter.ai/api/v1/chat/completions",
            ProviderType.CUSTOM.buildUrl(cfg),
        )
    }

    /**
     * 测试 CUSTOM 的 customEndpoint 为相对路径时拼接到 baseUrl 之后。
     */
    @Test
    fun testBuildUrl_customRelativeEndpoint_appendedToBaseUrl() {
        val cfg = config(
            ProviderType.CUSTOM,
            baseUrl = "https://openrouter.ai/api",
            customEndpoint = "/v1/chat/completions",
        )
        assertEquals(
            "https://openrouter.ai/api/v1/chat/completions",
            ProviderType.CUSTOM.buildUrl(cfg),
        )
    }

    /**
     * 测试 CUSTOM 缺少 customEndpoint 时抛出 IllegalArgumentException。
     */
    @Test
    fun testBuildUrl_customMissingEndpoint_throws() {
        val cfg = config(ProviderType.CUSTOM, "https://openrouter.ai", customEndpoint = null)
        assertThrows(IllegalArgumentException::class.java) {
            ProviderType.CUSTOM.buildUrl(cfg)
        }
    }

    /**
     * 测试非法 baseUrl 时抛出 IllegalArgumentException。
     */
    @Test
    fun testBuildUrl_invalidBaseUrl_throws() {
        val cfg = config(ProviderType.OPENAI, "not-a-valid-url")
        assertThrows(IllegalArgumentException::class.java) {
            ProviderType.OPENAI.buildUrl(cfg)
        }
    }

    // ──────────────────────────────────────
    // buildModelsUrl — GET /models 地址
    // ──────────────────────────────────────

    /**
     * 测试 OpenAI 兼容类型的模型列表地址拼接 v1/models。
     */
    @Test
    fun testBuildModelsUrl_openAiCompatible_appendsV1Models() {
        val cfg = config(ProviderType.OPENAI, "https://api.openai.com")
        assertEquals(
            "https://api.openai.com/v1/models",
            ProviderType.OPENAI.buildModelsUrl(cfg),
        )
    }

    /**
     * 测试 DeepSeek 的模型列表地址拼接 models（无 v1 前缀）。
     */
    @Test
    fun testBuildModelsUrl_deepseek_appendsModels() {
        val cfg = config(ProviderType.DEEPSEEK, "https://api.deepseek.com")
        assertEquals(
            "https://api.deepseek.com/models",
            ProviderType.DEEPSEEK.buildModelsUrl(cfg),
        )
    }

    /**
     * 测试 Azure 不支持拉取模型列表，抛出 UnsupportedOperationException。
     */
    @Test
    fun testBuildModelsUrl_azure_throwsUnsupported() {
        val cfg = config(
            ProviderType.AZURE,
            "https://my-resource.openai.azure.com",
            apiVersion = "2024-02-01",
        )
        assertThrows(UnsupportedOperationException::class.java) {
            ProviderType.AZURE.buildModelsUrl(cfg)
        }
    }

    // ──────────────────────────────────────
    // buildHeaders — 认证 Header
    // ──────────────────────────────────────

    /**
     * 测试 OpenAI 兼容类型使用 Bearer Token 认证头。
     */
    @Test
    fun testBuildHeaders_openAiCompatible_bearerToken() {
        val headers = ProviderType.OPENAI.buildHeaders("sk-secret")
        assertEquals(mapOf("Authorization" to "Bearer sk-secret"), headers)

        // DeepSeek / Moonshot / SiliconFlow / Custom 同样使用 Bearer
        assertEquals(
            "Bearer sk-secret",
            ProviderType.DEEPSEEK.buildHeaders("sk-secret")["Authorization"],
        )
        assertEquals(
            "Bearer sk-secret",
            ProviderType.CUSTOM.buildHeaders("sk-secret")["Authorization"],
        )
    }

    /**
     * 测试 Azure 使用 api-key 认证头而非 Authorization。
     */
    @Test
    fun testBuildHeaders_azure_apiKeyHeader() {
        val headers = ProviderType.AZURE.buildHeaders("azure-secret")
        assertEquals(mapOf("api-key" to "azure-secret"), headers)
        assertTrue(!headers.containsKey("Authorization"))
    }
}
