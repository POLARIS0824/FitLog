@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.fitlog.editor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModel
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
) : ViewModel() {
    val text = TextFieldState()
    var loading by mutableStateOf(true); private set
    var writable by mutableStateOf(false); private set
    var name by mutableStateOf(route.date + ".md"); private set
    var state by mutableStateOf(EditorSaveState.Unsaved); private set
    var error by mutableStateOf<Int?>(null); private set
    var recovery by mutableStateOf<EditorDraft?>(null); private set
    var collision by mutableStateOf<MarkdownFile?>(null); private set
    var conflict by mutableStateOf(false); private set
    var exitRequested by mutableStateOf(false); private set
    var reloadRequested by mutableStateOf(false); private set
    var closing by mutableStateOf(false); private set
    private var document = route.document
    private var baseline = ""
    private var sourceFingerprint: String? = null
    private var bom = false
    private var version = 0L
    private var lastObserved = ""
    private var blocked = false
    private var operationBusy by mutableStateOf(false)
    private var target = route.document ?: route.date + ".md"
    private val saveLock = Mutex()
    private val draftLock = Mutex()
    private var debounce: Job? = null
    private var draftDebounce: Job? = null
    private var maxSave: Job? = null
    private var maxDraft: Job? = null
    val dirty: Boolean get() = text.text.toString() != baseline
    val canEdit: Boolean get() = !loading && writable && recovery == null && collision == null && !closing && !operationBusy
    val canFormat: Boolean get() = canEdit && text.composition == null

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
                val draft = drafts.read(route.vault, target)
                recovery = draft
                document?.let { applySnapshot(documents.read(it)) }
                    ?: run { writable = true; collision = documents.find(route.vault, name) }
                blocked = draft != null || collision != null
            } catch (e: Exception) { fail(e); writable = false }
            finally { loading = false }
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
        try {
            val source = draft.document?.let { documents.read(it) }
            document = draft.document
            name = draft.name
            bom = draft.bom
            sourceFingerprint = draft.fingerprint
            writable = source?.file?.writable ?: true
            baseline = source?.text ?: ""
            conflict = source != null && source.fingerprint != draft.fingerprint
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
        if (!dirty || blocked || closing) return
        debounce = viewModelScope.launch { delay(1500); save() }
        if (maxSave?.isActive != true) maxSave = viewModelScope.launch { delay(10000); save() }
    }

    private suspend fun storeDraftSafely() {
        if (loading || operationBusy || recovery != null || !writable || (!dirty && !conflict)) return
        try { persistDraft() } catch (e: Exception) { fail(e) }
    }

    private suspend fun persistDraft() = draftLock.withLock {
        val snapshot = EditorDraft(route.vault, target, document, name, text.text.toString(),
            text.selection.start, text.selection.end, version, sourceFingerprint, bom)
        drafts.save(snapshot)
        document?.takeIf { it != target }?.let { drafts.save(snapshot.copy(target = it)) }
    }

    private suspend fun deleteDraft() = draftLock.withLock {
        drafts.remove(route.vault, target)
        document?.takeIf { it != target }?.let { drafts.remove(route.vault, it) }
    }

    fun saveNow() = viewModelScope.launch { blocked = conflict || collision != null || recovery != null; save() }

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
                    val created = documents.create(route.vault, name)
                    document = created.uri
                    sourceFingerprint = documents.read(created.uri).fingerprint
                    persistDraft()
                }
                val uri = requireNotNull(document)
                val before = documents.read(uri)
                if (before.fingerprint != sourceFingerprint) throw DocumentConflict()
                val original = (if (before.bom) byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()) else byteArrayOf()) + before.text.toByteArray(Charsets.UTF_8)
                drafts.backup(route.vault, uri, original)
                val result = documents.write(uri, value, bom, requireNotNull(sourceFingerprint))
                baseline = value
                sourceFingerprint = result.fingerprint
                error = null
                blocked = false
                if (dirty) persistDraft() else deleteDraft()
                state = if (dirty) EditorSaveState.Unsaved else EditorSaveState.Saved
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
                while (documents.find(route.vault, copyName) != null) copyName = "$stem-conflict-$timestamp-${index++}.md"
                persistDraft()
                val created = documents.create(route.vault, copyName)
                document = created.uri; name = created.name
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
    fun onBackground() = viewModelScope.launch { if (!closing) { storeDraftSafely(); if (!blocked) save() } }
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
