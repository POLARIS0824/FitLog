package com.example.fitlog.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainBoundaryTest {
    @Test fun domainAndModelStayIndependentOfOuterLayersAndAndroid() {
        val sourceRoot = listOf(File("src/main/java/com/example/fitlog"), File("app/src/main/java/com/example/fitlog"))
            .firstOrNull { it.isDirectory } ?: error("Cannot locate production Kotlin sources")
        val violations = mutableListOf<String>()
        for (layer in listOf("domain", "model")) {
            val directory = File(sourceRoot, layer)
            val sources = directory.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
            assertTrue("No sources found for $layer", sources.isNotEmpty())
            for (file in sources) {
                file.readLines().forEachIndexed { index, line ->
                    val imported = Regex("^import\\s+([^ ;]+)").find(line)?.groupValues?.get(1)
                        ?: return@forEachIndexed
                    val forbidden = imported.startsWith("android.") || imported.startsWith("androidx.") ||
                        listOf("app", "data", "feature", "ui").any { imported.startsWith("com.example.fitlog.$it.") } ||
                        (layer == "model" && imported.startsWith("com.example.fitlog.domain."))
                    if (forbidden) violations += "${file.relativeTo(sourceRoot)}:${index + 1} imports $imported"
                }
            }
        }
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }
}
