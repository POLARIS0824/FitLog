package com.example.fitlog

import android.app.Application
import com.example.fitlog.util.log.FitLogBootstrap
import dagger.hilt.android.HiltAndroidApp

/**
 * 应用入口，启用 Hilt 依赖注入。
 */
@HiltAndroidApp
class FitLogApplication : Application() {

    override fun onCreate() {
        // 日志子系统先于 Hilt 装配（attachBaseContext 已完成，filesDir 可用）：
        // 紧随其后的 DI 初始化若抛异常，也能被 CrashHandler 落盘
        FitLogBootstrap.init(this)
        super.onCreate()
    }
}
