package com.example.fitlog.data.vault

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 资料库文件夹信息模型，包含 URI、显示名称与访问能力。
 */
data class VaultFolderInfo(
    val uri: Uri,
    val displayName: String?,
    val accessStatus: VaultAccessStatus,
)

/**
 * Vault 文件与目录操作的统一入口 Repository
 */
class VaultRepository(
    private val safAccessor: SafDirectoryAccessor,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * 供生产环境直接基于 Context 创建。
     */
    constructor(context: Context) : this(
        safAccessor = AndroidSafDirectoryAccessor(context.applicationContext),
        ioDispatcher = Dispatchers.IO,
    )

    /**
     * 查询目标 URI 的详细信息与访问状态。
     */
    suspend fun inspectFolder(uri: Uri): VaultFolderInfo = withContext(ioDispatcher) {
        try {
            // 1. 检查持久化授权：必须已获取读权限
            if (!safAccessor.hasPersistedReadPermission(uri)) {
                return@withContext VaultFolderInfo(uri, null, VaultAccessStatus.NeedsReauthorization)
            }

            // 2. 查询实际目录信息（阻塞 SAF 操作在 IO 调度器运行）
            val directoryInfo = safAccessor.queryDirectoryInfo(uri)
                ?: return@withContext VaultFolderInfo(uri, null, VaultAccessStatus.DirectoryUnavailable)

            // 3. 校验目标是否确为目录
            if (!directoryInfo.isDirectory) {
                return@withContext VaultFolderInfo(uri, directoryInfo.displayName, VaultAccessStatus.DirectoryUnavailable)
            }

            // 4. 结合写权限与 FLAG_DIR_SUPPORTS_CREATE 判定是否可创建文件
            val hasWritePermission = safAccessor.hasPersistedWritePermission(uri)
            val status = if (hasWritePermission && directoryInfo.supportsCreate) {
                VaultAccessStatus.CanCreateFiles
            } else {
                VaultAccessStatus.ReadOnly
            }
            VaultFolderInfo(uri, directoryInfo.displayName, status)
        } catch (e: CancellationException) {
            // 严格保留协程取消语义，不将取消转为普通失败
            throw e
        } catch (e: SecurityException) {
            // 系统或 ContentProvider 权限异常视为需要重新授权
            VaultFolderInfo(uri, null, VaultAccessStatus.NeedsReauthorization)
        } catch (e: Throwable) {
            // 其余未知异常统一记录为检查失败
            VaultFolderInfo(uri, null, VaultAccessStatus.Failed(e))
        }
    }

    /**
     * 请求持久授权并检查目录访问能力（用于候选目录初次选择预览）。
     */
    suspend fun takePermissionAndInspect(uri: Uri): VaultFolderInfo = withContext(ioDispatcher) {
        try {
            val permResult = safAccessor.takePersistablePermission(uri)
            if (permResult.isFailure) {
                val ex = permResult.exceptionOrNull()
                if (ex is SecurityException) {
                    return@withContext VaultFolderInfo(uri, null, VaultAccessStatus.NeedsReauthorization)
                } else if (ex != null) {
                    return@withContext VaultFolderInfo(uri, null, VaultAccessStatus.Failed(ex))
                }
            }
            inspectFolder(uri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            VaultFolderInfo(uri, null, VaultAccessStatus.NeedsReauthorization)
        } catch (e: Throwable) {
            VaultFolderInfo(uri, null, VaultAccessStatus.Failed(e))
        }
    }

    /**
     * 校验目标 URI 的持久授权有效性与目录访问能力。
     *
     * @param uri 待检查的目录 tree URI
     * @return [VaultAccessStatus] 枚举对应的检查结果
     */
    suspend fun checkAccess(uri: Uri): VaultAccessStatus = inspectFolder(uri).accessStatus
}
