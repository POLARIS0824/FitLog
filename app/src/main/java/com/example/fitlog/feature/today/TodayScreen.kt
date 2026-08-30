package com.example.fitlog.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitlog.model.WorkoutPlan
import com.example.fitlog.model.ai.CoachAction
import com.example.fitlog.ui.components.SectionLabel
import com.example.fitlog.ui.theme.FitLogTheme

/**
 * 1. 容器层 (Stateful)
 * 绑定 Hilt ViewModel，处理生命周期感知的状态收集。
 *
 * @param onNavigateToSettings 跳转设置回调
 * @param onNavigateToWorkout 跳转训练记录回调
 * @param onNavigateToStats 跳转统计页回调
 * @param onNavigateToChat 跳转 AI 教练对话回调
 */
@Composable
fun TodayRoute(
    onNavigateToSettings: () -> Unit = {},
    onNavigateToWorkout: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onNavigateToChat: () -> Unit = {},
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
        onNavigateToStats = onNavigateToStats,
        onNavigateToChat = onNavigateToChat,
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
 * 各区块拆分为同包独立文件（CoachInsightCard / WeekProgressSection / TodayPlanCard /
 * PlanPickerSheet），本文件只保留容器与顶栏。
 */
@Composable
fun TodayScreen(
    uiState: TodayUiState,
    allPlans: List<WorkoutPlan>,
    onNavigateToSettings: () -> Unit,
    onNavigateToWorkout: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToChat: () -> Unit = {},
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
            TodayTopBar(
                onNavigateToStats = onNavigateToStats,
                onNavigateToChat = onNavigateToChat,
                onNavigateToSettings = onNavigateToSettings,
            )
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
 * 中间：居中 "Today" 标题
 * 右侧：统计入口 + AI 教练入口 + 带彩环的个人资料 / 设置入口按钮
 *
 * @param onNavigateToStats 跳转统计页回调
 * @param onNavigateToChat 跳转 AI 教练对话回调
 * @param onNavigateToSettings 跳转设置回调（个人中心彩环按钮）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodayTopBar(
    onNavigateToStats: () -> Unit,
    onNavigateToChat: () -> Unit,
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
        actions = {
            IconButton(onClick = onNavigateToStats) {
                Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = "统计",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = onNavigateToChat) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = "AI 教练对话",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
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
                    observation = "本周已练 2/3 次 · 距上次训练 1 天",
                    recommendation = "下一课：腿日 · 股四头后侧链",
                    action = CoachAction.START_WORKOUT,
                    isAiGenerated = true,
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
            onNavigateToStats = {},
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
                weekProgress = WeekProgressState(),
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
            onNavigateToStats = {},
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
            onNavigateToStats = {},
            onDisplayModeSelected = {},
            onPlanSelected = {},
            onErrorShown = {},
        )
    }
}
