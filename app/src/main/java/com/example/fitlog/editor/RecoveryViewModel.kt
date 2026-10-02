package com.example.fitlog.editor

import androidx.compose.runtime.*
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitlog.R
import com.example.fitlog.navigation.FitLogRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.UUID

class RecoveryViewModel(
    private val state: SavedStateHandle,
    private val drafts: Drafts,
    private val resolveVaultId: suspend (String) -> String? = { null },
) : ViewModel() {
    private val operations = Mutex()
    private var selectionGeneration = 0L
    var entries by mutableStateOf<List<RecoveryEntry>>(emptyList()); private set
    var selectedId by mutableStateOf(state.get<String>("selected")); private set
    val selected get() = entries.firstOrNull { it.id == selectedId }
    var busy by mutableStateOf(false); private set
    var notice by mutableStateOf<Int?>(null); private set

    fun select(id: String?) { selectionGeneration++; selectedId = id; state["selected"] = id; notice = null }
    fun refresh() = runOperation { entries = drafts.entries() }
    fun delete(id: String) = runOperation {
        drafts.deleteEntry(id)
        if (selectedId == id) select(null)
        entries = drafts.entries()
    }
    fun restore(onOpen: (FitLogRoute.Editor) -> Unit): kotlinx.coroutines.Job {
        val selection = selectedId
        val generation = selectionGeneration
        return runOperation {
            val entry = requireNotNull(selection?.let { drafts.entry(it) })
            if (generation != selectionGeneration) return@runOperation
            var draft = requireNotNull(entry.draft)
            val directory = requireNotNull(draft.originalDirectory())
            require(draft.vault.isNotEmpty())
            val resolvedId = resolveVaultId(draft.vault)
            require(draft.vaultId == null || resolvedId == null || draft.vaultId == resolvedId)
            val vaultId = resolvedId ?: draft.vaultId
            var id = entry.id
            if (entry.backup) {
                val target = "recovered:${UUID.randomUUID()}"
                draft = draft.copy(target = target, originTarget = target, recoveryId = UUID.randomUUID().toString(),
                    manualSave = true, restoredBackup = true, directory = directory, updatedAt = System.currentTimeMillis(),
                    vaultId = vaultId)
                drafts.save(draft)
                id = "draft:${draft.identity()}"
            }
            if (generation != selectionGeneration) return@runOperation
            onOpen(FitLogRoute.Editor(vault = draft.vault, document = draft.document, directory = directory,
                fileName = draft.name, recoveryId = id, displayPath = draft.displayPath, vaultId = vaultId))
        }
    }
    fun prepareExport(launch: (String) -> Unit, fallbackName: String) {
        val entry = selected ?: return
        if (busy || entry.draft == null) return
        state["exportId"] = entry.id
        launch(entry.draft.name.ifBlank { fallbackName })
    }
    fun cancelExport() { state.remove<String>("exportId") }
    fun export(write: suspend (EditorDraft) -> Unit) = runOperation(queue = true) {
        val id = requireNotNull(state.remove<String>("exportId"))
        val entry = requireNotNull(drafts.entry(id))
        write(requireNotNull(entry.draft))
        notice = R.string.recovery_exported
    }
    private fun runOperation(queue: Boolean = false, block: suspend () -> Unit) = viewModelScope.launch {
        if (queue) operations.lock() else if (!operations.tryLock()) return@launch
        busy = true; notice = null
        try { block() }
        catch (e: Exception) { if (e is CancellationException) throw e; notice = R.string.recovery_operation_failed }
        finally { busy = false; operations.unlock() }
    }
}
