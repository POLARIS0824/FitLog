package com.example.fitlog.insight

import com.example.fitlog.data.analysis.*
import com.example.fitlog.data.index.*
import com.example.fitlog.data.insight.*
import org.junit.Assert.*
import org.junit.Test

class InsightDataTest {
    private fun data(records: List<ConfirmedDiaryRecord>, aliases: ExerciseAliases = ExerciseAliases(),
        index: SourceIndexSnapshot = insightIndex(*records.toTypedArray())) =
        buildInsightData(INSIGHT_VAULT, index, records, aliases, INSIGHT_TODAY)

    @Test fun sameDayDiariesAndSessionsCountOnceAndEmptyConfirmationsAreNotRestDays() {
        val a = insightRecord("a.md", sets = listOf(insightSet(), insightSet()), sessionCount = 2)
        val b = insightRecord("b.md", name = "Abs", sets = emptyList())
        val empty = insightRecord("empty.md", sessionCount = 0)
        val result = data(listOf(a, b, empty))
        assertEquals(InsightTotals(3, 1, 4, 1), result.totals(InsightRange.ALL))
        assertEquals(2, result.activity().getValue(INSIGHT_TODAY).size)
        assertEquals(3, result.sources.count { it.status == InsightSourceStatus.CURRENT })
        assertTrue(exerciseSeries(result.exercise("abs", InsightRange.ALL)!!).isEmpty())
    }

    @Test fun reConfirmationReplacesOldSetsInsteadOfAccumulatingAndKeepsProvenance() {
        val original = insightRecord()
        val updated = original.copy(diary = original.diary.copy(confirmedAt = 2), sessions = insightRecord(
            sets = listOf(insightSet(60.0), insightSet(60.0))).sessions)
        val result = data(listOf(original, updated), index = insightIndex(updated))
        assertEquals(2, result.totals(InsightRange.ALL).sets)
        val point = exerciseSeries(result.exercise("bench", InsightRange.ALL)!!).single().points.single()
        assertEquals(60.0, point.value, 0.0)
        assertEquals(listOf(SourceKey(INSIGHT_VAULT, original.diary.relPath)), point.sourceKeys)
    }

    @Test fun staleMissingUnverifiedUnconfirmedAndFutureSourcesStayVisibleButDoNotCount() {
        val valid = insightRecord("valid.md")
        val stale = insightRecord("stale.md")
        val unknown = insightRecord("unknown.md")
        val missing = insightRecord("missing.md")
        val orphan = insightRecord("orphan.md")
        val future = insightRecord("future.md", date = INSIGHT_TODAY.plusDays(1))
        val sources = listOf(insightSource(valid), insightSource(stale).copy(contentHash = "b".repeat(64)),
            insightSource(unknown).copy(hashVersion = 2), insightSource(missing, IndexedSource.MISSING),
            insightSource(future), insightSource(insightRecord("unconfirmed.md")))
        val result = data(listOf(valid, stale, unknown, missing, orphan, future),
            index = insightIndex().copy(sources = sources))
        assertEquals(1, result.totals(InsightRange.ALL).sessions)
        assertEquals(mapOf("valid.md" to InsightSourceStatus.CURRENT, "stale.md" to InsightSourceStatus.STALE,
            "unknown.md" to InsightSourceStatus.UNVERIFIABLE, "missing.md" to InsightSourceStatus.MISSING,
            "orphan.md" to InsightSourceStatus.MISSING, "future.md" to InsightSourceStatus.FUTURE,
            "unconfirmed.md" to InsightSourceStatus.UNCONFIRMED), result.sources.associate { it.key.relPath to it.status })
    }

    @Test fun sourceChangeThenReConfirmationRestoresTheLatestConfirmedVersion() {
        val original = insightRecord()
        val changedIndex = insightIndex(original).copy(sources = listOf(insightSource(original).copy(contentHash = "b".repeat(64))))
        assertEquals(0, data(listOf(original), index = changedIndex).totals(InsightRange.ALL).sessions)
        val confirmed = original.copy(diary = original.diary.copy(contentHash = "b".repeat(64), confirmedAt = 2))
        assertEquals(1, data(listOf(confirmed), index = changedIndex).totals(InsightRange.ALL).sessions)
    }

