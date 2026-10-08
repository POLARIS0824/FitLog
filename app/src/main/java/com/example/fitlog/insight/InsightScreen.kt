package com.example.fitlog.insight

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fitlog.ui.preview.FitLogPreviews
import com.example.fitlog.ui.preview.FitLogPreview
import com.example.fitlog.R
import com.example.fitlog.ui.components.FitLogPageHeader

@Composable
fun InsightScreen(onOpenLog: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 640.dp).fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp)) {
            item { FitLogPageHeader(stringResource(R.string.insight_title)) }
            item {
                Surface(shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.tertiaryContainer) {
                            Icon(painterResource(R.drawable.analytics_24px), null,
                                modifier = Modifier.padding(16.dp).size(32.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                        Text(stringResource(R.string.insight_unavailable_title), style = MaterialTheme.typography.headlineSmallEmphasized)
                        Text(stringResource(R.string.insight_unavailable_description), style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FilledTonalButton(onClick = onOpenLog,
                            shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                            modifier = Modifier.heightIn(min = ButtonDefaults.MediumContainerHeight)) {
                            Text(stringResource(R.string.today_browse), style = MaterialTheme.typography.labelLargeEmphasized)
                        }
                    }
                }
            }
        }
    }
}

@FitLogPreviews
@Composable
private fun InsightPreview() {
    FitLogPreview { InsightScreen({}) }
}
