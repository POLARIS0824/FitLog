package com.example.fitlog.benchmark

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

internal val benchJson = Json { encodeDefaults = true; explicitNulls = true }
internal fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it) }

@Serializable
internal data class BenchmarkCase(
    val id: String,
    val relPath: String,
    val text: String,
    val rawSha256: String,
    val contentHash: String,
    val responseJson: String = "{\"schemaVersion\":1,\"sessions\":[],\"issues\":[]}",
    val delayMs: Long = 30,
    val fault: String? = null,
    val httpStatus: Int = 200,
    val finishReason: String = "stop",
    val checkpoints: List<Checkpoint> = emptyList(),
    val audited: Boolean = false,
    val rawBytes: Int? = null,
    val charsUtf16: Int? = null,
)

@Serializable
internal data class Checkpoint(val path: String, val expectedJson: String)

@Serializable
internal data class BenchmarkProfile(
    val id: String,
    val runtime: String = "lightweight",
    val concurrency: Int = 1,
    val caseIds: List<String> = emptyList(),
    val round: Int = 0,
    val period: String = "pilot",
    val arrivalIntervalMs: Long? = null,
    val cancelAfterMs: Long? = null,
    val warmup: Boolean = false,
)

@Serializable
internal data class BenchmarkSpec(
    val mode: String = "replay",
    val outputDir: String,
    val datasetFingerprint: String,
    val vaultId: String,
    val sourceRoot: String? = null,
    val sourceRevision: String,
    val baseUrl: String = "https://benchmark.invalid/v1",
    val modelId: String = "replay",
    val apiKeyEnv: String = "FITLOG_BENCH_API_KEY",
    val maxRequests: Int = 50,
    val seed: Int = 42,
    val cases: List<BenchmarkCase>,
    val profiles: List<BenchmarkProfile>,
    val platform: String = "jvm-robolectric",
    val proxyUrl: String? = null,
    val codeFingerprint: String = "unspecified",
    val previousRequests: Int = 0,
    val previousWarmups: Int = 0,
    val budgetLimit: Int = 50,
    val studyStage: String = "pilot",
) {
    fun validate() {
        require(mode in setOf("replay", "live"))
        require(budgetLimit in 1..10_000 && maxRequests in 1..budgetLimit)
        require(previousRequests >= 0 && previousRequests.toLong() + maxRequests <= budgetLimit)
        require(studyStage in setOf("pilot", "connectivity", "concurrency", "runtime", "folder"))
        require(previousWarmups in 0..previousRequests)
        require(cases.isNotEmpty() && cases.map { it.id }.distinct().size == cases.size)
        require(profiles.isNotEmpty() && profiles.map { it.id }.distinct().size == profiles.size)
        require((cases.map { it.id } + profiles.map { it.id }).all { Regex("[A-Za-z0-9_-]+").matches(it) })
        require(cases.all { it.delayMs >= 0 })
        java.util.UUID.fromString(vaultId)
        cases.forEach { case ->
            com.example.fitlog.data.analysis.SourceKey(vaultId, case.relPath)
            require(sha256(case.text.toByteArray()) == case.contentHash)
            case.checkpoints.forEach { point -> atPath(benchJson.parseToJsonElement("{}"), point.path); benchJson.parseToJsonElement(point.expectedJson) }
        }
        require(profiles.all { it.runtime in setOf("baseline", "lightweight", "adk") && it.concurrency in 1..16 })
        require(profiles.all { it.runtime != "baseline" || it.concurrency == 1 })
        require(profiles.all { it.arrivalIntervalMs == null || it.arrivalIntervalMs > 0 })
        require(profiles.all { it.cancelAfterMs == null || it.cancelAfterMs > 0 })
        require(profiles.all { it.caseIds.all { id -> cases.any { it.id == id } } })
        require(profiles.all { it.caseIds.distinct().size == it.caseIds.size })
        require(profiles.all { it.runtime != "baseline" || it.arrivalIntervalMs == null })
        require(mode != "live" || cases.all { it.fault == null && it.httpStatus == 200 })
    }
    companion object {
        fun read(file: File) = benchJson.decodeFromString<BenchmarkSpec>(file.readText()).also { it.validate() }
    }
}
