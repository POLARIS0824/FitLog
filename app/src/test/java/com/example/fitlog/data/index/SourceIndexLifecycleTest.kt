package com.example.fitlog.data.index

import android.app.Activity
import android.content.res.Configuration
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SourceIndexLifecycleTest {
    @Test fun recreatedActivityAndNewActivityShareScanSaveCoordinator() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val before = SourceIndexRepository.get(controller.get())
        val configuration = Configuration(controller.get().resources.configuration).apply {
            orientation = if (orientation == Configuration.ORIENTATION_LANDSCAPE)
                Configuration.ORIENTATION_PORTRAIT else Configuration.ORIENTATION_LANDSCAPE
        }
        controller.configurationChange(configuration)
        assertSame(before, SourceIndexRepository.get(controller.get()))
        val next = Robolectric.buildActivity(Activity::class.java).setup()
        assertSame(before, SourceIndexRepository.get(next.get()))
        next.pause().stop().destroy()
        controller.pause().stop().destroy()
    }
}
