package com.example.fitlog.feature.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitlog.model.Exercise
import com.example.fitlog.model.SetType
import com.example.fitlog.model.Workout
import com.example.fitlog.ui.components.FitLogCard
import com.example.fitlog.ui.theme.fitLogColors
import com.example.fitlog.util.VolumeAggregator
import com.example.fitlog.util.VolumeFormatter
import java.time.LocalDate
import java.util.Locale

/**
 * 训练页容器层：绑定 [WorkoutViewModel]，生命周期安全地收集状态。
 *
 * @param autoStart 进入页面即自动启动会话（Today「开始训练」导航携带；
 *   已有进行中会话时为 no-op，即"继续训练"语义）
 * @param onBack 返回上一页回调（Navigation3 回退栈语义）
 */
@Composable
fun WorkoutRoute(
    autoStart: Boolean = false,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: WorkoutViewModel = hiltViewModel(), // 由 Hilt 自动注入
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val exerciseCatalog by viewModel.exerciseCatalog.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (autoStart) viewModel.maybeAutoStart()
    }

    WorkoutScreen(
        uiState = uiState,
        activeSession = activeSession,
        exerciseCatalog = exerciseCatalog,
        message = message,
        onBack = onBack,
        onStartSession = viewModel::startSession,
        onFinishSession = viewModel::finishSession,
        onDiscardSession = viewModel::discardSession,
        onAddExercise = viewModel::addExercise,
        onRemoveExercise = viewModel::removeExercise,
        onAddSet = viewModel::addSet,
        onUpdateSet = viewModel::updateSet,
        onToggleSetType = viewModel::toggleSetType,
        onRemoveSet = viewModel::removeSet,
        onDeleteWorkout = viewModel::deleteWorkout,
        onMessageShown = viewModel::onMessageShown,
        modifier = modifier,
    )
}

/**
 * 训练页纯 UI 层：进行中会话（[ActiveSessionView]）优先呈现；
 * 无会话时显示历史记录列表（含空态的"开始训练"入口）。
 *
 * @param uiState 历史列表状态（加载/错误/数据三态，进行中会话行已由 VM 过滤）
 * @param activeSession 进行中的训练会话（null = 无会话）
 * @param exerciseCatalog 动作库目录（会话内添加动作选择器）
 * @param message 一次性操作提示（Snackbar 展示后经 [onMessageShown] 清除）
 * @param onStartSession 启动训练会话
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    uiState: WorkoutUiState,
    activeSession: ActiveSession?,
    exerciseCatalog: List<Exercise>,
    message: String? = null,
    onBack: () -> Unit = {},
    onStartSession: () -> Unit = {},
    onFinishSession: (String?) -> Unit = {},
    onDiscardSession: () -> Unit = {},
    onAddExercise: (Exercise) -> Unit = {},
    onRemoveExercise: (Long) -> Unit = {},
    onAddSet: (Long) -> Unit = {},
    onUpdateSet: (Long, Float, Int, SetType) -> Unit = { _, _, _, _ -> },
    onToggleSetType: (Long) -> Unit = {},
    onRemoveSet: (Long) -> Unit = {},
    onDeleteWorkout: (Workout) -> Unit = {},
    onMessageShown: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // 待删除确认的记录（瞬时 UI 状态）：删除会级联清空动作与组数明细，必须二次确认
    var pendingDelete by remember { mutableStateOf<Workout?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            onMessageShown()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (activeSession != null) "训练进行中" else "训练记录",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.fitLogColors.pageBackground,
                    scrolledContainerColor = MaterialTheme.fitLogColors.pageBackground,
                ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (activeSession != null) {
                // 会话进行中：整页让给会话视图（历史在结束后可见）
                ActiveSessionView(
                    session = activeSession,
                    exerciseCatalog = exerciseCatalog,
                    onFinishSession = onFinishSession,
                    onDiscardSession = onDiscardSession,
                    onAddExercise = onAddExercise,
                    onRemoveExercise = onRemoveExercise,
                    onAddSet = onAddSet,
                    onUpdateSet = onUpdateSet,
                    onToggleSetType = onToggleSetType,
                    onRemoveSet = onRemoveSet,
                )
            } else {
                when (uiState) {
                    is WorkoutUiState.Loading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )

                    is WorkoutUiState.Success -> {
                        if (uiState.workouts.isEmpty()) {
                            WorkoutEmptyState(onStartSession = onStartSession)
                        } else {
                            WorkoutHistoryList(
                                workouts = uiState.workouts,
                                onDeleteWorkout = { pendingDelete = it },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }

    // 删除确认对话框
    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除训练记录") },
            text = {
                Text("将删除 ${target.date} 的训练记录及其全部动作与组数明细，此操作无法恢复。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDeleteWorkout(target)
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消")
                }
            },
        )
    }
}

/** 空态：引导开始第一次训练（Today 有计划课次时启动会话会自动预填动作）。 */
@Composable
private fun WorkoutEmptyState(onStartSession: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "还没有训练记录",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "从今日计划开始，或进行一次自由训练",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onStartSession) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("开始训练")
        }
    }
}