    @Test fun rangesUseInclusiveLocalDatesAndHeatmapHasItsOwn365DayWindow() {
        val records = listOf(0L, 29L, 30L, 89L, 90L, 364L, 365L).map { days ->
            insightRecord("$days.md", date = INSIGHT_TODAY.minusDays(days))
        }
        val result = data(records)
        assertEquals(2, result.totals(InsightRange.THIRTY).sessions)
        assertEquals(4, result.totals(InsightRange.NINETY).sessions)
        assertEquals(6, result.totals(InsightRange.YEAR).sessions)
        assertEquals(7, result.totals(InsightRange.ALL).sessions)
        assertEquals(6, result.activity().size)
    }

    @Test fun basisAndUnitRulesDoNotDoubleWeightsInventBodyMassOrUseStoredKgBlindly() {
        val sets = listOf(insightSet(20.0), insightSet(44.0924524369755, unit = WeightUnit.LB),
            insightSet(20.0, WeightBasis.PER_SIDE), insightSet(5.0, WeightBasis.ADDED),
            insightSet(30.0, WeightBasis.ASSISTED), insightSet(15.0, WeightBasis.ASSISTED),
            insightSet(null, WeightBasis.BODYWEIGHT, null, 12), insightSet(null, WeightBasis.BODYWEIGHT, null, 9),
            insightSet(999.0, WeightBasis.UNKNOWN), insightSet(50.0, unit = WeightUnit.UNKNOWN))
        val result = data(listOf(insightRecord(sets = sets.map { it.copy(weightKg = 9999.0) })))
        val series = exerciseSeries(result.exercise("bench", InsightRange.ALL)!!).associateBy { it.basis }
        assertEquals(5, series.size)
        assertEquals(20.0, series.getValue(WeightBasis.TOTAL).points.single().value, 0.00001)
        assertEquals(20.0, series.getValue(WeightBasis.PER_SIDE).points.single().value, 0.0)
        assertEquals(5.0, series.getValue(WeightBasis.ADDED).points.single().value, 0.0)
        assertEquals(15.0, series.getValue(WeightBasis.ASSISTED).record!!.value, 0.0)
        assertEquals(12.0, series.getValue(WeightBasis.BODYWEIGHT).record!!.value, 0.0)
        assertEquals(10, result.totals(InsightRange.ALL).sets)
    }

    @Test fun assistanceRecordsUseTheLowestDayAndOrdinaryRecordsTheHighestWithExactSources() {
        val a = insightRecord("a.md", sets = listOf(insightSet(40.0), insightSet(30.0, WeightBasis.ASSISTED)))
        val b = insightRecord("b.md", sets = listOf(insightSet(50.0), insightSet(20.0, WeightBasis.ASSISTED)))
        val c = insightRecord("c.md", date = INSIGHT_TODAY.minusDays(1), sets = listOf(insightSet(60.0), insightSet(10.0, WeightBasis.ASSISTED)))
        val series = exerciseSeries(data(listOf(a, b, c)).exercise("bench", InsightRange.ALL)!!).associateBy { it.basis }
        assertEquals(60.0, series.getValue(WeightBasis.TOTAL).record!!.value, 0.0)
        assertEquals(10.0, series.getValue(WeightBasis.ASSISTED).record!!.value, 0.0)
        assertEquals(listOf(SourceKey(INSIGHT_VAULT, "b.md")), series.getValue(WeightBasis.TOTAL).points.last().sourceKeys)
    }

