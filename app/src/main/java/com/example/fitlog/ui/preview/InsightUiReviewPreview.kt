package com.example.fitlog.ui.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.fitlog.R
import com.example.fitlog.data.analysis.ConfirmedExerciseRecord
import com.example.fitlog.data.analysis.ConfirmedExerciseRow
import com.example.fitlog.data.analysis.ConfirmedSessionRecord
import com.example.fitlog.data.analysis.ConfirmedSessionRow
import com.example.fitlog.data.analysis.ConfirmedSetRow
import com.example.fitlog.data.analysis.RecordOrigin
import com.example.fitlog.data.analysis.TrainingRecord
import com.example.fitlog.data.analysis.TrainingRecordDetails
import com.example.fitlog.data.analysis.WeightBasis
import com.example.fitlog.data.analysis.WeightUnit
import com.example.fitlog.data.index.IndexedScan
import com.example.fitlog.data.insight.ExerciseAliases
import com.example.fitlog.insight.InsightChart
import com.example.fitlog.insight.InsightData
import com.example.fitlog.insight.InsightDashboardContent
import com.example.fitlog.insight.InsightProgressCard
import com.example.fitlog.insight.InsightWeeklyTrends
import com.example.fitlog.insight.InsightHeatmap
import com.example.fitlog.insight.InsightPoint
import com.example.fitlog.insight.InsightRange
import com.example.fitlog.insight.InsightRangePicker
import com.example.fitlog.insight.InsightSeries
import com.example.fitlog.insight.buildInsightData
import com.example.fitlog.insight.overview
import com.example.fitlog.insight.focused
import com.example.fitlog.log.LogActions
import com.example.fitlog.log.LogContent
import com.example.fitlog.log.LogDiaryStatus
import com.example.fitlog.log.LogUiState
import com.example.fitlog.ui.components.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.example.fitlog.ui.components.FitLogSectionTitle
import java.time.LocalDate
import java.util.UUID

/** Component review only. Fixtures stay in memory and never create a ViewModel or open storage. */
@FitLogPreviews
@Composable
internal fun InsightDashboardReviewPreview() {
    FitLogPreview { InsightDashboardContent(previewInsightData(), InsightRange.THIRTY) }
}

@Preview(name = "Insights · Empty", widthDp = 412, heightDp = 892, locale = "zh")
@Composable
internal fun InsightDashboardEmptyReviewPreview() {
    FitLogPreview { InsightDashboardContent(previewInsightData(empty = true), InsightRange.THIRTY) }
}

@Preview(name = "Insights · 320dp · 200%", widthDp = 320, heightDp = 1100, fontScale = 2f, locale = "zh")
@Composable
internal fun InsightDashboardLargeTextReviewPreview() {
    FitLogPreview { InsightDashboardContent(previewInsightData(), InsightRange.THIRTY) }
}

@Preview(name = "Insights · First day", widthDp = 412, heightDp = 1100, locale = "en")
@Composable
internal fun InsightBaselineReviewPreview() {
    FitLogPreview { InsightDashboardContent(previewInsightData(singleDay = true), InsightRange.THIRTY) }
}

@FitLogPreviews
@Composable
internal fun InsightBodyweightReviewPreview() {
    FitLogPreview {
        val progress = previewInsightData(basis = WeightBasis.BODYWEIGHT).overview(InsightRange.THIRTY).focused(null)
        Column(Modifier.padding(24.dp)) { progress.forEach { InsightProgressCard(it) {} } }
    }
}

@FitLogPreviews
@Composable
internal fun InsightAssistedReviewPreview() {
    FitLogPreview {
        val progress = previewInsightData(basis = WeightBasis.ASSISTED).overview(InsightRange.THIRTY).focused(null)
        Column(Modifier.padding(24.dp)) { progress.forEach { InsightProgressCard(it) {} } }
    }
}

@FitLogPreviews
@Preview(name = "Progress · 320dp · 200%", widthDp = 320, heightDp = 1100, fontScale = 2f, locale = "zh")
@Composable
internal fun InsightProgressReviewPreview() {
    FitLogPreview {
        val progress = previewInsightData().overview(InsightRange.THIRTY).focused(null)
        LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            progress.forEach { item { InsightProgressCard(it) {} } }
        }
    }
}

@FitLogPreviews
@Preview(name = "Weekly · 320dp · 200%", widthDp = 320, heightDp = 1100, fontScale = 2f, locale = "zh")
@Composable
internal fun InsightWeeklyReviewPreview() {
    FitLogPreview {
        val overview = previewInsightData().overview(InsightRange.THIRTY)
        LazyColumn(contentPadding = PaddingValues(24.dp)) { item { InsightWeeklyTrends(overview.weeks) } }
    }
}

@Preview(name = "Activity · Populated", widthDp = 412, heightDp = 892, locale = "en")
@Composable
internal fun InsightActivityReviewPreview() {
    FitLogPreview { InsightActivityReviewContent(previewInsightData()) }
}

