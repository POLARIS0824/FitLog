package com.example.fitlog.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    val primary = colorScheme.primary
    val onPrimary = colorScheme.onPrimary
    val primaryContainer = colorScheme.primaryContainer
    val onPrimaryContainer = colorScheme.onPrimaryContainer

    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,

        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(
            toolbarContainerColor = primaryContainer,
            toolbarContentColor = onPrimaryContainer,
        ),

        floatingActionButton = {
            FloatingToolbarDefaults.VibrantFloatingActionButton(
                onClick = onRecordWorkoutClick,
            ) {
                Icon(
                    painter = painterResource(R.drawable.add_24px),
                    contentDescription = "Add",
                )
            }
        },
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

                colors = ToggleButtonDefaults.colors(
                    containerColor = primaryContainer,
                    contentColor = onPrimaryContainer,
                    checkedContainerColor = primary,
                    checkedContentColor = onPrimary,
                ),

                shapes = ToggleButtonShapes(
                    shape = CircleShape,
                    CircleShape,
                    CircleShape,
                ),

                modifier = Modifier.height(48.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(destination.iconRes),
                        contentDescription = destination.label,
                    )

                    if (selected) {
                        Text(
                            text = destination.label,
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.padding(
                                start = ButtonDefaults.IconSpacing,
                            ),
                        )
                    }
                }
            }
        }
    }
}