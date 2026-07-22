package com.example.fitlog.ui.dataimport

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
) : ViewModel() {

    private val _uiState = MutableStateFlow(DataImportUiState())
    val uiState: StateFlow<DataImportUiState> = _uiState.asStateFlow()

    /** 用户通过 SAF 选定文件夹后触发扫描。 */
    fun onFolderSelected(treeUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, message = null) }
            try {
                val result = withContext(Dispatchers.IO) {
                    MarkdownFileScanner.scanFolder(context.contentResolver, treeUri)
                }
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        successes = result.successes,
                        failures = result.failures,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isScanning = false, message = "扫描失败：${e.message}")
                }
            }
        }
    }

    /** 导入扫描结果：跳过已存在的文件，其余写入数据库。 */
    fun onImport() {
        val scanned = _uiState.value.successes
        if (scanned.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            var imported = 0
            var skipped = 0
            try {
                scanned.forEach { item ->
                    if (workoutRepository.existsBySourceFileName(item.fileName)) {
                        skipped++
                    } else {
                        workoutRepository.insert(
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
                        imported++
                    }
                }
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        message = "导入完成：新增 $imported 条，跳过 $skipped 条",
                    )
                }
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
