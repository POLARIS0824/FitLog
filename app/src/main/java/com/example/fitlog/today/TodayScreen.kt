package com.example.fitlog.today

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.fitlog.ui.preview.FitLogPreviews
import com.example.fitlog.ui.preview.FitLogPreview
import androidx.compose.ui.unit.dp
import com.example.fitlog.R
import com.example.fitlog.ui.components.FitLogSectionTitle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.ui.platform.LocalConfiguration

@Composable
fun TodayScreen(
    onSettings: () -> Unit,
    onOpenToday: () -> Unit,
    onOpenLog: () -> Unit,
    onImportFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val date = LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = 640.dp).fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item { TodayHeader(onSettings) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(date, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.today_hero_title), style = MaterialTheme.typography.displaySmallEmphasized)
                    Text(stringResource(R.string.today_hero_description), style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Button(onClick = onOpenToday,
                    shapes = ButtonDefaults.shapesFor(ButtonDefaults.LargeContainerHeight),
                    contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.LargeContainerHeight),
                    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.LargeContainerHeight)) {
                    Icon(painterResource(R.drawable.note_add_24px), null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.today_write), style = MaterialTheme.typography.titleLargeEmphasized)
                }
            }
            item {
                FitLogSectionTitle(stringResource(R.string.today_library))
                SegmentedListItem(onClick = onOpenLog,
                    shapes = ListItemDefaults.segmentedShapes(0, 2),
                    leadingContent = { Icon(painterResource(R.drawable.list_alt_24px), null) },
                    supportingContent = { Text(stringResource(R.string.today_browse_description)) }) {
                    Text(stringResource(R.string.today_browse), style = MaterialTheme.typography.titleMediumEmphasized)
                }
                Spacer(Modifier.height(ListItemDefaults.SegmentedGap))
                SegmentedListItem(onClick = onImportFolder,
                    shapes = ListItemDefaults.segmentedShapes(1, 2),
                    leadingContent = { Icon(painterResource(R.drawable.drive_folder_upload_24px), null) },
                    supportingContent = { Text(stringResource(R.string.today_import_description)) }) {
                    Text(stringResource(R.string.nav_action_import_folder))
                }
            }
        }
    }
}

@FitLogPreviews
@Composable
private fun TodayPreview() {
    FitLogPreview { TodayScreen({}, {}, {}, {}) }
}
