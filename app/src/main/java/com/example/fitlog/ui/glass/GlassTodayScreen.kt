package com.example.fitlog.ui.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import com.example.fitlog.R
import com.example.fitlog.ui.theme.FitLogTheme
import com.example.fitlog.ui.theme.GlassColors

@Composable
internal fun GlassTodayScreen(date: String, onSettings: () -> Unit, onOpenToday: () -> Unit,
    onOpenLog: () -> Unit, onImport: () -> Unit, modifier: Modifier = Modifier) {
    val glass = FitLogTheme.dimens.glass
    val spacing = FitLogTheme.spacing
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = FitLogTheme.dimens.page.contentWidth).fillMaxSize().glassPageSource(),
            contentPadding = PaddingValues(start = glass.pageGutter, end = glass.pageGutter, top = glass.pageGutter,
                bottom = glass.todayBottomPadding),
            verticalArrangement = Arrangement.spacedBy(glass.todaySectionGap)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(glass.todayHeaderGap)) {
                        Text(stringResource(R.string.today_title), style = MaterialTheme.typography.headlineLargeEmphasized,
                            modifier = Modifier.semantics { heading() })
                        Text(date, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    GlassIconButton(R.drawable.settings_24px, stringResource(R.string.settings_title), onSettings)
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    val primary = MaterialTheme.colorScheme.primary
                    val plateColor = MaterialTheme.colorScheme.primaryContainer
                    val glow = GlassColors.todayHeroGlow(primary)
                    Column(Modifier.padding(spacing.xl), verticalArrangement = Arrangement.spacedBy(glass.todayHeroGap)) {
                        Canvas(Modifier.fillMaxWidth().height(glass.todayIllustrationHeight).clearAndSetSemantics {}) {
                            val center = Offset(size.width * glass.todayGlowCenterX, size.height * glass.todayGlowCenterY)
                            drawCircle(Brush.radialGradient(listOf(glow, Color.Transparent),
                                center = center, radius = size.height), size.height, center)
                            val unit = size.height / 100f
                            val x = center.x; val y = center.y
                            drawLine(primary, Offset(x - 48 * unit, y + 13 * unit), Offset(x + 48 * unit, y - 13 * unit),
                                strokeWidth = 7 * unit, cap = StrokeCap.Round)
                            for (side in listOf(-1f, 1f)) {
                                val at = x + side * 31 * unit
                                val cy = y - side * 9 * unit
                                drawLine(plateColor, Offset(at - 6 * unit, cy - 21 * unit), Offset(at + 6 * unit, cy + 21 * unit),
                                    strokeWidth = 13 * unit, cap = StrokeCap.Round)
                            }
                        }
                        Text(stringResource(R.string.today_hero_title), style = MaterialTheme.typography.displaySmallEmphasized,
                            modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.today_hero_description), style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        GlassButton(stringResource(R.string.today_write), onOpenToday, Modifier.fillMaxWidth(),
                            icon = R.drawable.note_add_24px, accent = true)
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(glass.todayLibraryGap)) {
                    Text(stringResource(R.string.today_library), style = MaterialTheme.typography.titleMediumEmphasized,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { heading() })
                    GlassLibraryRow(R.drawable.list_alt_24px, stringResource(R.string.today_browse),
                        stringResource(R.string.today_browse_description), onOpenLog)
                    GlassLibraryRow(R.drawable.drive_folder_upload_24px, stringResource(R.string.nav_action_import_folder),
                        stringResource(R.string.today_import_description), onImport)
                }
            }
        }
    }
}

@Composable
private fun GlassLibraryRow(icon: Int, title: String, subtitle: String, onClick: () -> Unit) {
    val spacing = FitLogTheme.spacing
    GlassCard(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(FitLogTheme.dimens.glass.todayLibraryRowPadding), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.l)) {
            GlassIconButton(icon, title, onClick)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
