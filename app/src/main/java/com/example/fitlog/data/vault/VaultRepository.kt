package com.example.fitlog.data.vault

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Vault 文件与目录操作的统一入口仓库。
 *
 * 当前仅提供目录访问性校验，为后续读取与创建 Markdown 保留扩展点，不提前引入未使用的逻辑。
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
     * 校验目标 URI 的持久授权有效性与目录访问能力。
     *
     * @param uri 待检查的目录 tree URI
     * @return [VaultAccessStatus] 枚举对应的检查结果
     */
    suspend fun checkAccess(uri: Uri): VaultAccessStatus = withContext(ioDispatcher) {
        try {
            // 1. 检查持久化授权：必须已获取读权限
            if (!safAccessor.hasPersistedReadPermission(uri)) {
                return@withContext VaultAccessStatus.NeedsReauthorization
            }

            // 2. 查询实际目录信息（阻塞 SAF 操作在 IO 调度器运行）
            val directoryInfo = safAccessor.queryDirectoryInfo(uri)
                ?: return@withContext VaultAccessStatus.DirectoryUnavailable

            // 3. 校验目标是否确为目录
            if (!directoryInfo.isDirectory) {
                return@withContext VaultAccessStatus.DirectoryUnavailable
            }

            // 4. 结合写权限与 FLAG_DIR_SUPPORTS_CREATE 判定是否可创建文件
            val hasWritePermission = safAccessor.hasPersistedWritePermission(uri)
            if (hasWritePermission && directoryInfo.supportsCreate) {
                VaultAccessStatus.CanCreateFiles
            } else {
                VaultAccessStatus.ReadOnly
            }
        } catch (e: CancellationException) {
            // 严格保留协程取消语义，不将取消转为普通失败
            throw e
        } catch (e: SecurityException) {
            // 系统或 ContentProvider 权限异常视为需要重新授权
            VaultAccessStatus.NeedsReauthorization
        } catch (e: Throwable) {
            // 其余未知异常统一记录为检查失败
            VaultAccessStatus.Failed(e)
        }
    }
}
