package com.example.fitlog.benchmark

import java.io.File

/** Live credentials are separate from specs and consumed before the first HTTP dispatch. */
internal fun consumeDeviceCredential(root: File, relative: String): String {
    require(Regex("\\.bench-key-[A-Za-z0-9_-]+\\.txt").matches(relative))
    val file = File(root, relative).canonicalFile
    require(file.parentFile == root.canonicalFile && file.name == relative && file.isFile)
    val bytes = try {
        require(file.length() in 1..4096)
        file.readBytes()
    } finally {
        check(file.delete()) { "Credential file could not be removed" }
    }
    return try {
        bytes.toString(Charsets.UTF_8).trim().also { key ->
            require(key.isNotEmpty() && key.all { it in '!'..'~' }) { "Invalid credential" }
        }
    } finally { bytes.fill(0) }
}

internal fun deviceSpec(original: BenchmarkSpec, output: File, allowLive: Boolean): BenchmarkSpec {
    require(original.sourceRoot == null && original.proxyUrl == null) {
        "Device requests require frozen snapshots and native networking"
    }
    require(original.mode == "replay" || allowLive) { "Live device execution must be explicit" }
    return original.copy(outputDir = output.absolutePath, platform = "android-device")
}
