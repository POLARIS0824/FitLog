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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.fitlog.R
import com.example.fitlog.domain.insight.InsightData
import com.example.fitlog.domain.insight.InsightPoint
import com.example.fitlog.domain.insight.InsightRange
import com.example.fitlog.domain.insight.InsightSeries
import com.example.fitlog.domain.insight.buildInsightData
import com.example.fitlog.feature.insight.InsightChart
import com.example.fitlog.feature.insight.InsightDashboardContent
import com.example.fitlog.feature.insight.prepareInsight
import com.example.fitlog.model.record.ConfirmedExercise
import com.example.fitlog.model.record.ConfirmedExerciseRecord
import com.example.fitlog.model.record.ConfirmedSession
import com.example.fitlog.model.record.ConfirmedSessionRecord
import com.example.fitlog.model.record.ConfirmedSet
import com.example.fitlog.model.record.ExerciseAliases
import com.example.fitlog.model.record.RecordOrigin
import com.example.fitlog.model.record.TrainingRecord
import com.example.fitlog.model.record.TrainingRecordDetails
import com.example.fitlog.model.record.WeightBasis
import com.example.fitlog.model.record.WeightUnit
import com.example.fitlog.ui.components.FitLogTopAppBar
import com.example.fitlog.ui.components.fitLogPagePadding
import com.example.fitlog.ui.components.rememberFitLogTopAppBarBehavior
import java.time.LocalDate
import java.util.UUID

@Preview(locale = "en", widthDp = 412, heightDp = 892, showBackground = true)
@Composable
private fun InsightDashboardReviewPreview() {
    FitLogPreview { InsightDashboardContent(prepareInsight(previewInsightData(), InsightRange.THIRTY), InsightRange.THIRTY) }
}

@Preview(locale = "en", widthDp = 412, heightDp = 892, showBackground = true)
@Composable
private fun InsightDashboardEmptyReviewPreview() {
    FitLogPreview { InsightDashboardContent(prepareInsight(previewInsightData(empty = true), InsightRange.THIRTY), InsightRange.THIRTY) }
}

@Preview(locale = "en", widthDp = 412, heightDp = 892, showBackground = true)
@Composable
private fun ExerciseChartReviewPreview() {
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

@Composable
private fun previewInsightData(empty: Boolean = false): InsightData {
    val today = LocalDate.parse(PreviewDiary.DATE)
    val name = stringResource(R.string.preview_exercise_name)
    val records = if (empty) emptyList() else (0..72).map { day ->
        val id = UUID.nameUUIDFromBytes("insight-ui-preview-$day".toByteArray()).toString()
        TrainingRecordDetails(
            TrainingRecord(id, null, null, today.minusDays(day * 5L).toString(), null, null, null,
                PreviewDiary.TIMESTAMP, false, origin = RecordOrigin.NATIVE),
            (0..(day % 4)).map { session ->
                val sessionId = "$id-session-$session"
                val exerciseId = "$sessionId-exercise"
                val weight = if (day < 6) 60.0 else 55.0
                ConfirmedSessionRecord(ConfirmedSession(sessionId, id, session, null, null),
                    listOf(ConfirmedExerciseRecord(ConfirmedExercise(exerciseId, sessionId, 0, name, null, null, null),
                        listOf(
                            ConfirmedSet("$exerciseId-set", exerciseId, 0, weight, WeightUnit.KG,
                                WeightBasis.TOTAL, 10 - day % 6, weight, null, null, false),
                            ConfirmedSet("$exerciseId-heavy-single", exerciseId, 1, 70.0, WeightUnit.KG,
                                WeightBasis.TOTAL, 1, 70.0, null, null, false),
                        ))))
            },
        )
    }
    return buildInsightData(null, null, records, ExerciseAliases(), today)
}
