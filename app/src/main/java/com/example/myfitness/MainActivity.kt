package com.example.myfitness

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.myfitness.feature.ai.AITestScreen
import com.example.myfitness.ui.theme.MyFitnessTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 应用主入口 Activity。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyFitnessTheme {
                AITestScreen()
            }
        }
    }
}
