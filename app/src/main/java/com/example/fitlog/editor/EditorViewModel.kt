@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.fitlog.editor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.fitlog.R
import com.example.fitlog.data.vault.*
import com.example.fitlog.navigation.FitLogRoute
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.charset.CharacterCodingException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class EditorSaveState { Unsaved, Saving, Saved, Failed }

class EditorViewModel(
    val route: FitLogRoute.Editor,
    private val documents: MarkdownDocuments,
    private val drafts: Drafts,
    private val sessionState: SavedStateHandle = SavedStateHandle(),
    private val onSaved: suspend (MarkdownSnapshot, String, String?) -> Unit = { _, _, _ -> },
) : ViewModel() {
    val text = TextFieldState()
    var loading by mutableStateOf(true); private set
    var writable by mutableStateOf(false); private set
    var name by mutableStateOf(sessionState.get<String>("name") ?: route.fileName); private set
    var state by mutableStateOf(EditorSaveState.Unsaved); private set
    var error by mutableStateOf<Int?>(null); private set
    var recovery by mutableStateOf<EditorDraft?>(null); private set
    var collision by mutableStateOf<MarkdownFile?>(null); private set
    var conflict by mutableStateOf(false); private set
    var exitRequested by mutableStateOf(false); private set
    var reloadRequested by mutableStateOf(false); private set
    var closing by mutableStateOf(false); private set
    var notice by mutableStateOf<Int?>(null); private set
    private var manualSave = false
    private var recoveryIdentity = ""
    private val displayPath: String? get() = route.displayPath?.let {
        val parent = it.substringBeforeLast('/', "")
        if (parent.isEmpty()) name else "$parent/$name"
    }
    private var document = sessionState.get<String>("document") ?: route.document
    private var recoveryEntryId = if (sessionState.contains("recoveryId")) sessionState.get<String>("recoveryId") else route.recoveryId
    private var baseline = ""
    private var sourceFingerprint: String? = null
    private var bom = false
    private var version = 0L
    private var lastObserved = ""
    private var blocked = false
    private var operationBusy by mutableStateOf(false)
    private var target = sessionState.get<String>("target") ?: document ?: newDraftTarget(route)
    private val saveLock = Mutex()
    private val draftLock = Mutex()
    private var debounce: Job? = null
    private var draftDebounce: Job? = null
    private var maxSave: Job? = null
    private var maxDraft: Job? = null
    val dirty: Boolean get() = text.text.toString() != baseline
    val canEdit: Boolean get() = !loading && writable && recovery == null && collision == null && !closing && !operationBusy
    val canFormat: Boolean get() = canEdit && text.composition == null
    val requiresManualSave: Boolean get() = manualSave

    init {
        load()
        viewModelScope.launch {
            snapshotFlow { text.text.toString() }.collectLatest { value ->
                if (loading || value == lastObserved) return@collectLatest
                lastObserved = value
                version++
                if (state != EditorSaveState.Saving && !blocked) state = if (dirty) EditorSaveState.Unsaved else EditorSaveState.Saved
                if (recovery == null && writable) schedule()
            }
        }
    }

    fun load() {
        if (closing) return
        loading = true
        error = null
        viewModelScope.launch {
            try {
                val draft = if (recoveryEntryId != null) {
                    drafts.entry(requireNotNull(recoveryEntryId))?.draft
                } else drafts.read(route.vault, target)
                recovery = draft
                if (recoveryEntryId != null && draft != null) { writable = true; return@launch }
                if (recoveryEntryId != null && document == null) {
                    // A completed recovery may have removed its draft before process recreation.
                    document = requireNotNull(documents.find(route.directory, name)).uri
                }
                document?.let { applySnapshot(documents.read(it)) }
                    ?: run { writable = true; collision = documents.find(route.directory, name) }
                blocked = draft != null || collision != null
            } catch (e: Exception) { fail(e); writable = false }
            finally {
                loading = false
                if (recoveryEntryId != null && recovery != null) restoreDraft()
            }
        }
    }

    private fun applySnapshot(snapshot: MarkdownSnapshot) {
        document = snapshot.file.uri
        name = snapshot.file.name
        baseline = snapshot.text
        sourceFingerprint = snapshot.fingerprint
        bom = snapshot.bom
        writable = snapshot.file.writable
        replace(snapshot.text, TextRange(0))
        state = EditorSaveState.Saved
    }

    private fun replace(value: String, selection: TextRange) {
        lastObserved = value
        text.edit { replace(0, length, value); this.selection = selection }
        text.undoState.clearHistory()
    }

    fun edit(edit: MarkdownEditCommands.Edit) {
        if (!canFormat) return
        text.edit { replace(edit.start, edit.end, edit.replacement); selection = edit.selection }
    }

    fun restoreDraft() = viewModelScope.launch {
        if (operationBusy) return@launch
        val draft = recovery ?: return@launch
        operationBusy = true
        target = draft.originTarget
        recoveryIdentity = draft.identity()
        manualSave = draft.manualSave || recoveryEntryId != null
        try {
            val source = draft.document?.let { documents.read(it) }
            document = draft.document
            name = draft.name
            bom = draft.bom
            sourceFingerprint = if (draft.restoredBackup) source?.fingerprint else draft.fingerprint
            writable = true // A local recovery remains editable; SAF capabilities are checked on write.
            baseline = source?.text ?: ""
            conflict = source != null && source.fingerprint != sourceFingerprint
            collision = null
            replace(draft.text, TextRange(draft.selectionStart.coerceIn(0, draft.text.length), draft.selectionEnd.coerceIn(0, draft.text.length)))
            version = draft.version
            recovery = null
            blocked = conflict
            state = if (dirty) EditorSaveState.Unsaved else EditorSaveState.Saved
            if (conflict) { error = R.string.editor_conflict; state = EditorSaveState.Failed }
            else schedule()
        } catch (e: Exception) {
            // Keep the recoverable text accessible even when its source was removed.
            replace(draft.text, TextRange(draft.text.length))
            document = draft.document
            sourceFingerprint = draft.fingerprint
            name = draft.name
            bom = draft.bom
            recovery = null
            collision = null
            writable = true
            conflict = true
            fail(e)
        } finally { operationBusy = false }
    }

    fun discardDraft() = viewModelScope.launch {
        if (operationBusy) return@launch
        operationBusy = true
        try {
            val old = recovery
            deleteDraft()
            old?.document?.takeIf { it != target }?.let { drafts.remove(route.vault, it) }
            old?.originTarget?.takeIf { it != target }?.let { drafts.remove(route.vault, it) }
            recovery = null; blocked = collision != null
        }
        catch (e: Exception) { fail(e) }
        finally { operationBusy = false }
    }

    fun openCollision() = viewModelScope.launch {
        if (operationBusy) return@launch
        val file = collision ?: return@launch
        operationBusy = true
        try {
            // Retain any new-file draft; opening an existing file must not overwrite it.
            if (dirty) persistDraft()
            applySnapshot(documents.read(file.uri))
            target = file.uri
            recoveryEntryId = null; recoveryIdentity = ""; manualSave = false
            rememberDestination()
            collision = null
            blocked = false
            error = null
            recovery = drafts.read(route.vault, file.uri)
            if (recovery != null) blocked = true
        } catch (e: Exception) { fail(e) }
        finally { operationBusy = false }
    }

    private fun schedule() {
        draftDebounce?.cancel()
        draftDebounce = viewModelScope.launch { delay(350); storeDraftSafely() }
        if (maxDraft?.isActive != true) maxDraft = viewModelScope.launch { delay(2000); storeDraftSafely() }
        debounce?.cancel()
        if (!dirty || blocked || closing || manualSave) return
        debounce = viewModelScope.launch { delay(1500); save() }
        if (maxSave?.isActive != true) maxSave = viewModelScope.launch { delay(10000); save() }
    }

    private suspend fun storeDraftSafely() {
        if (loading || operationBusy || recovery != null || !writable || (!dirty && !conflict)) return
        try { persistDraft() } catch (e: Exception) { fail(e) }
    }

    private suspend fun persistDraft() = draftLock.withLock {
        val snapshot = EditorDraft(route.vault, target, document, name, text.text.toString(),
            text.selection.start, text.selection.end, version, sourceFingerprint, bom,
            directory = route.directory, displayPath = displayPath,
            updatedAt = System.currentTimeMillis(), recoveryId = recoveryIdentity,
            manualSave = manualSave, vaultId = route.vaultId)
        drafts.save(snapshot)
        if (recoveryEntryId == null) document?.takeIf { it != target }?.let { drafts.save(snapshot.copy(target = it)) }
    }

    private suspend fun deleteDraft() = draftLock.withLock {
        recoveryEntryId?.let { drafts.deleteEntry(it) }
        drafts.remove(route.vault, target)
        if (recoveryEntryId == null) document?.takeIf { it != target }?.let { drafts.remove(route.vault, it) }
    }

    fun saveNow() = viewModelScope.launch {
        blocked = conflict || collision != null || recovery != null
        if (save()) manualSave = false
    }

    private suspend fun save(): Boolean = saveLock.withLock {
        if (loading || operationBusy || !writable || blocked || conflict || recovery != null || collision != null) return@withLock false
        if (!dirty && document != null) return@withLock true
        if (document == null && text.text.isEmpty()) return@withLock true
        debounce?.takeIf { it != currentCoroutineContext()[Job] }?.cancel()
        maxSave?.takeIf { it != currentCoroutineContext()[Job] }?.cancel()
        state = EditorSaveState.Saving
        val value = text.text.toString()
        try {
            // Once writing starts, finishing recovery bookkeeping survives navigation cancellation.
            withContext(NonCancellable) {
                persistDraft()
                if (document == null) {
                    val created = documents.create(route.directory, name)
                    document = created.uri
                    rememberDestination()
                    sourceFingerprint = documents.read(created.uri).fingerprint
                    persistDraft()
                }
                val uri = requireNotNull(document)
                val before = documents.read(uri)
                if (before.fingerprint != sourceFingerprint) throw DocumentConflict()
                val original = (if (before.bom) byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()) else byteArrayOf()) + before.text.toByteArray(Charsets.UTF_8)
                drafts.prepareBackup(EditorDraft(route.vault, uri, uri, name, before.text, 0, 0, version,
                    before.fingerprint, before.bom, directory = route.directory,
                    displayPath = displayPath, updatedAt = System.currentTimeMillis(), vaultId = route.vaultId), original)
                val result = documents.write(uri, value, bom, requireNotNull(sourceFingerprint))
                baseline = value
                sourceFingerprint = result.fingerprint
                error = null
                blocked = false
                if (dirty) persistDraft() else deleteDraft()
                state = if (dirty) EditorSaveState.Unsaved else EditorSaveState.Saved
                try { drafts.completeBackup(route.vault, uri) }
                catch (e: Exception) { if (e is CancellationException) throw e; notice = R.string.recovery_bookkeeping_failed }
                try { onSaved(result, route.directory, displayPath) }
                catch (e: Exception) { if (e is CancellationException) throw e; notice = R.string.index_save_failed }
            }
            if (dirty) schedule()
            true
        } catch (e: NameCollision) { collision = e.file; fail(e); false }
        catch (e: DocumentConflict) { conflict = true; fail(e); false }
        catch (e: Exception) { fail(e); false }
    }

    fun requestExit(onExit: () -> Unit) {
        if (loading || closing || state == EditorSaveState.Saving) { exitRequested = true; return }
        if (!dirty && recovery == null) onExit() else exitRequested = true
    }
    fun cancelExit() { exitRequested = false }
    fun leave(keepDraft: Boolean, onExit: () -> Unit) = viewModelScope.launch {
        if (closing || recovery != null || loading) return@launch
        closing = true
        try {
            if (keepDraft) saveLock.withLock { persistDraft() }
            else { blocked = conflict || collision != null; if (!save() || dirty) return@launch }
            exitRequested = false
            onExit()
        } catch (e: Exception) { fail(e) }
        finally { closing = false }
    }

    fun requestReload() { reloadRequested = true }
    fun cancelReload() { reloadRequested = false }
    fun reload() = viewModelScope.launch {
        if (operationBusy) return@launch
        operationBusy = true
        saveLock.withLock {
            try {
                val source = documents.read(requireNotNull(document))
                deleteDraft()
                applySnapshot(source)
                conflict = false; blocked = false; error = null; reloadRequested = false
            } catch (e: Exception) { fail(e) }
        }
        operationBusy = false
    }

    fun saveCopy() = viewModelScope.launch {
        if (operationBusy) return@launch
        operationBusy = true
        saveLock.withLock {
            try {
                val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"))
                val stem = name.substringBeforeLast('.')
                var copyName = "$stem-conflict-$timestamp.md"
                var index = 2
                while (documents.find(route.directory, copyName) != null) copyName = "$stem-conflict-$timestamp-${index++}.md"
                persistDraft()
                val created = documents.create(route.directory, copyName)
                document = created.uri; name = created.name
                rememberDestination()
                sourceFingerprint = documents.read(created.uri).fingerprint
                baseline = ""; bom = false; writable = true
                conflict = false; blocked = false; collision = null
                persistDraft()
            } catch (e: Exception) { operationBusy = false; fail(e); return@launch }
        }
        operationBusy = false
        save()
    }

    fun dismissCollision() { collision = null /* Keep autosave blocked until an explicit retry. */ }
    fun onBackground() = viewModelScope.launch { if (!closing) { storeDraftSafely(); if (!blocked && !manualSave) save() } }
    fun prepareExport(launch: (String) -> Unit) = viewModelScope.launch {
        if (loading || recovery != null || operationBusy || sessionState.contains("exportId")) return@launch
        operationBusy = true
        try {
            persistDraft()
            // Keep the picker snapshot independent of autosave and its draft cleanup.
            val id = java.util.UUID.randomUUID().toString()
            val snapshot = EditorDraft(route.vault, "export:$id", document, name, text.text.toString(), 0, 0,
                version, sourceFingerprint, bom, directory = route.directory,
                displayPath = displayPath, updatedAt = System.currentTimeMillis(), recoveryId = id,
                manualSave = true, vaultId = route.vaultId)
            drafts.save(snapshot)
            sessionState["exportId"] = "draft:${snapshot.identity()}"
            launch(name)
        } catch (e: Exception) { fail(e) }
        finally { operationBusy = false }
    }
    fun export(exporter: suspend (EditorDraft) -> Unit) = viewModelScope.launch {
        val id = sessionState.remove<String>("exportId")
        try {
            val value = requireNotNull(id?.let { drafts.entry(it)?.draft })
            exporter(value)
            notice = R.string.recovery_exported
        }
        catch (e: Exception) { if (e is CancellationException) throw e; notice = R.string.recovery_export_failed }
    }
    fun cancelExport() { sessionState.remove<String>("exportId") }
    private fun rememberDestination() {
        sessionState.set("document", document)
        sessionState.set("name", name)
        sessionState.set("target", target)
        sessionState.set("recoveryId", recoveryEntryId)
    }
    fun onReauthorized() = viewModelScope.launch {
        if (loading || operationBusy || state == EditorSaveState.Saving) return@launch
        if (!writable) { load(); return@launch }
        operationBusy = true
        try {
            val source = document?.let { documents.read(it) }
            conflict = source != null && source.fingerprint != sourceFingerprint
            blocked = conflict
            error = if (conflict) R.string.editor_conflict else null
            if (!conflict) state = if (dirty) EditorSaveState.Unsaved else EditorSaveState.Saved
        } catch (e: Exception) { fail(e) }
        finally { operationBusy = false }
    }
    fun retryLoad() { if (loading) return; load() }
    private fun fail(e: Exception) {
        if (e is CancellationException) throw e
        blocked = true
        state = EditorSaveState.Failed
        error = when (e) {
            is DocumentConflict -> R.string.editor_conflict
            is NameCollision -> R.string.editor_name_collision
            is UnexpectedDocumentName -> R.string.editor_provider_renamed
            is CharacterCodingException -> R.string.editor_invalid_utf8
            is SecurityException -> R.string.vault_error_needs_reauthorization
            else -> R.string.editor_io_failed
        }
    }
}

internal fun newDraftTarget(route: FitLogRoute.Editor): String {
    // Preserve legacy root draft keys; child folders must not share date-based drafts.
    val root = runCatching {
        val uri = android.net.Uri.parse(route.vault)
        android.provider.DocumentsContract.buildDocumentUriUsingTree(uri,
            android.provider.DocumentsContract.getTreeDocumentId(uri)).toString()
    }.getOrNull()
    return if (route.directory == route.vault || route.directory == root) route.fileName
    else route.directory + "\n" + route.fileName
}