    @Test fun missingFieldsAffectOnlyTheirMetricsAndNeverInventSetCountsOrWeights() {
        val recorded = insightRecord("recorded.md", sets = listOf(insightSet(null), insightSet(40.0, reps = null),
            insightSet(null, WeightBasis.BODYWEIGHT, null, null)))
        val named = insightRecord("named.md", name = "Abs", sets = emptyList())
        val result = data(listOf(recorded, named))
        assertEquals(InsightTotals(2, 1, 3, 1), result.totals(InsightRange.ALL))
        val curves = exerciseSeries(result.exercise("bench", InsightRange.ALL)!!)
        assertEquals(1, curves.size)
        assertEquals(40.0, curves.single().points.single().value, 0.0)
        assertEquals(3, result.exercise("bench", InsightRange.ALL)!!.occurrences.single().exercise.sets.size)
    }

    @Test fun originalNameRoutesFollowTheLatestAliasAfterPrimaryChangesAndMemberRemoval() {
        val records = listOf(insightRecord("a.md"), insightRecord("b.md", name = "卧推"))
        val original = ExerciseAliases(groups = listOf(ExerciseAliasGroup("Bench", listOf("bench", "卧推"))))
        val changed = original.replacing("bench", ExerciseAliasGroup("卧推", listOf("bench", "卧推")))
        assertEquals("卧推", data(records, changed).exercise("bench", InsightRange.ALL)!!.name)
        assertEquals(2, data(records, changed).exercise("bench", InsightRange.ALL)!!.occurrences.size)
        val removed = changed.replacing("卧推", ExerciseAliasGroup("卧推", listOf("卧推")))
        assertEquals("Bench", data(records, removed).exercise("bench", InsightRange.ALL)!!.name)
        assertEquals(1, data(records, removed).exercise("bench", InsightRange.ALL)!!.occurrences.size)
        assertEquals(1, data(records, removed).exercise("卧推", InsightRange.ALL)!!.occurrences.size)
    }

    @Test fun aliasesMergeAndSplitStatisticsWithoutChangingAnyRawRecords() {
        val records = listOf(insightRecord("a.md", name = " Bench "), insightRecord("b.md", name = "BENCH"),
            insightRecord("c.md", name = "卧推"))
        assertEquals(2, data(records).exercises(InsightRange.ALL).size)
        val aliases = ExerciseAliases(groups = listOf(ExerciseAliasGroup("Bench", listOf("bench", "卧推"))))
        val merged = data(records, aliases)
        assertEquals(3, merged.exercise("卧推", InsightRange.ALL)!!.occurrences.size)
        assertEquals(3, merged.totals(InsightRange.ALL).sets)
        assertEquals(2, data(records, aliases.replacing("bench", null)).exercises(InsightRange.ALL).size)
        assertEquals(listOf(" Bench ", "BENCH", "卧推"), records.flatMap { it.sessions }.flatMap { it.exercises }.map { it.exercise.rawName })
    }

    @Test fun partialScanDoesNotInventMissingSourcesAndOtherVaultsNeverCount() {
        val a = insightRecord()
        val other = insightRecord("other.md", vaultId = OTHER_INSIGHT_VAULT)
        val index = insightIndex(a, other).copy(scan = IndexedScan(INSIGHT_VAULT, IndexedScan.PARTIAL, 1))
        val result = data(listOf(a, other), index = index)
        assertEquals(1, result.totals(InsightRange.ALL).sessions)
        assertEquals(1, result.sources.size)
    }

    @Test fun unreadableAndAmbiguousLocatorsCannotBeCounted() {
        val a = insightRecord()
        val duplicate = insightSource(a).copy(uri = "content://other")
        assertEquals(InsightSourceStatus.UNVERIFIABLE, data(listOf(a), index = insightIndex(a).copy(
            sources = listOf(insightSource(a), duplicate))).sources.single().status)
        assertEquals(InsightSourceStatus.UNVERIFIABLE, data(listOf(a), index = insightIndex(a).copy(
            sources = listOf(insightSource(a, IndexedSource.READ_FAILED)))).sources.single().status)
    }
}
