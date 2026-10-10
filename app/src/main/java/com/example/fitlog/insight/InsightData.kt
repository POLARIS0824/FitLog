package com.example.fitlog.insight

import com.example.fitlog.data.analysis.*
import com.example.fitlog.data.index.IndexedSource
import com.example.fitlog.data.index.SourceIndexSnapshot
import com.example.fitlog.data.insight.ExerciseAliases
import com.example.fitlog.data.insight.exerciseNameKey
import java.time.LocalDate

enum class InsightRange(val days: Long?) { THIRTY(30), NINETY(90), YEAR(365), ALL(null);
    fun includes(date: LocalDate, today: LocalDate) = !date.isAfter(today) &&
        (days == null || !date.isBefore(today.minusDays(days - 1)))
}

enum class InsightSourceStatus { CURRENT, UNCONFIRMED, STALE, MISSING, UNVERIFIABLE, FUTURE }

data class InsightSource(val key: SourceKey, val source: IndexedSource?, val status: InsightSourceStatus)
data class InsightDiary(val record: ConfirmedDiaryRecord, val source: IndexedSource, val date: LocalDate) {
    val key get() = SourceKey(record.diary.vaultId, record.diary.relPath)
    val sessions get() = record.sessions.filter { it.exercises.isNotEmpty() }
    val setCount get() = sessions.sumOf { session -> session.exercises.sumOf { it.sets.size } }
}

data class ExerciseOccurrence(val diary: InsightDiary, val sessionId: String, val exercise: ConfirmedExerciseRecord)
data class InsightExercise(val nameKey: String, val name: String, val memberKeys: Set<String>,
    val occurrences: List<ExerciseOccurrence>)
data class InsightTotals(val sessions: Int, val days: Int, val sets: Int, val unknownGroups: Int)
data class InsightPoint(val date: LocalDate, val value: Double, val sourceKeys: List<SourceKey>)
data class InsightSeries(val basis: WeightBasis, val points: List<InsightPoint>) {
    val record: InsightPoint? get() = if (basis == WeightBasis.ASSISTED) points.minByOrNull { it.value }
        else points.maxByOrNull { it.value }
}

data class InsightData(val vaultId: String, val today: LocalDate, val sources: List<InsightSource>,
    val diaries: List<InsightDiary>, val names: Map<String, String>, val aliases: ExerciseAliases) {
    fun diaries(range: InsightRange) = diaries.filter { range.includes(it.date, today) }
    fun totals(range: InsightRange): InsightTotals {
        val records = diaries(range)
        val sessions = records.flatMap { it.sessions }
        val exercises = sessions.flatMap { it.exercises }
        return InsightTotals(sessions.size, records.filter { it.sessions.isNotEmpty() }.map { it.date }.distinct().size,
            exercises.sumOf { it.sets.size }, exercises.count { it.sets.isEmpty() })
    }

    fun exercises(range: InsightRange): List<InsightExercise> {
        val occurrences = diaries(range).flatMap { diary -> diary.sessions.flatMap { session ->
            session.exercises.map { ExerciseOccurrence(diary, session.session.id, it) }
        } }
        return occurrences.groupBy { item ->
            val key = exerciseNameKey(item.exercise.exercise.rawName)
            aliases.groupFor(key)?.primaryKey ?: key
        }.map { (key, entries) ->
            val group = aliases.groupFor(key)
            InsightExercise(key, group?.primaryName ?: names.getValue(key), group?.members?.toSet() ?: setOf(key),
                entries.sortedWith(compareByDescending<ExerciseOccurrence> { it.diary.date }
                    .thenBy { it.diary.key.relPath }.thenBy { it.exercise.exercise.position }))
        }.sortedWith(compareByDescending<InsightExercise> { it.occurrences.first().diary.date }.thenBy { it.name })
    }

    fun exercise(nameKey: String, range: InsightRange): InsightExercise? {
        val canonical = aliases.groupFor(nameKey)?.primaryKey ?: nameKey
        return exercises(range).firstOrNull { it.nameKey == canonical }
    }

    fun activity(): Map<LocalDate, List<InsightDiary>> = diaries.filter {
        !it.date.isBefore(today.minusDays(364)) && it.sessions.isNotEmpty()
    }.groupBy { it.date }
}

