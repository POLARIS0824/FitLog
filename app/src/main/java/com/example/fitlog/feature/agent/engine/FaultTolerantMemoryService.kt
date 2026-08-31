package com.example.fitlog.feature.agent.engine

import android.util.Log
import com.google.adk.kt.memory.MemoryService
import com.google.adk.kt.memory.SearchMemoryResponse
import kotlin.coroutines.cancellation.CancellationException

/**
 * [MemoryService] 的防故障装饰器：检索失败降级为空结果，不阻断对话。
 *
 * ## 为什么需要
 *
 * ADK [com.google.adk.kt.tools.PreloadMemoryTool] 在每轮请求前调用 [searchMemory]
 * 检索记忆库，其内部不捕获异常：AppSearch 持续故障（库损坏、初始化失败等）会让
 * 异常沿 prepareRequest 一路上抛进事件流，聊天从此每条消息都报「Agent 请求失败」
 * 且 app 侧无法兜底——与记忆功能设计文档承诺的「检索异常 → 本轮不注入记忆，
 * 无报错」降级语义相反。本类在装饰层把检索失败降级为空结果。
 *
 * 写入侧（[addSessionToMemory]）不在此兜底：调用方 [AgentEngineImpl.clearSession]
 * 已用 runCatching 包裹并记日志，清空的主语义不能被记忆库故障阻断。
 *
 * 取消（[CancellationException]）按协程语义原样上抛，不吞。
 */
class FaultTolerantMemoryService(
    private val delegate: MemoryService,
) : MemoryService by delegate {

    /** {@inheritDoc}：失败记日志并返回空结果，本轮对话退化为「无长期记忆」继续进行。 */
    override suspend fun searchMemory(appName: String, userId: String, query: String): SearchMemoryResponse =
        try {
            delegate.searchMemory(appName, userId, query)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "长期记忆检索失败，本轮不注入记忆", e)
            SearchMemoryResponse(emptyList())
        }

    companion object {
        private const val TAG = "FaultTolerantMemory"
    }
}
