package com.example.fitlog.feature.today

import com.example.fitlog.feature.today.components.TodayHeader
import com.example.fitlog.feature.today.components.TodayLibrary
import com.example.fitlog.feature.today.components.TodayTrainingCard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import com.example.fitlog.ui.components.rememberFitLogTopAppBarBehavior
import com.example.fitlog.ui.theme.FitLogTheme
import com.example.fitlog.ui.theme.fitLogPagePadding
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import com.example.fitlog.data.settings.UiLook
import com.example.fitlog.ui.glass.LocalUiLook
import com.example.fitlog.ui.glass.GlassTodayScreen
import com.example.fitlog.ui.glass.glassPageSource

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
    if (LocalUiLook.current == UiLook.LIQUID_GLASS) {
        GlassTodayScreen(date, onSettings, onOpenToday, onOpenLog, onImportFolder, modifier)
        return
    }
    val spacing = FitLogTheme.spacing
    val page = FitLogTheme.dimens.page
    val today = FitLogTheme.dimens.today
    val behavior = rememberFitLogTopAppBarBehavior()
    val windowSize = LocalWindowInfo.current.containerSize
    val windowWidth = with(LocalDensity.current) { windowSize.width.toDp() }
    val fontScale = LocalDensity.current.fontScale
    val allowTwoColumns = fontScale < today.singleColumnFontScale
    Column(modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection)) {
        TodayHeader(onSettings, date, behavior,
            if (windowWidth >= page.wideContentWidth && allowTwoColumns) page.wideContentWidth else page.contentWidth)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            val horizontalPadding = fitLogPagePadding()
            // Page spacing and widths are design choices, separate from Material component tokens.
            val showSupportingColumn = maxWidth >= page.wideContentWidth && allowTwoColumns
            val showIllustration = maxHeight >= today.illustrationMinHeight
            LazyColumn(
                modifier = Modifier.widthIn(max = if (showSupportingColumn) page.wideContentWidth else page.contentWidth).fillMaxSize().glassPageSource(),
                contentPadding = PaddingValues(
                    start = horizontalPadding,
                    top = spacing.l,
                    end = horizontalPadding,
                    bottom = page.todayBottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(spacing.xxl),
            ) {
                item(key = "content") {
                    if (showSupportingColumn) {
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xxl)) {
                            TodayTrainingCard(onOpenToday, showIllustration, Modifier.weight(1f))
                            TodayLibrary(onOpenLog, onImportFolder, Modifier.weight(1f))
                        }
                    } else {
                        Column(
                            modifier = Modifier.widthIn(max = page.contentWidth).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(spacing.xxl),
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
