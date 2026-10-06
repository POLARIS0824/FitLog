package com.example.fitlog.data.analysis.adapter

import com.example.fitlog.data.analysis.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*

internal val diaryCandidateJson = Json {
    ignoreUnknownKeys = true
}

internal sealed interface DecodedDiaryResult {
    data class Success(val diary: DiaryCandidate) : DecodedDiaryResult
    data class Failure(val reason: DiaryParseFailure) : DecodedDiaryResult
}

/** Decode one whole response. Structural errors are failures, never partial empty successes. */
internal object DiaryCandidateCodec {
    fun decode(rawJson: String): DecodedDiaryResult {
        val root = try {
            diaryCandidateJson.parseToJsonElement(rawJson) as? JsonObject
                ?: return DecodedDiaryResult.Failure(DiaryParseFailure.INVALID_TOP_LEVEL)
        } catch (_: SerializationException) {
            return DecodedDiaryResult.Failure(DiaryParseFailure.MALFORMED_JSON)
        }
        val version = root["schemaVersion"] as? JsonPrimitive
        if (version == null || version.isString || version.intOrNull == null ||
            root["sessions"] !is JsonArray || ("issues" in root && root["issues"] !is JsonArray)) {
            return DecodedDiaryResult.Failure(DiaryParseFailure.INVALID_TOP_LEVEL)
        }
        if (version.intOrNull != DIARY_SCHEMA_VERSION) {
            return DecodedDiaryResult.Failure(DiaryParseFailure.UNSUPPORTED_SCHEMA)
        }
        return try {
            DecodedDiaryResult.Success(diaryCandidateJson.decodeFromJsonElement<DiaryCandidate>(root))
        } catch (_: SerializationException) {
            DecodedDiaryResult.Failure(DiaryParseFailure.INVALID_RESPONSE)
        } catch (_: IllegalArgumentException) {
            DecodedDiaryResult.Failure(DiaryParseFailure.INVALID_RESPONSE)
        }
    }
}

internal fun MutableList<ValidationIssue>.error(path: String, code: ValidationCode) {
    add(ValidationIssue(path, code, IssueSeverity.ERROR))
}

internal fun MutableList<ValidationIssue>.review(path: String, code: ValidationCode) {
    add(ValidationIssue(path, code, IssueSeverity.REVIEW))
}
