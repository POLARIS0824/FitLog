@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.fitlog.editor

import com.example.fitlog.ui.components.FitLogLoadingIndicator

import com.example.fitlog.ui.preview.FitLogPreviews
import com.example.fitlog.ui.preview.FitLogPreview
import com.example.fitlog.ui.preview.PreviewDiary
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.fitlog.R
import com.example.fitlog.ui.components.FitLogNotice

@Composable
private fun Action(id: Int, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled) { Text(stringResource(id)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolAction(icon: Int, label: Int, enabled: Boolean = true, onClick: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(stringResource(label)) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick, enabled = enabled, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(icon), stringResource(label))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: EditorViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var wrongFolder by remember { mutableStateOf(false) }
    val exporter = remember(context) { RecoveryExporter(context.contentResolver) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri == null) vm.cancelExport() else vm.export { exporter.export(uri, it) }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            if (uri.toString() != vm.route.vaultUri) wrongFolder = true
            else try {
                com.example.fitlog.data.vault.AndroidSafDirectoryAccessor(context).takePersistablePermission(uri).getOrThrow()
                wrongFolder = false
                vm.onReauthorized()
            } catch (_: SecurityException) { wrongFolder = true }
        }
    }
    BackHandler { vm.requestExit(onBack) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { if (!vm.loading && vm.recovery == null) vm.onBackground() }
    EditorContent(
        state = EditorUiState(
            text = vm.text,
            name = vm.name,
            canEdit = vm.canEdit,
            canFormat = vm.canFormat,
            state = vm.state,
            loading = vm.loading,
            writable = vm.writable,
            error = vm.error,
            notice = vm.notice,
            requiresManualSave = vm.requiresManualSave,
            conflict = vm.conflict,
            recovery = vm.recovery,
            collision = vm.collision,
            exitRequested = vm.exitRequested,
            closing = vm.closing,
            reloadRequested = vm.reloadRequested,
        ),
        actions = EditorActions(
            saveNow = { vm.saveNow() },
            retryLoad = { vm.retryLoad() },
            requestReload = { vm.requestReload() },
            saveCopy = { vm.saveCopy() },
            restoreDraft = { vm.restoreDraft() },
            discardDraft = { vm.discardDraft() },
            dismissCollision = { vm.dismissCollision() },
            openCollision = { vm.openCollision() },
            cancelExit = { vm.cancelExit() },
            cancelReload = { vm.cancelReload() },
            reload = { vm.reload() },
            requestExit = { vm.requestExit(it) },
            leave = { draft, back -> vm.leave(draft, back) },
            edit = { vm.edit(it) },
            prepareExport = { vm.prepareExport(export::launch) },
            reauthorize = { permission.launch(Uri.parse(vm.route.vaultUri)) },
        ),
        onBack = onBack, modifier = modifier, wrongFolder = wrongFolder,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun EditorContent(state: EditorUiState, actions: EditorActions, onBack: () -> Unit, modifier: Modifier = Modifier, wrongFolder: Boolean = false) {
    val editorFocus = remember { FocusRequester() }
    val editorScroll = rememberScrollState()
    val text = state.text
    val inputTransformation = remember(text) { MarkdownInputTransformation { text.composition != null } }
    val editorLabel = stringResource(R.string.editor_content)
    var formatMenu by remember { mutableStateOf(false) }
    var linkSelection by remember { mutableStateOf<TextRange?>(null) }
    var linkSource by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    Box(modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 840.dp).fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
            TopAppBar(
                title = { Text(state.name, style = MaterialTheme.typography.titleLargeEmphasized,
                    maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = { actions.requestExit(onBack) }) {
                    Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.cd_back))
                } },
                actions = {
                    Button(onClick = { actions.saveNow() }, enabled = state.canEdit && state.state != EditorSaveState.Saving,
                        shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.editor_save), style = MaterialTheme.typography.labelLargeEmphasized)
                    }
                },
                windowInsets = WindowInsets(0),
            )
            Column(Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = MaterialTheme.shapes.large,
                    color = if (state.state == EditorSaveState.Failed) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (state.state == EditorSaveState.Failed) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onSecondaryContainer) {
                    Text(stringResource(when {
                        state.loading -> R.string.editor_loading
                        !state.writable -> R.string.editor_read_only
                        else -> when (state.state) {
                            EditorSaveState.Unsaved -> R.string.editor_unsaved
                            EditorSaveState.Saving -> R.string.editor_saving
                            EditorSaveState.Saved -> R.string.editor_saved
                            EditorSaveState.Failed -> R.string.editor_failed
                        }
                    }), style = MaterialTheme.typography.labelLargeEmphasized,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
                state.error?.let { error ->
                    FitLogNotice(stringResource(error), error = true)
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        Action(R.string.log_retry) { if (!state.writable) actions.retryLoad() else actions.saveNow() }
                        Action(R.string.editor_reauthorize) { actions.reauthorize() }
                    }
                }
                state.notice?.let { FitLogNotice(stringResource(it)) }
                if (state.requiresManualSave) FitLogNotice(stringResource(R.string.recovery_manual_save))
                if (wrongFolder) FitLogNotice(stringResource(R.string.editor_wrong_folder), error = true)
                if (state.conflict) Row(Modifier.horizontalScroll(rememberScrollState())) {
                    Action(R.string.editor_reload) { actions.requestReload() }
                    Action(R.string.editor_copy) { actions.saveCopy() }
                }
            }
            BasicTextField(state = state.text, readOnly = !state.canEdit,
                inputTransformation = inputTransformation, scrollState = editorScroll,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.large).padding(16.dp)
                    .focusRequester(editorFocus).semantics { contentDescription = editorLabel },
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface))
            HorizontalFloatingToolbar(expanded = true,
                colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
                modifier = Modifier.align(Alignment.CenterHorizontally)) {
                ToolAction(R.drawable.undo_24px, R.string.editor_undo, state.canFormat && state.text.undoState.canUndo) { state.text.undoState.undo() }
                ToolAction(R.drawable.redo_24px, R.string.editor_redo, state.canFormat && state.text.undoState.canRedo) { state.text.undoState.redo() }
                Box {
                    ToolAction(R.drawable.format_24px, R.string.editor_format, state.canFormat) { formatMenu = true }
                    DropdownMenu(expanded = formatMenu, onDismissRequest = { formatMenu = false }) {
                        fun apply(command: (String, TextRange) -> MarkdownEditCommands.Edit) {
                            actions.edit(command(state.text.text.toString(), state.text.selection)); formatMenu = false
                            editorFocus.requestFocus()
                        }
                        listOf(R.string.editor_h1, R.string.editor_h2, R.string.editor_h3, R.string.editor_paragraph).forEachIndexed { i, id ->
                            DropdownMenuItem(text = { Text(stringResource(id)) }, onClick = { apply { t, s -> MarkdownEditCommands.heading(t, s, if (i == 3) 0 else i + 1) } })
                        }
                        DropdownMenuItem(text = { Text(stringResource(R.string.editor_bold)) }, onClick = { apply { t, s -> MarkdownEditCommands.wrap(t, s, "**") } })
                        DropdownMenuItem(text = { Text(stringResource(R.string.editor_italic)) }, onClick = { apply { t, s -> MarkdownEditCommands.wrap(t, s, "*") } })
                        DropdownMenuItem(text = { Text(stringResource(R.string.editor_list)) }, onClick = { apply { t, s -> MarkdownEditCommands.list(t, s, false) } })
                        DropdownMenuItem(text = { Text(stringResource(R.string.editor_task)) }, onClick = { apply { t, s -> MarkdownEditCommands.list(t, s, true) } })
                        DropdownMenuItem(text = { Text(stringResource(R.string.editor_ordered_list)) }, onClick = { apply(MarkdownEditCommands::orderedList) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.editor_indent)) }, onClick = { apply { t, s -> MarkdownEditCommands.indent(t, s, false) } })
                        DropdownMenuItem(text = { Text(stringResource(R.string.editor_outdent)) }, onClick = { apply { t, s -> MarkdownEditCommands.indent(t, s, true) } })
                        DropdownMenuItem(text = { Text(stringResource(R.string.editor_link)) }, onClick = {
                            linkSource = state.text.text.toString(); linkSelection = state.text.selection
                            label = linkSource.substring(state.text.selection.min, state.text.selection.max)
                            address = ""; formatMenu = false
                        })
                    }
                }
                ToolAction(R.drawable.export_24px, R.string.recovery_export, !state.loading && state.recovery == null) {
                    actions.prepareExport()
                }
            }
        }
        FitLogLoadingIndicator(state.loading || state.state == EditorSaveState.Saving,
            Modifier.align(Alignment.TopCenter).widthIn(max = 840.dp).fillMaxWidth().padding(horizontal = 16.dp))
    }
    linkSelection?.let { selection ->
        AlertDialog(onDismissRequest = { linkSelection = null }, title = { Text(stringResource(R.string.editor_link)) }, text = {
            Column {
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text(stringResource(R.string.editor_label)) }, singleLine = true)
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text(stringResource(R.string.editor_address)) }, singleLine = true)
            }
        }, confirmButton = { Action(R.string.editor_confirm, address.isNotBlank() && state.text.text.toString() == linkSource) {
            actions.edit(MarkdownEditCommands.link(selection, label, address.trim())); linkSelection = null
        } }, dismissButton = { Action(R.string.editor_cancel) { linkSelection = null } })
    }
    if (state.recovery != null) AlertDialog(onDismissRequest = {}, title = { Text(stringResource(R.string.editor_recover)) },
        text = { Text(stringResource(R.string.editor_draft_found)) },
        confirmButton = { Action(R.string.editor_recover) { actions.restoreDraft() } },
        dismissButton = { Action(R.string.editor_discard) { actions.discardDraft() } })
    else if (state.collision != null) AlertDialog(onDismissRequest = actions.dismissCollision,
        text = { Text(stringResource(R.string.editor_name_collision)) },
        confirmButton = { Action(R.string.editor_open_existing) { actions.openCollision() } },
        dismissButton = { Action(R.string.editor_cancel) { actions.dismissCollision() } })
    if (state.exitRequested) AlertDialog(onDismissRequest = actions.cancelExit, title = { Text(stringResource(R.string.editor_leave)) },
        text = { Text(stringResource(R.string.editor_leave_message)) },
        confirmButton = { Action(R.string.editor_save_exit, !state.closing) { actions.leave(false, onBack) } },
        dismissButton = { Column {
            Action(R.string.editor_draft_exit, !state.closing) { actions.leave(true, onBack) }
            Action(R.string.editor_cancel) { actions.cancelExit() }
        } })
    if (state.reloadRequested) AlertDialog(onDismissRequest = actions.cancelReload,
        text = { Text(stringResource(R.string.editor_reload_message)) },
        confirmButton = { Action(R.string.editor_confirm) { actions.reload() } },
        dismissButton = { Action(R.string.editor_cancel) { actions.cancelReload() } })
}

