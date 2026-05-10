package com.example.fitlog

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * 应用入口，启用 Hilt 依赖注入。
 */
@HiltAndroidApp
class FitLogApplication : Application()
