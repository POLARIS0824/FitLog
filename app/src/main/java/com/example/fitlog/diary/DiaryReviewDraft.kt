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
) {
    fun withWeight(address: DiarySetAddress, value: Double?) = update(address) { it.copy(weight = value) }
    fun withReps(address: DiarySetAddress, value: Int?) = update(address) { it.copy(reps = value) }

    private fun update(address: DiarySetAddress, transform: (ReviewedSet) -> ReviewedSet): DiaryReviewDraft {
        val session = sessions.getOrNull(address.session) ?: return this
        val exercise = session.exercises.getOrNull(address.exercise) ?: return this
        val set = exercise.sets.getOrNull(address.set) ?: return this
        val updated = transform(set)
        val baseline = originalValues.firstOrNull { it.address == address }
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
        fun fromCandidate(candidate: StoredDiaryCandidate, confirmed: ConfirmedDiaryRecord?): DiaryReviewDraft {
            val analysis = candidate.analysis
            return DiaryReviewDraft(candidate.attempt.id, suggestDiaryDate(analysis.parseKey.sourceKey, analysis).suggested?.toString().orEmpty(),
                analysis.sessions.map { session ->
                    ReviewedSession(session.exercises.map { exercise ->
                        ReviewedExercise(exercise.candidate.rawName, exercise.sets.map { set ->
                            ReviewedSet(set.weight.value, set.unit.value, set.basis.value, set.reps.value,
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

        fun fromConfirmed(record: ConfirmedDiaryRecord, original: StoredDiaryCandidate) = DiaryReviewDraft(record.diary.parseRunId, record.diary.date,
            record.sessions.map { session ->
                ReviewedSession(session.exercises.map { exercise ->
                    ReviewedExercise(exercise.exercise.rawName, exercise.sets.map { set ->
                        ReviewedSet(set.weight, set.unit, set.basis, set.reps, set.groupIndex, set.setInGroup, set.userEdited)
                    }, exercise.exercise.notes, exercise.exercise.evidence, exercise.exercise.sourcePath)
                }, session.session.notes, session.session.sourcePath)
            }, record.diary.acceptedPartialResult, record.diary.confirmedAt, record.diary.acceptedPartialResult, fromCandidate = false)
            .withOriginalValues(original)
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
