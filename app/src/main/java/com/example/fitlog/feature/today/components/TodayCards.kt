package com.example.fitlog.feature.today.components


import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.example.fitlog.R
import com.example.fitlog.ui.theme.FitLogContainment
import com.example.fitlog.ui.theme.FitLogTheme

@Composable
internal fun TodayTrainingCard(
    onOpenToday: () -> Unit,
    showIllustration: Boolean,
    modifier: Modifier = Modifier,
) {
    val spacing = FitLogTheme.spacing
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = FitLogContainment.heroShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.l),
        ) {
            if (showIllustration) TodayTrainingIllustration()
            Text(
                text = stringResource(R.string.today_hero_title),
                style = MaterialTheme.typography.displaySmallEmphasized,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.today_hero_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            val largeText = LocalDensity.current.fontScale >= FitLogTheme.dimens.today.compactButtonFontScale
            val height = ButtonDefaults.MediumContainerHeight
            Button(
                onClick = onOpenToday,
                shapes = ButtonDefaults.shapesFor(height),
                contentPadding = if (largeText) PaddingValues(spacing.l)
                    else ButtonDefaults.contentPaddingFor(height),
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
}

/** A static dumbbell gives the hero a training motif without adding spoken content. */
@Composable
private fun TodayTrainingIllustration() {
    val plates = MaterialTheme.colorScheme.primary
    val bar = MaterialTheme.colorScheme.secondary
    val dimens = FitLogTheme.dimens.today
    Canvas(Modifier.size(width = dimens.illustrationWidth, height = dimens.illustrationHeight).clearAndSetSemantics {}) {
        // Drawing coordinates live in a 120 x 80 artboard scaled to the canvas.
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
internal fun TodayLibrary(
    onOpenLog: () -> Unit,
    onImportFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FitLogTheme.spacing.l)) {
        Text(
            text = stringResource(R.string.today_library),
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = FitLogTheme.spacing.s).semantics { heading() },
        )
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            SegmentedListItem(
                onClick = onOpenLog,
                shapes = ListItemDefaults.segmentedShapes(0, 2),
                colors = FitLogContainment.pageListItemColors(),
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.list_alt_24px),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                supportingContent = { Text(stringResource(R.string.today_browse_description)) },
            ) {
                Text(stringResource(R.string.today_browse), style = MaterialTheme.typography.titleMedium)
            }
            SegmentedListItem(
                onClick = onImportFolder,
                shapes = ListItemDefaults.segmentedShapes(1, 2),
                colors = FitLogContainment.pageListItemColors(),
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.drive_folder_upload_24px),
                        contentDescription = null,
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
