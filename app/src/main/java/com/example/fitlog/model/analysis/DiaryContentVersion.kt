package com.example.fitlog.model.analysis

import kotlinx.serialization.Serializable

@Serializable
data class DiaryContentVersion(val contentHash: String, val hashVersion: Int) {
    init {
        require(contentHash.matches(Regex("[0-9a-f]{64}")))
        require(hashVersion > 0)
    }
    companion object
}

enum class ConfirmationFreshness { UNCONFIRMED, CONFIRMED, NEEDS_UPDATE, UNVERIFIABLE }