/** 历史记录列表：按日期降序的完整训练卡片（点击展开动作与组明细）。 */
@Composable
private fun WorkoutHistoryList(
    workouts: List<Workout>,
    onDeleteWorkout: (Workout) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 单条展开的明细：-1 = 全部折叠（rememberSaveable：旋转后展开态保留）
    var expandedId by rememberSaveable { mutableLongStateOf(-1L) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(workouts, key = { it.id }) { workout ->
            WorkoutHistoryCard(
                workout = workout,
                expanded = expandedId == workout.id,
                onToggleExpanded = {
                    expandedId = if (expandedId == workout.id) -1L else workout.id
                },
                onDelete = { onDeleteWorkout(workout) },
            )
        }
    }
}

/** 单条历史记录卡片：日期 + 摘要（正式组/容量/时长/感受），展开显示动作与组明细。 */
@Composable
private fun WorkoutHistoryCard(
    workout: Workout,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onDelete: () -> Unit,
) {
    val workingSets = workout.exercises.sumOf { log ->
        log.sets.count { it.setType == SetType.WORKING }
    }
    val volumeKg = VolumeAggregator.workingVolume(listOf(workout))
    val durationMinutes = if (workout.startedAt != null && workout.endedAt != null) {
        ((workout.endedAt - workout.startedAt) / 60_000L).coerceAtLeast(0)
    } else {
        null
    }

    FitLogCard(modifier = Modifier.clickable(onClick = onToggleExpanded)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = workout.date.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = buildString {
                            append("$workingSets 组 · ${VolumeFormatter.formatVolume(volumeKg)}")
                            durationMinutes?.let { append(" · $it 分钟") }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "删除 ${workout.date} 的训练记录",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            workout.feelings?.takeIf { it.isNotBlank() }?.let { feelings ->
                Text(
                    text = "感受：$feelings",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    workout.exercises.forEach { log ->
                        Text(
                            text = buildString {
                                append(log.name)
                                append(" | ")
                                append(
                                    log.sets.joinToString("; ") { set ->
                                        "${VolumeFormatter.formatWeightKg(set.weightKg)}kg×${set.reps}" +
                                            if (set.setType == SetType.WARMUP) "(热身)" else ""
                                    },
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                    if (workout.exercises.isEmpty()) {
                        Text(
                            text = "仅存档记录（无动作明细）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 预览：数据态（无需模拟数据库和 Hilt 容器）。
 */
@Preview(showBackground = true)
@Composable
fun WorkoutScreenSuccessPreview() {
    val mockWorkouts = listOf(
        Workout(
            id = 1L,
            userId = 0L,
            date = LocalDate.now(),
            exercises = listOf(
                com.example.fitlog.model.ExerciseLog(
                    name = "Barbell Bench Press",
                    exerciseKey = "barbell-bench-press",
                    sets = listOf(
                        com.example.fitlog.model.SetLog(weightKg = 60f, reps = 8),
                        com.example.fitlog.model.SetLog(weightKg = 62.5f, reps = 8),
                    ),
                ),
            ),
            feelings = "状态拉满，泵感强烈！",
            startedAt = 0L,
            endedAt = 3_600_000L,
        ),
    )
    com.example.fitlog.ui.theme.FitLogTheme {
        WorkoutScreen(
            uiState = WorkoutUiState.Success(mockWorkouts),
            activeSession = null,
            exerciseCatalog = emptyList(),
        )
    }
}

/**
 * 预览：加载态。
 */
@Preview(showBackground = true)
@Composable
fun WorkoutScreenLoadingPreview() {
    WorkoutScreen(
        uiState = WorkoutUiState.Loading,
        activeSession = null,
        exerciseCatalog = emptyList(),
    )
}
