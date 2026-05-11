package com.example.fitlog.feature.workout

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.fitlog.feature.workout.components.ExerciseCard
import com.example.fitlog.feature.workout.components.ExerciseSelector
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 训练日志编辑界面。
 *
 * 支持新建训练（不传 [date]）或编辑已有训练（传入 [date]）。
 * 通过 [hiltViewModel] 获取 [WorkoutLogViewModel]，并观察其 [WorkoutLogUiState]
 * 驱动 UI。
 *
 * @param date 可选的编辑日期，null 表示新建模式
 * @param viewModel 训练日志 ViewModel，默认通过 Hilt 注入
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutLogScreen(
    date: LocalDate? = null,
    viewModel: WorkoutLogViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showDatePicker by remember { mutableStateOf(false) }
    var showExerciseSelector by remember { mutableStateOf(false) }

    LaunchedEffect(date) {
        viewModel.init(date)
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            snackbarHostState.showSnackbar("保存成功")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (date == null) "新建训练" else "编辑训练") },
                actions = {
                    IconButton(
                        onClick = viewModel::save,
                        enabled = !uiState.isSaving,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "保存",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showExerciseSelector = true }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "添加动作",
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                        OutlinedTextField(
                            value = uiState.date.format(formatter),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("日期") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("更改日期")
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = uiState.feelings,
                            onValueChange = viewModel::updateFeelings,
                            label = { Text("训练感受 / 备注") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                        )
                    }

                    itemsIndexed(
                        items = uiState.exercises,
                        key = { index, _ -> index },
                    ) { index, exercise ->
                        ExerciseCard(
                            exercise = exercise,
                            index = index,
                            onAddSet = viewModel::addSet,
                            onRemoveExercise = viewModel::removeExercise,
                            onRemoveSet = viewModel::removeSet,
                            onUpdateSet = viewModel::updateSet,
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }

            if (showDatePicker) {
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = uiState.date
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli(),
                )
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                datePickerState.selectedDateMillis?.let { millis ->
                                    val selected = Instant.ofEpochMilli(millis)
                                        .atZone(ZoneId.systemDefault())
                                        .toLocalDate()
                                    viewModel.updateDate(selected)
                                }
                                showDatePicker = false
                            },
                        ) {
                            Text("确定")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) {
                            Text("取消")
                        }
                    },
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            if (showExerciseSelector) {
                ExerciseSelector(
                    availableExercises = uiState.availableExercises,
                    onSelect = viewModel::addExercise,
                    onDismiss = { showExerciseSelector = false },
                )
            }
        }
    }
}
