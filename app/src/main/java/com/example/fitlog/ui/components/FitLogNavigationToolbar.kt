package com.example.fitlog.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
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
    onCreateFileClick: () -> Unit,
    onImportFolderClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val motionScheme = MaterialTheme.motionScheme
    val haptic = LocalHapticFeedback.current

    val toolbarColors = FloatingToolbarDefaults.standardFloatingToolbarColors(
        toolbarContainerColor = colorScheme.surfaceContainer,
        toolbarContentColor = colorScheme.onSurfaceVariant,
        fabContainerColor = colorScheme.primary,
        fabContentColor = colorScheme.onPrimary,
    )

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
                HorizontalFloatingToolbar(
                    expanded = true,
                    colors = toolbarColors,
                ) {
                    topLevelDestinations.forEach { destination ->
                        val selected = currentRoute == destination.route

                        ToggleButton(
                            checked = selected,
                            onCheckedChange = {
                                onFabMenuExpandedChange(false)
                                if (!selected) {
                                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    onDestinationClick(destination)
                                }
                            },

                            modifier = Modifier.height(48.dp),

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
                            Icon(
                                painter = painterResource(destination.iconRes),
                                contentDescription = destination.label,
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
                                    text = destination.label,
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
                }
                ToggleFloatingActionButton(
                    checked = fabMenuExpanded,
                    onCheckedChange = { expanded ->
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        onFabMenuExpandedChange(expanded)
                    },

                    containerColor = {
                        colorScheme.primary
                    },
                ) {
                    Icon(
                        painter = painterResource(
                            if (fabMenuExpanded) R.drawable.close_24px else R.drawable.add_24px
                        ),
                        contentDescription = if (fabMenuExpanded) "Close menu" else "Open menu",
                        tint = colorScheme.onPrimary,
                    )
                }
            }
        },
    ) {
        FloatingActionButtonMenuItem(
            onClick = {
                onFabMenuExpandedChange(false)
                onCreateFileClick()
            },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.note_add_24px),
                    contentDescription = null,
                )
            },
            text = { Text("Add New File") },
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
            text = { Text("Import Folder") },
        )
    }
}
