package com.example.fitlog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.fitlog.feature.aisettings.AISettingsRoute
import com.example.fitlog.ui.SettingsRoute
import com.example.fitlog.ui.theme.FitLogTheme
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
            FitLogTheme {
                // TODO: 导航框架搭建前先直接挂载 AI 设置页用于测试
                AISettingsRoute()
            }
        }
    }
}
