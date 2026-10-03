package com.example.fitlog

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Ignore ON_START catch-up when registering during an already foreground process.
 *
 * 监听 ProcessLifecycle 的 ON_START，并特意跳过注册时已经处于前台造成的第一次伪触发，让真正的“从后台回前台”才执行刷新
 */
internal class SourceIndexForegroundObserver(lifecycle: Lifecycle, private val onForeground: () -> Unit) : LifecycleEventObserver {
    private var skipInitialStart = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event != Lifecycle.Event.ON_START) return
        if (skipInitialStart) skipInitialStart = false else onForeground()
    }
}
