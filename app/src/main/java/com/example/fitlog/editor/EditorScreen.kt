@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.fitlog.editor

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
    val editorFocus = remember { FocusRequester() }
    val editorScroll = rememberScrollState()
    val inputTransformation = remember(vm) { MarkdownInputTransformation { vm.text.composition != null } }
    val editorLabel = stringResource(R.string.editor_content)
    var formatMenu by remember { mutableStateOf(false) }
    var linkSelection by remember { mutableStateOf<TextRange?>(null) }
    var linkSource by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
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
    Box(modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 840.dp).fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
            TopAppBar(
                title = { Text(vm.name, style = MaterialTheme.typography.titleLargeEmphasized,
                    maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = { vm.requestExit(onBack) }) {
                    Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.cd_back))
                } },
                actions = {
                    Button(onClick = { vm.saveNow() }, enabled = vm.canEdit && vm.state != EditorSaveState.Saving,
                        shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.editor_save), style = MaterialTheme.typography.labelLargeEmphasized)
                    }
                },
                windowInsets = WindowInsets(0),
            )
            Column(Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = MaterialTheme.shapes.large,
                    color = if (vm.state == EditorSaveState.Failed) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (vm.state == EditorSaveState.Failed) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onSecondaryContainer) {
                    Text(stringResource(when {
                        vm.loading -> R.string.editor_loading
                        !vm.writable -> R.string.editor_read_only
                        else -> when (vm.state) {
                            EditorSaveState.Unsaved -> R.string.editor_unsaved
                            EditorSaveState.Saving -> R.string.editor_saving
                            EditorSaveState.Saved -> R.string.editor_saved
                            EditorSaveState.Failed -> R.string.editor_failed
                        }
                    }), style = MaterialTheme.typography.labelLargeEmphasized,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
                if (vm.loading || vm.state == EditorSaveState.Saving) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                vm.error?.let { error ->
                    FitLogNotice(stringResource(error), error = true)
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        Action(R.string.log_retry) { if (!vm.writable) vm.retryLoad() else vm.saveNow() }
                        Action(R.string.editor_reauthorize) { permission.launch(Uri.parse(vm.route.vaultUri)) }
                    }
                }
                vm.notice?.let { FitLogNotice(stringResource(it)) }
                if (vm.requiresManualSave) FitLogNotice(stringResource(R.string.recovery_manual_save))
                if (wrongFolder) FitLogNotice(stringResource(R.string.editor_wrong_folder), error = true)
                if (vm.conflict) Row(Modifier.horizontalScroll(rememberScrollState())) {
                    Action(R.string.editor_reload) { vm.requestReload() }
                    Action(R.string.editor_copy) { vm.saveCopy() }
                }
            }
            BasicTextField(state = vm.text, readOnly = !vm.canEdit,
                inputTransformation = inputTransformation, scrollState = editorScroll,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.large).padding(16.dp)
                    .focusRequester(editorFocus).semantics { contentDescription = editorLabel },
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface))
            HorizontalFloatingToolbar(expanded = true,
                colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
                modifier = Modifier.align(Alignment.CenterHorizontally)) {
                ToolAction(R.drawable.undo_24px, R.string.editor_undo, vm.canFormat && vm.text.undoState.canUndo) { vm.text.undoState.undo() }
                ToolAction(R.drawable.redo_24px, R.string.editor_redo, vm.canFormat && vm.text.undoState.canRedo) { vm.text.undoState.redo() }
                Box {
                    ToolAction(R.drawable.format_24px, R.string.editor_format, vm.canFormat) { formatMenu = true }
                    DropdownMenu(expanded = formatMenu, onDismissRequest = { formatMenu = false }) {
                        fun apply(command: (String, TextRange) -> MarkdownEditCommands.Edit) {
                            vm.edit(command(vm.text.text.toString(), vm.text.selection)); formatMenu = false
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
                            linkSource = vm.text.text.toString(); linkSelection = vm.text.selection
                            label = linkSource.substring(vm.text.selection.min, vm.text.selection.max)
                            address = ""; formatMenu = false
                        })
                    }
                }
                ToolAction(R.drawable.export_24px, R.string.recovery_export, !vm.loading && vm.recovery == null) {
                    vm.prepareExport(export::launch)
                }
            }
        }
    }
    linkSelection?.let { selection ->
        AlertDialog(onDismissRequest = { linkSelection = null }, title = { Text(stringResource(R.string.editor_link)) }, text = {
            Column {
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text(stringResource(R.string.editor_label)) }, singleLine = true)
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text(stringResource(R.string.editor_address)) }, singleLine = true)
            }
        }, confirmButton = { Action(R.string.editor_confirm, address.isNotBlank() && vm.text.text.toString() == linkSource) {
            vm.edit(MarkdownEditCommands.link(selection, label, address.trim())); linkSelection = null
        } }, dismissButton = { Action(R.string.editor_cancel) { linkSelection = null } })
    }
    if (vm.recovery != null) AlertDialog(onDismissRequest = {}, title = { Text(stringResource(R.string.editor_recover)) },
        text = { Text(stringResource(R.string.editor_draft_found)) },
        confirmButton = { Action(R.string.editor_recover) { vm.restoreDraft() } },
        dismissButton = { Action(R.string.editor_discard) { vm.discardDraft() } })
    else if (vm.collision != null) AlertDialog(onDismissRequest = vm::dismissCollision,
        text = { Text(stringResource(R.string.editor_name_collision)) },
        confirmButton = { Action(R.string.editor_open_existing) { vm.openCollision() } },
        dismissButton = { Action(R.string.editor_cancel) { vm.dismissCollision() } })
    if (vm.exitRequested) AlertDialog(onDismissRequest = vm::cancelExit, title = { Text(stringResource(R.string.editor_leave)) },
        text = { Text(stringResource(R.string.editor_leave_message)) },
        confirmButton = { Action(R.string.editor_save_exit, !vm.closing) { vm.leave(false, onBack) } },
        dismissButton = { Column {
            Action(R.string.editor_draft_exit, !vm.closing) { vm.leave(true, onBack) }
            Action(R.string.editor_cancel) { vm.cancelExit() }
        } })
    if (vm.reloadRequested) AlertDialog(onDismissRequest = vm::cancelReload,
        text = { Text(stringResource(R.string.editor_reload_message)) },
        confirmButton = { Action(R.string.editor_confirm) { vm.reload() } },
        dismissButton = { Action(R.string.editor_cancel) { vm.cancelReload() } })
}
