@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.fitlog.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.example.fitlog.R
import com.example.fitlog.data.settings.UiLook
import com.example.fitlog.ui.glass.GlassTopAppBar
import com.example.fitlog.ui.glass.LocalUiLook
import com.example.fitlog.ui.theme.FitLogTheme

/**
 * Pinned on purpose: an unbound or collapsing behavior absorbs nested scroll and starves the page
 * (see TopAppBarScrollBehaviorTest).
 */
@Composable
internal fun rememberFitLogTopAppBarBehavior(expanded: Boolean = false, scrollAway: Boolean = false): TopAppBarScrollBehavior =
    TopAppBarDefaults.pinnedScrollBehavior()

/**
 * The app owns system insets. Only the bar's content is constrained; its surface spans the window.
 *
 * Deeper pages use Material's small app bar with an up arrow. Top-level headers ([editorial] or
 * with a [subtitle]) and the Liquid Glass look keep the editorial header, whose height follows its
 * text: Material's fixed-height bars would clip a headline at 200% text.
 */
@Composable
internal fun FitLogTopAppBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    expanded: Boolean = false,
    maxContentWidth: Dp = FitLogTheme.dimens.page.wideContentWidth,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backEnabled: Boolean = true,
    editorial: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val materialBar = LocalUiLook.current != UiLook.LIQUID_GLASS && !editorial && subtitle == null
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = maxContentWidth).fillMaxWidth()) {
            if (materialBar) {
                FitLogSmallTopAppBar(title, scrollBehavior, onBack, backEnabled, actions)
            } else {
                GlassTopAppBar(title, subtitle, onBack, backEnabled, actions)
            }
        }
    }
}

@Composable
private fun FitLogSmallTopAppBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior?,
    onBack: (() -> Unit)?,
    backEnabled: Boolean,
    actions: @Composable RowScope.() -> Unit,
) {
    TopAppBar(
        title = {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
        },
        navigationIcon = {
            onBack?.let {
                IconButton(onClick = it, enabled = backEnabled, shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.cd_back))
                }
            }
        },
        actions = actions,
        // The app Scaffold already applied the status bar inset.
        windowInsets = WindowInsets(0, 0, 0, 0),
        // The page paints the background and the pinned bar never overlaps content. A colored
        // bar inside the constrained width would leave bare strips on wide windows.
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
        scrollBehavior = scrollBehavior,
    )
}
