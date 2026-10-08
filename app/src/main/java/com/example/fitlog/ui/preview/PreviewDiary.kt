package com.example.fitlog.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.fitlog.R
import com.example.fitlog.data.analysis.*
import com.example.fitlog.data.vault.MarkdownFile
import com.example.fitlog.data.vault.MarkdownSnapshot
import com.example.fitlog.editor.EditorDraft
import com.example.fitlog.editor.RecoveryEntry
import com.example.fitlog.navigation.FitLogRoute
import java.time.LocalDate

/** In-memory fixtures only: no ViewModel, database, SAF access, or network requests. */
internal object PreviewDiary {
    const val VAULT_ID = "00000000-0000-4000-8000-000000000001"
    const val VAULT_URI = "content://preview/tree/fitness"
    const val DATE = "2026-10-08"
    const val FILE_NAME = "$DATE.md"
    const val PATH = "diary/$FILE_NAME"
    const val DOCUMENT = "content://preview/document/$FILE_NAME"
    const val TIMESTAMP = 1791417600000L

    val route = FitLogRoute.DiaryDetail(VAULT_URI, VAULT_ID, DOCUMENT, PATH, VAULT_URI, FILE_NAME)
    val file = MarkdownFile(DOCUMENT, FILE_NAME, PATH, writable = true, directory = VAULT_URI)

    @Composable
    fun snapshot() = MarkdownSnapshot(file, stringResource(R.string.preview_diary_markdown), "preview", false)

    @Composable
    fun candidate(): StoredDiaryCandidate {
        val name = stringResource(R.string.preview_exercise_name)
        val quote = stringResource(R.string.preview_exercise_quote)
        val evidence = EvidenceQuote(quote = quote)
        val group = SetGroupCandidate(rawText = quote, weight = 60.0, unit = WeightUnit.KG,
            basis = WeightBasis.TOTAL, reps = 8, count = 3)
        val exercise = ExerciseCandidate(rawName = name, evidence = evidence, groups = listOf(group))
        val sets = (0..2).map { index ->
            ExpandedSet(0, index,
                CandidateValue(60.0, CandidateOrigin.EXPLICIT),
                CandidateValue(WeightUnit.KG, CandidateOrigin.EXPLICIT),
                CandidateValue(WeightBasis.TOTAL, CandidateOrigin.EXPLICIT),
                CandidateValue(8, CandidateOrigin.EXPLICIT), CandidateOrigin.EXPLICIT, 60.0)
        }
        val key = DiaryParseKey(SourceKey(VAULT_ID, PATH), "a".repeat(64), 1, "preview")
        val attempt = DiaryParseAttempt("preview-run", key, ParseRunStatus.SUCCEEDED, TIMESTAMP, TIMESTAMP, null)
        val analysis = DiaryAnalysis(key, listOf(ValidatedSession("sessions[0]", LocalDate.parse(DATE), null,
            listOf(ValidatedExercise("sessions[0].exercises[0]", exercise, evidence, sets)))), emptyList(), emptyList())
        return StoredDiaryCandidate(attempt, analysis)
    }

    @Composable
    fun recoveryEntry(): RecoveryEntry {
        val draft = EditorDraft(VAULT_URI, DOCUMENT, DOCUMENT, FILE_NAME,
            stringResource(R.string.preview_diary_markdown), 0, 0, 1, null, false,
            directory = VAULT_URI, displayPath = PATH, updatedAt = TIMESTAMP, vaultId = VAULT_ID)
        return RecoveryEntry("preview-draft", draft)
    }
}
