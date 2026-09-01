package com.example.fitlog.util

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity

/**
 * 沿 ContextWrapper 链找到宿主 [ComponentActivity]（Compose 的 context 常被主题包装）。
 *
 * 此前为 ChatScreen 的私有实现，TodayRoute/StatsRoute 把 tab 根页 VM 提升到
 * Activity 作用域时也需要同一语义，收口至此消除三处复制。
 */
tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
