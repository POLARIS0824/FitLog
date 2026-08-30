package com.example.fitlog.ui.settings.dataimport

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitlog.data.file.MarkdownFileScanner
import com.example.fitlog.data.repository.WorkoutRepository
import com.example.fitlog.model.Workout
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * 数据导入页 ViewModel。
 *
 * 流程：SAF 选目录 → [MarkdownFileScanner] 扫描（IO 线程）→ 展示结果 →
 * 导入时按 `sourceFileName` 去重（已导入过的文件跳过），新增写入 Room。
 */
@HiltViewModel
class DataImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workoutRepository: WorkoutRepository,
    private val markdownFileScanner: MarkdownFileScanner,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DataImportUiState())
    val uiState: StateFlow<DataImportUiState> = _uiState.asStateFlow()

    /** 用户通过 SAF 选定文件夹后触发扫描。 */
    fun onFolderSelected(treeUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, message = null) }
            try {
                val result = withContext(Dispatchers.IO) {
                    markdownFileScanner.scanFolder(context.contentResolver, treeUri)
                }
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        successes = result.successes,
                        failures = result.failures,
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
                _uiState.update {
                    it.copy(isScanning = false, message = "扫描失败：${e.message}")
                }
            }
        }
    }

    /**
     * 导入扫描结果：写入数据库，重复文件由 `sourceFileName` 唯一索引 +
     * IGNORE 策略在数据库层拒绝（insert 返回 -1 计入跳过）。
     *
     * 不再用应用层 existsBySourceFileName 前置查询做去重——check-then-insert
     * 存在 TOCTOU 窗口，且插入返回值此前被忽略会虚报"新增"计数。
     */
    fun onImport() {
        val scanned = _uiState.value.successes
        if (scanned.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            var imported = 0
            var skipped = 0
            try {
                scanned.forEach { item ->
                    val insertedId = workoutRepository.insert(
                        Workout(
                            id = 0,
                            userId = 0,
                            date = item.date,
                            exercises = emptyList(),
                            feelings = null,
                            sourceFileName = item.fileName,
                            rawContent = item.content,
                        )
                    )
                    if (insertedId == -1L) skipped++ else imported++
                }
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        message = "导入完成：新增 $imported 条，跳过 $skipped 条",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isImporting = false, message = "导入失败：${e.message}")
                }
            }
        }
    }

    /** 一次性提示已展示，清除。 */
    fun onMessageShown() = _uiState.update { it.copy(message = null) }
}
