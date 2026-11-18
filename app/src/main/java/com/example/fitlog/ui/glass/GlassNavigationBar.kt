package com.example.fitlog.ui.glass

import com.example.fitlog.ui.theme.FitLogTheme
import com.example.fitlog.ui.theme.GlassColors

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation3.runtime.NavKey
import com.example.fitlog.data.settings.UiLook
import com.example.fitlog.R
import com.example.fitlog.app.navigation.TopLevelDestination
import com.example.fitlog.app.navigation.topLevelDestinations
import com.example.fitlog.ui.components.FitLogBackGuard

/** A separate shell. The Material toolbar and its FAB implementation are never restyled. */
@Composable
internal fun GlassNavigationBar(currentRoute: NavKey?, expanded: Boolean, onExpanded: (Boolean) -> Unit,
    onDestination: (TopLevelDestination) -> Unit, onNewRecord: () -> Unit, onImport: () -> Unit,
    modifier: Modifier = Modifier) {
    FitLogBackGuard(enabled = expanded) { onExpanded(false) }
    val haptics = LocalHapticFeedback.current
    val destinationClick by rememberUpdatedState(onDestination)
    val expandClick by rememberUpdatedState(onExpanded)
    val menuExpanded by rememberUpdatedState(expanded)
    val page = LocalGlassPage.current
    val reduce = rememberReduceMotion()
    val fullLook = LocalUiLook.current == UiLook.LIQUID_GLASS
    val dark = GlassColors.isDark(MaterialTheme.colorScheme.surface)
    val glass = FitLogTheme.dimens.glass
    val spacing = FitLogTheme.spacing
    val tint = GlassColors.navTint(dark, fullLook)
    val activeColor = MaterialTheme.colorScheme.primary
    val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.fillMaxWidth().padding(horizontal = glass.pageGutter).padding(bottom = spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(spacing.m)) {
        AnimatedVisibility(expanded, enter = fadeIn(), exit = fadeOut()) {
            Column(Modifier.widthIn(max = glass.navigationMaxWidth).fillMaxWidth(), horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(glass.navigationActionGap)) {
                GlassButton(stringResource(R.string.record_new), {
                    onExpanded(false); onNewRecord()
                }, icon = R.drawable.note_add_24px)
                GlassButton(stringResource(R.string.nav_action_import_folder), {
                    onExpanded(false); onImport()
                }, icon = R.drawable.drive_folder_upload_24px)
            }
        }
        FloatingTabBar(
            selectedTabKey = currentRoute,
            modifier = Modifier.widthIn(max = glass.navigationMaxWidth),
            reduceMotion = reduce,
            colors = FloatingTabBarDefaults.colors(
                indicatorColor = GlassColors.tabIndicator(dark, MaterialTheme.colorScheme.primary),
            ),
            sizes = FloatingTabBarDefaults.sizes(
                tabBarContentPadding = PaddingValues(glass.tabBarPadding),
                tabExpandedContentPadding = PaddingValues(horizontal = spacing.xs, vertical = glass.tabVerticalPadding),
                componentSpacing = glass.tabBarClusterGap,
            ),
            tabBarContentModifier = { Modifier.glassSurface(CircleShape, page = page, tint = tint) },
            contentKey = currentRoute,
        ) {
            topLevelDestinations.forEach { destination ->
                val active = destination.route == currentRoute
                tab(destination.route,
                    title = {
                        Text(stringResource(destination.labelRes), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) activeColor else unselectedColor,
                            modifier = Modifier.semantics { selected = active })
                    },
                    icon = { Icon(painterResource(if (active) destination.selectedIconRes else destination.unselectedIconRes),
                        null, tint = if (active) activeColor else unselectedColor) },
                    onClick = { expandClick(false); destinationClick(destination) })
            }
            standaloneTab("actions", icon = {
                Icon(painterResource(R.drawable.add_24px), stringResource(if (menuExpanded) R.string.cd_close_menu else R.string.cd_open_menu),
                    tint = activeColor)
            }, onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                expandClick(!menuExpanded)
            })
        }
    }
}
