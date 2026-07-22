package com.example.fitlog.ui.profile

import com.example.fitlog.model.user.Gender
import com.example.fitlog.model.user.TrainingGoal

/**
 * 个人资料页的 UI 状态。
 *
 * 数值字段（年龄/身高/体重）以字符串持有原始输入，保存时才解析。
 */
data class ProfileUiState(
    val name: String = "",
    val age: String = "",
    val gender: Gender? = null,
    val height: String = "",
    val weight: String = "",
    val goal: TrainingGoal? = null,
    val isSaving: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null,
)
