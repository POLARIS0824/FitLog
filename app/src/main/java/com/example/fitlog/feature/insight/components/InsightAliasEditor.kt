@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.fitlog.feature.insight.components

import com.example.fitlog.feature.insight.InsightUiState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.example.fitlog.R
import com.example.fitlog.domain.insight.InsightData
import com.example.fitlog.model.record.ExerciseAliasGroup
import com.example.fitlog.ui.components.FitLogNotice
import com.example.fitlog.ui.components.FitLogSectionTitle
import com.example.fitlog.ui.components.FitLogSheetActions
import com.example.fitlog.ui.components.FitLogSheetDefaults
import com.example.fitlog.ui.components.FitLogSheetTitle
import com.example.fitlog.ui.theme.FitLogContainment
import com.example.fitlog.ui.theme.FitLogTheme

@Composable
internal fun AliasEditor(state: InsightUiState, data: InsightData, nameKey: String,
    onSave: (String?, ExerciseAliasGroup?) -> Unit, onDismiss: () -> Unit) {
    val existing = data.aliases.groupFor(nameKey)
    var chosen by rememberSaveable(nameKey) { mutableStateOf(existing?.members ?: listOf(nameKey)) }
    var primary by rememberSaveable(nameKey) { mutableStateOf(existing?.primaryKey ?: nameKey) }
    val names = data.names + mapOf(nameKey to (data.names[nameKey] ?: nameKey))
    val options = names.entries.sortedBy { it.value }
    val dismiss = { if (!state.aliasSaving) onDismiss() }
    val spacing = FitLogTheme.spacing
    val sheetPadding = FitLogTheme.dimens.page.sheetPadding
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        confirmValueChange = { !state.aliasSaving || it != SheetValue.Hidden })) {
        Column(Modifier.fillMaxWidth().heightIn(max = FitLogTheme.dimens.insight.formSheetMaxHeight).navigationBarsPadding()) {
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), contentPadding = FitLogSheetDefaults.ContentPadding,
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                item {
                    Column(Modifier.padding(bottom = spacing.m), verticalArrangement = Arrangement.spacedBy(spacing.m)) {
                        FitLogSheetTitle(stringResource(R.string.insight_manage_aliases))
                        Text(stringResource(R.string.insight_alias_help), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                itemsIndexed(options, key = { _, entry -> "member-${entry.key}" }) { index, (key, name) ->
                    val owner = data.aliases.groupFor(key)
                    val occupied = owner != null && owner.primaryKey != existing?.primaryKey
                    SegmentedListItem(checked = key in chosen, shapes = ListItemDefaults.segmentedShapes(index, options.size), onCheckedChange = { checked ->
                        val changed = if (checked) chosen + key else chosen - key
                        chosen = changed
                        if (primary !in changed) primary = changed.first()
                    }, enabled = !state.aliasSaving && !occupied && !(chosen.size == 1 && key in chosen),
                        colors = FitLogContainment.sheetListItemColors(),
                        content = { Text(name) }, supportingContent = {
                            if (occupied) Text(stringResource(R.string.insight_alias_owned, requireNotNull(owner).primaryName))
                        }, trailingContent = { Checkbox(key in chosen, onCheckedChange = null, enabled = !occupied && !state.aliasSaving) })
                }
                item { Box(Modifier.padding(top = spacing.m)) { FitLogSectionTitle(stringResource(R.string.insight_alias_primary)) } }
                itemsIndexed(chosen.toList(), key = { _, key -> "primary-$key" }) { index, key ->
                    SegmentedListItem(onClick = { primary = key }, enabled = !state.aliasSaving,
                        shapes = ListItemDefaults.segmentedShapes(index, chosen.size),
                        modifier = Modifier.semantics { role = Role.RadioButton; selected = primary == key },
                        colors = FitLogContainment.sheetListItemColors(),
                        content = { Text(names[key] ?: key) },
                        trailingContent = { RadioButton(primary == key, onClick = null, enabled = !state.aliasSaving) })
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = sheetPadding).padding(top = spacing.m, bottom = sheetPadding),
                verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                state.aliasError?.let { FitLogNotice(stringResource(it), error = true) }
                FitLogSheetActions(dismiss, onConfirm = { onSave(existing?.primaryKey,
                    ExerciseAliasGroup(names[primary] ?: primary, chosen.sorted())) },
                    enabled = !state.aliasSaving, dismissEnabled = !state.aliasSaving,
                    confirmLabel = stringResource(R.string.insight_alias_save))
                if (existing != null) TextButton(onClick = { onSave(existing.primaryKey, null) }, enabled = !state.aliasSaving, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.insight_alias_dissolve))
                }
            }
        }
    }
}
