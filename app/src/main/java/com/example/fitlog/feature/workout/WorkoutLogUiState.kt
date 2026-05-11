package com.example.fitlog.feature.workout

import com.example.fitlog.domain.model.Exercise
import java.time.LocalDate

/**
 * 训练日志编辑界面的 UI 状态。
 *
 * 驱动 [WorkoutLogScreen] 的展示与交互，包含当前训练的日期、动作列表、
 * 训练感受、动作库缓存以及加载/保存状态。
 *
 * @property date 当前训练的日期
 * @property exercises 当前训练中的动作列表（含组数据）
 * @property feelings 训练后的主观感受/备注
 * @property availableExercises 动作库缓存，用于动作选择器的数据源
 * @property isLoading 是否正在加载已有训练数据
 * @property isSaving 是否正在保存
 * @property saveSuccess 最近一次保存是否成功
 * @property errorMessage 错误提示信息，无错误时为 null
 */
data class WorkoutLogUiState(
    val date: LocalDate = LocalDate.now(),
    val exercises: List<ExerciseUiModel> = emptyList(),
    val feelings: String = "",
    val availableExercises: List<Exercise> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * 训练日志中单个动作的 UI 模型。
 *
 * @property exerciseKey 动作的业务标识（kebab-case），自定义动作可能为 null
 * @property name 动作显示名称
 * @property sets 该动作下的所有组
 */
data class ExerciseUiModel(
    val exerciseKey: String? = null,
    val name: String = "",
    val sets: List<SetUiModel> = emptyList(),
)

/**
 * 单组的 UI 模型。
 *
 * @property setNumber 组号（从 1 开始）
 * @property weightKg 重量（kg），未输入时为 null
 * @property reps 次数，未输入时为 null
 */
data class SetUiModel(
    val setNumber: Int = 1,
    val weightKg: Float? = null,
    val reps: Int? = null,
)
