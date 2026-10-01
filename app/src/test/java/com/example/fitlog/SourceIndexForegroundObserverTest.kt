package com.example.fitlog

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceIndexForegroundObserverTest {
    private class Owner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this)
    }
    @Test fun registeringAndRecreatingInForegroundDoNotRequestRefresh() {
        val owner = Owner()
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        var calls = 0
        val first = SourceIndexForegroundObserver(owner.lifecycle) { calls++ }
        owner.lifecycle.addObserver(first)
        assertEquals(0, calls)
        owner.lifecycle.removeObserver(first)
        owner.lifecycle.addObserver(SourceIndexForegroundObserver(owner.lifecycle) { calls++ })
        assertEquals(0, calls)
        owner.lifecycle.currentState = Lifecycle.State.CREATED
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        assertEquals(1, calls)
        owner.lifecycle.currentState = Lifecycle.State.CREATED
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        assertEquals(2, calls)
    }
    @Test fun registeringWhileBackgroundedChecksOnActualStart() {
        val owner = Owner()
        owner.lifecycle.currentState = Lifecycle.State.CREATED
        var calls = 0
        owner.lifecycle.addObserver(SourceIndexForegroundObserver(owner.lifecycle) { calls++ })
        assertEquals(0, calls)
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        assertEquals(1, calls)
    }
}
