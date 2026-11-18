package com.example.fitlog.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.example.fitlog.data.settings.UiLook
import com.example.fitlog.ui.glass.LocalUiLook
import com.example.fitlog.ui.glass.LookSurface
import com.example.fitlog.ui.glass.glassPageSource
import com.example.fitlog.ui.theme.FitLogContainment
import com.example.fitlog.ui.theme.FitLogTheme
import com.example.fitlog.ui.theme.fitLogPagePadding

/** The app owns system insets; this frame owns the app bar and one scrolling content list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FitLogSettingsPage(
    title: String,
    onBack: () -> Unit,
    backEnabled: Boolean = true,
    loading: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val behavior = rememberFitLogTopAppBarBehavior()
    val pagePadding = fitLogPagePadding()
    val spacing = FitLogTheme.spacing
    val contentWidth = FitLogTheme.dimens.page.contentWidth
    Column(Modifier.fillMaxSize().background(if (LocalUiLook.current == UiLook.LIQUID_GLASS)
        Color.Transparent else MaterialTheme.colorScheme.surface)
        .nestedScroll(behavior.nestedScrollConnection), horizontalAlignment = Alignment.CenterHorizontally) {
        FitLogTopAppBar(title, behavior, maxContentWidth = contentWidth,
            onBack = onBack, backEnabled = backEnabled, actions = actions)

        Box(Modifier.weight(1f).widthIn(max = contentWidth).fillMaxWidth().imePadding()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().glassPageSource(),
                contentPadding = PaddingValues(start = pagePadding, end = pagePadding, top = spacing.l, bottom = spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(spacing.xl),
                content = content,
            )
            FitLogLoadingIndicator(loading, Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = pagePadding))
        }
    }
}

@Composable
internal fun SettingsSectionTitle(title: String) {
    val spacing = FitLogTheme.spacing
    Text(title, style = MaterialTheme.typography.titleMediumEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = spacing.l, bottom = spacing.m).semantics { heading() })
}

/**
 * One contained group of related settings, one tonal step above the page. Rows inside it run edge
 * to edge, so the group uses the list container radius rather than a card's.
 */
@Composable
internal fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    LookSurface(shape = FitLogContainment.listGroupShape,
        color = FitLogContainment.listGroupColor,
        modifier = Modifier.fillMaxWidth()) {
        Column(content = content)
    }
}

/** A form on the settings page: a filled Material card; the Liquid Glass look keeps its glass group. */
@Composable
internal fun SettingsFormCard(content: @Composable ColumnScope.() -> Unit) {
    val spacing = FitLogTheme.spacing
    val form: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.fillMaxWidth().padding(spacing.l), verticalArrangement = Arrangement.spacedBy(spacing.m),
            content = content)
    }
    if (LocalUiLook.current == UiLook.LIQUID_GLASS) SettingsGroup(form)
    else Card(Modifier.fillMaxWidth(), content = form)
}

/** A navigation row inside [SettingsGroup]: Material's interactive list item on the group's surface. */
@Composable
internal fun SettingsEntry(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    // One neutral icon container for every row: per-row accent colors carry no meaning here.
    val (container, foreground) = scheme.surfaceContainerHighest to scheme.onSurfaceVariant
    ListItem(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        leadingContent = {
            Box(Modifier.size(FitLogTheme.dimens.components.settingsEntryIconContainerSize).background(container, CircleShape),
                contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), null, tint = foreground)
            }
        },
        trailingContent = trailing,
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodyMedium) },
        // The group owns the fill. Naming contentColor selects the interactive ListItem overload.
        colors = ListItemDefaults.colors(containerColor = Color.Transparent, contentColor = scheme.onSurface,
            supportingContentColor = scheme.onSurfaceVariant),
    ) {
        Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
    }
}
