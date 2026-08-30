package com.example.fitlog.feature.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitlog.model.Workout
import java.time.LocalDate

/**
 * 训练记录列表页容器层：绑定 [WorkoutViewModel]，生命周期安全地收集状态。
 *
 * @param onBack 返回上一页回调（Navigation3 回退栈语义）
 * @param modifier 修饰符
 */
@Composable
fun WorkoutRoute(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: WorkoutViewModel = hiltViewModel(), // 由 Hilt 自动注入
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    WorkoutScreen(
        uiState = uiState,
        onBack = onBack,
        onDeleteWorkout = viewModel::deleteWorkout,
        modifier = modifier,
    )
}

/**
 * 训练记录列表页纯 UI 层：顶栏 + 记录列表（每条带删除入口）。
 *
 * @param uiState 页面状态（加载/错误/数据三态）
 * @param onBack 返回上一页回调
 * @param onDeleteWorkout 删除一条训练记录
 * @param modifier 修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    uiState: WorkoutUiState,
    onBack: () -> Unit = {},
    onDeleteWorkout: (Workout) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 待删除确认的记录（瞬时 UI 状态）：删除会级联清空动作与组数明细，必须二次确认
    var pendingDelete by remember { mutableStateOf<Workout?>(null) }

    // Scaffold 统一处理系统栏 insets：边到边下列表底部不被手势条/导航栏遮挡
    // （同 StatsScreen/TodayScreen 惯例；此前裸 Column 使最后一行的删除按钮点不到）
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "训练记录",
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
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            when (uiState) {
                is WorkoutUiState.Loading -> CircularProgressIndicator()
                is WorkoutUiState.Success -> {
                    if (uiState.workouts.isEmpty()) {
                        Text("今天还没有记录训练，开始你的第一练吧！")
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(uiState.workouts, key = { it.id }) { workout ->
                                ListItem(
                                    headlineContent = {
                                        Text(text = "训练日期: ${workout.date}")
                                    },
                                    supportingContent = {
                                        Text(text = "感受: ${workout.feelings ?: "无"}")
                                    },
                                    trailingContent = {
                                        IconButton(onClick = { pendingDelete = workout }) {
                                            Icon(
                                                imageVector = Icons.Filled.DeleteOutline,
                                                contentDescription = "删除该训练记录",
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                is WorkoutUiState.Error -> Text(text = "加载出错: ${uiState.message}")
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
            exercises = emptyList(),
            feelings = "状态拉满，泵感强烈！",
        ),
    )
    WorkoutScreen(
        uiState = WorkoutUiState.Success(mockWorkouts),
        onDeleteWorkout = {},
    )
}

/**
 * 预览：加载态。
 */
@Preview(showBackground = true)
@Composable
fun WorkoutScreenLoadingPreview() {
    WorkoutScreen(
        uiState = WorkoutUiState.Loading,
        onDeleteWorkout = {},
    )
}
