package com.example.fitlog.feature.today

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import com.example.fitlog.model.WorkoutPlan
import com.example.fitlog.ui.components.FitLogCard
import com.example.fitlog.ui.components.LargeMetricCard
import com.example.fitlog.ui.components.MetricDashboardGrid
import com.example.fitlog.ui.components.MetricPageIndicator
import com.example.fitlog.ui.components.SectionLabel
import com.example.fitlog.ui.components.SmallMetricCard
import com.example.fitlog.ui.components.TonalIcon
import com.example.fitlog.ui.theme.FitLogTheme
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * 1. 容器层 (Stateful)
 * 绑定 Hilt ViewModel，处理生命周期感知的状态收集。
 *
 * @param onNavigateToSettings 跳转设置回调
 * @param onNavigateToWorkout 跳转训练记录回调
 */
@Composable
fun TodayRoute(
    onNavigateToSettings: () -> Unit = {},
    onNavigateToWorkout: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allPlans by viewModel.allPlans.collectAsStateWithLifecycle()
    TodayScreen(
        uiState = uiState,
        allPlans = allPlans,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToWorkout = onNavigateToWorkout,
        onDisplayModeSelected = viewModel::onDisplayModeSelected,
        onPlanSelected = viewModel::onPlanSelected,
        onErrorShown = viewModel::onErrorShown,
        modifier = modifier,
    )
}

/**
 * 2. 纯 UI 展示层 (Stateless)
 *
 * Today 主页：Coach Insight + 本周进度仪表盘 + 今日训练计划。
 * 顶栏使用 [CenterAlignedTopAppBar] 实现仿 Fit / Health 风格极简居中 AppBar。
 */
@Composable
fun TodayScreen(
    uiState: TodayUiState,
    allPlans: List<WorkoutPlan>,
    onNavigateToSettings: () -> Unit,
    onNavigateToWorkout: () -> Unit,
    onDisplayModeSelected: (WeekProgressDisplayMode) -> Unit,
    onPlanSelected: (String) -> Unit,
    onErrorShown: () -> Unit,
    onLogClick: () -> Unit = onNavigateToWorkout,
    onStartClick: () -> Unit = onNavigateToWorkout,
    onEditClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    var showPlanSheet by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        topBar = {
            TodayTopBar(onNavigateToSettings = onNavigateToSettings)
        },
    ) { innerPadding ->
        if (uiState.uiState.isLoading) {
            // 加载占位：顶部加载条（同 AISettings 的 isLoading 呈现），
            // 杜绝 initialValue 的默认值被当作真实空数据渲染（"Hello" 假问候等）
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CoachInsightCard(
                    insight = uiState.coachInsight,
                    onStartWorkoutClick = onNavigateToWorkout,
                )

                WeekProgressSection(
                    weekProgress = uiState.weekProgress,
                    onDisplayModeSelected = onDisplayModeSelected,
                    onLogClick = onLogClick,
                    onStartClick = onStartClick,
                    onEditClick = onEditClick ?: { showPlanSheet = true },
                )

                SectionLabel("今日训练")
                TodayPlanCard(
                    todayPlan = uiState.todayPlan,
                    onActionClick = {
                        when (uiState.todayPlan.status) {
                            PlanStatus.NO_PLAN -> showPlanSheet = true
                            else -> onNavigateToWorkout()
                        }
                    },
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // 计划选择弹层
    if (showPlanSheet) {
        PlanPickerSheet(
            plans = allPlans,
            activePlanId = uiState.todayPlan.planId,
            onSelect = {
                onPlanSelected(it)
                showPlanSheet = false
            },
            onDismiss = { showPlanSheet = false },
        )
    }

    // 错误提示
    uiState.uiState.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = onErrorShown,
            confirmButton = { TextButton(onClick = onErrorShown) { Text("知道了") } },
            title = { Text("出错了") },
            text = { Text(message) },
        )
    }
}

