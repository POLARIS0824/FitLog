package com.example.fitlog.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.example.fitlog.editor.EditorScreen
import com.example.fitlog.insight.InsightScreen
import com.example.fitlog.log.LogScreen
import com.example.fitlog.today.TodayScreen

@Composable
fun FitLogNavGraph(
    backStack: NavBackStack<NavKey>,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme

    NavDisplay(
        backStack = backStack,
        modifier = modifier,

        onBack = {
            backStack.removeLastOrNull()
        },

        transitionSpec = {
            fadeIn(
                animationSpec = motionScheme.defaultEffectsSpec(),
            ) togetherWith fadeOut(
                animationSpec = motionScheme.defaultEffectsSpec(),
            )
        },

        popTransitionSpec = {
            fadeIn(
                animationSpec = motionScheme.defaultEffectsSpec(),
            ) togetherWith fadeOut(
                animationSpec = motionScheme.defaultEffectsSpec(),
            )
        },

        predictivePopTransitionSpec = {
            fadeIn(
                animationSpec = motionScheme.defaultEffectsSpec(),
            ) togetherWith fadeOut(
                animationSpec = motionScheme.defaultEffectsSpec(),
            )
        },

        entryProvider = entryProvider {
            entry<FitLogRoute.Today> {
                TodayScreen()
            }

            entry<FitLogRoute.Log> {
                LogScreen()
            }

            entry<FitLogRoute.Insight> {
                InsightScreen()
            }

            entry<FitLogRoute.Editor> {
                EditorScreen(
                    onBack = {
                        backStack.removeLastOrNull()
                    }
                )
            }
        },
    )
}