package com.example.fitlog.data.vault

/**
 * SAF 目录访问能力检查结果。
 *
 * 由授权持久化状态与目标目录实际查询结果共同判定。
 */
sealed interface VaultAccessStatus {
    /** Readable directories support browsing; creation and writes require their own checks. */
    val usable: Boolean get() = this == CanCreateFiles || this == ReadOnly

    /**
     * 正常且拥有完整权限：目录存在，可读且支持在其下创建新文件。
     */
    data object CanCreateFiles : VaultAccessStatus

    /**
     * 目录可读，但不可在该目录下创建文件（如缺少写权限、只读存储介质或只读目录）。
     */
    data object ReadOnly : VaultAccessStatus

    /**
     * 缺少持久化读取权限，或系统/Provider 抛出 SecurityException，需要用户重新在 SAF 中授权。
     */
    data object NeedsReauthorization : VaultAccessStatus

    /**
     * 目录不可用：目标不存在、已被删除、路径不是文件夹或存储卷未挂载。
     */
    data object DirectoryUnavailable : VaultAccessStatus

    /**
     * 检查过程中发生未知异常（如 ContentProvider 崩溃或 I/O 错误）。
     */
    data class Failed(val cause: Throwable) : VaultAccessStatus
}
