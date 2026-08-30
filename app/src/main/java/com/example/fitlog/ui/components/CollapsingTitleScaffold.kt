package com.example.fitlog.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.lerp

/**
 * Material Expressive 动态双标题 Scaffold（设置页群共享，此前在 6 个屏幕逐字复制）。
 *
 * 交互契约：
 * 1. 顶栏采用常驻 [TopAppBar] + [androidx.compose.material3.TopAppBarDefaults.pinnedScrollBehavior]；
 * 2. 可滚动状态下：大标题（[androidx.compose.ui.text.TextStyle.headlineMedium]）置于滚动内容顶部，
 *    带 8dp 左缩进与卡片内部完全对齐；
 * 3. 滚动时大标题自然沉入不透明顶栏下方，顶栏标题根据滚动进度从下至上平滑渐变淡入
 *    （alpha 与 translationY 联动）；
 * 4. 滚动停止在半折叠状态时，触发 [androidx.compose.animation.core.spring] 弹簧动画
 *    自动吸附到最近的稳定边界（0 或大标题高度）；
 * 5. 不可滚动状态下：自动隐藏 Body 重复的大标题，顶栏直接稳定展示本页标题。
 *
 * 顶栏标题形态二选一：[parentTitle] 为 null 时仅展示 [title]（顶级页）；
 * 传入父级标题（如 "Settings"）时滚动过程从父级标题交叉淡入本页标题（子页）。
 *
 * @param title 页面标题（同时用于顶栏小标题与内容区大标题）
 * @param onBack 返回上一页回调
 * @param parentTitle 父级页面标题；滚动时顶栏从该标题交叉淡入 [title]，null 表示无父级
 * @param modifier 修饰符
 * @param actions 顶栏右侧动作槽（可选）
 * @param snackbarHost Scaffold 的 Snackbar 宿主（可选，如 Profile/DataImport 的堆叠提示）
 * @param contentColumnModifier 追加到内容滚动 Column 上的修饰符（可选，
 *   如 Profile 的 imePadding 与点击清焦点；位于 fillMaxSize 与 verticalScroll 之间）
 * @param content 页面内容（垂直滚动 Column 的作用域，自带 12dp 纵向间距与 16dp 水平内边距）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollapsingTitleScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    parentTitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    contentColumnModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollState = rememberScrollState()

    val density = LocalDensity.current
    val extraSpacingPx = remember(density) { with(density) { 12.dp.roundToPx() } }

    // 自适应双态：动态检测页面内容是否能够产生滚动
    val isScrollable by remember { derivedStateOf { scrollState.maxValue > 0 } }

    // 标题切换进度：0 = 完全展开（显示 Body 大标题），1 = 大标题刚好完全滚入顶栏之下。
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val titleFraction by remember {
        derivedStateOf {
            if (!isScrollable || headerHeightPx <= 0) 0f
            else (scrollState.value.toFloat() / headerHeightPx.toFloat()).coerceIn(0f, 1f)
        }
    }

    // 吸附效果：手势/惯性滚动停止后，若大标题处于半折叠的中间态，自动平滑吸附到最近的稳定边界
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
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        )
                    } catch (e: CancellationException) {
                        // 仅当 LaunchedEffect 自身仍活跃（即动画是被新手势打断）才吞掉；
                        // 若父协程已取消，ensureActive() 会重新抛出，让 collect 立即终止
                        coroutineContext.ensureActive()
                    }
                }
            }
    }

    // 顶栏背景色随滚动进度在 surfaceContainerLow (展开) 与 surfaceContainer (折叠) 之间平滑过渡
    val topAppBarContainerColor = lerp(
        MaterialTheme.colorScheme.surfaceContainerLow,
        MaterialTheme.colorScheme.surfaceContainer,
        titleFraction,
    )

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        snackbarHost = snackbarHost,
        topBar = {
            TopAppBar(
                title = {
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (parentTitle == null) {
                            // 顶级页：单一标题随进度渐入
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.graphicsLayer {
                                    alpha = if (isScrollable) titleFraction else 1f
                                    translationY = if (isScrollable) (1f - titleFraction) * 12.dp.toPx() else 0f
                                },
                            )
                        } else if (isScrollable) {
                            // 子页：滚动时从父级标题交叉淡入本页标题
                            Text(
                                text = parentTitle,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.graphicsLayer {
                                    alpha = 1f - titleFraction
                                    translationY = -titleFraction * 12.dp.toPx()
                                },
                            )
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.graphicsLayer {
                                    alpha = titleFraction
                                    translationY = (1f - titleFraction) * 12.dp.toPx()
                                },
                            )
                        } else {
                            Text(
                                text = title,
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
                actions = actions,
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
                .then(contentColumnModifier)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 可滚动页面渲染大标题 Header；不可滚动页面隐藏 Body 重复大标题
            if (isScrollable) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, top = 8.dp, bottom = 4.dp)
                        .onSizeChanged { size ->
                            headerHeightPx = size.height + extraSpacingPx
                        },
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
            }

            content()
        }
    }
}
