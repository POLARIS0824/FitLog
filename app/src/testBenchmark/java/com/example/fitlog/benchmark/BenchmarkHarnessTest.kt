package com.example.fitlog.benchmark

import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BenchmarkHarnessTest {
    @Test fun explicitlyConfiguredRun() = runBlocking {
        // Unit-test discovery cannot accidentally start a live benchmark.
        val path = System.getProperty("fitlog.benchmark.spec") ?: return@runBlocking
        BenchmarkRunner(RuntimeEnvironment.getApplication()).run(BenchmarkSpec.read(File(path)))
    }
}
