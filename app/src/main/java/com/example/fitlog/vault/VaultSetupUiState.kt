package com.example.fitlog.vault

import com.example.fitlog.data.vault.VaultAccessStatus
import com.example.fitlog.data.vault.VaultFolderInfo

/**
 * 资料库设置操作阶段
 */
enum class VaultOperationStage {
    Idle,
    Selecting,
    Checking,
    Saving,
    Completed,
}

/**
 * 资料库配置读取状态
 */
sealed interface VaultConfigUiState {
    data object Loading : VaultConfigUiState
    data object NotConfigured : VaultConfigUiState
    data class Configured(val vault: VaultFolderInfo) : VaultConfigUiState
    data class Failed(val cause: Throwable) : VaultConfigUiState
}

/**
 * 设置完成结果标记，包含请求 ID 与入口目的
 */
data class VaultSetupCompletion(
    val requestId: String,
    val createAfterSetup: Boolean,
)

/**
 * VaultSetup 页面不可变状态模型
 */
data class VaultSetupUiState(
    val createAfterSetup: Boolean = false,
    val configState: VaultConfigUiState = VaultConfigUiState.Loading,
    val candidateVault: VaultFolderInfo? = null,
    val stage: VaultOperationStage = VaultOperationStage.Idle,
    val errorResId: Int? = null,
    val completedResult: VaultSetupCompletion? = null,
) {
    /** 当前已保存并连接的资料库信息（若有） */
    val currentVault: VaultFolderInfo?
        get() = (configState as? VaultConfigUiState.Configured)?.vault

    /** 目标操作目录：优先使用候选新目录，其次使用已有当前目录 */
    val targetVault: VaultFolderInfo?
        get() = candidateVault ?: currentVault

    val hasCandidate: Boolean
        get() = candidateVault != null

    val isChecking: Boolean
        get() = stage == VaultOperationStage.Checking

    val isSaving: Boolean
        get() = stage == VaultOperationStage.Saving

    val isCompleted: Boolean
        get() = stage == VaultOperationStage.Completed

    /**
     * 确认/继续按钮可用性：
     * 由入口目的、目标目录能力和操作阶段共同推导，不维护多份独立布尔状态。
     */
    val canConfirm: Boolean
        get() {
            if (stage != VaultOperationStage.Idle) return false
            if (configState is VaultConfigUiState.Loading || configState is VaultConfigUiState.Failed) return false
            val target = targetVault ?: return false
            return when (target.accessStatus) {
                VaultAccessStatus.CanCreateFiles -> true
                VaultAccessStatus.ReadOnly -> true
                else -> false
            }
        }
}
