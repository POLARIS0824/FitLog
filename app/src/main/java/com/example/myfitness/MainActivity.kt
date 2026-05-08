package com.example.myfitness

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.myfitness.feature.ai.AITestScreen
import com.example.myfitness.feature.traininglog.TrainingLogScreen
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
                var selectedTab by rememberSaveable { mutableIntStateOf(0) }

                val tabs = listOf(
                    TabItem("AI 测试", Icons.Default.Psychology) { AITestScreen() },
                    TabItem("训练日志", Icons.Default.FitnessCenter) { TrainingLogScreen() },
                )

                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        tabs[selectedTab].content()
                    }

                    NavigationBar {
                        tabs.forEachIndexed { index, tab ->
                            NavigationBarItem(
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 底部导航栏标签项。
 *
 * @property label 标签文本
 * @property icon 图标
 * @property content 页面内容 composable
 */
private data class TabItem(
    val label: String,
    val icon: ImageVector,
    val content: @Composable () -> Unit,
)
