package com.example.fitlog.benchmark

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BenchmarkDeviceSafetyTest {
    private fun root() = File(RuntimeEnvironment.getApplication().cacheDir, UUID.randomUUID().toString()).also { it.mkdirs() }
    private fun spec() = BenchmarkSpec(outputDir = "unused", datasetFingerprint = "fixture",
        vaultId = UUID.randomUUID().toString(), sourceRevision = "fixture",
        cases = listOf(BenchmarkCase("empty", "empty.md", "", sha256(byteArrayOf()), sha256(byteArrayOf()))),
        profiles = listOf(BenchmarkProfile("one")))

    @Test fun credentialsAreConsumedOnceAndDeletedBeforeUseIncludingInvalidInput() {
        val root = root()
        val file = File(root, ".bench-key-fixture.txt")
        file.writeText("fixture-credential\n")
        assertEquals("fixture-credential", consumeDeviceCredential(root, file.name))
        assertFalse(file.exists())
        assertThrows(IllegalArgumentException::class.java) { consumeDeviceCredential(root, file.name) }
        for (invalid in listOf("", "invalid key", "x".repeat(4097))) {
            file.writeText(invalid)
            assertThrows(IllegalArgumentException::class.java) { consumeDeviceCredential(root, file.name) }
            assertFalse(file.exists())
        }
    }

    @Test fun traversalCannotReadOrDeleteOtherFiles() {
        val root = root()
        val other = File(root, "source.txt").also { it.writeText("source") }
        for (path in listOf(other.name, "../source.txt", other.absolutePath)) {
            assertThrows(IllegalArgumentException::class.java) { consumeDeviceCredential(root, path) }
        }
        assertEquals("source", other.readText())
    }

    @Test fun nativeLiveRequiresExplicitActivationAndFrozenSourceAndNoJvmProxy() {
        val live = spec().copy(mode = "live")
        val output = root()
        assertThrows(IllegalArgumentException::class.java) { deviceSpec(live, output, false) }
        assertThrows(IllegalArgumentException::class.java) { deviceSpec(live.copy(sourceRoot = "original"), output, true) }
        assertThrows(IllegalArgumentException::class.java) { deviceSpec(live.copy(proxyUrl = "http://host:1"), output, true) }
        val activated = deviceSpec(live, output, true)
        assertEquals("android-device", activated.platform)
        assertNull(activated.sourceRoot)
        assertEquals(output.absolutePath, activated.outputDir)
    }

    @Test fun raisingBudgetIsExplicitAndCumulativeCeilingStillApplies() {
        assertThrows(IllegalArgumentException::class.java) { spec().copy(maxRequests = 51).validate() }
        spec().copy(budgetLimit = 1500, maxRequests = 346, previousRequests = 26).validate()
        assertThrows(IllegalArgumentException::class.java) {
            spec().copy(budgetLimit = 1500, maxRequests = 346, previousRequests = 1200).validate()
        }
    }

    @Test fun leavingTheForegroundHostRequestsCancellationAndNormalCompletionDoesNot() {
        val controller = Robolectric.buildActivity(BenchmarkActivity::class.java).setup()
        var cancellations = 0
        controller.get().stopMeasurement = { cancellations++ }
        controller.pause().stop()
        assertEquals(1, cancellations)
        val completed = Robolectric.buildActivity(BenchmarkActivity::class.java).setup()
        completed.get().stopMeasurement = { cancellations++ }
        completed.get().finishMeasurement()
        completed.pause().stop()
        assertEquals(1, cancellations)
    }
}
