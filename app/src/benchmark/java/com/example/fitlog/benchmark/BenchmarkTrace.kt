package com.example.fitlog.benchmark

import java.io.File
import java.io.Closeable
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.serialization.json.*

internal class BenchmarkTrace(file: File) : Closeable {
    private val writer = file.bufferedWriter()
    private val origin = System.nanoTime()
    private var sequence = 0
    @Synchronized fun emit(type: String, profile: String? = null, caseId: String? = null,
        vararg fields: Pair<String, Any?>) {
        val event = buildJsonObject {
            put("sequence", sequence++)
            put("elapsed_ms", (System.nanoTime() - origin) / 1e6)
            put("type", type)
            profile?.let { put("profile", it) }
            caseId?.let { put("case_id", it) }
            fields.forEach { (key, value) -> put(key, element(value)) }
        }
        writer.write(event.toString()); writer.newLine(); writer.flush()
    }
    @Synchronized override fun close() { writer.close() }
    private fun element(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        else -> JsonPrimitive(value.toString())
    }
}

internal class CaseObservation(val profile: String, val caseId: String, val trace: BenchmarkTrace,
    val gate: RequestGate) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<CaseObservation>
    var arrivedNs = System.nanoTime()
    var sourceDoneNs = arrivedNs
    var httpMs = 0.0
    var queueMs = 0.0
    var runtimeInitMs = 0.0
    var runtimeMs = 0.0
    var parseValidateMs = 0.0
    var storageMs = 0.0
    var httpCalls = 0
    var events = 0
    var promptTokens: Int? = null
    var outputTokens: Int? = null
    var cachedTokens: Int? = null
    var requestHash: String? = null
    var responseHash: String? = null
    fun emit(type: String, vararg fields: Pair<String, Any?>) = trace.emit(type, profile, caseId, *fields)
    fun elapsedMs() = ((System.nanoTime() - arrivedNs) / 1e6).coerceAtLeast(0.0)
}

internal class RequestBudget(private val maximum: Int) {
    private val counter = AtomicInteger()
    val used get() = counter.get()
    fun claim(): Int {
        while (true) {
            val previous = counter.get()
            if (previous >= maximum) throw BenchmarkStopped("REQUEST_BUDGET")
            if (counter.compareAndSet(previous, previous + 1)) return previous + 1
        }
    }
}

internal class BenchmarkStopped(val reason: String) : IllegalStateException(reason)
