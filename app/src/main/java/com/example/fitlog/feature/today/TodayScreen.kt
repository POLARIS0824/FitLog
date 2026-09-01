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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.fitlog.util.findActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitlog.model.WorkoutPlan
import com.example.fitlog.model.ai.CoachAction
import com.example.fitlog.ui.components.SectionLabel
import com.example.fitlog.ui.theme.FitLogTheme
import com.example.fitlog.ui.theme.fitLogColors

/** 「AI 分析」小卡跳转 AI 教练时预填的分析请求。 */
private const val AI_ANALYSIS_PREFILL_QUESTION =
    "请基于我最近几周的训练数据和当前计划，分析训练量与恢复情况，并给出下周的调整建议"

/**
 * 1. 容器层 (Stateful)
 * 绑定 Hilt ViewModel，处理生命周期安全的状态收集。
 *
 * @param onNavigateToSettings 跳转设置回调
 * @param onNavigateToWorkout 跳转训练记录回调（仅查看历史）
 * @param onStartWorkout 启动训练回调（导航至训练页并自动开启会话；
 *   已有进行中会话时为"继续训练"语义）
 */
@Composable
fun TodayRoute(
    onNavigateToSettings: () -> Unit = {},
    onNavigateToWorkout: () -> Unit = {},
    onStartWorkout: () -> Unit = onNavigateToWorkout,
    onNavigateToChatWithPrefill: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // VM 作用域提升到 Activity：切 tab = 清栈重建 entry，entry 作用域的 VM 会
    // 被销毁重建（DB 重查 + 入场动画重放 + 今日动作打卡勾选丢失）。ChatRoute 已有
    // 同款先例；本页无导航参数，Activity 作用域安全（训练页 WorkoutKey 有
    // per-entry 参数，保持 entry 作用域）。Preview 无 Activity 时回落 entry 作用域。
    val activity = LocalContext.current.findActivity()
    val viewModel: TodayViewModel = if (activity != null) hiltViewModel(activity) else hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allPlans by viewModel.allPlans.collectAsStateWithLifecycle()
    TodayScreen(
        uiState = uiState,
        allPlans = allPlans,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToWorkout = onNavigateToWorkout,
        onStartWorkout = onStartWorkout,
        onNavigateToChatWithPrefill = onNavigateToChatWithPrefill,
        onDisplayModeSelected = viewModel::onDisplayModeSelected,
        onToggleExerciseCheck = viewModel::onToggleExerciseCheck,
        onPlanSelected = viewModel::onPlanSelected,
        onDeletePlan = viewModel::onDeletePlan,
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
    onStartWorkout: () -> Unit = onNavigateToWorkout,
    onDisplayModeSelected: (WeekProgressDisplayMode) -> Unit,
    onToggleExerciseCheck: (String) -> Unit = {},
    onPlanSelected: (String) -> Unit,
    onDeletePlan: (String) -> Unit = {},
    onErrorShown: () -> Unit,
    onLogClick: () -> Unit = onNavigateToWorkout,
    onEditClick: (() -> Unit)? = null,
    /** 「AI 分析」小卡点击：携带预填分析请求跳转 AI 教练（null 时不挂点击） */
    onNavigateToChatWithPrefill: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    // rememberSaveable：旋转/重建后弹层不静默消失
    var showPlanSheet by rememberSaveable { mutableStateOf(false) }

    // 待删除计划（删除确认弹窗，transient UI 态；id 可 saveable，对象按 id 回查）
    var pendingDeletePlanId by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingDeletePlan = allPlans.firstOrNull { it.id == pendingDeletePlanId }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.fitLogColors.pageBackground,
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
                    onStartWorkoutClick = onStartWorkout,
                )

                WeekProgressSection(
                    weekProgress = uiState.weekProgress,
                    onDisplayModeSelected = onDisplayModeSelected,
                    onLogClick = onLogClick,
                    onStartClick = onStartWorkout,
                    onEditClick = onEditClick ?: { showPlanSheet = true },
                    onAiAnalysisClick = {
                        onNavigateToChatWithPrefill(AI_ANALYSIS_PREFILL_QUESTION)
                    },
                )

                SectionLabel("今日训练")
                TodayPlanCard(
                    todayPlan = uiState.todayPlan,
                    onToggleExerciseCheck = onToggleExerciseCheck,
                    onActionClick = {
                        when (uiState.todayPlan.status) {
                            PlanStatus.NO_PLAN -> showPlanSheet = true
                            // 未开始/进行中 → 启动或继续会话；已完成 → 查看记录
                            PlanStatus.NOT_STARTED, PlanStatus.IN_PROGRESS -> onStartWorkout()
                            PlanStatus.COMPLETED -> onNavigateToWorkout()
                        }
                    },
                    onExerciseClick = {
                        when (uiState.todayPlan.status) {
                            PlanStatus.NO_PLAN -> showPlanSheet = true
                            PlanStatus.NOT_STARTED, PlanStatus.IN_PROGRESS -> onStartWorkout()
                            PlanStatus.COMPLETED -> onNavigateToWorkout()
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
            onDelete = { pendingDeletePlanId = it.id },
            onDismiss = { showPlanSheet = false },
        )
    }

    // 删除计划确认
    pendingDeletePlan?.let { plan ->
        AlertDialog(
            onDismissRequest = { pendingDeletePlanId = null },
            title = { Text("删除计划？") },
            text = {
                Text(
                    "「${plan.name}」及其全部训练日将被删除。" +
                        "已完成的训练记录不受影响；删除当前激活计划后 Today 将回到无计划状态。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePlan(plan.id)
                        pendingDeletePlanId = null
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletePlanId = null }) {
                    Text("取消")
                }
            },
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
 * 右侧：带彩环的个人资料 / 设置入口按钮（统计与 AI 教练入口已移至底部导航栏）
 *
 * @param onNavigateToSettings 跳转设置回调（个人中心彩环按钮）
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
            containerColor = MaterialTheme.fitLogColors.pageBackground,
            scrolledContainerColor = MaterialTheme.fitLogColors.pageBackground,
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
                    tagText = "推拉腿 · 第3天",
                    title = "腿日 · 股四腿后",
                    subtitle = "6 个动作 · 约 65 分钟",
                    progress = 0f,
                    status = PlanStatus.NOT_STARTED,
                    exercises = listOf(
                        TodayPlanExerciseState(
                            id = "ex-1",
                            exerciseKey = "barbell-full-squat",
                            name = "杠铃深蹲",
                            setsRepsText = "4 组 × 6-8",
                            weightText = "100 kg",
                            isCompleted = false,
                        ),
                        TodayPlanExerciseState(
                            id = "ex-2",
                            exerciseKey = "barbell-romanian-deadlift",
                            name = "罗马尼亚硬拉",
                            setsRepsText = "3 组 × 8-10",
                            weightText = "85 kg",
                            isCompleted = false,
                        ),
                        TodayPlanExerciseState(
                            id = "ex-3",
                            exerciseKey = "bulgarian-split-squat",
                            name = "保加利亚深蹲",
                            setsRepsText = "3 组 × 10",
                            weightText = "60 kg",
                            isCompleted = false,
                        ),
                        TodayPlanExerciseState(
                            id = "ex-4",
                            exerciseKey = "lever-lying-leg-curl",
                            name = "腿弯举",
                            setsRepsText = "3 组 × 12",
                            weightText = "45 kg",
                            isCompleted = false,
                        ),
                        TodayPlanExerciseState(
                            id = "ex-5",
                            exerciseKey = "lever-leg-extension",
                            name = "腿伸展",
                            setsRepsText = "3 组 × 15",
                            weightText = "40 kg",
                            isCompleted = false,
                        ),
                        TodayPlanExerciseState(
                            id = "ex-6",
                            exerciseKey = "lever-standing-calf-raise",
                            name = "提踵",
                            setsRepsText = "4 组 × 15",
                            weightText = "70 kg",
                            isCompleted = false,
                        ),
                    ),
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
