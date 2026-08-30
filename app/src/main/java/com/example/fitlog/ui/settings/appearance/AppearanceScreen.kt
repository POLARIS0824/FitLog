package com.example.fitlog.ui.settings.appearance

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitlog.data.repository.ThemeMode
import com.example.fitlog.ui.components.CollapsingTitleScaffold
import com.example.fitlog.ui.components.FitLogCard
import com.example.fitlog.ui.components.SectionLabel

/**
 * 1. 容器层 (Stateful)
 */
@Composable
fun AppearanceRoute(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: AppearanceViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AppearanceScreen(
        uiState = uiState,
        onBack = onBack,
        onThemeModeChange = viewModel::onThemeModeChange,
        onDynamicColorChange = viewModel::onDynamicColorChange,
        modifier = modifier,
    )
}

/**
 * 2. 纯 UI 展示层 (Stateless)
 *
 * 动态双标题交互契约见 [CollapsingTitleScaffold]。
 */
@Composable
fun AppearanceScreen(
    uiState: AppearanceUiState,
    onBack: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    CollapsingTitleScaffold(
        title = "Appearance",
        onBack = onBack,
        parentTitle = "Settings",
        modifier = modifier,
    ) {
        SectionLabel("主题")
        FitLogCard {
            Text("主题模式", style = MaterialTheme.typography.titleMedium)
            val options = listOf(
                ThemeMode.SYSTEM to "跟随系统",
                ThemeMode.LIGHT to "浅色",
                ThemeMode.DARK to "深色",
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = uiState.themeMode == mode,
                        onClick = { onThemeModeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = options.size,
                        ),
                    ) {
                        Text(label)
                    }
                }
            }
        }

        SectionLabel("颜色")
        FitLogCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("动态取色", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "根据壁纸生成整套配色（Material You）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = uiState.dynamicColor,
                    onCheckedChange = onDynamicColorChange,
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * 3. 预览层
 */
@Preview(showBackground = true)
@Composable
private fun AppearanceScreenPreview() {
    AppearanceScreen(
        uiState = AppearanceUiState(),
        onBack = {},
        onThemeModeChange = {},
        onDynamicColorChange = {},
    )
}
