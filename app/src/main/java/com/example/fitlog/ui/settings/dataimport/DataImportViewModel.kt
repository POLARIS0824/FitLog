package com.example.fitlog.ui.settings.dataimport

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitlog.data.file.MarkdownExporter
import com.example.fitlog.data.file.MarkdownFileScanner
import com.example.fitlog.data.repository.AIProviderConfigRepository
import com.example.fitlog.data.repository.ExerciseRepository
import com.example.fitlog.data.repository.WorkoutParseRepository
import com.example.fitlog.data.repository.WorkoutRepository
import com.example.fitlog.model.Exercise
import com.example.fitlog.model.SetType
import com.example.fitlog.model.Workout
import com.example.fitlog.util.log.FitLog
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * 数据导入页 ViewModel。
 *
 * 流水线：SAF 选目录 → [MarkdownFileScanner] 扫描（IO 线程）→ 展示结果 →
 * AI 逐条解析（[WorkoutParseRepository]，未配置服务商时弹窗引导）→
 * 用户确认（勾选 + 编辑明细）→ 写库。
 *
 * 写库幂等性仍由 `sourceFileName` 唯一索引 + IGNORE 策略在数据库层保证：
 * insert 返回 -1 时查既有记录——空明细存档则用解析结果覆盖升级（历史导入的
 * 纯存档记录重扫同一文件夹即可补全明细），已是完整记录则跳过。
 */
