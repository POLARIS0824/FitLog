package com.example.fitlog

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/** Ignore ON_START catch-up when registering during an already foreground process. */
internal class SourceIndexForegroundObserver(lifecycle: Lifecycle, private val onForeground: () -> Unit) : LifecycleEventObserver {
    private var skipInitialStart = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event != Lifecycle.Event.ON_START) return
        if (skipInitialStart) skipInitialStart = false else onForeground()
    }
}
