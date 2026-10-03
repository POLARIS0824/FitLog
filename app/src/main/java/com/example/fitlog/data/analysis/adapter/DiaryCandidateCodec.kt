package com.example.fitlog.data.analysis.adapter

import com.example.fitlog.data.analysis.CandidateIssue
import com.example.fitlog.data.analysis.DIARY_SCHEMA_VERSION
import com.example.fitlog.data.analysis.DiaryParseFailure
import com.example.fitlog.data.analysis.ExerciseCandidate
import com.example.fitlog.data.analysis.IssueSeverity
import com.example.fitlog.data.analysis.SessionCandidate
import com.example.fitlog.data.analysis.SetGroupCandidate
import com.example.fitlog.data.analysis.ValidationCode
import com.example.fitlog.data.analysis.ValidationIssue

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull

internal val diaryCandidateJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

internal sealed interface DecodedDiaryResult {
    data class Success(val diary: DecodedDiary) : DecodedDiaryResult
    data class Failure(val reason: DiaryParseFailure) : DecodedDiaryResult
}

internal data class DecodedDiary(
    val sessions: List<DecodedSession?>,
    val modelIssues: List<CandidateIssue>,
    val issues: List<ValidationIssue>,
)

// Null slots preserve the original JSON indices, including rejected children.
internal data class DecodedSession(
    val header: SessionCandidate,
    val exercises: List<DecodedExercise?>,
)

internal data class DecodedExercise(
    val header: ExerciseCandidate,
    val groups: List<SetGroupCandidate?>,
)

/**
 * Decode each session, exercise and group separately so a bad sibling cannot erase good data.
 *
 * 处理外部 JSON 格式，属于模型边界适配层，不直接负责 Room 内部 JSON 持久化
 */
internal object DiaryCandidateCodec {
    fun decode(rawJson: String): DecodedDiaryResult {
        val parsed = try {
            diaryCandidateJson.parseToJsonElement(rawJson)
        } catch (_: SerializationException) {
            return DecodedDiaryResult.Failure(DiaryParseFailure.MALFORMED_JSON)
        }
        val root = parsed as? JsonObject
            ?: return DecodedDiaryResult.Failure(DiaryParseFailure.INVALID_TOP_LEVEL)
        val version = root["schemaVersion"] as? JsonPrimitive
        if (version == null || version.isString || version.intOrNull == null) {
            return DecodedDiaryResult.Failure(DiaryParseFailure.INVALID_TOP_LEVEL)
        }
        if (version.intOrNull != DIARY_SCHEMA_VERSION) {
            return DecodedDiaryResult.Failure(DiaryParseFailure.UNSUPPORTED_SCHEMA)
        }
        val sessions = root["sessions"] as? JsonArray
            ?: return DecodedDiaryResult.Failure(DiaryParseFailure.INVALID_TOP_LEVEL)
        val modelIssueValues = if ("issues" in root) {
            root["issues"] as? JsonArray
                ?: return DecodedDiaryResult.Failure(DiaryParseFailure.INVALID_TOP_LEVEL)
        } else JsonArray(emptyList())
        val issues = mutableListOf<ValidationIssue>()
        val modelIssues = modelIssueValues.mapIndexedNotNull { index, value ->
            decodeValue<CandidateIssue>(value, "$.issues[$index]", issues)
        }
        val decodedSessions = sessions.mapIndexed { sessionIndex, value ->
            val path = "$.sessions[$sessionIndex]"
            val header = decodeValue<SessionCandidate>(value, path, issues, "exercises")
                ?: return@mapIndexed null
            val exercises = array(value as JsonObject, "exercises", "$path.exercises", issues)
                .orEmpty().mapIndexed exercise@ { exerciseIndex, exerciseValue ->
                    val exercisePath = "$path.exercises[$exerciseIndex]"
                    val exerciseHeader = decodeValue<ExerciseCandidate>(
                        exerciseValue, exercisePath, issues, "groups",
                    ) ?: return@exercise null
                    val groups = array(
                        exerciseValue as JsonObject, "groups", "$exercisePath.groups", issues,
                        optional = true,
                    ).orEmpty().mapIndexed { groupIndex, groupValue ->
                        decodeValue<SetGroupCandidate>(
                            groupValue, "$exercisePath.groups[$groupIndex]", issues,
                        )
                    }
                    DecodedExercise(exerciseHeader, groups)
                }
            DecodedSession(header, exercises)
        }
        return DecodedDiaryResult.Success(DecodedDiary(decodedSessions, modelIssues, issues.toList()))
    }

    private fun array(
        parent: JsonObject,
        key: String,
        path: String,
        issues: MutableList<ValidationIssue>,
        optional: Boolean = false,
    ): JsonArray? {
        val value = parent[key]
        if (value == null && optional) return null
        if (value is JsonArray) return value
        issues.error(path, ValidationCode.INVALID_FIELD)
        return null
    }

    private inline fun <reified T> decodeValue(
        value: JsonElement,
        path: String,
        issues: MutableList<ValidationIssue>,
        childArray: String? = null,
    ): T? {
        if (value !is JsonObject) {
            issues.error(path, ValidationCode.INVALID_FIELD)
            return null
        }
        val header = if (childArray == null) value else
            JsonObject(value + (childArray to JsonArray(emptyList())))
        return try {
            diaryCandidateJson.decodeFromJsonElement<T>(header)
        } catch (_: SerializationException) {
            issues.error(path, ValidationCode.INVALID_FIELD)
            null
        } catch (_: IllegalArgumentException) {
            issues.error(path, ValidationCode.INVALID_FIELD)
            null
        }
    }
}

internal fun MutableList<ValidationIssue>.error(path: String, code: ValidationCode) {
    add(ValidationIssue(path, code, IssueSeverity.ERROR))
}

internal fun MutableList<ValidationIssue>.review(path: String, code: ValidationCode) {
    add(ValidationIssue(path, code, IssueSeverity.REVIEW))
}
