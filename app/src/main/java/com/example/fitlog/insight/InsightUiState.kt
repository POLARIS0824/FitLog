package com.example.fitlog.insight

import com.example.fitlog.domain.insight.*
import com.example.fitlog.data.index.SourceIndexSnapshot
import com.example.fitlog.data.vault.VaultConfigState

internal data class InsightUiState(
    val config: VaultConfigState = VaultConfigState.Loading,
    val data: InsightData? = null,
    val presentation: InsightPresentation? = null,
    val index: SourceIndexSnapshot? = null,
    val loading: Boolean = true,
    val error: Int? = null,
    val message: Int? = null,
    val aliasSaving: Boolean = false,
    val aliasSaved: Boolean = false,
    val aliasError: Int? = null,
    val focusedKeys: Set<String>? = null,
    val focusSaving: Boolean = false,
    val focusSaved: Boolean = false,
    val focusError: Int? = null,
    val rangeSaving: Boolean = false,
    val range: InsightRange = InsightRange.NINETY,
)
