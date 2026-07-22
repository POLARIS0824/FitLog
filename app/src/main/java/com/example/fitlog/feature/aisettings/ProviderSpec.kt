package com.example.fitlog.feature.aisettings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.fitlog.R
import com.example.fitlog.model.ai.ProviderType

/**
 * 每种 AI 服务商的 UI 元数据。
 *
 * 把"显示名、API Key 标题、申请地址、默认 BaseUrl、推荐模型"等
 * 与具体 provider 绑定的信息集中在这里，Screen 本身不认识任何具体服务商。
 *
 * @property type 对应的协议类型
 * @property displayName 展示名，如 "DeepSeek"
 * @property keyLabel Credentials 卡片标题，如 "DeepSeek API Key"
 * @property keyLabelRes Credentials 卡片标题资源 ID
 * @property helpUrl API Key 申请地址（展示为帮助链接），空串表示不显示
 * @property defaultBaseUrl 默认 Base URL（未保存配置时的回填值）
 * @property defaultModel 未保存配置时的默认模型
 * @property recommendedModels 推荐模型 chips（未拉取/拉取失败时的离线选项）
 * @property supportsModelFetch 是否支持 GET /models 拉取模型列表（Azure 不支持）
 * @property needsCustomEndpoint 是否需要 customEndpoint 字段（仅 CUSTOM）
 * @property needsApiVersion 是否需要 apiVersion 字段（仅 AZURE）
 * @property logoRes 品牌 logo 资源（`res/drawable` 下的 `R.drawable.xxx`）。
 * 导入图标资源后在 [ProviderSpecs] 注册表中填入即可全局生效（填入处需 `import com.example.fitlog.R`）；
 * 为 null 时 [ProviderIcon] 回退到内置 tonal 图标。
 */
data class ProviderSpec(
    val type: ProviderType,
    val displayName: String,
    val keyLabel: String,
    @StringRes val keyLabelRes: Int = R.string.ai_settings_field_api_key,
    val helpUrl: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val recommendedModels: List<String> = emptyList(),
    val supportsModelFetch: Boolean = true,
    val needsCustomEndpoint: Boolean = false,
    val needsApiVersion: Boolean = false,
    @DrawableRes val logoRes: Int? = null,
)

/**
 * [ProviderSpec] 的注册表，通过 [of] 按类型查询。
 */
object ProviderSpecs {

    private val specs = mapOf(
        ProviderType.OPENAI to ProviderSpec(
            type = ProviderType.OPENAI,
            displayName = "OpenAI",
            keyLabel = "OpenAI API Key",
            keyLabelRes = R.string.ai_settings_key_openai,
            helpUrl = "https://platform.openai.com/api-keys",
            defaultBaseUrl = "https://api.openai.com",
            defaultModel = "gpt-5.6-sol",
            recommendedModels = listOf("gpt-5.6-sol", "gpt-5.5"),
            logoRes = R.drawable.openai_light,
        ),
        ProviderType.MOONSHOT to ProviderSpec(
            type = ProviderType.MOONSHOT,
            displayName = "Moonshot",
            keyLabel = "Moonshot API Key",
            keyLabelRes = R.string.ai_settings_key_moonshot,
            helpUrl = "https://platform.moonshot.cn/console/api-keys",
            defaultBaseUrl = "https://api.moonshot.cn",
            defaultModel = "kimi-k3",
            recommendedModels = listOf("kimi-k3", "kimi-k2.7"),
            logoRes = R.drawable.moonshotai,
        ),
        ProviderType.DEEPSEEK to ProviderSpec(
            type = ProviderType.DEEPSEEK,
            displayName = "DeepSeek",
            keyLabel = "DeepSeek API Key",
            keyLabelRes = R.string.ai_settings_key_deepseek,
            helpUrl = "https://platform.deepseek.com/api_keys",
            defaultBaseUrl = "https://api.deepseek.com",
            defaultModel = "deepseek-v4-pro",
            recommendedModels = listOf("deepseek-v4-pro", "deepseek-v4-flash"),
            logoRes = R.drawable.deepseek,
        ),
        ProviderType.SILICONFLOW to ProviderSpec(
            type = ProviderType.SILICONFLOW,
            displayName = "SiliconFlow",
            keyLabel = "SiliconFlow API Key",
            keyLabelRes = R.string.ai_settings_key_siliconflow,
            helpUrl = "https://cloud.siliconflow.cn/account/ak",
            defaultBaseUrl = "https://api.siliconflow.cn",
            defaultModel = "zai-org/GLM-5.2",
            recommendedModels = listOf("zai-org/GLM-5.2", "zai-org/GLM-5.1"),
            logoRes = R.drawable.zdotai
        ),
        ProviderType.AZURE to ProviderSpec(
            type = ProviderType.AZURE,
            displayName = "Azure OpenAI",
            keyLabel = "Azure API Key",
            keyLabelRes = R.string.ai_settings_key_azure,
            helpUrl = "https://portal.azure.com",
            defaultBaseUrl = "",
            defaultModel = "",
            supportsModelFetch = false,
            needsApiVersion = true,
            logoRes = R.drawable.azure,
        ),
        ProviderType.CUSTOM to ProviderSpec(
            type = ProviderType.CUSTOM,
            displayName = "Custom",
            keyLabel = "API Key",
            keyLabelRes = R.string.ai_settings_field_api_key,
            helpUrl = "",
            defaultBaseUrl = "",
            defaultModel = "",
            supportsModelFetch = true,
            needsCustomEndpoint = true,
            // logoRes = R.drawable.ic_logo_custom, // TODO: 导入品牌 logo 后填上
        ),
    )

    /** 按协议类型查询 UI 元数据。 */
    fun of(type: ProviderType): ProviderSpec = specs.getValue(type)
}
