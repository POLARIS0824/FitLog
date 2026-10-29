package com.example.fitlog.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.fitlog.data.settings.UiLook
import com.example.fitlog.ui.glass.LocalUiLook
import com.example.fitlog.ui.glass.LookSurface
import com.example.fitlog.ui.glass.glassPageSource

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
    Column(Modifier.fillMaxSize().background(if (LocalUiLook.current == UiLook.LIQUID_GLASS)
        androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLow)
        .nestedScroll(behavior.nestedScrollConnection), horizontalAlignment = Alignment.CenterHorizontally) {
        FitLogTopAppBar(title, behavior, maxContentWidth = FitLogDimens.ContentWidth,
            onBack = onBack, backEnabled = backEnabled, actions = actions)

        Box(Modifier.weight(1f).widthIn(max = FitLogDimens.ContentWidth).fillMaxWidth().imePadding()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().glassPageSource(),
                contentPadding = PaddingValues(start = pagePadding, end = pagePadding, top = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                content = content,
            )
            FitLogLoadingIndicator(loading, Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = pagePadding))
        }
    }
}

@Composable
internal fun SettingsSectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMediumEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, bottom = 12.dp).semantics { heading() })
}

/** Continuous groups and thin dividers intentionally follow the supplied reference image. */
@Composable
internal fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    LookSurface(shape = MaterialTheme.shapes.extraLargeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth()) {
        Column(content = content)
    }
}

@Composable
internal fun SettingsFormCard(content: @Composable ColumnScope.() -> Unit) {
    SettingsGroup {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content)
    }
}

@Composable
internal fun SettingsEntry(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tone: Int = 0,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 20.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        val scheme = MaterialTheme.colorScheme
        val (container, foreground) = when (tone) {
            1 -> scheme.secondaryContainer to scheme.onSecondaryContainer
            2 -> scheme.tertiaryContainer to scheme.onTertiaryContainer
            else -> scheme.primaryContainer to scheme.onPrimaryContainer
        }
        Box(Modifier.size(48.dp).background(container, CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), null, tint = foreground, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}
