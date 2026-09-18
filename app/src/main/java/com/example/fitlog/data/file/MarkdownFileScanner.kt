package com.example.fitlog.data.file

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.annotation.RequiresApi
import com.example.fitlog.util.log.FitLog
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject

/**
 * Markdown 训练日志文件扫描器。
 *
 * 通过 Storage Access Framework (SAF) 遍历用户授权的文件夹，
 * 读取所有 `.md` 文件内容，解析训练日期（多天导出文件按
 * `# yyyy-MM-dd 训练` 节标题拆分取各节日期，单日文件取文件名日期），
 * 并调用 [MarkdownParser.preprocess] 做预处理。
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
     * 单个成功扫描的 Markdown 文件（多天导出文件按节拆为多条）。
     *
     * @property fileName 原始文件名（含后缀），仅作展示
     * @property date 训练日期：有日期节标题时取节标题日期，否则取文件名日期
     * @property content 预处理后的文本内容（单节记录不含节标题行）
     * @property sourceFileName 入库唯一键（`workouts.sourceFileName`）：单记录文件
     *   即原始文件名；按节拆分的文件为 `文件名::节序号`——同一文件重复导入
     *   时各节键稳定不变，唯一索引照常幂等
     */
    data class ScannedMarkdown(
        val fileName: String,
        val date: LocalDate,
        val content: String,
        val sourceKey: String,
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
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null,
            null,
            null,
        )
        if (cursor == null) {
            FitLog.w(TAG, "目录无法枚举（provider 拒绝查询）：$treeUri")
            return ScanResult(
                successes = emptyList(),
                failures = listOf(
                    Failure(treeUri.lastPathSegment ?: "", "无法枚举目录（内容提供方拒绝了查询）"),
                ),
            )
        }

        // use 块外可见：目录是否含子文件夹的提示在块外给出
        var sawSubdirectory = false
        cursor.use { cursor ->
            // 游标列读取也纳入单文件容错：个别 provider 返回缺列时跳过该 provider
            // 的本次枚举，而不是把整个扫描炸掉（调用方只看到一个失败而非全部文件）
            val idColumn = try {
                cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            } catch (e: IllegalArgumentException) {
                FitLog.w(TAG, "目录缺文档 ID 列，跳过本次枚举", e)
                return ScanResult(
                    successes = emptyList(),
                    failures = listOf(Failure(treeUri.lastPathSegment ?: "", "目录不支持枚举：缺少文档 ID 列")),
                )
            }
            val nameColumn = try {
                cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            } catch (e: IllegalArgumentException) {
                FitLog.w(TAG, "目录缺文件名列，跳过本次枚举", e)
                return ScanResult(
                    successes = emptyList(),
                    failures = listOf(Failure(treeUri.lastPathSegment ?: "", "目录不支持枚举：缺少文件名列")),
                )
            }
            // MIME 列缺失（个别 provider 裁剪列）时降级为不识别子目录，而非
            // getColumnIndexOrThrow 使整个扫描中止——子目录提示仅是增强信息
            val mimeTypeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

            while (cursor.moveToNext()) {
                //个别 provider 异常返回 null 值列（列存在但值为 null）：直接跳过该行，
                //避免平台类型上调用 endsWith 抛 NPE 中断整次扫描（与既有单文件容错一致）
                val docId = cursor.getString(idColumn) ?: continue
                val fileName = cursor.getString(nameColumn) ?: continue
                if (mimeTypeColumn >= 0 &&
                    cursor.getString(mimeTypeColumn) == DocumentsContract.Document.MIME_TYPE_DIR
                ) {
                    sawSubdirectory = true
                }

                if (!fileName.endsWith(".md", ignoreCase = true)) continue

                try {
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    val content = readFileContent(contentResolver, fileUri)
                    val preprocessed = MarkdownParser.preprocess(content)
                    // 多天导出文件按节标题拆分（修复回环坍缩）；无节标题的
                    // 手写单日文件保持旧语义（文件名日期 + 全文一条记录）
                    val sections = splitDatedSections(preprocessed)
                    if (sections == null) {
                        successes.add(
                            ScannedMarkdown(
                                fileName = fileName,
                                date = parseDateFromFileName(fileName),
                                content = preprocessed,
                                sourceKey = fileName,
                            ),
                        )
                    } else {
                        sections.forEachIndexed { index, section ->
                            successes.add(
                                ScannedMarkdown(
                                    fileName = fileName,
                                    date = section.date,
                                    content = section.content,
                                    sourceKey = "$fileName::$index",
                                ),
                            )
                        }
                    }
                } catch (e: DateTimeParseException) {
                    FitLog.w(TAG, "文件名日期解析失败：$fileName")
                    failures.add(Failure(fileName, "文件名日期解析失败"))
                } catch (e: Exception) {
                    FitLog.w(TAG, "文件读取失败：$fileName", e)
                    failures.add(Failure(fileName, "读取失败: ${e.message}"))
                }
            }
        }

        // 全空结果但目录含子文件夹：明确告知子文件夹不扫描——否则用户指向
        // "文件夹套文件夹"时只看到"没有找到可导入的训练日志文件"，无从得知
        // 需要直接选择存放 .md 的那一层
        if (successes.isEmpty() && failures.isEmpty() && sawSubdirectory) {
            failures.add(
                Failure(treeUri.lastPathSegment ?: "", "未找到 .md 文件；注意：子文件夹不会被扫描，请直接选择存放日志文件的文件夹"),
            )
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

    /**
     * 单节拆分结果（节标题日期 + 节体文本）。
     */
    internal data class DatedSection(val date: LocalDate, val content: String)

    /**
     * 按日期节标题把整份文件拆成多天记录（纯函数，独立可见以便 JVM 单测）。
     *
     * 背景：[MarkdownExporter] 把全部训练导出为**单文件多天**（`# yyyy-MM-dd 训练`
     * 节标题分节），而导入日期传统上取自文件名——直接回导导出文件会把所有训练
     * 坍缩成"文件名日期"的一条存档记录，结构化明细与真实日期全部丢失。拆分后
     * 每节一条记录、以标题日期落库，导出→擦除→导入的备份回环成立。
     *
     * 规则：
     * - 无任何合法节标题 → 返回 null，调用方保持旧语义（文件名日期 + 全文一条
     *   记录），手写的单日 `yyyy-MM-dd.md` 不受影响；
     * - 首个节标题之前的散行并入第一节（手写文件标题前可能带正文）；
     * - 形似节标题但日期非法（如 2026-02-30）的行视作普通正文行；
     * - 节体全空白的节不产出记录（避免入库"只有日期没有内容"的幽灵行）。
     *
     * @param content 预处理后的文件全文
     * @return 按出现顺序的节列表；文件不含节标题时为 null
     */
    internal fun splitDatedSections(content: String): List<DatedSection>? {
        val lines = content.lines()
        if (lines.none { line -> headerDateOf(line) != null }) return null

        val sections = mutableListOf<DatedSection>()
        var currentDate: LocalDate? = null
        val body = StringBuilder()

        fun flushSection() {
            val date = currentDate ?: return // 尚无节标题：散行留在 body 并入第一节
            val trimmed = body.toString().trim()
            if (trimmed.isNotEmpty()) {
                sections += DatedSection(date, trimmed)
            }
            body.clear()
        }

        lines.forEach { line ->
            val headerDate = headerDateOf(line)
            if (headerDate != null) {
                flushSection()
                currentDate = headerDate
            } else {
                body.appendLine(line)
            }
        }
        flushSection()
        return sections.ifEmpty { null }
    }

    /** 行为日期节标题（`# yyyy-MM-dd 训练`）时返回其日期，否则 null。 */
    private fun headerDateOf(line: String): LocalDate? {
        val match = DATED_SECTION_HEADER.matchEntire(line.trim()) ?: return null
        return runCatching { LocalDate.parse(match.groupValues[1]) }.getOrNull()
    }

    private companion object {
        /** 导出格式（[MarkdownExporter.serializeStructured]）的节标题写法。 */
        val DATED_SECTION_HEADER = Regex("^#\\s*(\\d{4}-\\d{2}-\\d{2})\\s+训练\\s*$")
        private const val TAG = "MarkdownFileScanner"
    }
}
