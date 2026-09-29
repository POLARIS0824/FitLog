package com.example.fitlog.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.example.fitlog.R
import com.example.fitlog.navigation.TopLevelDestination
import com.example.fitlog.navigation.topLevelDestinations

@Composable
fun FitLogNavigationToolbar(
    currentRoute: NavKey?,
    fabMenuExpanded: Boolean,
    onFabMenuExpandedChange: (Boolean) -> Unit,
    onDestinationClick: (TopLevelDestination) -> Unit,
    onOpenTodayClick: () -> Unit,
    onImportFolderClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(fabMenuExpanded) {
        onFabMenuExpandedChange(false)
    }

    FloatingActionButtonMenu(
        modifier = modifier,
        expanded = fabMenuExpanded,
        button = {
            // Keep navigation and the FAB on one baseline while the menu expands upward.
            Row(
                modifier = Modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 1. 左侧：带局部同步遮罩的胶囊导航底栏
                NavToolbarWithScrim(
                    currentRoute = currentRoute,
                    fabMenuExpanded = fabMenuExpanded,
                    onDestinationClick = onDestinationClick,
                    onCloseMenu = { onFabMenuExpandedChange(false) },
                )

                // 2. 右侧：带 45° 旋转动效与触觉反馈的 FAB 展开按钮
                FabMenuToggleButton(
                    expanded = fabMenuExpanded,
                    onExpandedChange = onFabMenuExpandedChange,
                )
            }
        },
    ) {
        FloatingActionButtonMenuItem(
            onClick = {
                onFabMenuExpandedChange(false)
                onOpenTodayClick()
            },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.note_add_24px),
                    contentDescription = null,
                )
            },
            text = { Text(stringResource(R.string.nav_action_open_today)) },
        )
        FloatingActionButtonMenuItem(
            onClick = {
                onFabMenuExpandedChange(false)
                onImportFolderClick()
            },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.drive_folder_upload_24px),
                    contentDescription = null,
                )
            },
            text = { Text(stringResource(R.string.nav_action_import_folder)) },
        )
    }
}

/**
 * 封装带有局部遮罩的水平悬浮导航工具栏
 */
@Composable
private fun NavToolbarWithScrim(
    currentRoute: NavKey?,
    fabMenuExpanded: Boolean,
    onDestinationClick: (TopLevelDestination) -> Unit,
    onCloseMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val motionScheme = MaterialTheme.motionScheme

    val toolbarColors = FloatingToolbarDefaults.standardFloatingToolbarColors(
        toolbarContainerColor = colorScheme.surfaceContainer,
        toolbarContentColor = colorScheme.onSurfaceVariant,
        fabContainerColor = colorScheme.primary,
        fabContentColor = colorScheme.onPrimary,
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier,
    ) {
        HorizontalFloatingToolbar(
            expanded = true,
            colors = toolbarColors,
        ) {
            topLevelDestinations.forEach { destination ->
                NavTabButton(
                    destination = destination,
                    selected = currentRoute == destination.route,
                    onClick = {
                        onCloseMenu()
                        onDestinationClick(destination)
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = fabMenuExpanded,
            enter = fadeIn(animationSpec = motionScheme.defaultEffectsSpec()),
            exit = fadeOut(animationSpec = motionScheme.defaultEffectsSpec()),
            modifier = Modifier.matchParentSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(colorScheme.scrim.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        onClickLabel = stringResource(R.string.cd_close_menu),
                    ) {
                        onCloseMenu()
                    }
            )
        }
    }
}

/**
 * 封装单个导航胶囊 Tab 按钮（含图标、触觉反馈与横向展开文本）
 */
@Composable
private fun NavTabButton(
    destination: TopLevelDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val motionScheme = MaterialTheme.motionScheme
    val haptic = LocalHapticFeedback.current

    ToggleButton(
        checked = selected,
        onCheckedChange = {
            if (!selected) {
                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onClick()
            }
        },
        modifier = modifier.height(48.dp),
        colors = ToggleButtonDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = colorScheme.onSurfaceVariant,
            checkedContainerColor = colorScheme.secondaryContainer,
            checkedContentColor = colorScheme.onSecondaryContainer,
        ),
        shapes = ToggleButtonShapes(
            shape = CircleShape,
            pressedShape = CircleShape,
            checkedShape = CircleShape,
        ),
        // 重点：虽然不用官方 icon slot，
        // 但仍告诉 Material「这里有 leading icon」
        contentPadding = ToggleButtonDefaults.contentPaddingFor(
            buttonHeight = 48.dp,
            hasStartIcon = true,
        ),
    ) {
        val label = stringResource(destination.labelRes)
        val iconRes = if (selected) destination.selectedIconRes else destination.unselectedIconRes
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            modifier = Modifier.size(24.dp),
        )

        AnimatedVisibility(
            visible = selected,
            enter = expandHorizontally(
                expandFrom = Alignment.Start,
                animationSpec = motionScheme.defaultSpatialSpec(),
            ),
            exit = shrinkHorizontally(
                shrinkTowards = Alignment.Start,
                animationSpec = motionScheme.defaultSpatialSpec(),
            ),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier.padding(
                    start = ToggleButtonDefaults.IconSpacing,
                ),
            )
        }
    }
}

/**
 * 封装右侧 FAB 菜单触发按钮（含触觉反馈与 45° 旋转动效）
 */
@Composable
private fun FabMenuToggleButton(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current

    ToggleFloatingActionButton(
        modifier = modifier,
        checked = expanded,
        onCheckedChange = { newExpanded ->
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            onExpandedChange(newExpanded)
        },
        containerColor = {
            colorScheme.primary
        },
    ) {
        Icon(
            painter = painterResource(R.drawable.add_24px),
            contentDescription = stringResource(
                if (expanded) R.string.cd_close_menu else R.string.cd_open_menu
            ),
            tint = colorScheme.onPrimary,
            modifier = Modifier.graphicsLayer {
                // checkedProgress 在 0f ~ 1f 之间平滑变化，直接驱动 0° -> 45°
                rotationZ = checkedProgress * 45f
            },
        )
    }
}
