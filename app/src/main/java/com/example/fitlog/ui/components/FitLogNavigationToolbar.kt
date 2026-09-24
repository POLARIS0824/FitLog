package com.example.fitlog.ui.components

import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import com.example.fitlog.navigation.FitLogRoute
import com.example.fitlog.navigation.TopLevelDestination
import com.example.fitlog.navigation.topLevelDestinations

@Composable
fun FitLogNavigationToolbar(
    currentRoute: NavKey?,
    onDestinationClick: (TopLevelDestination) -> Unit,
    onRecordWorkoutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        floatingActionButton = {
            FloatingToolbarDefaults.VibrantFloatingActionButton(
                onClick = onRecordWorkoutClick,
            ) {
                Text("+")
            }
        }
    ) {
        topLevelDestinations.forEach { destination ->
            val selected = currentRoute == destination.route

            if (selected) {
                FilledTonalButton(
                    onClick = {
                        onDestinationClick(destination)
                    },
                ) {
                    Text(destination.label)
                }
            } else {
                TextButton(
                    onClick = {
                        onDestinationClick(destination)
                    },
                ) {
                    Text(destination.label)
                }
            }
        }
    }
}