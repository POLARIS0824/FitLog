package com.example.fitlog.feature.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
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
 * 1. 容器层 (Stateful)
 * 绑定 Hilt ViewModel，处理生命周期感知的状态收集。
 */
@Composable
fun WorkoutRoute(
    modifier: Modifier = Modifier,
    viewModel: WorkoutViewModel = hiltViewModel() // 由 Hilt 自动注入
) {
    // 关键：最新最佳实践，生命周期安全的状态订阅
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    WorkoutScreen(
        uiState = uiState,
        onDeleteWorkout = { viewModel.deleteWorkout(it) },
        modifier = modifier
    )
}


/**
 * 2. 纯 UI 展示层 (Stateless)
 * 不直接依赖任何 ViewModel 或 Hilt。极易进行单元测试与 Compose 预览。
 */
@Composable
fun WorkoutScreen(
    uiState: WorkoutUiState,
    onDeleteWorkout: (Workout) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (uiState) {
            is WorkoutUiState.Loading -> {
                CircularProgressIndicator() // 显示加载中
            }
            is WorkoutUiState.Success -> {
                if (uiState.workouts.isEmpty()) {
                    Text("今天还没有记录训练，开始你的第一练吧！")
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.workouts) { workout ->
                            // 渲染每一条训练记录...
                            Text(text = "训练日期: ${workout.date}, 感受: ${workout.feelings ?: "无"}")
                        }
                    }
                }
            }
            is WorkoutUiState.Error -> {
                Text(text = "加载出错: ${uiState.message}") // 显示错误信息
            }
        }
    }
}


/**
 * 3. 预览层
 * 无需模拟数据库和 Hilt 容器，可自由 mock 各种状态，即时预览界面。
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
            feelings = "状态拉满，泵感强烈！"
        )
    )
    WorkoutScreen(
        uiState = WorkoutUiState.Success(mockWorkouts),
        onDeleteWorkout = {}
    )
}

@Preview(showBackground = true)
@Composable
fun WorkoutScreenLoadingPreview() {
    WorkoutScreen(
        uiState = WorkoutUiState.Loading,
        onDeleteWorkout = {}
    )
}