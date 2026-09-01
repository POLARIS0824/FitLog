package com.example.fitlog.data.file

/**
 * Markdown 训练日志的预处理器。
 *
 * 不对文本做结构化解析，仅做基础清洗后存入数据库 [rawContent]，
 * 后续由大模型 API 完成结构化提取。
 */
object MarkdownParser {

    /**
     * 对原始 Markdown 文本进行简单预处理。
     *
     * 处理内容包括：
     * - 去除行首 `- ` 列表标记
     * - 去除 `**` 粗体标记
     * - 统一分隔符：`➕` → `+`，`✖️` `×` → `x`
     * - 去除首尾空白及多余空行
     *
     * @param content 原始 Markdown 文本
     * @return 清洗后的文本
     */
    fun preprocess(content: String): String {
        return content
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                line
                    .removePrefix("- ")
                    // 前缀移除后再修剪："-  两空格" 粘贴排版会把首空格带进
                    // rawContent（此前只在行级入口 trim 一次）
                    .trim()
                    .removePrefix("* ")
                    .removePrefix("+ ")
                    .replace("**", "")
                    .replace("➕", "+")
                    .replace("✖️", "x")
                    // 裸 ✖（无 VS16 变体选择符）：多数输入法直接产出该码点
                    .replace("✖", "x")
                    .replace("×", "x")
            }
            .joinToString("\n")
    }
}
