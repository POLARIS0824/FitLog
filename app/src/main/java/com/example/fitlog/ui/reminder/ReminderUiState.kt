package com.example.fitlog.ui.reminder

/**
 * 训练提醒页的 UI 状态。
 */
data class ReminderUiState(
    val enabled: Boolean = false,
    /** 提醒时间（一天中的分钟数） */
    val minutes: Int = 18 * 60,
)
