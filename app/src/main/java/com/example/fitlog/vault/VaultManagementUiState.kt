package com.example.fitlog.vault

import com.example.fitlog.data.vault.VaultFolderInfo

internal data class VaultManagementUiState(
    val busy: Boolean = false,
    val loading: Boolean = false,
    val folder: VaultFolderInfo? = null,
    val error: Int? = null,
    val confirmDisconnect: Boolean = false,
)
