package com.example.myfitness.feature.traininglog

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfitness.data.file.MarkdownFileScanner
import com.example.myfitness.domain.model.DailyCheckIn
import com.example.myfitness.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 训练日志列表页面的 ViewModel。
 */
@HiltViewModel
class TrainingLogViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrainingLogUiState())
    val uiState: StateFlow<TrainingLogUiState> = _uiState

    init {
        loadSessions()
    }

    /**
     * 加载所有已导入的训练记录。
     */
    fun loadSessions() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val sessions = workoutRepository.getSessions()
            _uiState.value = _uiState.value.copy(
                sessions = sessions,
                isLoading = false,
            )
        }
    }

    /**
     * 从用户选择的 SAF 文件夹导入 Markdown 训练日志。
     *
     * @param treeUri 用户授权的文件夹 Uri
     */
    fun importFromFolder(treeUri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isImporting = true, importResult = null)

            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)

                val result = MarkdownFileScanner.scanFolder(context.contentResolver, treeUri)

                result.successes.forEach { scanned ->
                    workoutRepository.importFromMarkdown(
                        content = scanned.content,
                        date = scanned.date,
                        sourceFileName = scanned.fileName,
                    )
                }

                val successCount = result.successes.size
                val failureCount = result.failures.size
                val failureDetails = result.failures.joinToString("\n") {
                    "${it.fileName}: ${it.reason}"
                }

                val message = buildString {
                    append("成功导入 $successCount 个文件")
                    if (failureCount > 0) {
                        append("，跳过 $failureCount 个文件")
                        append("\n\n$failureDetails")
                    }
                }

                _uiState.value = _uiState.value.copy(
                    isImporting = false,
                    importResult = message,
                )

                loadSessions()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isImporting = false,
                    importResult = "导入失败: ${e.message}",
                )
            }
        }
    }

    /**
     * 清除导入结果提示。
     */
    fun clearImportResult() {
        _uiState.value = _uiState.value.copy(importResult = null)
    }
}

/**
 * 训练日志页面的 UI 状态。
 *
 * @property sessions 已导入的训练记录列表
 * @property isLoading 是否正在加载记录
 * @property isImporting 是否正在导入文件夹
 * @property importResult 导入完成后的提示信息（成功或失败）
 */
data class TrainingLogUiState(
    val sessions: List<DailyCheckIn> = emptyList(),
    val isLoading: Boolean = false,
    val isImporting: Boolean = false,
    val importResult: String? = null,
)
