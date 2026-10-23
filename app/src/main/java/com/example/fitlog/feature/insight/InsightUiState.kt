package com.example.fitlog.feature.insight

import com.example.fitlog.data.vault.VaultConfigState
import com.example.fitlog.domain.insight.InsightData
import com.example.fitlog.domain.insight.InsightRange
import com.example.fitlog.model.source.SourceIndexSnapshot

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
