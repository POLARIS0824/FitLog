package com.example.fitlog.records

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

@Serializable
internal data class NativeRecordInput(val date: String, val exercises: List<ReviewedExercise> = emptyList(),
    val notes: String = "", val kind: RecordKind = RecordKind.TRAINING) {
    val hasContent get() = exercises.isNotEmpty() || notes.isNotBlank() || kind == RecordKind.REST
    fun sessions() = listOf(ReviewedSession(exercises, notes.takeIf { it.isNotBlank() }))
}

internal enum class RecordSaveState { EMPTY, SAVING, SAVED, FAILED }

/** One active form and one sequential writer. No draft store, timers or automatic retries. */
internal class NativeRecordViewModel(private val recordId: String?, private val repository: TrainingRecords,
    private val saveRecord: com.example.fitlog.domain.SaveNativeRecord,
    private val savedState: SavedStateHandle) : ViewModel() {
    val id: String = recordId ?: savedState.get<String>("recordId") ?: UUID.randomUUID().toString()
    private val _uiState = MutableStateFlow(
        NativeRecordUiState()
    )
    val uiState: StateFlow<NativeRecordUiState> = _uiState.asStateFlow()

    private inline fun updateState(transform: (NativeRecordUiState) -> NativeRecordUiState) {
        _uiState.update { transform(it) }
    }

    private val input get() = _uiState.value.input
    private val loading get() = _uiState.value.loading
    private val state get() = _uiState.value.state
    private val message get() = _uiState.value.message
    private val leaveRequested get() = _uiState.value.leaveRequested
    private val deleting get() = _uiState.value.deleting
    private val persisted get() = _uiState.value.persisted
    private var undoRemovalAction: (() -> Unit)? = null
    private var writer: Job? = null
    private var loadJob: Job? = null
    private var leaveJob: Job? = null
    private val readFailed get() = _uiState.value.readFailed
    private val hasChanges get() = _uiState.value.hasChanges
    private val canEdit get() = _uiState.value.canEdit

    init {
        savedState["recordId"] = id
        loadRecord()
    }

    private fun loadRecord() {
        if (loadJob?.isActive == true) return
        updateState { it.copy(loading = true, readFailed = false, message = null) }
        loadJob = viewModelScope.launch {
            try {
                val record = repository.read(id)
                require(recordId == null || record != null)
                require(record == null || record.diary.origin == RecordOrigin.NATIVE)
                val loaded = record?.let {
                    val session = it.reviewedSessions().singleOrNull() ?: ReviewedSession()
                    NativeRecordInput(it.diary.date, session.exercises, session.notes.orEmpty(), it.diary.kind)
                }
                // The marker is consumed once: a crash notice must not repeat on every later open.
                val interrupted = savedState.get<Boolean>("unsavedInput") == true
                if (interrupted) savedState["unsavedInput"] = false
                updateState { current -> current.copy(
                    persisted = loaded,
                    input = loaded ?: current.input,
                    state = if (loaded != null) RecordSaveState.SAVED else RecordSaveState.EMPTY,
                    message = if (interrupted) R.string.record_restore_interrupted else null,
                ) }

            } catch (e: Exception) {
                if (e is CancellationException) throw e
                updateState { current -> current.copy(readFailed = true, state = RecordSaveState.FAILED, message = R.string.record_read_failed) }
            } finally { updateState { current -> current.copy(loading = false) } }
        }
    }

    private fun change(next: NativeRecordInput) {
        if (!canEdit || next == input) return
        updateState { current -> current.copy(input = next) }
        savedState["unsavedInput"] = true
        if (state != RecordSaveState.FAILED) saveLatest()
    }
    fun date(value: LocalDate) = change(input.copy(date = value.toString()))
    fun notes(value: String) = change(input.copy(notes = value))
    fun addExercise() = change(input.copy(kind = RecordKind.TRAINING,
        exercises = input.exercises + ReviewedExercise("", listOf(ReviewedSet(unit = WeightUnit.KG, basis = WeightBasis.TOTAL)))))
    fun removeExercise(index: Int) {
        if (!canEdit) return
        val removed = input.exercises.getOrNull(index) ?: return
        change(input.copy(exercises = input.exercises.filterIndexed { i, _ -> i != index }))
        undoRemovalAction = {
            change(input.copy(kind = RecordKind.TRAINING, exercises = input.exercises.toMutableList().apply {
                add(index.coerceAtMost(size), removed)
            }))
        }
        updateState { it.copy(canUndoRemoval = true) }
    }
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
    fun removeSet(exerciseIndex: Int, set: Int) {
        if (!canEdit) return
        val removed = input.exercises.getOrNull(exerciseIndex)?.sets?.getOrNull(set) ?: return
        exercise(exerciseIndex) { it.copy(sets = it.sets.filterIndexed { i, _ -> i != set }) }
        undoRemovalAction = {
            exercise(exerciseIndex) { item -> item.copy(sets = item.sets.toMutableList().apply {
                add(set.coerceAtMost(size), removed)
            }) }
        }
        updateState { it.copy(canUndoRemoval = true) }
    }
    fun undoRemoval() {
        if (!canEdit) return
        val action = undoRemovalAction ?: return
        dismissUndo(); action()
    }
    fun dismissUndo() { undoRemovalAction = null; updateState { it.copy(canUndoRemoval = false) } }
    fun set(exerciseIndex: Int, index: Int, transform: (ReviewedSet) -> ReviewedSet) = exercise(exerciseIndex) { item ->
        item.copy(sets = item.sets.mapIndexed { i, value -> if (i == index) transform(value) else value })
    }
    private fun exercise(index: Int, transform: (ReviewedExercise) -> ReviewedExercise) =
        change(input.copy(exercises = input.exercises.mapIndexed { i, value -> if (i == index) transform(value) else value }))
    fun rest() { if (canEdit && input.exercises.isEmpty()) { dismissUndo(); change(input.copy(kind = RecordKind.REST)) } }

    private fun saveLatest() {
        if (!canEdit || !hasChanges || writer?.isActive == true) return
        updateState { current -> current.copy(state = RecordSaveState.SAVING, message = null) }
        writer = viewModelScope.launch {
            try {
                while (hasChanges) {
                    val snapshot = input
                    withContext(NonCancellable) {
                        saveRecord(id, LocalDate.parse(snapshot.date), snapshot.sessions(), snapshot.kind)
                    }
                    updateState { current -> current.copy(persisted = snapshot) }
                    savedState["unsavedInput"] = hasChanges
                }
                updateState { current -> current.copy(state = RecordSaveState.SAVED) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                updateState { current -> current.copy(state = RecordSaveState.FAILED, message = R.string.record_save_failed) }
            }
        }
    }
    fun retry() {
        if (readFailed) loadRecord()
        else { updateState { it.copy(state = RecordSaveState.EMPTY) }; saveLatest() }
    }
    fun requestLeave() {
        if (deleting || leaveJob?.isActive == true) return
        if (!hasChanges) { updateState { it.copy(closeRequested = true) }; return }
        if (state == RecordSaveState.FAILED) { updateState { it.copy(leaveRequested = true) }; return }
        saveLatest()
        leaveJob = viewModelScope.launch {
            writer?.join()
            updateState { it.copy(leaveRequested = hasChanges, closeRequested = !hasChanges) }
        }
    }
    fun closeHandled() { updateState { it.copy(closeRequested = false) } }
    fun cancelLeave() { updateState { it.copy(leaveRequested = false) } }
    fun retryAndLeave() { cancelLeave(); retry(); requestLeave() }
    fun discardAndLeave() {
        savedState["unsavedInput"] = false
        updateState { it.copy(leaveRequested = false, closeRequested = true) }
    }
    fun delete() {
        if (!canEdit) return
        updateState { it.copy(deleting = true) }
        viewModelScope.launch {
            try {
                writer?.join()
                withContext(NonCancellable) { repository.delete(id) }
                updateState { it.copy(deleted = true, closeRequested = true) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                updateState { it.copy(message = R.string.record_delete_failed) }
            } finally { updateState { it.copy(deleting = false) } }
        }
    }
}