/**
 * Today 顶栏组件：使用 Material3 [CenterAlignedTopAppBar] 实现仿 Fit / Health 居中顶栏。
 * 自动适配系统状态栏安全边距 (Status Bar Insets)。
 *
 * 左侧：设备/手表图标
 * 中间：居中 "Today" 标题
 * 右侧：带彩环的个人资料 / 设置入口按钮
 *
 * @param onNavigateToSettings 跳转设置回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodayTopBar(
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CenterAlignedTopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = "Today",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Normal,
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigateToSettings) {
                Icon(
                    imageVector = Icons.Default.Watch,
                    contentDescription = "设备与设置",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        actions = {
            IconButton(onClick = onNavigateToSettings) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .border(
                            width = 2.dp,
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color(0xFF4285F4),
                                    Color(0xFFEA4335),
                                    Color(0xFFFBBC05),
                                    Color(0xFF34A853),
                                    Color(0xFF4285F4),
                                ),
                            ),
                            shape = CircleShape,
                        )
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "个人中心",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    )
}

// ──────────────────────────────────────
// Coach Insight 区块
// ──────────────────────────────────────

/**
 * Gemini 风格的流动渐变背景容器组件。
 *
 * 利用 [InfiniteTransition] 驱动多组色彩斑斓的动态径向渐变 (Radial Gradient Blobs)
 * 在二维平面上作周期性平滑运动与膨胀收缩，结合 Material3 表面遮罩，
 * 打造类似于 Gemini App 界面中生动流畅的 Ambient Color Ambient Flow 效果。
 *
 * ## 帧消耗门控
 *
 * 动画仅在宿主生命周期 RESUMED 时挂载：Navigation3 转场 / 预测式返回手势 /
 * 浮层覆盖期间 entry 生命周期被压到 RESUMED 之下，此时把动画从组合中移除，
 * [InfiniteTransition] 无活跃动画即停止帧回调。
 * （稳态下被压栈的 entry 会整体移出组合；此门控覆盖瞬态窗口并防御未来 OverlayScene。）
 *
 * @param modifier 外部 Modifier 修饰符
 * @param isDarkTheme 当前是否处于深色主题模式
 * @param content 卡片内部内容布局
 */
@Composable
fun GeminiFlowingGradientBackground(
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable BoxScope.() -> Unit,
) {
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val isResumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

    val infiniteTransition = rememberInfiniteTransition(label = "GeminiGradientAnimation")

    val time by if (isResumed) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 10000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "time",
        )
    } else {
        // 非 RESUMED：动画移出组合，帧回调停止；恢复时从 0f 重启，10s 环境渐变下不可感知
        remember { mutableStateOf(0f) }
    }

    val color1 = if (isDarkTheme) Color(0xFF3865A8) else Color(0xFFA8C7FA) // 柔和天空蓝
    val color2 = if (isDarkTheme) Color(0xFF6B4EA2) else Color(0xFFD0BCFF) // 梦幻紫
    val color3 = if (isDarkTheme) Color(0xFF9E4870) else Color(0xFFFFD8E4) // 浪漫粉桃
    val color4 = if (isDarkTheme) Color(0xFF7C6D20) else Color(0xFFFFE088) // 暖阳明黄

    val surfaceContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(surfaceContainerColor)
            .drawBehind {
                val width = size.width
                val height = size.height

                if (width <= 0f || height <= 0f) return@drawBehind

                // Blob 1: 天空蓝 (左上 ↔ 中间)
                val c1X = width * (0.35f + 0.25f * sin(time))
                val c1Y = height * (0.3f + 0.2f * cos(time * 0.8f))
                val r1 = max(width, height) * (0.55f + 0.1f * sin(time * 1.2f))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color1.copy(alpha = 0.55f), Color.Transparent),
                        center = Offset(c1X, c1Y),
                        radius = r1,
                    ),
                    center = Offset(c1X, c1Y),
                    radius = r1,
                )

                // Blob 2: 梦幻紫 (右上 ↔ 右下)
                val c2X = width * (0.75f + 0.2f * cos(time * 1.1f))
                val c2Y = height * (0.6f + 0.25f * sin(time * 0.9f))
                val r2 = max(width, height) * (0.5f + 0.1f * cos(time))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color2.copy(alpha = 0.5f), Color.Transparent),
                        center = Offset(c2X, c2Y),
                        radius = r2,
                    ),
                    center = Offset(c2X, c2Y),
                    radius = r2,
                )

                // Blob 3: 暖黄 (左下 ↔ 中上)
                val c3X = width * (0.4f + 0.3f * cos(time * 0.7f))
                val c3Y = height * (0.8f + 0.15f * sin(time * 1.3f))
                val r3 = max(width, height) * (0.45f + 0.1f * sin(time * 0.8f))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color4.copy(alpha = 0.45f), Color.Transparent),
                        center = Offset(c3X, c3Y),
                        radius = r3,
                    ),
                    center = Offset(c3X, c3Y),
                    radius = r3,
                )

                // Blob 4: 浪漫粉桃 (中心浮动)
                val c4X = width * (0.6f + 0.25f * sin(time * 1.3f))
                val c4Y = height * (0.25f + 0.2f * cos(time * 1.1f))
                val r4 = max(width, height) * (0.4f + 0.08f * cos(time * 1.4f))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color3.copy(alpha = 0.4f), Color.Transparent),
                        center = Offset(c4X, c4Y),
                        radius = r4,
                    ),
                    center = Offset(c4X, c4Y),
                    radius = r4,
                )

                // 柔和半透明渐变罩层，确保前景文字在所有背景位置均具备极佳对比度
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            surfaceContainerColor.copy(alpha = 0.35f),
                            surfaceContainerColor.copy(alpha = 0.65f),
                        ),
                    ),
                )
            },
    ) {
        content()
    }
}

