package com.example.fitlog.ui.preview

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fitlog.R
import com.example.fitlog.ui.components.*

@FitLogPreviews
@Composable
private fun TopLevelAppBarReviewPreview() {
    FitLogPreview {
        val behavior = rememberFitLogTopAppBarBehavior(expanded = true)
        Column(Modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection),
            horizontalAlignment = Alignment.CenterHorizontally) {
            FitLogTopAppBar(stringResource(R.string.log_title), behavior, expanded = true)
            LazyColumn(Modifier.weight(1f).widthIn(max = FitLogDimens.WideContentWidth).fillMaxWidth(),
                contentPadding = PaddingValues(fitLogPagePadding()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    FitLogConnectedButtonGroup(listOf(false, true), false,
                        label = { stringResource(if (it) R.string.record_markdown else R.string.record_native) }, onSelect = {})
                }
                item {
                    OutlinedTextField("", {}, label = { Text(stringResource(R.string.log_search)) },
                        modifier = Modifier.fillMaxWidth())
                }
                item {
                    Button(onClick = {}, shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                        modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight)) {
                        Text(stringResource(R.string.record_new), style = MaterialTheme.typography.titleMediumEmphasized)
                    }
                }
            }
        }
    }
}

@FitLogPreviews
@Composable
private fun SecondaryAppBarReviewPreview() {
    FitLogPreview {
        val behavior = rememberFitLogTopAppBarBehavior()
        Column(Modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection),
            horizontalAlignment = Alignment.CenterHorizontally) {
            FitLogTopAppBar(stringResource(R.string.vault_management_title), behavior, maxContentWidth = FitLogDimens.ContentWidth, onBack = {})
            LazyColumn(Modifier.weight(1f).widthIn(max = FitLogDimens.ContentWidth).fillMaxWidth(),
                contentPadding = PaddingValues(fitLogPagePadding())) {
                item { FitLogNotice(stringResource(R.string.vault_management_description)) }
            }
        }
    }
}

@FitLogPreviews
@Composable
private fun SheetStyleReviewPreview() {
    FitLogPreview {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
          LazyColumn(Modifier.widthIn(max = FitLogDimens.ContentWidth).fillMaxWidth(), contentPadding = FitLogSheetDefaults.ContentPadding,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { FitLogSheetTitle(stringResource(R.string.detail_edit_exercise_title)) }
            item { OutlinedTextField("", {}, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.detail_exercise_name)) }) }
            item {
                FitLogConnectedButtonGroup(listOf(false, true), false,
                    label = { stringResource(if (it) R.string.detail_unit_lb_short else R.string.detail_unit_kg_short) }, onSelect = {})
            }
            item { FitLogSheetActions({}, {}, confirmLabel = stringResource(R.string.detail_apply_correction)) }
          }
        }
    }
}
