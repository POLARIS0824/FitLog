package com.example.fitlog.data.vault

import java.util.UUID

/**
 * Persistent application identity; a SAF URI is only the directory locator.
 *
 * 集中校验 vaultId 是否是合法 UUID，避免各处重复写身份验证逻辑
 */
fun requireVaultId(value: String): String {
    require(UUID.fromString(value).toString() == value) { "Vault identity must be a canonical UUID" }
    return value
}
