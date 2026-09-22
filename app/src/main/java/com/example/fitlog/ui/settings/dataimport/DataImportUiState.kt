package com.example.fitlog.ui.settings.dataimport

import com.example.fitlog.data.file.MarkdownFileScanner
import com.example.fitlog.model.Exercise
import java.time.LocalDate

/**
 * 导入项在「扫描 → AI 解析 → 确认导入」流水线中的状态。
 */
enum class ImportItemStatus {
    /** 已扫描，等待 AI 解析 */
    PENDING,

    /** AI 解析中 */
    PARSING,

    /** 解析成功，待确认（可勾选/可编辑） */
    PARSED,

    /** AI 解析失败（可重试；确认时可勾选走「仅存档」兜底） */
    FAILED,

    /** 同源文件已存在完整训练记录（exercises 非空），解析时跳过 */
    ALREADY_IMPORTED,

    /** 已作为完整记录导入（含存档升级） */
    IMPORTED,

    /** 已仅存档导入（原文入库，无动作明细） */
    ARCHIVED,
}

/**
 * 单条导入项的流水线状态（扫描结果 + AI 解析结果 + 用户确认选择的聚合）。
 *
 * @param sourceKey 入库唯一键（单记录文件为文件名，多天文件为「文件名::节序号」）
 * @param fileName 展示用文件名（多天文件为「文件名::节序号」）
 * @param date 训练日期（扫描器确定，不可编辑）
 * @param status 流水线状态
 * @param draft 解析成功的可编辑草稿；仅 [ImportItemStatus.PARSED] 非空
 * @param checked 勾选 = 确认导入时处理该条（解析成功默认勾选，失败项默认不勾选）
 * @param parseError AI 解析失败原因（[ImportItemStatus.FAILED] 时展示）
 */
data class ImportItemState(
    val sourceKey: String,
    val fileName: String,
    val date: LocalDate,
    val status: ImportItemStatus = ImportItemStatus.PENDING,
    val draft: ImportDraftWorkout? = null,
    val checked: Boolean = false,
    val parseError: String? = null,
)

/**
 * 导入审核工作台的状态过滤分类。
 */
enum class ImportFilterCategory(val label: String) {
    ALL("全部"),
    PENDING("待解析"),
    READY("就绪"),
    ALREADY("已导入"),
    FAILED("失败"),
}

/**
 * 导入执行完成后的统计摘要。
 */
data class ImportResultSummary(
    val imported: Int,
    val upgraded: Int,
    val archived: Int,
    val skipped: Int,
    val invalid: Int,
) {
    val totalProcessed: Int
        get() = imported + upgraded + archived + skipped + invalid
}

/**
 * 数据导入页的 UI 状态。
 *
 * 流水线：扫描（successes/failures）→ AI 解析（items 逐条推进 + 进度）→
 * 确认导入（勾选 + 编辑弹层）→ 写库结果（IMPORTED/ARCHIVED 状态 + Snackbar）。
 */
data class DataImportUiState(
    val isScanning: Boolean = false,
    /** 扫描成功的原始条目（AI 解析的输入；items 与其一一同序对应） */
    val successes: List<MarkdownFileScanner.ScannedMarkdown> = emptyList(),
    val failures: List<MarkdownFileScanner.Failure> = emptyList(),

    // ── AI 解析 ──
    /** 解析流水线条目（与 [successes] 同序、一一对应） */
    val items: List<ImportItemState> = emptyList(),
    val isParsing: Boolean = false,
    /** 本轮解析已完成数（含跳过） */
    val parseCompleted: Int = 0,
    /** 本轮解析目标总数 */
    val parseTotal: Int = 0,
    /** 解析进度卡是否被用户收起（收起后仅余一行紧凑指示，解析不受影响） */
    val isParseProgressHidden: Boolean = false,
    /** AI 未配置/密钥不可读弹窗文案（非空即弹，去配置或取消后清除） */
    val aiNotConfiguredMessage: String? = null,

    // ── 审核工作台与筛选 ──
    val selectedFilter: ImportFilterCategory = ImportFilterCategory.ALL,
    /** 最近一次导入执行完成的结算摘要（非空时工作台展示结果卡片/弹窗） */
    val lastResultSummary: ImportResultSummary? = null,

    // ── 确认导入 ──
    val isImporting: Boolean = false,
    /** 是否正在保存编辑草稿（动作名全量重跑动作库匹配中；期间表单冻结输入与保存按钮） */
    val isSavingDraft: Boolean = false,
    /** 当前编辑弹层对应的条目 sourceKey；null = 弹层关闭 */
    val editingSourceKey: String? = null,
    /** 当前编辑弹层的独立草稿缓冲；null = 弹层关闭；编辑仅修改此缓冲，保存成功后才提交给原条目 */
    val editingDraft: ImportDraftWorkout? = null,
    /** 编辑弹层「添加动作」选择器的动作库目录（首次打开编辑时按需加载） */
    val exerciseCatalog: List<Exercise> = emptyList(),

    // ── 导出 ──
    /** 是否正在导出（导出目标文件已选定、写入进行中） */
    val isExporting: Boolean = false,
    /** 一次性提示（Snackbar 展示，展示后清除） */
    val message: String? = null,
) {
    /** 确认导入按钮的计数：勾选条数。 */
    val checkedCount: Int
        get() = items.count { it.checked }

    /** 是否存在已扫描的导入批次。 */
    val hasActiveBatch: Boolean
        get() = items.isNotEmpty() || failures.isNotEmpty()

    val pendingCount: Int get() = items.count { it.status == ImportItemStatus.PENDING }
    val readyCount: Int get() = items.count { it.status == ImportItemStatus.PARSED }
    val alreadyCount: Int get() = items.count { it.status == ImportItemStatus.ALREADY_IMPORTED }
    val failedCount: Int get() = items.count { it.status == ImportItemStatus.FAILED }
    val importedCount: Int get() = items.count {
        it.status == ImportItemStatus.IMPORTED || it.status == ImportItemStatus.ARCHIVED
    }

    /** 当前筛选分类下可见的 items */
    val filteredItems: List<ImportItemState>
        get() = when (selectedFilter) {
            ImportFilterCategory.ALL -> items
            ImportFilterCategory.PENDING -> items.filter { it.status == ImportItemStatus.PENDING }
            ImportFilterCategory.READY -> items.filter { it.status == ImportItemStatus.PARSED }
            ImportFilterCategory.ALREADY -> items.filter { it.status == ImportItemStatus.ALREADY_IMPORTED }
            ImportFilterCategory.FAILED -> items.filter { it.status == ImportItemStatus.FAILED }
        }

    /** 当前筛选分类下可见的 failures（仅在 ALL 或 FAILED 分类下展示） */
    val filteredFailures: List<MarkdownFileScanner.Failure>
        get() = when (selectedFilter) {
            ImportFilterCategory.ALL, ImportFilterCategory.FAILED -> failures
            else -> emptyList()
        }
}
