package com.example.fitlog.model.ai

/**
 * Agent 工具协议的跨模块共享事实源。
 *
 * 以下两组值在多个模块各有一份硬拷贝时会静默漂移（一处新增工具/改上限，
 * 另一处的 UI 语义或截断口径悄悄失配），统一收口于此：
 * - [MAX_TOOL_CONTENT_CHARS]：OpenAiAdapters（OpenAI 请求装配的工具结果截断）
 *   与 FitnessTools.getImportedWorkoutContent（Gemini 原生路径的工具侧收口）
 *   必须同值，保证双路径同限；
 * - [WRITE_TOOLS]：FitnessTools 的 `requireConfirmation = true` 注解与
 *   AgentStepFormatter 的写工具图标语义必须一致——新增写工具时两处同步。
 */
object AgentToolContract {

    /** 单个工具返回内容的字符上限。 */
    const val MAX_TOOL_CONTENT_CHARS = 8_000

    /** 需要用户确认的写工具函数名（对应 FitnessTools 中 requireConfirmation = true 的方法）。 */
    val WRITE_TOOLS: Set<String> = setOf("logBodyWeight", "setActivePlan", "createPlan")
}
