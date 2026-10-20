package com.example.fitlog.records

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.fitlog.R
import com.example.fitlog.data.analysis.*
import com.example.fitlog.data.vault.MarkdownFile
import com.example.fitlog.log.LogScreen
import com.example.fitlog.log.LogViewModel
import com.example.fitlog.log.LogTopAppBar
import com.example.fitlog.ui.components.FitLogNotice
import com.example.fitlog.ui.preview.FitLogPreview
import kotlinx.coroutines.CancellationException

@Composable
internal fun RecordsLogScreen(vm: LogViewModel, repository: TrainingRecordRepository,
    onOpenNative: (String?) -> Unit, onOpenMarkdown: (String, MarkdownFile) -> Unit,
    onConnect: () -> Unit, onManage: () -> Unit, onReview: () -> Unit, onAiSettings: () -> Unit,
    markdown: Boolean, onMarkdown: (Boolean) -> Unit) {
    var records by remember { mutableStateOf<List<TrainingRecordDetails>>(emptyList()) }
    var error by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(repository, retry) {
        error = false
        try { repository.observeRecords().collect { rows -> records = rows.filter { it.diary.origin == RecordOrigin.NATIVE } } }
        catch (e: Exception) { if (e is CancellationException) throw e; error = true }
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        RecordsLogHeader(markdown, vm.refreshing, onMarkdown, { vm.refresh() }, onConnect, onManage)
        if (markdown) LogScreen(vm, onOpenMarkdown, onConnect, onManage,
            modifier = Modifier.weight(1f), onReview = onReview, onAiSettings = onAiSettings, showAppBar = false)
        else LazyColumn(Modifier.widthIn(max = 840.dp).fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(stringResource(R.string.record_ui_list_subtitle), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                Button(onClick = { onOpenNative(null) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
                    shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                    contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight)) {
                    Icon(painterResource(R.drawable.add_24px), null, Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MediumContainerHeight)))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.record_new), style = MaterialTheme.typography.labelLargeEmphasized)
                }
            }
            if (error) item {
                FitLogNotice(stringResource(R.string.record_read_failed), error = true)
                TextButton(onClick = { retry++ }) { Text(stringResource(R.string.record_retry)) }
            }
            if (!error && records.isEmpty()) item { FitLogNotice(stringResource(R.string.record_no_records)) }
            items(records, key = { it.diary.id }) { record ->
                Surface(onClick = { onOpenNative(record.diary.id) }, shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(record.diary.date, style = MaterialTheme.typography.titleLargeEmphasized)
                        Text(stringResource(R.string.record_source_native), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val names = record.sessions.flatMap { it.exercises }.map { it.exercise.rawName.trim() }.filter { it.isNotEmpty() }
                        Text(when {
                            record.diary.kind == RecordKind.REST -> stringResource(R.string.record_rest)
                            names.isEmpty() -> stringResource(R.string.record_incomplete)
                            else -> names.joinToString(stringResource(R.string.record_name_separator))
                        }, style = MaterialTheme.typography.bodyLarge)
                        if (record.diary.kind == RecordKind.TRAINING) {
                            val validSets = record.sessions.flatMap { it.exercises }.sumOf { exercise ->
                                if (exercise.exercise.rawName.isBlank()) 0 else exercise.sets.count { (it.reps ?: 0) > 0 }
                            }
                            if (validSets > 0) Text(pluralStringResource(R.plurals.record_ui_completed_sets, validSets, validSets),
                                style = MaterialTheme.typography.labelLargeEmphasized,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        if (record.diary.kind == RecordKind.TRAINING && record.sessions.flatMap { it.exercises }
                                .none { it.exercise.rawName.isNotBlank() && it.sets.any { set -> (set.reps ?: 0) > 0 } }) {
                            Text(stringResource(R.string.record_incomplete), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordsLogHeader(markdown: Boolean, refreshing: Boolean, onMarkdown: (Boolean) -> Unit,
    onRefresh: () -> Unit, onConnect: () -> Unit, onManage: () -> Unit) {
    LogTopAppBar(markdown, refreshing, onRefresh, onConnect, onManage)
    RecordSourceSelector(markdown, onMarkdown)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RecordSourceSelector(markdown: Boolean, onMarkdown: (Boolean) -> Unit) {
    BoxWithConstraints(Modifier.widthIn(max = 840.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
          val labels = listOf(stringResource(R.string.record_native), stringResource(R.string.record_markdown))
          val style = MaterialTheme.typography.labelLargeEmphasized
          val measurer = rememberTextMeasurer()
          val density = LocalDensity.current
          val itemWidth = maxOf((maxWidth - ButtonGroupDefaults.ConnectedSpaceBetween) / 2,
              labels.maxOf { with(density) { measurer.measure(it, style, maxLines = 1).size.width.toDp() } } + 32.dp)
          Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
              horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            listOf(false, true).forEachIndexed { index, source ->
                FilledTonalToggleButton(checked = markdown == source, onCheckedChange = { onMarkdown(source) },
                    modifier = Modifier.width(itemWidth).heightIn(min = 48.dp).semantics { role = Role.RadioButton },
                    shapes = if (index == 0) ButtonGroupDefaults.connectedLeadingButtonShapes()
                        else ButtonGroupDefaults.connectedTrailingButtonShapes()) {
                    Text(stringResource(if (source) R.string.record_markdown else R.string.record_native),
                        style = if (markdown == source) MaterialTheme.typography.labelLargeEmphasized
                            else MaterialTheme.typography.labelLarge,
                        maxLines = 1, softWrap = false)
                }
            }
          }
    }
}

@Preview(name = "Record sources", widthDp = 412, heightDp = 400, locale = "en")
@Composable
private fun RecordSourcesReviewPreview() {
    FitLogPreview {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            RecordSourceSelector(markdown = false, onMarkdown = {})
            RecordSourceSelector(markdown = true, onMarkdown = {})
        }
    }
}

@Preview(name = "Log app bar · Local records", widthDp = 412, heightDp = 240, locale = "en")
@Composable
private fun RecordsLogHeaderReviewPreview() {
    FitLogPreview { Column { RecordsLogHeader(false, false, {}, {}, {}, {}) } }
}

@Preview(name = "Log app bar · Markdown", widthDp = 412, heightDp = 240, locale = "en")
@Composable
private fun RecordsLogMarkdownHeaderReviewPreview() {
    FitLogPreview { Column { RecordsLogHeader(true, false, {}, {}, {}, {}) } }
}

@Preview(name = "Record sources · Large text", widthDp = 320, heightDp = 560, fontScale = 2f, locale = "zh")
@Composable
private fun RecordSourcesLargeTextReviewPreview() {
    RecordSourcesReviewPreview()
}
