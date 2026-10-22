package com.example.fitlog.data.llm

import io.ktor.http.URLBuilder
import io.ktor.http.encodedPath
import java.net.URI
import java.net.URISyntaxException

/**
 * Shared validation and path handling for every OpenAI-compatible request.
 *
 * Provider configuration, diary extraction and agent answers all build their URL here, so the
 * HTTPS-only, no-credentials, no-query, no-fragment rule is enforced in exactly one place. This
 * symbol used to live inside the diary adapter and was imported back by provider configuration.
 */
internal fun openAiEndpoint(baseUrl: String, path: String): String {
    val uri = try { URI(baseUrl.trim()) } catch (_: URISyntaxException) {
        throw IllegalArgumentException("Invalid AI base URL")
    }
    require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() &&
        uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
        "AI base URL must be HTTPS without credentials, query or fragment"
    }
    return URLBuilder(uri.toASCIIString()).apply {
        encodedPath = encodedPath.trimEnd('/') + "/" + path
    }.buildString()
}
