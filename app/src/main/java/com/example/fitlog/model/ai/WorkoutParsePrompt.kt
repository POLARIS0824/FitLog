package com.example.fitlog.model.ai

import java.time.LocalDate

/**
 * AI 解析训练日志的 Prompt 构建器（导入流程的单轮结构化调用）。
 *
 * ## 设计要点
 *
 * - **JSON 结构化输出**（非 function calling），与 [CoachInsightPrompt] 同模式；
 *   部分服务商不支持 json_object，客户端解析容错见 [parseWorkoutJson]。
 * - 日期不进输出契约：文件名/节标题日期已由扫描器确定，模型只提取动作明细，
 *   避免模型改写日期引入错位（dateHint 仅随原文提供上下文）。
 * - 忠实性优先：只提取原文明确写出的内容，不补全、不排序、不换算单位——
 *   解析瑕疵由后续的用户确认/编辑环节兜底，宁可缺失也不杜撰。
 */
object WorkoutParsePrompt {

    /** 系统提示词：角色 + 输出契约 + 忠实性约束。 */
    val SYSTEM_PROMPT = ChatMessage(
        role = "system",
        content = """
            你是健身训练日志解析器，把用户给出的一天训练 Markdown 原文解析为结构化 JSON。

            【输出契约】
            - 只输出一个 JSON 对象，禁止输出 markdown 代码块、解释或任何其他文字
            - JSON 结构：{"feelings": "...", "startTime": "...", "endTime": "...", "exercises": [{"name": "...", "sets": [{"weightKg": 60, "reps": 10, "type": "WORKING"}]}]}
            - feelings：原文中的训练感受/备注（如状态、体重、RPE），没有则 null
            - startTime/endTime：训练开始/结束时间，格式 "HH:mm"，原文没有则 null
            - exercises：动作列表，顺序与原文一致；name 保留原文写法（通常为中文）
            - sets：每组一条，顺序与原文一致；weightKg 为数字（单位 kg，自重/无负重为 0，
              原文未写重量时为 0）；reps 为正整数；type 只能是 "WARMUP"（热身组）或
              "WORKING"（正式组），默认 WORKING

            【忠实性约束】
            - 只提取原文明确写出的动作与组，不杜撰、不合并、不拆分、不换算单位
            - 原文重量数值原样输出（视为 kg）；无法判断对应关系时宁可遗漏该组
            - 动作名保留原文写法，不要翻译或改写为其他语言
        """.trimIndent(),
    )

    /**
     * 组装一次解析请求的完整消息列表（system + user）。
     *
     * @param dateHint 扫描器确定的该条记录日期（文件名或节标题），仅作上下文，
     *   不参与输出契约
     * @param content 预处理后的 Markdown 原文（MarkdownParser.preprocess 产物）
     */
    fun buildMessages(dateHint: LocalDate, content: String): List<ChatMessage> {
        val user = buildString {
            appendLine("【日期】$dateHint")
            appendLine("【原文】")
            append(content)
        }
        return listOf(SYSTEM_PROMPT, ChatMessage(role = "user", content = user))
    }
}
