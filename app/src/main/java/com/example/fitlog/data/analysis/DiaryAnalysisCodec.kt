package com.example.fitlog.data.analysis

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** These are storage versions, independent of the model schema and extractor version. */
internal object DiaryAnalysisCodec {
    const val CANDIDATE_VERSION = 2
    private val json = Json { encodeDefaults = true }
    @Serializable private data class Candidate(val formatVersion: Int, val analysis: DiaryAnalysis)

    fun encodeCandidate(analysis: DiaryAnalysis): String = json.encodeToString(Candidate(CANDIDATE_VERSION, analysis))
    fun decodeCandidate(text: String): DiaryAnalysis = json.decodeFromString<Candidate>(text).also {
        require(it.formatVersion == CANDIDATE_VERSION) { "Unsupported candidate storage version" }
    }.analysis
}
