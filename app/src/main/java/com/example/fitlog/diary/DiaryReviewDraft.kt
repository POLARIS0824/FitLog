package com.example.fitlog.diary

import com.example.fitlog.data.analysis.*
import java.time.LocalDate
import kotlinx.serialization.Serializable

/** SavedStateHandle holds unfinished review values; confirmed data stays in the repository. */
@Serializable
internal data class DiaryReviewDraft(
    val parseRunId: String,
    val date: String,
    val sessions: List<ReviewedSession>,
    val partial: Boolean,
    val expectedConfirmedAt: Long?,
    val acceptedPartial: Boolean = false,
    val fromCandidate: Boolean = true,
    val fragments: List<DiaryReviewFragment> = emptyList(),
    val originalValues: List<DiarySetOriginal> = emptyList(),
    val sourceVersion: DiaryContentVersion? = null,
) {
    fun withWeight(address: DiarySetAddress, value: Double?, unit: WeightUnit?, basis: WeightBasis? = null) =
        update(address) { it.copy(weight = value, unit = unit, basis = basis ?: it.basis) }
    fun withReps(address: DiarySetAddress, value: Int?) = update(address) { it.copy(reps = value) }

    fun withBasis(address: DiarySetAddress, basis: WeightBasis, applyToExercise: Boolean): DiaryReviewDraft {
        val exercise = sessions.getOrNull(address.session)?.exercises?.getOrNull(address.exercise) ?: return this
        if (exercise.sets.getOrNull(address.set) == null) return this
        val addresses = if (applyToExercise) exercise.sets.indices.map { address.copy(set = it) } else listOf(address)
        val updated = addresses.fold(this) { draft, target -> draft.update(target) { set ->
            if (basis == WeightBasis.BODYWEIGHT) set.copy(weight = null, unit = null, basis = basis)
            else set.copy(basis = basis)
        } }
        return if (!applyToExercise) updated else updated.copy(fragments = updated.fragments.map { fragment ->
            if (fragment.session != address.session || fragment.exercise != address.exercise) fragment
            else if (basis == WeightBasis.BODYWEIGHT) fragment.copy(weight = null, unit = null, basis = basis)
            else fragment.copy(basis = basis)
        })
    }

    fun withExercise(sessionIndex: Int, exerciseIndex: Int, name: String, setCount: Int,
        fragmentCounts: Map<Int, Int>): DiaryReviewDraft {
        if (name.isBlank() || setCount !in 0..1000 || fragmentCounts.values.any { it !in 0..1000 }) return this
        val session = sessions.getOrNull(sessionIndex) ?: return this
        val exercise = session.exercises.getOrNull(exerciseIndex) ?: return this
        val expanded = fragments.filter { it.session == sessionIndex && it.exercise == exerciseIndex && it.group in fragmentCounts }
        if (setCount + expanded.sumOf { fragmentCounts.getValue(it.group) } > 1000) return this
        val sets = exercise.sets.take(setCount) + List((setCount - exercise.sets.size).coerceAtLeast(0)) { ReviewedSet(userEdited = true) } +
            expanded.flatMap { fragment -> List(fragmentCounts.getValue(fragment.group)) {
                ReviewedSet(fragment.weight, fragment.unit, fragment.basis, fragment.reps, userEdited = true)
            } }
        if (sets.size > 1000) return this
        val remaining = fragments - expanded.toSet()
        return copy(sessions = sessions.toMutableList().also { all ->
            all[sessionIndex] = session.copy(exercises = session.exercises.toMutableList().also {
                it[exerciseIndex] = exercise.copy(rawName = name, sets = sets)
            })
        }, fragments = remaining)
    }

    fun addExercise(sessionIndex: Int, name: String, setCount: Int): DiaryReviewDraft {
        if (sessionIndex !in 0..sessions.size || name.isBlank() || setCount !in 0..1000) return this
        val exercise = ReviewedExercise(name, List(setCount) { ReviewedSet(userEdited = true) })
        return copy(sessions = sessions.toMutableList().also { all ->
            if (sessionIndex == all.size) all.add(ReviewedSession(listOf(exercise)))
            else all[sessionIndex] = all[sessionIndex].copy(exercises = all[sessionIndex].exercises + exercise)
        })
    }

    fun removeExercise(sessionIndex: Int, exerciseIndex: Int): DiaryReviewDraft {
        val session = sessions.getOrNull(sessionIndex) ?: return this
        if (exerciseIndex !in session.exercises.indices) return this
        if (session.exercises.size == 1) return removeSession(sessionIndex)
        return copy(sessions = sessions.mapIndexed { index, value ->
            if (index != sessionIndex) value else value.copy(exercises = value.exercises.filterIndexed { exercise, _ -> exercise != exerciseIndex })
        }, fragments = fragments.mapNotNull { fragment ->
            when {
                fragment.session != sessionIndex || fragment.exercise < exerciseIndex -> fragment
                fragment.exercise == exerciseIndex -> null
                else -> fragment.copy(exercise = fragment.exercise - 1)
            }
        }, originalValues = originalValues.mapNotNull { original ->
            val address = original.address
            when {
                address.session != sessionIndex || address.exercise < exerciseIndex -> original
                address.exercise == exerciseIndex -> null
                else -> original.copy(address = address.copy(exercise = address.exercise - 1))
            }
        })
    }

    fun removeSession(sessionIndex: Int): DiaryReviewDraft {
        if (sessionIndex !in sessions.indices) return this
        return copy(sessions = sessions.filterIndexed { index, _ -> index != sessionIndex },
            fragments = fragments.mapNotNull { fragment ->
                when {
                    fragment.session < sessionIndex -> fragment
                    fragment.session == sessionIndex -> null
                    else -> fragment.copy(session = fragment.session - 1)
                }
            }, originalValues = originalValues.mapNotNull { original ->
                val address = original.address
                when {
                    address.session < sessionIndex -> original
                    address.session == sessionIndex -> null
                    else -> original.copy(address = address.copy(session = address.session - 1))
                }
            })
    }

    fun clearTraining() = copy(sessions = emptyList(), fragments = emptyList(), originalValues = emptyList())

    private fun update(address: DiarySetAddress, transform: (ReviewedSet) -> ReviewedSet): DiaryReviewDraft {
        val session = sessions.getOrNull(address.session) ?: return this
        val exercise = session.exercises.getOrNull(address.exercise) ?: return this
        val set = exercise.sets.getOrNull(address.set) ?: return this
        val updated = transform(set)
        val baseline = if (set.groupIndex != null && set.setInGroup != null)
            originalValues.firstOrNull { it.address == address } else null
        return copy(sessions = sessions.toMutableList().also { all ->
            all[address.session] = session.copy(exercises = session.exercises.toMutableList().also { exercises ->
                exercises[address.exercise] = exercise.copy(sets = exercise.sets.toMutableList().also { sets ->
                    sets[address.set] = updated.copy(userEdited = baseline == null || baseline.differsFrom(updated))
                })
            })
        })
    }

    /** Compare against this review's parse, even after a newer candidate is produced. */
    fun withOriginalValues(candidate: StoredDiaryCandidate): DiaryReviewDraft {
        require(candidate.attempt.id == parseRunId)
        val exercises = candidate.analysis.sessions.flatMap { it.exercises }.associateBy { it.path }
        val values = sessions.flatMapIndexed { sessionIndex, session ->
            session.exercises.flatMapIndexed { exerciseIndex, exercise ->
                val original = exercises[exercise.sourcePath]
                exercise.sets.mapIndexedNotNull { setIndex, set ->
                    original?.sets?.find { it.groupIndex == set.groupIndex && it.setInGroup == set.setInGroup }?.let {
                        DiarySetOriginal(DiarySetAddress(sessionIndex, exerciseIndex, setIndex),
                            it.weight.value, it.unit.value, it.basis.value, it.reps.value)
                    }
                }
            }
        }
        val originals = values.associateBy { it.address }
        return copy(originalValues = values, sessions = sessions.mapIndexed { sessionIndex, session ->
            session.copy(exercises = session.exercises.mapIndexed { exerciseIndex, exercise ->
                exercise.copy(sets = exercise.sets.mapIndexed { setIndex, set ->
                    val baseline = originals[DiarySetAddress(sessionIndex, exerciseIndex, setIndex)]
                    set.copy(userEdited = baseline == null || baseline.differsFrom(set))
                })
            })
        })
    }

    companion object {
        fun fromCandidate(candidate: StoredDiaryCandidate, confirmed: TrainingRecordDetails?): DiaryReviewDraft {
            val analysis = candidate.analysis
            return DiaryReviewDraft(candidate.attempt.id, suggestDiaryDate(analysis.parseKey.sourceKey, analysis).suggested?.toString().orEmpty(),
                analysis.sessions.map { session ->
                    ReviewedSession(session.exercises.map { exercise ->
                        ReviewedExercise(exercise.candidate.rawName, exercise.sets.map { set ->
                            ReviewedSet(set.weight.value,
                                if (set.basis.value == WeightBasis.BODYWEIGHT && set.weight.value == null) null
                                else reviewWeightUnit(set.weight.value, set.unit.value), set.basis.value, set.reps.value,
                                set.groupIndex, set.setInGroup)
                        }, exercise.candidate.notes, exercise.evidence.takeIf {
                            it.segmentId == DiaryParseInput.SEGMENT_ID && it.quote.isNotBlank()
                        }, exercise.path)
                    }, session.notes, session.path)
                }, analysis.hasErrors || analysis.sessions.any { session -> session.exercises.any { exercise ->
                    exercise.candidate.groups.any { it.count == null && it.repsList == null }
                } }, confirmed?.diary?.confirmedAt, fragments = analysis.sessions.flatMapIndexed { sessionIndex, session ->
                    session.exercises.flatMapIndexed { exerciseIndex, exercise ->
                        exercise.candidate.groups.mapIndexedNotNull { groupIndex, group ->
                            if (group.count != null || group.repsList != null || analysis.issues.any {
                                it.severity == IssueSeverity.ERROR && it.path.startsWith("${exercise.path}.groups[$groupIndex]")
                            }) null else DiaryReviewFragment(sessionIndex, exerciseIndex, groupIndex, group.weight, group.unit, group.basis, group.reps)
                        }
                    }
                }).withOriginalValues(candidate)
        }

        fun fromConfirmed(record: TrainingRecordDetails, original: StoredDiaryCandidate? = null) = DiaryReviewDraft(record.diary.parseRunId.orEmpty(), record.diary.date,
            record.sessions.map { session ->
                ReviewedSession(session.exercises.map { exercise ->
                    ReviewedExercise(exercise.exercise.rawName, exercise.sets.map { set ->
                        ReviewedSet(set.weight, set.unit, set.basis, set.reps, set.groupIndex, set.setInGroup, set.userEdited)
                    }, exercise.exercise.notes, exercise.exercise.evidence, exercise.exercise.sourcePath)
                }, session.session.notes, session.session.sourcePath)
            }, record.diary.acceptedPartialResult, record.diary.confirmedAt, record.diary.acceptedPartialResult, fromCandidate = false,
                sourceVersion = DiaryContentVersion(requireNotNull(record.diary.contentHash), requireNotNull(record.diary.hashVersion)))
            .let { draft -> if (original == null) draft else draft.withOriginalValues(original) }
    }
}

@Serializable
internal data class DiarySetAddress(val session: Int, val exercise: Int, val set: Int)

@Serializable
internal data class DiarySetOriginal(val address: DiarySetAddress, val weight: Double?, val unit: WeightUnit?,
    val basis: WeightBasis?, val reps: Int?) {
    fun differsFrom(set: ReviewedSet) = weight != set.weight || unit != set.unit || basis != set.basis || reps != set.reps
}

@Serializable
internal data class DiaryReviewFragment(val session: Int, val exercise: Int, val group: Int,
    val weight: Double?, val unit: WeightUnit?, val basis: WeightBasis?, val reps: Int?)

internal fun reviewDate(value: String): LocalDate? = try {
    LocalDate.parse(value).takeIf { it.year in 1..9999 }
} catch (_: java.time.format.DateTimeParseException) { null }
