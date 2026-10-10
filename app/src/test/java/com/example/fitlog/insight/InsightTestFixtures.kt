package com.example.fitlog.insight

import com.example.fitlog.data.analysis.*
import com.example.fitlog.data.index.*
import java.time.LocalDate

internal const val INSIGHT_VAULT = "00000000-0000-4000-8000-000000000001"
internal const val OTHER_INSIGHT_VAULT = "00000000-0000-4000-8000-000000000002"
internal val INSIGHT_TODAY: LocalDate = LocalDate.of(2026, 10, 10)
internal fun insightSet(weight: Double? = 40.0, basis: WeightBasis? = WeightBasis.TOTAL,
    unit: WeightUnit? = WeightUnit.KG, reps: Int? = 8) =
    ConfirmedSetRow("set", "exercise", 0, weight, unit, basis, reps, weight, null, null, false)

internal fun insightRecord(path: String = "2026-10-10.md", date: LocalDate = INSIGHT_TODAY,
    name: String = "Bench", sets: List<ConfirmedSetRow> = listOf(insightSet()), sessionCount: Int = 1,
    vaultId: String = INSIGHT_VAULT): ConfirmedDiaryRecord {
    val row = ConfirmedDiaryRow("diary-$path", vaultId, path, date.toString(), "a".repeat(64), 1, "parse-$path", 1, false)
    return ConfirmedDiaryRecord(row, (0 until sessionCount).map { index ->
        val session = ConfirmedSessionRow("$path-session-$index", row.id, index, null, null)
        val exercise = ConfirmedExerciseRow("$path-exercise-$index", session.id, 0, name, null, null, null)
        ConfirmedSessionRecord(session, listOf(ConfirmedExerciseRecord(exercise, sets.mapIndexed { i, set ->
            set.copy(id = "${exercise.id}-set-$i", exerciseId = exercise.id, position = i)
        })))
    })
}

internal fun insightSource(record: ConfirmedDiaryRecord, status: String = IndexedSource.AVAILABLE) =
    IndexedSource(record.diary.vaultId, "content://notes/${record.diary.relPath}", record.diary.relPath,
        record.diary.relPath, null, false, status, record.diary.contentHash, record.diary.hashVersion)

internal fun insightIndex(vararg records: ConfirmedDiaryRecord) = SourceIndexSnapshot(records.map(::insightSource),
    IndexedScan(INSIGHT_VAULT, IndexedScan.COMPLETE, 1))