@HiltViewModel
class DataImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workoutRepository: WorkoutRepository,
    private val markdownFileScanner: MarkdownFileScanner,
    private val workoutParseRepository: WorkoutParseRepository,
    private val providerConfigRepository: AIProviderConfigRepository,
    private val exerciseRepository: ExerciseRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DataImportUiState())
    val uiState: StateFlow<DataImportUiState> = _uiState.asStateFlow()

    /** 解析协程句柄：取消与防重入。 */
    private var parseJob: Job? = null

    /** 草稿行/动作的会话内自增 id（Compose 文本框 remember 隔离用）。 */
    private val draftIdCounter = AtomicLong(0)
    private fun nextDraftId(): Long = draftIdCounter.incrementAndGet()

    /** 用户通过 SAF 选定文件夹后触发扫描。 */
    fun onFolderSelected(treeUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, message = null) }
            try {
                val startMs = System.currentTimeMillis()
                val result = withContext(Dispatchers.IO) {
                    markdownFileScanner.scanFolder(context.contentResolver, treeUri)
                }
                FitLog.i(
                    TAG,
                    "文件夹扫描完成：成功 ${result.successes.size} 个、失败 ${result.failures.size} 个" +
                        "（耗时${System.currentTimeMillis() - startMs}ms）",
                )
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        successes = result.successes,
                        failures = result.failures,
                        // 新扫描重置流水线：编辑弹层/进度收起态一并清掉，避免残留上一文件夹的状态
                        items = result.successes.map { scanned ->
                            ImportItemState(
                                sourceKey = scanned.sourceKey,
                                fileName = scanned.fileName,
                                date = scanned.date,
                            )
                        },
                        editingSourceKey = null,
                        isParseProgressHidden = false,
                        selectedFilter = ImportFilterCategory.ALL,
                        lastResultSummary = null,
                        // 空结果同样要给反馈：provider 拒绝枚举时扫描器已返回失败条目，
                        // 这里兜底"文件夹里没有 .md"——否则 spinner 停止后界面毫无变化
                        message = if (result.successes.isEmpty() && result.failures.isEmpty()) {
                            "所选文件夹中没有找到可导入的训练日志文件"
                        } else {
                            null
                        },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                FitLog.w(TAG, "文件夹扫描失败", e)
                // 清掉上一文件夹的扫描结果：否则界面仍展示旧列表且"解析"按钮
                // 可点，用户以为在解析新选的文件夹，实际重放的是旧快照
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        successes = emptyList(),
                        failures = emptyList(),
                        items = emptyList(),
                        message = "扫描失败：${e.message}",
                    )
                }
            }
        }
    }

    // ── AI 解析 ──

    /** 开始 AI 解析：目标为全部待解析条目。 */
    fun onParse() = startParsing { it.status == ImportItemStatus.PENDING }

    /** 重试解析失败条目（AI 单条失败可能是网络抖动，重跑往往可恢复）。 */
    fun onRetryFailed() = startParsing { it.status == ImportItemStatus.FAILED }

    /** 取消进行中的解析（已解析完成的条目保留）。 */
    fun onCancelParse() {
        parseJob?.cancel()
        parseJob = null
    }

    /** 收起/展开解析进度卡（收起后仅余一行紧凑指示，解析继续）。 */
    fun onToggleParseProgressHidden() =
        _uiState.update { it.copy(isParseProgressHidden = !it.isParseProgressHidden) }

    /**
     * 解析入口：预检服务商配置 → 逐条顺序解析（每条独立请求，进度逐条推进）。
     *
     * 每条解析前先查库：同 sourceKey 已是完整记录（exercises 非空）则标
     * ALREADY_IMPORTED 跳过——省一次 AI 调用；空明细存档则照常解析
     * （确认后覆盖升级该条）。失败项记原因、默认不勾选（走仅存档兜底由用户决定）。
     */
    private fun startParsing(targetFilter: (ImportItemState) -> Boolean) {
        if (parseJob?.isActive == true) return
        parseJob = viewModelScope.launch {
            // 预检：未配置/密钥不可读直接弹引导框，不发任何请求（文案与 AIChatRepository 一致）
            val config = providerConfigRepository.activeProvider.first()
            if (config == null) {
                FitLog.w(TAG, "导入解析被拦截：未配置 AI 服务商")
                _uiState.update {
                    it.copy(aiNotConfiguredMessage = "未设置 AI 服务商，请先在设置中配置 API Key")
                }
                return@launch
            }
            if (config.apiKey.isBlank()) {
                FitLog.w(TAG, "导入解析被拦截：API Key 为空（可能备份恢复后失效）")
                _uiState.update {
                    it.copy(
                        aiNotConfiguredMessage =
                        "API Key 无法读取（可能因备份恢复或系统凭据变更失效），请到 AI 设置中重新保存密钥",
                    )
                }
                return@launch
            }

            val targets = _uiState.value.items.filter(targetFilter).map { it.sourceKey }
            if (targets.isEmpty()) return@launch
            val scannedByKey = _uiState.value.successes.associateBy { it.sourceKey }
            FitLog.i(TAG, "开始 AI 解析：共 ${targets.size} 条（model=${config.model}）")

            _uiState.update { it.copy(isParsing = true, parseCompleted = 0, parseTotal = targets.size) }
            try {
                targets.forEach { sourceKey ->
                    val scanned = scannedByKey[sourceKey] ?: return@forEach
                    updateItem(sourceKey) { it.copy(status = ImportItemStatus.PARSING, parseError = null) }

                    val existing = workoutRepository.getBySourceFileName(scanned.sourceKey)
                    if (existing != null && existing.exercises.isNotEmpty()) {
                        FitLog.d(TAG, "跳过已导入：$sourceKey")
                        updateItem(sourceKey) {
                            it.copy(status = ImportItemStatus.ALREADY_IMPORTED, checked = false)
                        }
                    } else {
                        val itemStartMs = System.currentTimeMillis()
                        workoutParseRepository.parse(scanned.content, scanned.date).fold(
                            onSuccess = { parsed ->
                                FitLog.i(
                                    TAG,
                                    "解析成功：$sourceKey（耗时${System.currentTimeMillis() - itemStartMs}ms，" +
                                        "${parsed.exercises.size} 个动作）",
                                )
                                updateItem(sourceKey) {
                                    it.copy(
                                        status = ImportItemStatus.PARSED,
                                        draft = ImportDraftWorkout.from(parsed, ::nextDraftId),
                                        checked = true,
                                        parseError = null,
                                    )
                                }
                            },
                            onFailure = { error ->
                                FitLog.w(TAG, "解析失败：$sourceKey", error)
                                updateItem(sourceKey) {
                                    it.copy(
                                        status = ImportItemStatus.FAILED,
                                        checked = false,
                                        parseError = error.message ?: "解析失败",
                                    )
                                }
                            },
                        )
                    }
                    _uiState.update { it.copy(parseCompleted = it.parseCompleted + 1) }
                }
                _uiState.update { state ->
                    val parsed = state.items.count { it.status == ImportItemStatus.PARSED }
                    val failed = state.items.count { it.status == ImportItemStatus.FAILED }
                    val already = state.items.count { it.status == ImportItemStatus.ALREADY_IMPORTED }
                    FitLog.i(TAG, "AI 解析完成：成功 $parsed 条，已导入过 $already 条，失败 $failed 条")
                    state.copy(
                        isParsing = false,
                        message = buildString {
                            append("解析完成")
                            listOfNotNull(
                                if (parsed > 0) "成功 $parsed 条" else null,
                                if (already > 0) "$already 条已导入过" else null,
                                if (failed > 0) "失败 $failed 条" else null,
                            ).joinToString("，").takeIf { it.isNotEmpty() }?.let { append("：$it") }
                        },
                    )
                }
            } catch (e: CancellationException) {
                // 用户取消或离开页面：状态收口（解析中条目回退待解析），不落提示
                _uiState.update { state ->
                    state.copy(
                        isParsing = false,
                        items = state.items.map {
                            if (it.status == ImportItemStatus.PARSING) {
                                it.copy(status = ImportItemStatus.PENDING)
                            } else {
                                it
                            }
                        },
                    )
                }
                throw e
            } catch (e: Exception) {
                FitLog.w(TAG, "AI 解析中断", e)
                _uiState.update {
                    it.copy(isParsing = false, message = "解析中断：${e.message}")
                }
            }
        }
    }

    /** AI 未配置弹窗关闭（取消/去配置后均清除文案）。 */
    fun onAiNotConfiguredDismiss() = _uiState.update { it.copy(aiNotConfiguredMessage = null) }

    /** 切换工作台筛选分类。 */
    fun onSelectFilter(category: ImportFilterCategory) =
        _uiState.update { it.copy(selectedFilter = category) }

    /** 勾选/取消勾选一条（解析成功 = 导入完整记录，解析失败 = 仅存档兜底）。 */
    fun onToggleItemChecked(sourceKey: String) = updateItem(sourceKey) {
        it.copy(checked = !it.checked)
    }

    /** 全选当前可见/可操作条目（PARSED 或 FAILED）。 */
    fun onSelectAll() {
        val visibleKeys = _uiState.value.filteredItems
            .filter { it.status == ImportItemStatus.PARSED || it.status == ImportItemStatus.FAILED }
            .map { it.sourceKey }
            .toSet()
        _uiState.update { state ->
            state.copy(
                items = state.items.map { item ->
                    if (item.sourceKey in visibleKeys) item.copy(checked = true) else item
                },
            )
        }
    }

    /** 取消全部勾选。 */
    fun onDeselectAll() {
        _uiState.update { state ->
            state.copy(items = state.items.map { it.copy(checked = false) })
        }
    }

    /** 清空当前导入批次。 */
    fun onClearBatch() {
        parseJob?.cancel()
        parseJob = null
        _uiState.update {
            it.copy(
                successes = emptyList(),
                failures = emptyList(),
                items = emptyList(),
                isParsing = false,
                parseCompleted = 0,
                parseTotal = 0,
                editingSourceKey = null,
                selectedFilter = ImportFilterCategory.ALL,
                lastResultSummary = null,
            )
        }
    }

    /** 关闭结算弹窗/卡片。 */
    fun onDismissResultSummary() = _uiState.update { it.copy(lastResultSummary = null) }

    // ── 编辑弹层（表单缓冲在 VM，确认前不落库） ──

    /** 打开编辑弹层；动作库目录按需加载（选择器过滤用，与会话页同款全量内存过滤）。 */
    fun onStartEdit(sourceKey: String) {
        viewModelScope.launch {
            if (_uiState.value.exerciseCatalog.isEmpty()) {
                val catalog = exerciseRepository.getAll()
                _uiState.update { it.copy(exerciseCatalog = catalog) }
            }
            _uiState.update { it.copy(editingSourceKey = sourceKey) }
        }
    }

    /** 关闭编辑弹层（不保存）。 */
    fun onDismissEdit() = _uiState.update { it.copy(editingSourceKey = null) }

    /** 保存编辑：动作名全量重跑动作库匹配（含用户改名的），写回后关闭弹层。 */
    fun onSaveEdit() {
        val sourceKey = _uiState.value.editingSourceKey ?: return
        viewModelScope.launch {
            val draft = _uiState.value.items.firstOrNull { it.sourceKey == sourceKey }?.draft
            if (draft != null) {
                val rematched = draft.copy(
                    exercises = draft.exercises.map { exercise ->
                        val name = exercise.name.trim()
                        if (name.isEmpty()) {
                            // 空名保留原样：确认导入清洗时随无组动作一并剔除
                            exercise.copy(name = name)
                        } else {
                            exercise.copy(
                                name = name,
                                exerciseKey = workoutParseRepository.resolveExerciseKey(name),
                            )
                        }
                    },
                )
                updateItem(sourceKey) { it.copy(draft = rematched) }
            }
            _uiState.update { it.copy(editingSourceKey = null) }
        }
    }

    /** 修改感受/备注（编辑框直接绑定，确认导入时空白按无处理）。 */
    fun onDraftFeelingsChange(feelings: String) = mutateEditingDraft { it.copy(feelings = feelings) }

    /** 修改动作名（匹配 key 不即时更新，保存时全量重跑）。 */
    fun onDraftExerciseNameChange(exerciseLocalId: Long, name: String) =
        mutateEditingDraft { draft ->
            draft.copy(
                exercises = draft.exercises.map {
                    if (it.localId == exerciseLocalId) it.copy(name = name) else it
                },
            )
        }

    /** 更新一组数值（重量 kg / 次数；文本框逐键提交，非法输入按 0 处理为占位组）。 */
    fun onDraftSetChange(exerciseLocalId: Long, setLocalId: Long, weightKg: Float, reps: Int) =
        mutateEditingDraft { draft ->
            draft.copy(
                exercises = draft.exercises.map { exercise ->
                    if (exercise.localId != exerciseLocalId) {
                        exercise
                    } else {
                        exercise.copy(
                            sets = exercise.sets.map { set ->
                                if (set.localId == setLocalId) {
                                    set.copy(weightKg = weightKg.coerceAtLeast(0f), reps = reps.coerceAtLeast(0))
                                } else {
                                    set
                                }
                            },
                        )
                    }
                },
            )
        }

    /** 翻转一组类型（正式 ↔ 热身）。 */
    fun onToggleDraftSetType(exerciseLocalId: Long, setLocalId: Long) =
        mutateEditingDraft { draft ->
            draft.copy(
                exercises = draft.exercises.map { exercise ->
                    if (exercise.localId != exerciseLocalId) {
                        exercise
                    } else {
                        exercise.copy(
                            sets = exercise.sets.map { set ->
                                if (set.localId == setLocalId) {
                                    set.copy(
                                        setType = if (set.setType == SetType.WORKING) {
                                            SetType.WARMUP
                                        } else {
                                            SetType.WORKING
                                        },
                                    )
                                } else {
                                    set
                                }
                            },
                        )
                    }
                },
            )
        }

    /** 删除一组。 */
    fun onRemoveDraftSet(exerciseLocalId: Long, setLocalId: Long) =
        mutateEditingDraft { draft ->
            draft.copy(
                exercises = draft.exercises.map { exercise ->
                    if (exercise.localId != exerciseLocalId) {
                        exercise
                    } else {
                        exercise.copy(sets = exercise.sets.filterNot { it.localId == setLocalId })
                    }
                },
            )
        }

    /** 追加一组（复制末组数值，便于连续录入同重量递增次数的场景）。 */
    fun onAddDraftSet(exerciseLocalId: Long) =
        mutateEditingDraft { draft ->
            draft.copy(
                exercises = draft.exercises.map { exercise ->
                    if (exercise.localId != exerciseLocalId) {
                        exercise
                    } else {
                        val last = exercise.sets.lastOrNull()
                        exercise.copy(
                            sets = exercise.sets + ImportDraftSet(
                                localId = nextDraftId(),
                                weightKg = last?.weightKg ?: 0f,
                                reps = last?.reps ?: 0,
                                setType = last?.setType ?: SetType.WORKING,
                            ),
                        )
                    }
                },
            )
        }

    /** 移除动作。 */
    fun onRemoveDraftExercise(exerciseLocalId: Long) =
        mutateEditingDraft { draft ->
            draft.copy(exercises = draft.exercises.filterNot { it.localId == exerciseLocalId })
        }

    /** 从动作库添加动作（带一个待录入占位组）。 */
    fun onAddDraftExercise(exercise: Exercise) =
        mutateEditingDraft { draft ->
            draft.copy(
                exercises = draft.exercises + ImportDraftExercise.fromCatalog(exercise, ::nextDraftId),
            )
        }

    // ── 确认导入 ──

    /**
     * 确认导入勾选条目：解析成功的写完整记录（含动作/组明细），解析失败的仅存档原文。
     *
     * 幂等与升级：insert 返回 -1（sourceFileName 唯一索引命中）时查既有记录——
     * 空明细存档则用解析结果覆盖升级（update 级联重写子行），已是完整记录则跳过。
     * 每条独立事务，中断后重试不重复（与既有导入语义一致）。
     */
    fun onConfirmImport() {
        if (_uiState.value.isImporting) return
        val targets = _uiState.value.items.filter { it.checked }
        if (targets.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            var imported = 0
            var upgraded = 0
            var archived = 0
            var skipped = 0
            var invalid = 0
            val scannedByKey = _uiState.value.successes.associateBy { it.sourceKey }
            try {
                targets.forEach { item ->
                    val scanned = scannedByKey[item.sourceKey] ?: return@forEach
                    if (item.status == ImportItemStatus.FAILED) {
                        // 仅存档兜底：与既有导入行为一致，只写原文，无动作明细
                        val insertedId = workoutRepository.insert(archiveWorkout(scanned))
                        if (insertedId == -1L) {
                            skipped++
                        } else {
                            archived++
                        }
                        updateItem(item.sourceKey) { it.copy(status = ImportItemStatus.ARCHIVED, checked = false) }
                    } else {
                        val draft = item.draft
                        if (draft == null || draft.validExerciseCount == 0) {
                            // 清洗后没有任何有效明细（如全部组被删空）：不入库，提示用户编辑
                            invalid++
                            return@forEach
                        }
                        val workout = draft.toWorkout(scanned.date, scanned.sourceKey, scanned.content)
                        val insertedId = workoutRepository.insert(workout)
                        if (insertedId == -1L) {
                            val existing = workoutRepository.getBySourceFileName(scanned.sourceKey)
                            if (existing != null && existing.exercises.isEmpty()) {
                                // 历史导入的空明细存档 → 用解析结果覆盖升级
                                workoutRepository.update(
                                    workout.copy(id = existing.id, userId = existing.userId),
                                )
                                upgraded++
                            } else {
                                skipped++
                            }
                        } else {
                            imported++
                        }
                        updateItem(item.sourceKey) {
                            it.copy(
                                status = if (it.status == ImportItemStatus.ALREADY_IMPORTED) {
                                    ImportItemStatus.ALREADY_IMPORTED
                                } else {
                                    ImportItemStatus.IMPORTED
                                },
                                checked = false,
                            )
                        }
                    }
                }
                FitLog.i(
                    TAG,
                    "导入完成：新增 $imported、升级 $upgraded、存档 $archived、跳过 $skipped、无效 $invalid",
                )
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        lastResultSummary = ImportResultSummary(
                            imported = imported,
                            upgraded = upgraded,
                            archived = archived,
                            skipped = skipped,
                            invalid = invalid,
                        ),
                        message = buildString {
                            append("导入完成：")
                            append(
                                listOfNotNull(
                                    if (imported > 0) "新增 $imported 条" else null,
                                    if (upgraded > 0) "升级 $upgraded 条" else null,
                                    if (archived > 0) "存档 $archived 条" else null,
                                    if (skipped > 0) "跳过 $skipped 条" else null,
                                    if (invalid > 0) "无效 $invalid 条（请编辑后重试）" else null,
                                ).joinToString("，").ifEmpty { "无变更" },
                            )
                        },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 每个文件是独立事务：中途失败时前面的文件已落库（唯一索引保证
                // 重试不重复），提示必须带上已成功的计数，否则用户不知道实际进度
                FitLog.w(
                    TAG,
                    "导入中断（已新增 $imported、升级 $upgraded、存档 $archived、跳过 $skipped）",
                    e,
                )
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        message = "导入中断（已新增 $imported、升级 $upgraded、存档 $archived、跳过 $skipped）：" +
                            "${e.message}；重试可从断点继续",
                    )
                }
            }
        }
    }

    /** 仅存档记录构造（与既有导入行为一致：只写日期/原文，无动作明细）。 */
    private fun archiveWorkout(item: MarkdownFileScanner.ScannedMarkdown) = Workout(
        id = 0,
        userId = 0,
        date = item.date,
        exercises = emptyList(),
        feelings = null,
        sourceFileName = item.sourceKey,
        rawContent = item.content,
    )

    /** 按条目 sourceKey 局部更新流水线状态。 */
    private fun updateItem(sourceKey: String, transform: (ImportItemState) -> ImportItemState) {
        _uiState.update { state ->
            state.copy(items = state.items.map { if (it.sourceKey == sourceKey) transform(it) else it })
        }
    }

    /** 对当前编辑中的条目草稿施加变更（弹层未打开时为 no-op）。 */
    private fun mutateEditingDraft(transform: (ImportDraftWorkout) -> ImportDraftWorkout) {
        val sourceKey = _uiState.value.editingSourceKey ?: return
        updateItem(sourceKey) { item -> item.copy(draft = item.draft?.let(transform)) }
    }

    /** 一次性提示已展示，清除。 */
    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    // ── 导出（数据所有权闭环：与导入对称的出口） ──

    /** SAF 建档回调：目标文件就绪后写出全部训练记录。 */
    fun onExportTargetSelected(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, message = null) }
            try {
                val workouts = workoutRepository.getWorkouts().first()
                val markdown = MarkdownExporter.export(workouts)
                if (markdown.isBlank()) {
                    FitLog.i(TAG, "导出跳过：没有可导出的训练记录")
                    _uiState.update {
                        it.copy(isExporting = false, message = "没有可导出的训练记录")
                    }
                    return@launch
                }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(markdown.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("无法打开导出文件")
                }
                FitLog.i(TAG, "导出完成：${workouts.size} 条训练记录 → $uri")
                _uiState.update {
                    it.copy(isExporting = false, message = "导出完成：共 ${workouts.size} 条训练记录")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                FitLog.w(TAG, "导出失败", e)
                _uiState.update {
                    it.copy(isExporting = false, message = "导出失败：${e.message}")
                }
            }
        }
    }

    private companion object {
        private const val TAG = "DataImportViewModel"
    }
}
