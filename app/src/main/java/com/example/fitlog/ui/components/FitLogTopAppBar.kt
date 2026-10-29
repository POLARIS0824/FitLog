@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.fitlog.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.fitlog.R
import com.example.fitlog.data.settings.UiLook
import com.example.fitlog.ui.glass.LocalUiLook
import com.example.fitlog.ui.glass.GlassTopAppBar

@Composable
internal fun rememberFitLogTopAppBarBehavior(expanded: Boolean = false, scrollAway: Boolean = false): TopAppBarScrollBehavior =
    if (scrollAway) TopAppBarDefaults.enterAlwaysScrollBehavior(
        snapAnimationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
    ) else if (expanded) TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        snapAnimationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
    ) else TopAppBarDefaults.pinnedScrollBehavior()

/** The app owns system insets. Only the bar's content is constrained; its surface spans the window. */
@Composable
internal fun FitLogTopAppBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    expanded: Boolean = false,
    maxContentWidth: Dp = FitLogDimens.WideContentWidth,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backEnabled: Boolean = true,
    editorial: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    if (LocalUiLook.current == UiLook.LIQUID_GLASS) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = maxContentWidth).fillMaxWidth()) {
                GlassTopAppBar(title, subtitle, onBack, backEnabled, actions)
            }
        }
        return
    }
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val containerColor = if (editorial) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLow
    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = containerColor,
        scrolledContainerColor = containerColor,
    )
    val titleContent: @Composable () -> Unit = {
        if (editorial) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMediumEmphasized,
                modifier = Modifier.semantics { heading() })
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else Text(title, fontWeight = MaterialTheme.typography.titleLargeEmphasized.fontWeight,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
    }
    val navigation: @Composable () -> Unit = {
        if (onBack != null) IconButton(onClick = onBack, enabled = backEnabled,
            shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.cd_back))
        }
    }
    val subtitleContent: (@Composable () -> Unit)? = if (subtitle == null) null else ({
        Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
    })
    Surface(color = containerColor, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = maxContentWidth).fillMaxWidth()
                .padding(horizontal = fitLogPagePadding() - FitLogDimens.CompactPadding)) {
                if (expanded && !editorial) MediumFlexibleTopAppBar(
                    title = titleContent,
                    subtitle = subtitleContent,
                    navigationIcon = navigation, actions = actions,
                    windowInsets = WindowInsets(0), colors = colors, scrollBehavior = scrollBehavior,
                ) else TopAppBar(
                    title = titleContent, navigationIcon = navigation, actions = actions,
                    expandedHeight = if (editorial) { (if (subtitle == null) 96.dp else 112.dp) * fontScale }
                        else TopAppBarDefaults.TopAppBarExpandedHeight,
                    windowInsets = WindowInsets(0), colors = colors, scrollBehavior = scrollBehavior,
                )
            }
        }
    }
}
