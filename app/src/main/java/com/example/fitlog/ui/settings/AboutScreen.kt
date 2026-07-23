package com.example.fitlog.ui.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.fitlog.BuildConfig
import com.example.fitlog.ui.components.SectionLabel
import com.example.fitlog.ui.components.SettingsCard
import com.example.fitlog.ui.components.TonalIcon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive

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
 *
 * @param onBack 返回回调
 * @param modifier 修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollState = rememberScrollState()
    val uriHandler = LocalUriHandler.current

    val density = LocalDensity.current
    val extraSpacingPx = remember(density) { with(density) { 12.dp.roundToPx() } }

    val isScrollable by remember { derivedStateOf { scrollState.maxValue > 0 } }
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val titleFraction by remember {
        derivedStateOf {
            if (!isScrollable || headerHeightPx <= 0) 0f
            else (scrollState.value.toFloat() / headerHeightPx.toFloat()).coerceIn(0f, 1f)
        }
    }

    LaunchedEffect(scrollState, headerHeightPx, isScrollable) {
        if (!isScrollable) return@LaunchedEffect
        snapshotFlow { scrollState.isScrollInProgress }
            .collect { inProgress ->
                if (inProgress) return@collect
                val currentScroll = scrollState.value
                if (headerHeightPx > 0 && currentScroll in 1 until headerHeightPx) {
                    val target = if (currentScroll < headerHeightPx / 2) 0 else headerHeightPx
                    try {
                        scrollState.animateScrollTo(
                            value = target,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    } catch (e: CancellationException) {
                        // 仅当 LaunchedEffect 自身仍活跃（即动画是被新手势打断）才吞掉；
                        // 若父协程已取消，ensureActive() 会重新抛出，让 collect 立即终止
                        coroutineContext.ensureActive()
                    }
                }
            }
    }

    val topAppBarContainerColor = lerp(
        MaterialTheme.colorScheme.surfaceContainerLow,
        MaterialTheme.colorScheme.surfaceContainer,
        titleFraction
    )

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        topBar = {
            TopAppBar(
                title = {
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (isScrollable) {
                            Text(
                                text = "Settings",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.graphicsLayer {
                                    alpha = 1f - titleFraction
                                    translationY = -titleFraction * 12.dp.toPx()
                                },
                            )
                            Text(
                                text = "About",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.graphicsLayer {
                                    alpha = titleFraction
                                    translationY = (1f - titleFraction) * 12.dp.toPx()
                                },
                            )
                        } else {
                            Text(
                                text = "About",
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topAppBarContainerColor,
                    scrolledContainerColor = topAppBarContainerColor,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isScrollable) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, top = 8.dp, bottom = 4.dp)
                        .onSizeChanged { size ->
                            headerHeightPx = size.height + extraSpacingPx
                        }
                ) {
                    Text(
                        text = "About",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
            }

            SectionLabel("应用")
            SettingsCard {
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
            SettingsCard {
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
}

@Preview(showBackground = true)
@Composable
private fun AboutScreenPreview() {
    AboutScreen()
}
