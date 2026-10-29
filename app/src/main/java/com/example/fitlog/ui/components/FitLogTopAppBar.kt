@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.fitlog.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.example.fitlog.ui.glass.GlassTopAppBar

@Composable
internal fun rememberFitLogTopAppBarBehavior(expanded: Boolean = false, scrollAway: Boolean = false): TopAppBarScrollBehavior =
    TopAppBarDefaults.pinnedScrollBehavior()

/** The app owns system insets. Only the bar's content is constrained; its surface spans the window. */
@Composable
internal fun FitLogTopAppBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    expanded: Boolean = false,
    maxContentWidth: Dp = FitLogDimens.WideContentWidth,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backEnabled: Boolean = true,
    editorial: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = maxContentWidth).fillMaxWidth()) {
            GlassTopAppBar(title, subtitle, onBack, backEnabled, actions)
        }
    }
}
