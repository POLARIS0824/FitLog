package com.example.fitlog.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitlog.model.WorkoutPlan
import com.example.fitlog.ui.components.WeeklyWorkoutTracker
import com.example.fitlog.ui.theme.FitLogShapes
import com.example.fitlog.ui.theme.FitLogTheme
import com.example.fitlog.ui.theme.GoogleBlue
import com.example.fitlog.ui.theme.GoogleGreen
import com.example.fitlog.ui.theme.GoogleRed
import com.example.fitlog.ui.theme.GoogleYellow
import com.example.fitlog.ui.theme.fitLogColors
import com.example.fitlog.util.findActivity
import kotlinx.coroutines.delay

private const val COACH_PREFILL_QUESTION = "请结合今天的训练安排和我最近的训练状态，给我具体建议"

@Composable
fun TodayRoute(
    onNavigateToSettings: () -> Unit = {},
    onNavigateToWorkout: () -> Unit = {},
    onStartWorkout: () -> Unit = onNavigateToWorkout,
    onNavigateToChatWithPrefill: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
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

/** Material Expressive Today：教练建议、当前训练、周节奏、最近完成。 */
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
    onNavigateToChatWithPrefill: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showPlanSheet by rememberSaveable { mutableStateOf(false) }
    var pendingDeletePlanId by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingDeletePlan = allPlans.firstOrNull { it.id == pendingDeletePlanId }
    val planAction = {
        when (uiState.todayPlan.status) {
            PlanStatus.NO_PLAN -> showPlanSheet = true
            PlanStatus.NOT_STARTED, PlanStatus.IN_PROGRESS -> onStartWorkout()
            PlanStatus.COMPLETED -> onNavigateToWorkout()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.fitLogColors.pageBackground,
        topBar = { TodayTopBar(onNavigateToSettings) },
    ) { innerPadding ->
        if (uiState.uiState.isLoading) {
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)) {
                    Text(
                        text = "今日",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-1).sp,
                    )
                    Text(
                        text = uiState.dateLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                CoachInsightCard(
                    insight = uiState.coachInsight,
                    onCoachClick = { onNavigateToChatWithPrefill(COACH_PREFILL_QUESTION) },
                )

                TodayWorkoutHero(
                    state = uiState.todayPlan,
                    onActionClick = planAction,
                )

                if (uiState.weekDays.isNotEmpty()) {
                    WeeklyWorkoutTracker(
                        days = uiState.weekDays,
                        completedCount = uiState.weekProgress.completedWorkouts,
                        targetCount = uiState.weekProgress.targetWorkouts,
                        title = "本周训练",
                        showTargetRatio = true,
                    )
                }

                uiState.recentWorkout?.let { recent ->
                    RecentWorkoutCard(recent = recent, onClick = onLogClick)
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }

    if (showPlanSheet) {
        PlanPickerSheet(
            plans = allPlans,
            activePlanId = uiState.todayPlan.planId,
            onSelect = { onPlanSelected(it); showPlanSheet = false },
            onDelete = { pendingDeletePlanId = it.id },
            onDismiss = { showPlanSheet = false },
        )
    }

    pendingDeletePlan?.let { plan ->
        AlertDialog(
            onDismissRequest = { pendingDeletePlanId = null },
            title = { Text("删除计划？") },
            text = { Text("「${plan.name}」及其全部训练日将被删除。已完成的训练记录不受影响。") },
            confirmButton = {
                TextButton(onClick = { onDeletePlan(plan.id); pendingDeletePlanId = null }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletePlanId = null }) { Text("取消") }
            },
        )
    }

    uiState.uiState.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = onErrorShown,
            confirmButton = { TextButton(onClick = onErrorShown) { Text("知道了") } },
            title = { Text("出错了") },
            text = { Text(message) },
        )
    }
}

@Composable
private fun TodayWorkoutHero(
    state: TodayPlanState,
    onActionClick: () -> Unit,
) {
    val elapsed = rememberElapsedText(state.startedAtMs)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = FitLogShapes.Card,
        colors = CardDefaults.cardColors(
            containerColor = if (state.status == PlanStatus.IN_PROGRESS) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.fitLogColors.card
            },
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                ) {
                    Text(
                        text = when (state.status) {
                            PlanStatus.IN_PROGRESS -> "进行中"
                            PlanStatus.COMPLETED -> "已完成"
                            PlanStatus.NOT_STARTED -> "今日训练"
                            PlanStatus.NO_PLAN -> "开始训练"
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (state.status == PlanStatus.IN_PROGRESS) {
                    Text(
                        text = elapsed,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }

            Column {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
                )
                Text(
                    text = listOf(state.tagText, state.subtitle).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.targetWorkingSets > 0 || state.completedWorkingSets > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("训练进度", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = if (state.targetWorkingSets > 0) {
                            "${state.completedWorkingSets}/${state.targetWorkingSets} 组"
                        } else {
                            "${state.completedWorkingSets} 组"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                LinearProgressIndicator(
                    progress = { state.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
                    trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                )
            }

            state.nextSetText?.let {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.FitnessCenter, contentDescription = null)
                        Text(it, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Button(
                onClick = onActionClick,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = CircleShape,
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(state.buttonText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun rememberElapsedText(startedAtMs: Long?): String {
    val elapsedSeconds by produceState(initialValue = 0L, key1 = startedAtMs) {
        if (startedAtMs == null) return@produceState
        while (true) {
            value = ((System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L) / 1000L)
            delay(1000L)
        }
    }
    val hours = elapsedSeconds / 3600
    val minutes = (elapsedSeconds % 3600) / 60
    val seconds = elapsedSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}

@Composable
private fun RecentWorkoutCard(recent: RecentWorkoutState, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = FitLogShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.fitLogColors.card),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.FitnessCenter, contentDescription = null)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("最近完成", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(recent.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "${recent.supportingText} · ${recent.setCount} 组 · ${recent.volumeText}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "查看训练记录")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodayTopBar(onNavigateToSettings: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = "FitLog",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        actions = {
            IconButton(onClick = onNavigateToSettings) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .border(
                            2.dp,
                            Brush.sweepGradient(listOf(GoogleBlue, GoogleRed, GoogleYellow, GoogleGreen, GoogleBlue)),
                            CircleShape,
                        )
                        .padding(3.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Icon(Icons.Default.Person, contentDescription = "个人中心", modifier = Modifier.size(20.dp))
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.fitLogColors.pageBackground,
            scrolledContainerColor = MaterialTheme.fitLogColors.pageBackground,
        ),
    )
}

@Preview(showBackground = true)
@Composable
private fun TodayScreenPreview() {
    FitLogTheme {
        TodayScreen(
            uiState = TodayUiState(
                coachInsight = CoachInsightState(
                    greeting = "下午好，Polaris",
                    observation = "本周已完成一次训练，节奏正在建立。",
                    recommendation = "今天继续完成腿部力量训练。",
                    isAvailable = true,
                ),
                weekProgress = WeekProgressState(completedWorkouts = 1, targetWorkouts = 3),
                todayPlan = TodayPlanState(
                    tagText = "推拉腿 · 第3天",
                    title = "腿部力量",
                    subtitle = "6 个动作 · 60 分钟",
                    progress = 4f / 18f,
                    status = PlanStatus.IN_PROGRESS,
                    completedWorkingSets = 4,
                    targetWorkingSets = 18,
                    nextSetText = "杠铃深蹲 · 下一组 80 kg × 8 次",
                ),
                uiState = UiState(),
                dateLabel = "9月23日 · 星期三",
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