private data class EditorUiState(
    val text: androidx.compose.foundation.text.input.TextFieldState,
    val name: String,
    val canEdit: Boolean = true,
    val canFormat: Boolean = true,
    val state: EditorSaveState = EditorSaveState.Unsaved,
    val loading: Boolean = false,
    val writable: Boolean = true,
    val error: Int? = null,
    val notice: Int? = null,
    val requiresManualSave: Boolean = false,
    val conflict: Boolean = false,
    val recovery: EditorDraft? = null,
    val collision: com.example.fitlog.data.vault.MarkdownFile? = null,
    val exitRequested: Boolean = false,
    val closing: Boolean = false,
    val reloadRequested: Boolean = false,
)

private data class EditorActions(
    val saveNow: () -> Unit = {},
    val retryLoad: () -> Unit = {},
    val requestReload: () -> Unit = {},
    val saveCopy: () -> Unit = {},
    val restoreDraft: () -> Unit = {},
    val discardDraft: () -> Unit = {},
    val dismissCollision: () -> Unit = {},
    val openCollision: () -> Unit = {},
    val cancelExit: () -> Unit = {},
    val cancelReload: () -> Unit = {},
    val reload: () -> Unit = {},
    val requestExit: (() -> Unit) -> Unit = { it() },
    val leave: (Boolean, () -> Unit) -> Unit = { _, back -> back() },
    val edit: (MarkdownEditCommands.Edit) -> Unit = {},
    val prepareExport: () -> Unit = {},
    val reauthorize: () -> Unit = {},
)

@FitLogPreviews
@Composable
private fun EditorPreview() {
    val initialText = stringResource(R.string.preview_diary_markdown)
    val text = remember { androidx.compose.foundation.text.input.TextFieldState(initialText = initialText) }
    FitLogPreview {
        EditorContent(EditorUiState(text = text, name = PreviewDiary.FILE_NAME),
            EditorActions(edit = { edit -> text.edit { replace(edit.start, edit.end, edit.replacement); selection = edit.selection } }), {})
    }
}

@FitLogPreviews
@Composable
private fun EditorSaveFailedPreview() {
    val initialText = stringResource(R.string.preview_diary_markdown)
    val text = remember { androidx.compose.foundation.text.input.TextFieldState(initialText = initialText) }
    FitLogPreview {
        EditorContent(EditorUiState(text = text, name = PreviewDiary.FILE_NAME,
            state = EditorSaveState.Failed, error = R.string.editor_failed), EditorActions(), {})
    }
}
