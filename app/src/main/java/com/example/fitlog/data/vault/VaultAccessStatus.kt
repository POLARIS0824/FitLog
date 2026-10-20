package com.example.fitlog.data.vault

sealed interface VaultAccessStatus {
    val usable: Boolean get() = this == Readable
    data object Readable : VaultAccessStatus
    data object NeedsReauthorization : VaultAccessStatus
    data object DirectoryUnavailable : VaultAccessStatus
    data class Failed(val cause: Throwable) : VaultAccessStatus
}
