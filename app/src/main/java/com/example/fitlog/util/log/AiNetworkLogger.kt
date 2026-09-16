package com.example.fitlog.util.log

import com.example.fitlog.BuildConfig
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import okio.Buffer

/**
 * AI 网络请求日志拦截器（替代 HttpLoggingInterceptor，接入 [FitLog]）。
 *
 * 输出策略（已与项目日志约定对齐）：
 * - 所有构建 INFO 级元数据：方法 + 脱敏 URL + HTTP 状态码 + 耗时——
 *   离线诊断 AI 链路（coach insight / chat / 导入解析）的最低保障；
 * - 非 2xx 响应 WARN 级，附截断后的错误响应体（provider 错误消息）；
 * - IO 异常 WARN 级；用户主动取消（stopRun）降为 INFO 避免噪音；
 * - DEBUG 构建额外记录截断的请求/响应正文（[FitLog.d]）——正式构建
 *   的文件 sink 只收 INFO+，正文不会留档。
 *
 * 脱敏：API key 均在 Header（Authorization/api-key，由各配置方注入），
 * 本拦截器不输出任何 Header；查询参数中疑似密钥的键（key/api_key/
 * token 等）统一替换为 ***，防御自定义端点把密钥放 URL 的写法。
 *
 * @param logBody 是否记录请求/响应正文（仅 debug 构建开启）
 */
class AiNetworkLogger(
    private val logBody: Boolean = BuildConfig.DEBUG,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val describe = "${request.method} ${request.url.redacted()}"
        val startNanos = System.nanoTime()

        val response = try {
            chain.proceed(request)
        } catch (e: IOException) {
            val ms = elapsedMs(startNanos)
            if (chain.call().isCanceled()) {
                FitLog.i(TAG, "AI 请求已取消：$describe（${ms}ms）")
            } else {
                FitLog.w(TAG, "AI 请求失败：$describe（${ms}ms）", e)
            }
            throw e
        }

        val ms = elapsedMs(startNanos)
        if (response.isSuccessful) {
            FitLog.i(TAG, "AI 响应：$describe → HTTP ${response.code}（${ms}ms）")
        } else {
            val errorBody = runCatching { response.peekBody(MAX_ERROR_BODY_BYTES).string() }.getOrNull()
            FitLog.w(
                TAG,
                "AI 响应异常：$describe → HTTP ${response.code}（${ms}ms）" +
                    (errorBody?.let { " body=${truncate(it)}" } ?: ""),
            )
        }

        if (logBody) {
            logRequestBody(request, describe)
            logResponseBody(response, describe)
        }
        return response
    }

    /** DEBUG 正文：请求体（prompt/工具定义，截断落盘）。 */
    private fun logRequestBody(request: Request, describe: String) {
        val body = request.body ?: return
        val text = runCatching {
            val buffer = Buffer()
            body.writeTo(buffer)
            buffer.readUtf8(MAX_BODY_BYTES)
        }.getOrNull() ?: return
        FitLog.d(TAG, "AI 请求体：$describe\n${truncate(text)}")
    }

    /** DEBUG 正文：响应体（模型输出，截断落盘）。 */
    private fun logResponseBody(response: Response, describe: String) {
        val text = runCatching { response.peekBody(MAX_BODY_BYTES).string() }.getOrNull() ?: return
        FitLog.d(TAG, "AI 响应体：$describe\n${truncate(text)}")
    }

    /** 查询参数密钥脱敏（本 app 的 key 均在 Header，此处为防御自定义端点）。 */
    private fun HttpUrl.redacted(): String {
        val sensitive = queryParameterNames.filter { it.lowercase() in REDACTED_QUERY_KEYS }
        if (sensitive.isEmpty()) return toString()
        val builder = newBuilder()
        sensitive.forEach { builder.setQueryParameter(it, REDACTED_VALUE) }
        return builder.build().toString()
    }

    private fun elapsedMs(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000

    private fun truncate(text: String): String =
        if (text.length <= MAX_BODY_CHARS) {
            text
        } else {
            text.take(MAX_BODY_CHARS) + "…（共${text.length}字符，已截断）"
        }

    private companion object {
        private const val TAG = "AiNetwork"

        /** 正文/错误体读取与截断上限（4KB）。 */
        private const val MAX_BODY_BYTES = 4L * 1024
        private const val MAX_BODY_CHARS = 4 * 1024
        private const val MAX_ERROR_BODY_BYTES = 1L * 1024
        private val REDACTED_QUERY_KEYS =
            setOf("key", "api_key", "apikey", "api-key", "token", "access_token")
        private const val REDACTED_VALUE = "***"
    }
}
