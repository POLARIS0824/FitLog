package com.example.fitlog.records

import androidx.compose.runtime.*
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitlog.R
import com.example.fitlog.data.analysis.*
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class NativeRecordInput(val date: String, val exercises: List<ReviewedExercise> = emptyList(),
    val notes: String = "", val kind: RecordKind = RecordKind.TRAINING) {
    val hasContent get() = exercises.isNotEmpty() || notes.isNotBlank() || kind == RecordKind.REST
    fun sessions() = listOf(ReviewedSession(exercises, notes.takeIf { it.isNotBlank() }))
}

internal enum class RecordSaveState { EMPTY, SAVING, SAVED, FAILED }

/** One active form and one sequential writer. No draft store, timers or automatic retries. */
internal class NativeRecordViewModel(recordId: String?, private val repository: TrainingRecordRepository,
    private val savedState: SavedStateHandle) : ViewModel() {
    val id: String = recordId ?: savedState.get<String>("recordId") ?: UUID.randomUUID().toString()
    var input by mutableStateOf(NativeRecordInput(LocalDate.now().toString())); private set
    var loading by mutableStateOf(true); private set
    var state by mutableStateOf(RecordSaveState.EMPTY); private set
    var message by mutableStateOf<Int?>(null); private set
    var leaveRequested by mutableStateOf(false); private set
    var deleting by mutableStateOf(false); private set
    private var persisted: NativeRecordInput? = null
    private var writer: Job? = null
    private var pendingLeave: (() -> Unit)? = null
    private var readFailed = false
    val exists get() = persisted != null
    val hasChanges get() = input != persisted && (persisted != null || input.hasContent)
    val canEdit get() = !loading && !deleting && !readFailed

    init {
        savedState["recordId"] = id
        viewModelScope.launch {
            try {
                val record = if (recordId != null || savedState.contains("input")) repository.read(id) else null
                require(recordId == null || record != null)
                require(record == null || record.diary.origin == RecordOrigin.NATIVE)
                persisted = record?.let {
                    val session = it.reviewedSessions().singleOrNull() ?: ReviewedSession()
                    NativeRecordInput(it.diary.date, session.exercises, session.notes.orEmpty(), it.diary.kind)
                }
                input = savedState.get<String>("input")?.let { Json.decodeFromString<NativeRecordInput>(it) }
                    ?: persisted ?: input
                state = if (hasChanges) RecordSaveState.FAILED else if (persisted != null) RecordSaveState.SAVED else RecordSaveState.EMPTY
                if (hasChanges) message = R.string.record_save_failed
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                readFailed = true; state = RecordSaveState.FAILED; message = R.string.record_read_failed
            } finally { loading = false }
        }
    }

    private fun change(next: NativeRecordInput) {
        if (!canEdit || next == input) return
        input = next
        savedState["input"] = Json.encodeToString(next)
        if (state != RecordSaveState.FAILED) saveLatest()
    }
    fun date(value: LocalDate) = change(input.copy(date = value.toString()))
    fun notes(value: String) = change(input.copy(notes = value))
    fun addExercise() = change(input.copy(kind = RecordKind.TRAINING,
        exercises = input.exercises + ReviewedExercise("", listOf(ReviewedSet(unit = WeightUnit.KG, basis = WeightBasis.TOTAL)))))
    fun removeExercise(index: Int) = change(input.copy(exercises = input.exercises.filterIndexed { i, _ -> i != index }))
    fun name(index: Int, value: String) = exercise(index) { it.copy(rawName = value) }
    fun exerciseNotes(index: Int, value: String) = exercise(index) { it.copy(notes = value.takeIf(String::isNotBlank)) }
    fun addSet(index: Int) = exercise(index) {
        if (it.sets.size >= 1000) it else {
            val previous = it.sets.lastOrNull()
            it.copy(sets = it.sets + ReviewedSet(
                unit = if (previous == null) WeightUnit.KG else previous.unit,
                basis = previous?.basis ?: WeightBasis.TOTAL))
        }
    }
    fun copySet(exerciseIndex: Int, index: Int) = exercise(exerciseIndex) { item ->
        if (item.sets.size >= 1000) item else item.copy(sets = item.sets + item.sets[index].copy(userEdited = true))
    }
    fun removeSet(exerciseIndex: Int, set: Int) = exercise(exerciseIndex) { it.copy(sets = it.sets.filterIndexed { i, _ -> i != set }) }
    fun set(exerciseIndex: Int, index: Int, transform: (ReviewedSet) -> ReviewedSet) = exercise(exerciseIndex) { item ->
        item.copy(sets = item.sets.mapIndexed { i, value -> if (i == index) transform(value) else value })
    }
    private fun exercise(index: Int, transform: (ReviewedExercise) -> ReviewedExercise) =
        change(input.copy(exercises = input.exercises.mapIndexed { i, value -> if (i == index) transform(value) else value }))
    fun rest() { if (input.exercises.isEmpty()) change(input.copy(kind = RecordKind.REST)) }

    private fun saveLatest() {
        if (!canEdit || !hasChanges || writer?.isActive == true) return
        state = RecordSaveState.SAVING; message = null
        writer = viewModelScope.launch {
            try {
                while (hasChanges) {
                    val snapshot = input
                    withContext(NonCancellable) {
                        repository.saveNative(id, LocalDate.parse(snapshot.date), snapshot.sessions(), snapshot.kind)
                    }
                    persisted = snapshot
                }
                state = RecordSaveState.SAVED
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                state = RecordSaveState.FAILED; message = R.string.record_save_failed
            }
        }
    }
    fun retry() { if (!readFailed) { state = RecordSaveState.EMPTY; saveLatest() } }
    fun requestLeave(action: () -> Unit) {
        if (deleting) return
        if (!hasChanges) { action(); return }
        pendingLeave = action
        if (state == RecordSaveState.FAILED) { leaveRequested = true; return }
        saveLatest()
        viewModelScope.launch {
            writer?.join()
            if (hasChanges) leaveRequested = true else { pendingLeave = null; action() }
        }
    }
    fun cancelLeave() { pendingLeave = null; leaveRequested = false }
    fun retryAndLeave() {
        val action = pendingLeave ?: return
        leaveRequested = false
        retry()
        requestLeave(action)
    }
    fun discardAndLeave() {
        val action = pendingLeave
        savedState.remove<String>("input")
        cancelLeave(); action?.invoke()
    }
    fun delete(onDeleted: () -> Unit) {
        if (!canEdit) return
        deleting = true
        viewModelScope.launch {
            try {
                writer?.join()
                withContext(NonCancellable) { repository.delete(id) }
                savedState.remove<String>("input")
                onDeleted()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                message = R.string.record_delete_failed
            } finally { deleting = false }
        }
    }
}
