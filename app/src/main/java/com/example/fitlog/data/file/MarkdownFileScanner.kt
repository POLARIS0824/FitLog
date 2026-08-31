package com.example.fitlog.data.file

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.annotation.RequiresApi
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject

/**
 * Markdown 训练日志文件扫描器。
 *
 * 通过 Storage Access Framework (SAF) 遍历用户授权的文件夹，
 * 读取所有 `.md` 文件内容，从文件名提取日期，并调用 [MarkdownParser.preprocess] 做预处理。
 *
 * 以 @Inject 类形式提供（而非 object），便于 ViewModel 构造注入、测试替换替身。
 */
class MarkdownFileScanner @Inject constructor() {

    /**
     * 扫描结果。
     *
     * @property successes 成功解析并读取的文件列表
     * @property failures 解析或读取失败的文件名及原因
     */
    data class ScanResult(
        val successes: List<ScannedMarkdown>,
        val failures: List<Failure>,
    )

    /**
     * 单个成功扫描的 Markdown 文件。
     *
     * @property fileName 原始文件名（含后缀）
     * @property date 从文件名解析的训练日期
     * @property content 预处理后的文本内容
     */
    data class ScannedMarkdown(
        val fileName: String,
        val date: LocalDate,
        val content: String,
    )

    /**
     * 扫描失败的文件记录。
     *
     * @property fileName 原始文件名
     * @property reason 失败原因描述
     */
    data class Failure(
        val fileName: String,
        val reason: String,
    )

    /**
     * 扫描指定文件夹下的所有 Markdown 文件。
     *
     * @param contentResolver 用于读取文件内容的 ContentResolver
     * @param treeUri 用户通过 SAF 授权的文件夹 Uri
     * @return [ScanResult] 包含成功和失败列表
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun scanFolder(contentResolver: ContentResolver, treeUri: Uri): ScanResult {
        val successes = mutableListOf<ScannedMarkdown>()
        val failures = mutableListOf<Failure>()

        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)

        // query 返回 null 表示 provider 拒绝枚举（非法 Uri/权限被回收等）——
        // 必须以失败条目呈现，而非静默空结果让调用方误判为"空文件夹"
        val cursor = contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ),
            null,
            null,
            null,
        )
        if (cursor == null) {
            return ScanResult(
                successes = emptyList(),
                failures = listOf(
                    Failure(treeUri.lastPathSegment ?: "", "无法枚举目录（内容提供方拒绝了查询）"),
                ),
            )
        }

        cursor.use { cursor ->
            // 游标列读取也纳入单文件容错：个别 provider 返回缺列时跳过该 provider
            // 的本次枚举，而不是把整个扫描炸掉（调用方只看到一个失败而非全部文件）
            val idColumn = try {
                cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            } catch (e: IllegalArgumentException) {
                return ScanResult(
                    successes = emptyList(),
                    failures = listOf(Failure(treeUri.lastPathSegment ?: "", "目录不支持枚举：缺少文档 ID 列")),
                )
            }
            val nameColumn = try {
                cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            } catch (e: IllegalArgumentException) {
                return ScanResult(
                    successes = emptyList(),
                    failures = listOf(Failure(treeUri.lastPathSegment ?: "", "目录不支持枚举：缺少文件名列")),
                )
            }

            while (cursor.moveToNext()) {
                //个别 provider 异常返回 null 值列（列存在但值为 null）：直接跳过该行，
                //避免平台类型上调用 endsWith 抛 NPE 中断整次扫描（与既有单文件容错一致）
                val docId = cursor.getString(idColumn) ?: continue
                val fileName = cursor.getString(nameColumn) ?: continue

                if (!fileName.endsWith(".md", ignoreCase = true)) continue

                try {
                    val date = parseDateFromFileName(fileName)
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    val content = readFileContent(contentResolver, fileUri)
                    val preprocessed = MarkdownParser.preprocess(content)
                    successes.add(ScannedMarkdown(fileName, date, preprocessed))
                } catch (e: DateTimeParseException) {
                    failures.add(Failure(fileName, "文件名日期解析失败"))
                } catch (e: Exception) {
                    failures.add(Failure(fileName, "读取失败: ${e.message}"))
                }
            }
        }

        return ScanResult(successes, failures)
    }

    /**
     * 从文件名解析日期（纯函数，独立可见以便 JVM 单测）。
     *
     * 期望文件名格式：`yyyy-MM-dd.md`（后缀大小写不敏感）
     *
     * @param fileName 文件名（含后缀）
     * @return 解析后的 [LocalDate]
     * @throws DateTimeParseException 如果文件名不符合 ISO 日期格式
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun parseDateFromFileName(fileName: String): LocalDate {
        val nameWithoutExtension = fileName
            .removeSuffix(".md")
            .removeSuffix(".MD")
            .removeSuffix(".Md")
            .removeSuffix(".mD")
        return LocalDate.parse(nameWithoutExtension)
    }

    /**
     * 通过 ContentResolver 读取文件完整文本内容。
     *
     * Java/Kotlin 的 Reader 不剥离 UTF-8 BOM，而 Windows 记事本等工具保存的
     * Markdown 常带 BOM：`\uFEFF` 属 Cf 类字符不会被 trim 去除，会混入首行入库
     * 并破坏 `# 标题`/`- 列表` 的前缀解析，读取后统一剥离。
     *
     * @param contentResolver ContentResolver
     * @param fileUri 文件 Uri
     * @return UTF-8 文本内容（已剥离 BOM）
     */
    private fun readFileContent(contentResolver: ContentResolver, fileUri: Uri): String {
        return contentResolver.openInputStream(fileUri)?.use { stream ->
            stream.bufferedReader().use { it.readText() }
        }?.removePrefix("\uFEFF")
            ?: throw IllegalStateException("无法打开文件输入流")
    }
}
