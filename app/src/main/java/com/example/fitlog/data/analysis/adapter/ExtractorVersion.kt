package com.example.fitlog.data.analysis.adapter

import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.example.fitlog.data.llm.openAiEndpoint

/**
 * Response format requested for extraction. It participates in the extractor identity, so changing
 * it must also change the produced version (or bump [DiaryExtractionPrompt.VERSION]).
 */
internal const val DIARY_JSON_OUTPUT = false

/** Only values affecting extraction participate; credentials and connection display names never do. */
internal fun diaryExtractorVersion(baseUrl: String, modelId: String): String {
    val identity = Json.encodeToString(
        ExtractionIdentity(
            openAiEndpoint(baseUrl, "chat/completions"),
            modelId,
            DIARY_JSON_OUTPUT,
            DiaryExtractionPrompt.VERSION,
            DiaryCandidateValidator.VERSION,
        )
    )
    return "openai-compatible:" + MessageDigest.getInstance("SHA-256")
        .digest(identity.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

@Serializable
private data class ExtractionIdentity(
    val endpoint: String,
    val model: String,
    val jsonOutput: Boolean,
    val prompt: String,
    val postprocessor: String,
)
