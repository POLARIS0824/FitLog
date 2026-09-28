package com.example.fitlog.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    onDestinationClick: (TopLevelDestination) -> Unit,
    onRecordWorkoutClick: () -> Unit,
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

    Row(
        modifier = modifier,
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
                        if (!selected) {
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

        FloatingActionButton(
            onClick = onRecordWorkoutClick,

            // 和 toolbar 外容器等高
//            modifier = Modifier.size(
//                FloatingToolbarDefaults.ContainerSize
//            ),

            shape = FloatingActionButtonDefaults.shape,

            // 唯一真正的 primary emphasis
            containerColor = colorScheme.primary,
            contentColor = colorScheme.onPrimary,
        ) {
            Icon(
                painter = painterResource(R.drawable.add_24px),
                contentDescription = "Add workout",
            )
        }
    }
}