/**
 * Coach Insight 卡片：左侧仅包含 AI Coach 圆形图标，右侧垂直排列 AI Coach 标签、动态问候、训练摘要、针对性建议及开始训练按钮。
 *
 * 整体布局采用左右双栏模式 (Row)，右侧包含：
 * 1. AI Coach 标签 (小字号加粗主色)
 * 2. 问候语 (紧贴 AI Coach 标签)
 * 3. 本周摘要文案
 * 4. 推荐训练建议 (加粗主色)
 * 5. 开始训练 (CTA 按钮)
 *
 * 背景基于 [GeminiFlowingGradientBackground] 渲染 Gemini 风格流动渐变。
 *
 * @param insight AI 教练建议状态数据
 * @param onStartWorkoutClick 点击“开始训练”按钮时的回调
 * @param modifier 修饰符
 */
@Composable
private fun CoachInsightCard(
    insight: CoachInsightState,
    onStartWorkoutClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    GeminiFlowingGradientBackground(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // 左侧：仅在左上角放置一个 AI Coach 圆形图标
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(42.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // 右侧：全部文字与按钮在 Column 内垂直对齐
            Column(
                modifier = Modifier.weight(1f),
            ) {
                // 1. AI Coach 标签
                Text(
                    text = "AI Coach",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )

                // 2. 问候语 (紧贴 AI Coach 标签，间距 2dp)
                Text(
                    text = "${insight.greeting} 👋",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                )

                if (insight.isAvailable) {
                    // 3. 训练完成情况与状态摘要
                    Text(
                        text = insight.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 4. 高亮显示的教练针对性建议
                    Text(
                        text = insight.recommendation,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 5. 行动号召 (CTA) 按钮："开始训练"
                    Button(
                        onClick = onStartWorkoutClick,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "开始训练",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "完成首次训练或选择一套计划后，这里会生成你的专属训练建议",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ──────────────────────────────────────
// 本周进度区块
// ──────────────────────────────────────

/**
 * 本周进度区块：HorizontalPager 卡片横向滑动 + 底部 MetricPageIndicator 纯展示指示点。
 * 性能优化：通过 [snapshotFlow] 仅监听 [settledPage]（即手势释放且页面停稳后），
 * 避免在拖拽中途频繁触发 ViewModel 状态刷新与数据库查询导致的滑动卡顿。
 */
@Composable
private fun WeekProgressSection(
    weekProgress: WeekProgressState,
    onDisplayModeSelected: (WeekProgressDisplayMode) -> Unit,
    onLogClick: () -> Unit = {},
    onStartClick: () -> Unit = {},
    onEditClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val modes = WeekProgressDisplayMode.entries
    val initialPage = remember { modes.indexOf(weekProgress.displayMode).coerceAtLeast(0) }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { modes.size },
    )

    // 仅在拖拽释放、动画结束停稳后 (settledPage) 才通知 ViewModel 切换显示模式，彻底解决滑动卡顿
    LaunchedEffect(pagerState) {
        androidx.compose.runtime.snapshotFlow { pagerState.settledPage }.collect { page ->
            val selectedMode = modes[page]
            if (selectedMode != weekProgress.displayMode) {
                onDisplayModeSelected(selectedMode)
            }
        }
    }

    val currentMode = modes[pagerState.currentPage]

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionLabel(currentMode.label)

        // 卡片横向滑动 Pager（使用预计算好的 itemsMap，让每一页数据即刻就位，滑动无延迟）
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            pageSpacing = 12.dp,
        ) { page ->
            val mode = modes[page]
            val pageItems = weekProgress.itemsMap[mode] ?: weekProgress.items
            WeekProgressDashboard(
                weekProgress = weekProgress.copy(
                    displayMode = mode,
                    items = pageItems,
                )
            )
        }

        // 底部指示点：纯展示状态，不开启点击事件以防误触
        MetricPageIndicator(
            pageCount = modes.size,
            currentPage = pagerState.currentPage,
        )

        // 快捷操作按钮组：Log / Start / 编辑
        MetricActionButtons(
            onLogClick = onLogClick,
            onStartClick = onStartClick,
            onEditClick = onEditClick,
        )
    }
}

/**
 * 仪表盘下方快捷操作按钮组：[+ Log]、[🏃 Start]、[✏️ 编辑]
 * 支持点击与按住时细腻的弹性微膨胀及旁侧按钮微挤压 (compress) 物理动效。
 *
 * @param onLogClick 点击 Log 按钮回调
 * @param onStartClick 点击 Start 按钮回调
 * @param onEditClick 点击编辑按钮回调
 */
@Composable
private fun MetricActionButtons(
    onLogClick: () -> Unit,
    onStartClick: () -> Unit,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val logInteractionSource = remember { MutableInteractionSource() }
    val isLogPressed by logInteractionSource.collectIsPressedAsState()

    val startInteractionSource = remember { MutableInteractionSource() }
    val isStartPressed by startInteractionSource.collectIsPressedAsState()

    val editInteractionSource = remember { MutableInteractionSource() }
    val isEditPressed by editInteractionSource.collectIsPressedAsState()

    // 点击/按住状态延时维持 150ms，确保短促的轻点 (click) 也能完整触发微妙挤压动效
    var isLogActive by remember { mutableStateOf(false) }
    var isStartActive by remember { mutableStateOf(false) }
    var isEditActive by remember { mutableStateOf(false) }

    LaunchedEffect(isLogPressed) {
        if (isLogPressed) {
            isLogActive = true
            delay(150)
            if (!isLogPressed) isLogActive = false
        } else {
            isLogActive = false
        }
    }

    LaunchedEffect(isStartPressed) {
        if (isStartPressed) {
            isStartActive = true
            delay(150)
            if (!isStartPressed) isStartActive = false
        } else {
            isStartActive = false
        }
    }

    LaunchedEffect(isEditPressed) {
        if (isEditPressed) {
            isEditActive = true
            delay(150)
            if (!isEditPressed) isEditActive = false
        } else {
            isEditActive = false
        }
    }

    // 微型挤压比例（轻微变化，极致优雅）
    val logWeightTarget = when {
        isLogActive -> 1.08f
        isStartActive -> 0.92f
        isEditActive -> 0.96f
        else -> 1.0f
    }

    val startWeightTarget = when {
        isStartActive -> 1.08f
        isLogActive -> 0.92f
        isEditActive -> 0.96f
        else -> 1.0f
    }

    val editWidthTarget = when {
        isEditActive -> 48.dp
        isLogActive || isStartActive -> 41.dp
        else -> 44.dp
    }

    val springSpec = spring<Float>(
        stiffness = Spring.StiffnessMedium,
        dampingRatio = Spring.DampingRatioLowBouncy,
    )

    val dpSpringSpec = spring<Dp>(
        stiffness = Spring.StiffnessMedium,
        dampingRatio = Spring.DampingRatioLowBouncy,
    )

    val animatedLogWeight by animateFloatAsState(
        targetValue = logWeightTarget,
        animationSpec = springSpec,
        label = "logWeight",
    )

    val animatedStartWeight by animateFloatAsState(
        targetValue = startWeightTarget,
        animationSpec = springSpec,
        label = "startWeight",
    )

    val animatedEditWidth by animateDpAsState(
        targetValue = editWidthTarget,
        animationSpec = dpSpringSpec,
        label = "editWidth",
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Log 按钮
        Surface(
            onClick = onLogClick,
            interactionSource = logInteractionSource,
            modifier = Modifier
                .weight(animatedLogWeight)
                .height(44.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Log",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }

        // Start 按钮
        Surface(
            onClick = onStartClick,
            interactionSource = startInteractionSource,
            modifier = Modifier
                .weight(animatedStartWeight)
                .height(44.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.DirectionsRun,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Start",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }

        // 编辑图标按钮
        Surface(
            onClick = onEditClick,
            interactionSource = editInteractionSource,
            modifier = Modifier
                .height(44.dp)
                .width(animatedEditWidth),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "编辑",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** 小卡槽位图标的轮换序列（badgeIconType 字段 v1 不消费）。 */
private val smallCardIcons = listOf(
    Icons.Default.Star,
    Icons.Default.Favorite,
    Icons.Default.AutoAwesome,
)

/**
 * 本周进度仪表盘：[WeekProgressState.items] 契约固定 4 个——
 * item[0] 进左侧大卡（title=标题、valueText=主数值、subtitle=副标题，
 * progress 驱动水波，ringSegments 非空时渲染环形图），
 * item[1..3] 进右侧小卡；不足 4 个时小卡槽位填占位（防御性兜底）。
 */
@Composable
private fun WeekProgressDashboard(weekProgress: WeekProgressState) {
    val items = weekProgress.items
    if (items.isEmpty()) {
        FitLogCard {
            Text(
                text = "完成首次训练或选择一套计划后，这里会展示你的本周进度",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val smallColors = listOf(
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant,
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer,
    )

    MetricDashboardGrid(
        largeCardLeft = { gridModifier ->
            val head = items[0]
            LargeMetricCard(
                title = head.title,
                value = head.valueText ?: head.subtitle,
                subtitle = if (head.valueText != null) head.subtitle else weekProgress.statusText,
                icon = Icons.Default.FitnessCenter,
                progress = head.progress,
                ringSegments = head.ringSegments,
                modifier = gridModifier,
            )
        },
        smallCardTop = { gridModifier ->
            SmallMetricCardSlot(items.getOrNull(1), smallColors[0], smallCardIcons[0], gridModifier)
        },
        smallCardMiddle = { gridModifier ->
            SmallMetricCardSlot(items.getOrNull(2), smallColors[1], smallCardIcons[1], gridModifier)
        },
        smallCardBottom = { gridModifier ->
            SmallMetricCardSlot(items.getOrNull(3), smallColors[2], smallCardIcons[2], gridModifier)
        },
    )
}

/** 小卡槽位：有数据渲染真实指标，无数据渲染占位。 */
@Composable
private fun SmallMetricCardSlot(
    item: ProgressItemState?,
    colors: Pair<Color, Color>,
    icon: ImageVector,
    modifier: Modifier,
) {
    SmallMetricCard(
        title = item?.title ?: "—",
        value = item?.subtitle ?: "暂无数据",
        icon = icon,
        containerColor = colors.first,
        contentColor = colors.second,
        badgeContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        badgeContentColor = colors.second,
        modifier = modifier,
    )
}

// ──────────────────────────────────────
// 今日训练计划区块
// ──────────────────────────────────────

/** 今日训练计划卡片：标题 + 状态徽章 + 进度条 + 三态按钮。 */
@Composable
private fun TodayPlanCard(
    todayPlan: TodayPlanState,
    onActionClick: () -> Unit,
) {
    FitLogCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = todayPlan.title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = todayPlan.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PlanStatusChip(status = todayPlan.status)
        }

        if (todayPlan.status != PlanStatus.NO_PLAN) {
            LinearProgressIndicator(
                progress = { todayPlan.progress },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = todayPlan.progressPercentageText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when (todayPlan.status) {
            PlanStatus.NOT_STARTED, PlanStatus.IN_PROGRESS -> {
                Button(
                    onClick = onActionClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(todayPlan.buttonText)
                }
            }

            PlanStatus.COMPLETED, PlanStatus.NO_PLAN -> {
                FilledTonalButton(
                    onClick = onActionClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(todayPlan.buttonText)
                }
            }
        }
    }
}

/** 计划状态徽章。 */
@Composable
private fun PlanStatusChip(status: PlanStatus) {
    val label = when (status) {
        PlanStatus.NO_PLAN -> "无计划"
        PlanStatus.NOT_STARTED -> "未开始"
        PlanStatus.IN_PROGRESS -> "进行中"
        PlanStatus.COMPLETED -> "已完成"
    }
    AssistChip(
        onClick = {},
        label = { Text(label) },
    )
}

// ──────────────────────────────────────
// 计划选择弹层
// ──────────────────────────────────────

/** 计划选择弹层：列出全部计划，标注当前激活，点选即设为激活计划。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanPickerSheet(
    plans: List<WorkoutPlan>,
    activePlanId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "选择训练计划",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        if (plans.isEmpty()) {
            Text(
                "暂无可用计划",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
        plans.forEach { plan ->
            ListItem(
                modifier = Modifier.clickable { onSelect(plan.id) },
                overlineContent = null,
                supportingContent = {
                    Text(
                        text = "${plan.sessionsPerWeek} 次/周 · ${plan.durationWeeks} 周",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingContent = {
                    if (plan.id == activePlanId) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "当前激活",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                elevation = ListItemDefaults.elevation(ListItemDefaults.Elevation),
                content = { Text(plan.name) },
            )
        }
        Spacer(modifier = Modifier.height(32.dp)) // 避开底部手势区
    }
}

// ──────────────────────────────────────
// 工具
// ──────────────────────────────────────

/** 今天的日期行："7月24日 星期五"。 */
private fun todayDateLine(): String {
    val today = LocalDate.now()
    val dayOfWeek = when (today.dayOfWeek.value) {
        1 -> "一"
        2 -> "二"
        3 -> "三"
        4 -> "四"
        5 -> "五"
        6 -> "六"
        else -> "日"
    }
    return "${today.monthValue}月${today.dayOfMonth}日 星期$dayOfWeek"
}

// ──────────────────────────────────────
// 3. 预览层
// ──────────────────────────────────────

/** 正常态预览：有计划、有进度、今日待练。 */
@Preview(showBackground = true)
@Composable
private fun TodayScreenPreview() {
    FitLogTheme {
        TodayScreen(
            uiState = TodayUiState(
                coachInsight = CoachInsightState(
                    userName = "Polaris",
                    greeting = "下午好，Polaris",
                    summary = "本周已练 2/3 次 · 距上次训练 1 天",
                    recommendation = "下一课：腿日 · 股四头后侧链",
                    isAvailable = true,
                ),
                weekProgress = WeekProgressState(
                    completedWorkouts = 2,
                    targetWorkouts = 3,
                    displayMode = WeekProgressDisplayMode.SPLIT,
                    items = listOf(
                        ProgressItemState(
                            id = "week-total",
                            title = "本周训练",
                            subtitle = "目标 3 次",
                            progress = 2f / 3f,
                            valueText = "2 次",
                        ),
                        ProgressItemState("next-session", "下一训练", "腿日 · 股四头后侧链"),
                        ProgressItemState("last-session", "最近训练", "拉日 · 背二头"),
                        ProgressItemState("supplement", "补剂摄入", "即将上线"),
                    ),
                    statusText = "继续加油！",
                ),
                todayPlan = TodayPlanState(
                    planId = "plan-1",
                    sessionId = "s3",
                    title = "腿日 · 股四头后侧链",
                    subtitle = "6 个动作 · 60 分钟",
                    progress = 0f,
                    status = PlanStatus.NOT_STARTED,
                ),
                uiState = UiState(),
            ),
            allPlans = emptyList(),
            onNavigateToSettings = {},
            onNavigateToWorkout = {},
            onDisplayModeSelected = {},
            onPlanSelected = {},
            onErrorShown = {},
        )
    }
}

/** 空态预览：全新用户（无资料、无计划、无训练）。 */
@Preview(showBackground = true)
@Composable
private fun TodayScreenEmptyPreview() {
    FitLogTheme {
        TodayScreen(
            uiState = TodayUiState(
                coachInsight = CoachInsightState(greeting = "下午好"),
                weekProgress = WeekProgressState(statusText = "这周还没开始"),
                todayPlan = TodayPlanState(
                    title = "还没有训练计划",
                    subtitle = "选择一套计划开始系统训练",
                    status = PlanStatus.NO_PLAN,
                ),
                uiState = UiState(),
            ),
            allPlans = emptyList(),
            onNavigateToSettings = {},
            onNavigateToWorkout = {},
            onDisplayModeSelected = {},
            onPlanSelected = {},
            onErrorShown = {},
        )
    }
}

/** 加载态预览：仅 TopBar + 空内容区，等待首批真实数据。 */
@Preview(showBackground = true)
@Composable
private fun TodayScreenLoadingPreview() {
    FitLogTheme {
        TodayScreen(
            uiState = TodayUiState(
                coachInsight = CoachInsightState(),
                weekProgress = WeekProgressState(),
                todayPlan = TodayPlanState(),
                uiState = UiState(isLoading = true),
            ),
            allPlans = emptyList(),
            onNavigateToSettings = {},
            onNavigateToWorkout = {},
            onDisplayModeSelected = {},
            onPlanSelected = {},
            onErrorShown = {},
        )
    }
}