package com.example.fitlog.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.fitlog.model.ai.CoachInsight
import com.example.fitlog.model.ai.CoachInsightContext
import com.example.fitlog.model.ai.CoachInsightPrompt
import com.example.fitlog.model.ai.parseCoachInsight
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Coach Insight 卡片的 AI 建议仓库。
 *
 * ## 职责
 *
 * 1. 用 [CoachInsightContext] 组装 prompt（[CoachInsightPrompt]），经
 *    [AIChatRepository] 请求 AI（JSON mode，单轮结构化输出）
 * 2. 容错解析回复（[parseCoachInsight]：容忍 code fence 与多余文字）
 * 3. **指纹缓存**：以 [CoachInsightContext.fingerprint] 为键把 AI 原文存入
 *    DataStore——同一天内训练状态未变时反复进入 Today 页零网络请求；
 *    记一笔训练/换计划/跨天后指纹变化才重新生成
 *
 * ## 错误处理
 *
 * 与 [AIChatRepository] 同模板：[CancellationException] 向上传播，
 * 其余异常（无网络、服务商不支持 JSON mode、回复无法解析）统一
 * [Result.failure]，由 ViewModel 静默回退到规则版文案。
 */
class CoachInsightRepository @Inject constructor(
    private val aiChatRepository: AIChatRepository,
    private val providerConfigRepo: AIProviderConfigRepository,
    private val dataStore: DataStore<Preferences>,
) {

    /** 是否已配置 AI 服务商（ViewModel 据此决定是否展示 AI 加载态）。 */
    val aiAvailable: Flow<Boolean> = providerConfigRepo.activeProvider.map { it != null }

    /**
     * 获取 AI 教练建议（先查指纹缓存，未命中再请求 AI）。
     *
     * @param context 全部上下文材料（训练状态、计划、最近训练摘要）
     * @return [Result.success] 含 [CoachInsight]；[Result.failure] 走规则兜底
     */
    suspend fun getAiInsight(context: CoachInsightContext): Result<CoachInsight> {
        val fingerprint = context.fingerprint()

        // ── 缓存命中：同一训练状态下不重复请求付费 API ──
        readCache(fingerprint)?.let { return Result.success(it) }

        // ── 请求 AI（短文案：限制 maxTokens 控成本，JSON mode 约束结构） ──
        val reply = aiChatRepository.chat(
            messages = CoachInsightPrompt.buildMessages(context),
            temperature = 0.7,
            maxTokens = 300,
            jsonMode = true,
        ).getOrElse { return Result.failure(it) }

        val insight = parseCoachInsight(reply.content)
            ?: return Result.failure(IllegalStateException("AI 返回内容无法解析为教练建议"))

        writeCache(fingerprint, reply.content)
        return Result.success(insight)
    }

    /** 读取缓存：指纹匹配且内容可解析时返回，否则 null（视为未命中）。 */
    private suspend fun readCache(fingerprint: String): CoachInsight? = try {
        val prefs = dataStore.data.first()
        if (prefs[KEY_FINGERPRINT] == fingerprint) {
            prefs[KEY_INSIGHT_RAW]?.let { parseCoachInsight(it) }
        } else {
            null
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null // 缓存读取失败视为未命中，不影响主流程
    }

    /** 写入缓存（保存 AI 原文，读取时重新解析）：失败静默，不影响主流程。 */
    private suspend fun writeCache(fingerprint: String, rawInsight: String) = try {
        dataStore.edit { prefs ->
            prefs[KEY_FINGERPRINT] = fingerprint
            prefs[KEY_INSIGHT_RAW] = rawInsight
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // 缓存写失败仅意味着下次重新请求，静默
    }

    private companion object {
        val KEY_FINGERPRINT = stringPreferencesKey("coach_insight_fingerprint")
        val KEY_INSIGHT_RAW = stringPreferencesKey("coach_insight_raw")
    }
}
