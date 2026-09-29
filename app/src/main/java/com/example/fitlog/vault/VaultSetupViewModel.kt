package com.example.fitlog.vault

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.fitlog.R
import com.example.fitlog.data.vault.VaultAccessStatus
import com.example.fitlog.data.vault.VaultConfigState
import com.example.fitlog.data.vault.VaultPreferences
import com.example.fitlog.data.vault.VaultRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * VaultSetup 页面状态管理 ViewModel。
 *
 * 负责读取配置、检查目录、确认保存和重试。不持有 Activity、系统选择器或导航栈。
 */
class VaultSetupViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val createAfterSetup: Boolean,
    private val requestId: String,
    private val vaultPreferences: VaultPreferences,
    private val vaultRepository: VaultRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        VaultSetupUiState(createAfterSetup = createAfterSetup)
    )
    val uiState: StateFlow<VaultSetupUiState> = _uiState.asStateFlow()

    private var configGeneration = 0
    private var configJob: Job? = null
    private var checkGeneration = 0
    private var checkJob: Job? = null
    private var saveJob: Job? = null

    init {
        loadConfig()
        restoreCandidate()
    }

    private fun restoreCandidate() {
        val candidateUriString: String? = savedStateHandle[KEY_CANDIDATE_URI]
        if (!candidateUriString.isNullOrBlank()) {
            val uri = Uri.parse(candidateUriString)
            inspectTarget(uri, isCandidate = true)
        }
    }

    fun loadConfig() {
        val token = ++configGeneration
        _uiState.update { it.copy(configState = VaultConfigUiState.Loading, errorResId = null) }
        configJob?.cancel()
        configJob = viewModelScope.launch {
            try {
                val configState = vaultPreferences.getVaultConfig()
                if (token != configGeneration) return@launch
                when (configState) {
                    is VaultConfigState.Configured -> {
                        val folderInfo = vaultRepository.inspectFolder(configState.uri)
                        if (token != configGeneration) return@launch
                        val error = if (folderInfo.accessStatus != VaultAccessStatus.CanCreateFiles &&
                            (folderInfo.accessStatus == VaultAccessStatus.ReadOnly).not()
                        ) {
                            accessError(folderInfo.accessStatus)
                        } else null

                        _uiState.update {
                            it.copy(
                                configState = VaultConfigUiState.Configured(folderInfo),
                                errorResId = if (it.candidateVault != null || it.isChecking) it.errorResId else error,
                            )
                        }
                    }
                    VaultConfigState.NotConfigured -> {
                        _uiState.update { it.copy(configState = VaultConfigUiState.NotConfigured) }
                    }
                    is VaultConfigState.Failed -> {
                        _uiState.update {
                            it.copy(
                                configState = VaultConfigUiState.Failed(configState.cause),
                                errorResId = R.string.vault_error_load_config_failed,
                            )
                        }
                    }
                    VaultConfigState.Loading -> Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (token != configGeneration) return@launch
                _uiState.update {
                    it.copy(
                        configState = VaultConfigUiState.Failed(e),
                        errorResId = R.string.vault_error_load_config_failed,
                    )
                }
            }
        }
    }

    fun onSelectingFolder() {
        if (_uiState.value.stage != VaultOperationStage.Idle) return
        _uiState.update { it.copy(stage = VaultOperationStage.Selecting) }
    }

    fun onFolderPicked(uri: Uri?) {
        if (_uiState.value.isSaving || _uiState.value.isCompleted) return
        if (uri == null) {
            // 取消选择系统选择器，保留原页面状态
            _uiState.update {
                if (it.stage == VaultOperationStage.Selecting) it.copy(stage = VaultOperationStage.Idle)
                else it
            }
            return
        }

        val token = ++checkGeneration
        checkJob?.cancel()
        _uiState.update {
            it.copy(
                stage = VaultOperationStage.Checking,
                errorResId = null,
            )
        }

        checkJob = viewModelScope.launch {
            try {
                val folderInfo = vaultRepository.takePermissionAndInspect(uri)
                if (token != checkGeneration) return@launch

                savedStateHandle[KEY_CANDIDATE_URI] = uri.toString()

                val isAllowed = folderInfo.accessStatus == VaultAccessStatus.CanCreateFiles ||
                    (folderInfo.accessStatus == VaultAccessStatus.ReadOnly)

                val error = if (!isAllowed) accessError(folderInfo.accessStatus) else null

                _uiState.update {
                    it.copy(
                        candidateVault = folderInfo,
                        stage = VaultOperationStage.Idle,
                        errorResId = error,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (token != checkGeneration) return@launch
                _uiState.update {
                    it.copy(
                        stage = VaultOperationStage.Idle,
                        errorResId = R.string.vault_setup_error_access_failed,
                    )
                }
            }
        }
    }

    fun confirm() {
        val state = _uiState.value
        if (!state.canConfirm) return

        val candidate = state.candidateVault
        val current = state.currentVault

        val token = ++checkGeneration
        checkJob?.cancel()

        if (candidate != null) {
            // 场景 1：确认连接新选择的候选目录
            _uiState.update { it.copy(stage = VaultOperationStage.Checking, errorResId = null) }
            saveJob = viewModelScope.launch {
                try {
                    // 确认时再次检查目录能力，避免使用过期结果
                    val checkedStatus = vaultRepository.checkAccess(candidate.uri)
                    if (token != checkGeneration) return@launch

                    val allowed = checkedStatus == VaultAccessStatus.CanCreateFiles ||
                        checkedStatus == VaultAccessStatus.ReadOnly

                    if (!allowed) {
                        _uiState.update {
                            it.copy(
                                candidateVault = candidate.copy(accessStatus = checkedStatus),
                                stage = VaultOperationStage.Idle,
                                errorResId = accessError(checkedStatus),
                            )
                        }
                        return@launch
                    }

                    // 能力检查通过，进入保存状态
                    _uiState.update { it.copy(stage = VaultOperationStage.Saving) }
                    val saveResult = vaultPreferences.setVaultUri(candidate.uri)
                    if (token != checkGeneration) return@launch

                    if (saveResult.isFailure) {
                        _uiState.update {
                            it.copy(
                                stage = VaultOperationStage.Idle,
                                errorResId = R.string.vault_error_save_config_failed,
                            )
                        }
                    } else {
                        savedStateHandle.remove<String>(KEY_CANDIDATE_URI)
                        _uiState.update {
                            it.copy(
                                stage = VaultOperationStage.Completed,
                                completedResult = VaultSetupCompletion(
                                    requestId = requestId,
                                    createAfterSetup = createAfterSetup,
                                ),
                            )
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    if (token != checkGeneration) return@launch
                    _uiState.update {
                        it.copy(
                            stage = VaultOperationStage.Idle,
                            errorResId = R.string.vault_error_save_config_failed,
                        )
                    }
                }
            }
        } else if (current != null) {
            // 场景 2：已有资料库点击“继续”，重新检查能力，无需重复写入相同配置
            _uiState.update { it.copy(stage = VaultOperationStage.Checking, errorResId = null) }
            checkJob = viewModelScope.launch {
                try {
                    val checkedStatus = vaultRepository.checkAccess(current.uri)
                    if (token != checkGeneration) return@launch

                    val allowed = checkedStatus == VaultAccessStatus.CanCreateFiles ||
                        checkedStatus == VaultAccessStatus.ReadOnly

                    if (!allowed) {
                        _uiState.update {
                            it.copy(
                                configState = VaultConfigUiState.Configured(current.copy(accessStatus = checkedStatus)),
                                stage = VaultOperationStage.Idle,
                                errorResId = accessError(checkedStatus),
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                stage = VaultOperationStage.Completed,
                                completedResult = VaultSetupCompletion(
                                    requestId = requestId,
                                    createAfterSetup = createAfterSetup,
                                ),
                            )
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    if (token != checkGeneration) return@launch
                    _uiState.update {
                        it.copy(
                            stage = VaultOperationStage.Idle,
                            errorResId = R.string.vault_setup_error_access_failed,
                        )
                    }
                }
            }
        }
    }

    /** Rechecking access never implicitly confirms or saves the selection. */
    private fun inspectTarget(uri: Uri, isCandidate: Boolean) {
        val token = ++checkGeneration
        checkJob?.cancel()
        _uiState.update { it.copy(stage = VaultOperationStage.Checking, errorResId = null) }
        checkJob = viewModelScope.launch {
            val folder = vaultRepository.inspectFolder(uri)
            if (token != checkGeneration) return@launch
            val allowed = folder.accessStatus == VaultAccessStatus.CanCreateFiles ||
                (folder.accessStatus == VaultAccessStatus.ReadOnly)
            _uiState.update {
                it.copy(
                    candidateVault = if (isCandidate) folder else it.candidateVault,
                    configState = if (isCandidate) it.configState else VaultConfigUiState.Configured(folder),
                    stage = VaultOperationStage.Idle,
                    errorResId = if (it.configState is VaultConfigUiState.Failed) {
                        R.string.vault_error_load_config_failed
                    } else if (allowed) null else accessError(folder.accessStatus),
                )
            }
        }
    }

    fun retry() {
        val state = _uiState.value
        if (state.stage != VaultOperationStage.Idle) return
        if (state.configState is VaultConfigUiState.Failed) {
            loadConfig()
        } else if (state.errorResId == R.string.vault_error_save_config_failed) {
            confirm()
        } else {
            val target = state.targetVault ?: return
            inspectTarget(target.uri, isCandidate = state.hasCandidate)
        }
    }

    fun onBack(): Boolean {
        // A commit already in progress must finish before leaving the screen.
        if (_uiState.value.isSaving) return false
        ++configGeneration
        ++checkGeneration
        configJob?.cancel()
        checkJob?.cancel()
        saveJob?.cancel()
        configJob = null
        checkJob = null
        saveJob = null
        _uiState.update { it.copy(stage = VaultOperationStage.Idle, completedResult = null) }
        return true
    }

    fun onCompletionConsumed() {
        _uiState.update { it.copy(completedResult = null) }
    }

    private fun accessError(access: VaultAccessStatus): Int = when (access) {
        VaultAccessStatus.NeedsReauthorization -> R.string.vault_error_needs_reauthorization
        VaultAccessStatus.DirectoryUnavailable -> R.string.vault_error_directory_unavailable
        VaultAccessStatus.ReadOnly -> R.string.vault_error_read_only
        else -> R.string.vault_setup_error_access_failed
    }

    companion object {
        private const val KEY_CANDIDATE_URI = "key_candidate_uri"
    }

    class Factory(
        private val createAfterSetup: Boolean,
        private val requestId: String,
        private val vaultPreferences: VaultPreferences,
        private val vaultRepository: VaultRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val savedStateHandle = extras.createSavedStateHandle()
            return VaultSetupViewModel(
                savedStateHandle = savedStateHandle,
                createAfterSetup = createAfterSetup,
                requestId = requestId,
                vaultPreferences = vaultPreferences,
                vaultRepository = vaultRepository,
            ) as T
        }
    }
}