@Preview(name = "Activity · Empty", widthDp = 412, heightDp = 892, locale = "en")
@Composable
internal fun InsightActivityEmptyReviewPreview() {
    FitLogPreview { InsightActivityReviewContent(previewInsightData(empty = true)) }
}

@Preview(name = "Activity · 320dp · 200%", widthDp = 320, heightDp = 892, fontScale = 2f, locale = "zh")
@Composable
internal fun InsightActivityLargeTextReviewPreview() {
    FitLogPreview { InsightActivityReviewContent(previewInsightData()) }
}

@Composable
private fun InsightActivityReviewContent(data: InsightData) {
    val behavior = rememberFitLogTopAppBarBehavior(expanded = true)
    Column(Modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection)) {
        FitLogTopAppBar(stringResource(R.string.insight_title), behavior, expanded = true)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(fitLogPagePadding()),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { InsightRangePicker(InsightRange.THIRTY, {}) }
            item { FitLogSectionTitle(stringResource(R.string.insight_activity)) }
            item { InsightHeatmap(data) }
        }
    }
}

@Preview(name = "Chart · Populated", widthDp = 412, heightDp = 560, locale = "en")
@Composable
internal fun ExerciseChartReviewPreview() {
    FitLogPreview { ExerciseChartReviewContent() }
}

@Preview(name = "Chart · 320dp · 200%", widthDp = 320, heightDp = 760, fontScale = 2f, locale = "zh")
@Composable
internal fun ExerciseChartLargeTextReviewPreview() {
    FitLogPreview { ExerciseChartReviewContent() }
}

@Composable
private fun ExerciseChartReviewContent() {
    val behavior = rememberFitLogTopAppBarBehavior()
    Column(Modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection)) {
        FitLogTopAppBar(stringResource(R.string.preview_exercise_name), behavior, onBack = {})
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(fitLogPagePadding()),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.insight_basis_total), style = MaterialTheme.typography.titleMediumEmphasized)
                    Text(stringResource(R.string.insight_metric_kg), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    InsightChart(InsightSeries(WeightBasis.TOTAL, listOf(40.0, 45.0, 42.5, 50.0).mapIndexed { index, value ->
                        InsightPoint(LocalDate.parse(PreviewDiary.DATE).minusDays((3L - index) * 7), value, emptyList())
                    }), onOpen = {})
                }
            } }
        }
    }
}

@Preview(name = "Markdown · 320dp · 200%", widthDp = 320, heightDp = 1100, fontScale = 2f, locale = "en")
@Composable
internal fun LogLargeTextReviewPreview() {
    FitLogPreview {
        val file = PreviewDiary.file
        LogContent(LogUiState(vault = PreviewDiary.VAULT_URI, files = listOf(file), visibleFiles = listOf(file),
            scanStatus = IndexedScan.COMPLETE, pendingCount = 2, statuses = mapOf(file.path to LogDiaryStatus.PENDING)),
            LogActions(), { _, _ -> }, {}, {})
    }
}

@Composable
private fun previewInsightData(empty: Boolean = false, singleDay: Boolean = false,
    basis: WeightBasis = WeightBasis.TOTAL): InsightData {
    val today = LocalDate.parse(PreviewDiary.DATE)
    val name = stringResource(if (basis == WeightBasis.TOTAL) R.string.preview_exercise_name else R.string.preview_pullup_name)
    val records = if (empty) emptyList() else (0..if (singleDay) 0 else 72).map { day ->
        val id = UUID.nameUUIDFromBytes("insight-ui-preview-$day".toByteArray()).toString()
        TrainingRecordDetails(
            TrainingRecord(id, null, null, today.minusDays(day * 5L).toString(), null, null, null,
                PreviewDiary.TIMESTAMP, false, origin = RecordOrigin.NATIVE),
            (0..(day % 4)).map { session ->
                val sessionId = "$id-session-$session"
                val exerciseId = "$sessionId-exercise"
                val weight = when (basis) {
                    WeightBasis.BODYWEIGHT -> null
                    WeightBasis.ASSISTED -> 20.0 + day % 6 * 2
                    else -> if (day < 6) 60.0 else 55.0
                }
                ConfirmedSessionRecord(ConfirmedSessionRow(sessionId, id, session, null, null),
                    listOf(ConfirmedExerciseRecord(ConfirmedExerciseRow(exerciseId, sessionId, 0, name, null, null, null),
                        listOf(ConfirmedSetRow("$exerciseId-set", exerciseId, 0, weight,
                            if (weight == null) null else WeightUnit.KG,
                            basis, 10 - day % 6, weight, null, null, false)) +
                            if (basis == WeightBasis.TOTAL) listOf(ConfirmedSetRow("$exerciseId-heavy-single", exerciseId,
                                1, 70.0, WeightUnit.KG, basis, 1, 70.0, null, null, false)) else emptyList())))
            },
        )
    }
    return buildInsightData(null, null, records, ExerciseAliases(), today)
}
