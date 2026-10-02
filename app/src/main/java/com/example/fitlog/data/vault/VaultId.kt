package com.example.fitlog.data.vault

import java.util.UUID

/** Persistent application identity; a SAF URI is only the directory locator. */
fun requireVaultId(value: String): String {
    require(UUID.fromString(value).toString() == value) { "Vault identity must be a canonical UUID" }
    return value
}
