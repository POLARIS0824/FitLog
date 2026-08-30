package com.example.fitlog.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.fitlog.BuildConfig
import com.example.fitlog.ui.components.CollapsingTitleScaffold
import com.example.fitlog.ui.components.FitLogCard
import com.example.fitlog.ui.components.SectionLabel
import com.example.fitlog.ui.components.TonalIcon

/**
 * 1. 容器层 (Route)
 *
 * 关于页：应用信息展示，路由容器层。
 *
 * @param onBack 返回回调
 * @param modifier 修饰符
 */
@Composable
fun AboutRoute(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    AboutScreen(
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * 2. 纯 UI 展示层 (Stateless)
 *
 * 关于页纯 UI 展示，包含应用名称、版本号与项目链接。
 * 动态双标题交互契约见 [CollapsingTitleScaffold]。
 *
 * @param onBack 返回回调
 * @param modifier 修饰符
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current

    CollapsingTitleScaffold(
        title = "About",
        onBack = onBack,
        parentTitle = "Settings",
        modifier = modifier,
    ) {
        SectionLabel("应用")
        FitLogCard {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TonalIcon(
                    icon = Icons.Default.FitnessCenter,
                    index = 0,
                    size = 64.dp,
                )
                Text(
                    "FitLog",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    "Version ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionLabel("链接")
        FitLogCard {
            Text(
                text = "GitHub 仓库",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable {
                    uriHandler.openUri("https://github.com/POLARIS0824/FitLog")
                },
            )
            Text(
                text = "AI 驱动的训练记录与分析，个人项目",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun AboutScreenPreview() {
    AboutScreen()
}
