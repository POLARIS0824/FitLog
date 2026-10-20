package com.example.fitlog.benchmark

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.*
import java.io.File

/** Same-APK instrumentation keeps the runtime inside the R8 program being measured. */
class BenchmarkInstrumentation : Instrumentation() {
    private var arguments = Bundle()

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        this.arguments = arguments ?: Bundle()
        start() // The platform calls onStart on its instrumentation thread.
    }

    override fun onStart() {
        var activity: BenchmarkActivity? = null
        var resultCode = Activity.RESULT_OK
        val result = Bundle()
        try {
            runBlocking {
                val relative = requireNotNull(arguments.getString("benchmarkSpec")) { "Missing benchmarkSpec" }
                val root = requireNotNull(targetContext.getExternalFilesDir(null)).canonicalFile
                val file = File(root, relative).canonicalFile
                require(file.toPath().startsWith(root.toPath()))
                val original = BenchmarkSpec.read(file)
                val outputName = arguments.getString("benchmarkOutput") ?: "results"
                require(Regex("[A-Za-z0-9_-]+").matches(outputName))
                val output = File(root, outputName)
                require(!output.exists()) { "Device output directory already used" }
                check(output.mkdirs())
                val spec = deviceSpec(original, output, arguments.getString("benchmarkLive") == "true")
                val credential = if (spec.mode == "live") consumeDeviceCredential(root,
                    requireNotNull(arguments.getString("benchmarkCredential"))) else null
                File(output, "spec.json").writeText(benchJson.encodeToString(BenchmarkSpec.serializer(), spec))
                if (spec.mode == "live" || arguments.getString("benchmarkForeground") == "true") {
                    activity = startActivitySync(Intent(targetContext, BenchmarkActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as BenchmarkActivity
                }
                val measurement = async(start = CoroutineStart.LAZY) { BenchmarkRunner(targetContext).run(spec, credential) }
                runOnMainSync { activity?.stopMeasurement = { measurement.cancel() } }
                measurement.start()
                measurement.await()
            }
            result.putString("stream", "Benchmark completed; inspect events and quality results.\n")
        } catch (failure: Throwable) {
            // Class names and frames only: no input, model answer or exception messages.
            val stack = generateSequence(failure) { it.cause }.take(8).joinToString("\n") {
                it.javaClass.name + "\n" + it.stackTrace.joinToString("\n")
            }
            resultCode = Activity.RESULT_CANCELED
            result.putString("stream", "Benchmark failed or cancelled.\n")
            result.putString("stack", stack)
        } finally { runOnMainSync { activity?.finishMeasurement() } }
        finish(resultCode, result)
    }
}
