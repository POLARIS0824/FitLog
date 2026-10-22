package com.example.fitlog.today

import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.example.fitlog.ui.components.*

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.fitlog.R
import com.example.fitlog.ui.preview.FitLogPreview
import com.example.fitlog.ui.preview.FitLogPreviews
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun TodayScreen(
    onSettings: () -> Unit,
    onOpenToday: () -> Unit,
    onOpenLog: () -> Unit,
    onImportFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val date = LocalDate.now().format(
        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
    )
    val behavior = rememberFitLogTopAppBarBehavior(expanded = true)
    val windowSize = LocalWindowInfo.current.containerSize
    val windowWidth = with(LocalDensity.current) { windowSize.width.toDp() }
    val fontScale = LocalDensity.current.fontScale
    Column(modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection)) {
        TodayHeader(onSettings, date, behavior,
            if (windowWidth >= FitLogDimens.WideContentWidth && fontScale < 1.5f)
                FitLogDimens.WideContentWidth else FitLogDimens.ContentWidth)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            val horizontalPadding = fitLogPagePadding()
            // Page spacing and widths are design choices, separate from Material component tokens.
            val showSupportingColumn = maxWidth >= FitLogDimens.WideContentWidth && fontScale < 1.5f
            val showIllustration = maxHeight >= 600.dp
            LazyColumn(
                modifier = Modifier.widthIn(max = if (showSupportingColumn) FitLogDimens.WideContentWidth else FitLogDimens.ContentWidth).fillMaxSize(),
                contentPadding = PaddingValues(
                    start = horizontalPadding,
                    top = 16.dp,
                    end = horizontalPadding,
                    bottom = FitLogDimens.TodayBottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                item(key = "content") {
                    if (showSupportingColumn) {
                        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                            TodayTrainingCard(onOpenToday, showIllustration, Modifier.weight(1f))
                            TodayLibrary(onOpenLog, onImportFolder, Modifier.weight(1f))
                        }
                    } else {
                        Column(
                            modifier = Modifier.widthIn(max = FitLogDimens.ContentWidth).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(32.dp),
                        ) {
                            TodayTrainingCard(onOpenToday, showIllustration)
                            TodayLibrary(onOpenLog, onImportFolder)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayTrainingCard(
    onOpenToday: () -> Unit,
    showIllustration: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (showIllustration) TodayTrainingIllustration()
                Text(
                    text = stringResource(R.string.today_hero_title),
                    style = MaterialTheme.typography.displaySmallEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.today_hero_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val largeText = LocalDensity.current.fontScale >= 1.3f
        val height = ButtonDefaults.MediumContainerHeight
        Button(
            onClick = onOpenToday,
            shapes = ButtonDefaults.shapesFor(height),
            contentPadding = if (largeText) PaddingValues(16.dp)
                else ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
            modifier = Modifier.fillMaxWidth().heightIn(min = height),
        ) {
            if (!largeText) {
                Icon(
                    painter = painterResource(R.drawable.note_add_24px),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
                )
                Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(height)))
            }
            Text(
                text = stringResource(R.string.today_write),
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
        }
    }
}

/** A static dumbbell gives the hero a training motif without adding spoken content. */
@Composable
private fun TodayTrainingIllustration() {
    val plates = MaterialTheme.colorScheme.primary
    val bar = MaterialTheme.colorScheme.secondary
    Canvas(Modifier.size(width = 84.dp, height = 56.dp).clearAndSetSemantics {}) {
        scale(scaleX = size.width / 120f, scaleY = size.height / 80f, pivot = Offset.Zero) {
            rotate(degrees = -15f, pivot = Offset(60f, 40f)) {
                drawLine(bar, Offset(8f, 40f), Offset(112f, 40f), strokeWidth = 8f, cap = StrokeCap.Round)
                for (x in listOf(26f, 78f)) {
                    drawRoundRect(plates, Offset(x, 12f), Size(16f, 56f), CornerRadius(6f))
                }
                for (x in listOf(12f, 96f)) {
                    drawRoundRect(plates, Offset(x, 24f), Size(12f, 32f), CornerRadius(4f))
                }
            }
        }
    }
}

@Composable
private fun TodayLibrary(
    onOpenLog: () -> Unit,
    onImportFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(R.string.today_library),
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp).semantics { heading() },
        )
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            SegmentedListItem(
                onClick = onOpenLog,
                shapes = ListItemDefaults.segmentedShapes(0, 2),
                colors = ListItemDefaults.segmentedColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                leadingContent = {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.list_alt_24px),
                            contentDescription = null,
                            modifier = Modifier.padding(12.dp).size(24.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                },
                supportingContent = { Text(stringResource(R.string.today_browse_description)) },
            ) {
                Text(stringResource(R.string.today_browse), style = MaterialTheme.typography.titleMediumEmphasized)
            }
            SegmentedListItem(
                onClick = onImportFolder,
                shapes = ListItemDefaults.segmentedShapes(1, 2),
                colors = ListItemDefaults.segmentedColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.drive_folder_upload_24px),
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp).size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                supportingContent = { Text(stringResource(R.string.today_import_description)) },
            ) {
                Text(stringResource(R.string.nav_action_import_folder), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@FitLogPreviews
@Preview(name = "Small phone", locale = "en", widthDp = 320, heightDp = 640, showBackground = true)
@Preview(name = "Large text", locale = "en", widthDp = 360, heightDp = 800, fontScale = 2f, showBackground = true)
@Preview(name = "Wide window", locale = "en", widthDp = 1024, heightDp = 768, showBackground = true)
@Composable
private fun TodayPreview() {
    FitLogPreview { TodayScreen({}, {}, {}, {}) }
}
