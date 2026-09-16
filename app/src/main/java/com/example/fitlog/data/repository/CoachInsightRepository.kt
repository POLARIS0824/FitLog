package com.example.fitlog.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.fitlog.model.ai.CoachInsight
import com.example.fitlog.model.ai.CoachInsightContext
import com.example.fitlog.model.ai.CoachInsightPrompt
import com.example.fitlog.model.ai.parseCoachInsight
import com.example.fitlog.util.log.FitLog
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
 *
 * 失败不写指纹缓存，若不设防会形成"每次进 Today 重烧一轮 token"的
 * 循环——失败后写入冷却标记（同指纹 10 分钟内不再请求，见
 * [getAiInsight]），指纹变化或生成成功即解除。
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
     * 请求参数设计：
     * - 允许思考：不强制关闭模型思考模式，兼容 GLM-5 / DeepSeek 等思考型与普通模型；
     * - maxTokens 2048：保证充足的 Token 预算容纳"思考过程（800~1500 tokens）+
     *   正文 JSON 契约（约 150 tokens）"，避免在思考阶段即因达到上限而截断；
     * - 温度 0.3：低温提升 JSON 契约遵循度与 action 一致性。
     *
     * 失败冷却：请求/解析失败不写指纹缓存，若不设防，指纹不变时每次
     * 进入 Today 都会重烧一轮 token——同指纹失败后 [FAILURE_COOLDOWN_MS]
     * 内直接走规则兜底，零请求；指纹变化（记了训练/换计划/跨天）立即重试。
     *
     * @param context 全部上下文材料（训练状态、计划、最近训练摘要）
     * @return [Result.success] 含 [CoachInsight]；[Result.failure] 走规则兜底
     */
    suspend fun getAiInsight(context: CoachInsightContext): Result<CoachInsight> {
        val fingerprint = context.fingerprint()

        // ── 缓存命中：同一训练状态下不重复请求付费 API ──
        readCache(fingerprint)?.let {
            FitLog.i(TAG, "教练建议缓存命中，跳过 AI 请求")
            return Result.success(it)
        }

        // ── 失败冷却：同指纹近期已失败，不再烧 token ──
        if (isInFailureCooldown(fingerprint)) {
            FitLog.i(TAG, "教练建议处于失败冷却期，走规则兜底")
            return Result.failure(IllegalStateException("教练建议生成近期失败，冷却中"))
        }

        // ── 请求 AI（JSON mode 约束结构，充足 maxTokens 容纳思考与正文） ──
        val reply = aiChatRepository.chat(
            messages = CoachInsightPrompt.buildMessages(context),
            temperature = 0.3,
            maxTokens = 2048,
            jsonMode = true,
        ).getOrElse {
            FitLog.w(TAG, "教练建议 AI 请求失败", it)
            writeFailureMarker(fingerprint)
            return Result.failure(it)
        }

        val insight = parseCoachInsight(reply.content)
        if (insight == null) {
            FitLog.w(TAG, "教练建议解析失败：回复长度=${reply.content.length}")
            writeFailureMarker(fingerprint)
            return Result.failure(IllegalStateException("AI 返回内容无法解析为教练建议"))
        }

        writeCache(fingerprint, reply.content)
        clearFailureMarker()
        FitLog.i(TAG, "教练建议已生成并写入缓存")
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
    } catch (e: Exception) {
        FitLog.w(TAG, "教练建议缓存读取失败，视为未命中", e)
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
    } catch (e: Exception) {
        // 缓存写失败仅意味着下次重新请求
        FitLog.w(TAG, "教练建议缓存写入失败", e)
    }

    /** 同指纹是否处于失败冷却窗口内（冷却中不再发起请求）。 */
    private suspend fun isInFailureCooldown(fingerprint: String): Boolean = try {
        val prefs = dataStore.data.first()
        prefs[KEY_FAILED_FINGERPRINT] == fingerprint &&
            (prefs[KEY_FAILED_AT] ?: 0L) + FAILURE_COOLDOWN_MS > System.currentTimeMillis()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // 冷却标记读取失败不拦截主流程，按未冷却处理
        false
    }

    /** 写入失败冷却标记（指纹 + 时间戳）。 */
    private suspend fun writeFailureMarker(fingerprint: String) = try {
        dataStore.edit { prefs ->
            prefs[KEY_FAILED_FINGERPRINT] = fingerprint
            prefs[KEY_FAILED_AT] = System.currentTimeMillis()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        FitLog.w(TAG, "失败冷却标记写入失败", e)
    }

    /** 清除失败冷却标记（生成成功后调用）。 */
    private suspend fun clearFailureMarker() = try {
        dataStore.edit { prefs ->
            prefs.remove(KEY_FAILED_FINGERPRINT)
            prefs.remove(KEY_FAILED_AT)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // 清不掉最多多冷却一会儿，无害
    }

    private companion object {
        val KEY_FINGERPRINT = stringPreferencesKey("coach_insight_fingerprint")
        val KEY_INSIGHT_RAW = stringPreferencesKey("coach_insight_raw")
        val KEY_FAILED_FINGERPRINT = stringPreferencesKey("coach_insight_failed_fingerprint")
        val KEY_FAILED_AT = longPreferencesKey("coach_insight_failed_at")

        /** 失败冷却窗口：同指纹失败后窗口内不再发起请求（防重复烧 token）。 */
        const val FAILURE_COOLDOWN_MS = 10 * 60 * 1000L

        private const val TAG = "CoachInsightRepository"
    }
}