/** Uses the last indexed semantic version, not SAF metadata or a raw-byte fingerprint. */
fun buildInsightData(vaultId: String, index: SourceIndexSnapshot, confirmed: List<ConfirmedDiaryRecord>,
    aliases: ExerciseAliases, today: LocalDate): InsightData {
    val indexed = index.sources.filter { it.vaultId == vaultId }.groupBy { it.path }
    val records = confirmed.filter { it.diary.vaultId == vaultId }.groupBy { it.diary.relPath }
        .mapValues { (_, records) -> records.maxBy { it.diary.confirmedAt } }
    val diaries = mutableListOf<InsightDiary>()
    val sources = (indexed.keys + records.keys).sorted().map { path ->
        val candidates = indexed[path].orEmpty()
        val available = candidates.filter { it.status == IndexedSource.AVAILABLE }
        val source = available.singleOrNull() ?: candidates.singleOrNull()
        val record = records[path]
        val date = record?.diary?.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val status = when {
            source == null && candidates.isEmpty() -> InsightSourceStatus.MISSING
            source == null || available.size > 1 -> InsightSourceStatus.UNVERIFIABLE
            source.status == IndexedSource.MISSING -> InsightSourceStatus.MISSING
            source.status != IndexedSource.AVAILABLE -> InsightSourceStatus.UNVERIFIABLE
            record == null -> InsightSourceStatus.UNCONFIRMED
            source.contentHash == null || source.hashVersion == null -> InsightSourceStatus.UNVERIFIABLE
            confirmationFreshness(record.diary, DiaryContentVersion(source.contentHash, source.hashVersion)) ==
                ConfirmationFreshness.UNVERIFIABLE -> InsightSourceStatus.UNVERIFIABLE
            source.contentHash != record.diary.contentHash -> InsightSourceStatus.STALE
            date == null -> InsightSourceStatus.UNVERIFIABLE
            date.isAfter(today) -> InsightSourceStatus.FUTURE
            else -> InsightSourceStatus.CURRENT
        }
        if (status == InsightSourceStatus.CURRENT) diaries += InsightDiary(requireNotNull(record), requireNotNull(source), requireNotNull(date))
        InsightSource(SourceKey(vaultId, path), source, status)
    }
    val names = linkedMapOf<String, String>()
    records.toSortedMap().values.flatMap { it.sessions }.flatMap { it.exercises }.forEach { exercise ->
        val name = exercise.exercise.rawName.trim()
        if (name.isNotEmpty()) names.putIfAbsent(exerciseNameKey(name), name)
    }
    aliases.groups.forEach { group -> group.members.forEach { names.putIfAbsent(it, it) }
        names[group.primaryKey] = group.primaryName }
    return InsightData(vaultId, today, sources, diaries.sortedWith(compareByDescending<InsightDiary> { it.date }.thenBy { it.key.relPath }), names, aliases)
}

/** One point per local date and weight basis; its provenance lists only the sets attaining that value. */
fun exerciseSeries(exercise: InsightExercise): List<InsightSeries> = WeightBasis.entries.filter { it != WeightBasis.UNKNOWN }.mapNotNull { basis ->
    val values = exercise.occurrences.flatMap { occurrence -> occurrence.exercise.sets.mapNotNull { set ->
        if (set.basis != basis) return@mapNotNull null
        val value = if (basis == WeightBasis.BODYWEIGHT) set.reps?.takeIf { it > 0 }?.toDouble() else when (set.unit) {
            WeightUnit.KG -> set.weight
            WeightUnit.LB -> set.weight?.times(0.45359237)
            else -> null
        }
        value?.takeIf { it.isFinite() && it >= 0 }?.let { Triple(occurrence.diary.date, it, occurrence.diary.key) }
    } }
    val points = values.groupBy { it.first }.toSortedMap().map { (date, day) ->
        val value = if (basis == WeightBasis.ASSISTED) day.minOf { it.second } else day.maxOf { it.second }
        InsightPoint(date, value, day.filter { it.second == value }.map { it.third }.distinct())
    }
    points.takeIf { it.isNotEmpty() }?.let { InsightSeries(basis, it) }
}
