package com.example.fitlog.feature.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
    Column(modifier = modifier.fillMaxSize()) {
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

        Box(
            modifier = Modifier.fillMaxSize(),
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
                                        IconButton(onClick = { onDeleteWorkout(workout) }) {
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
