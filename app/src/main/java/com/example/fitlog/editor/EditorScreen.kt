@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.fitlog.editor

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

@Composable
private fun Action(id: Int, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled) { Text(stringResource(id)) }
}

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
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            if (uri.toString() != vm.route.vault) wrongFolder = true
            else try {
                context.contentResolver.takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                if (!vm.writable) vm.retryLoad() else vm.saveNow()
            } catch (_: SecurityException) { wrongFolder = true }
        }
    }
    BackHandler { vm.requestExit(onBack) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { if (!vm.loading && vm.recovery == null) vm.onBackground() }
    Column(modifier.fillMaxSize().imePadding().padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Action(R.string.cd_back) { vm.requestExit(onBack) }
            Action(R.string.editor_save, vm.canEdit && vm.state != EditorSaveState.Saving) { vm.saveNow() }
        }
        Text(vm.name, style = MaterialTheme.typography.titleLarge)
        Text(stringResource(when {
            vm.loading -> R.string.editor_loading
            !vm.writable -> R.string.editor_read_only
            else -> when (vm.state) {
                EditorSaveState.Unsaved -> R.string.editor_unsaved
                EditorSaveState.Saving -> R.string.editor_saving
                EditorSaveState.Saved -> R.string.editor_saved
                EditorSaveState.Failed -> R.string.editor_failed
            }
        }))
        vm.error?.let { error ->
            Text(stringResource(error), color = MaterialTheme.colorScheme.error)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                Action(R.string.log_retry) { if (!vm.writable) vm.retryLoad() else vm.saveNow() }
                Action(R.string.editor_reauthorize) { permission.launch(Uri.parse(vm.route.vault)) }
            }
        }
        if (wrongFolder) Text(stringResource(R.string.editor_wrong_folder), color = MaterialTheme.colorScheme.error)
        if (vm.conflict) Row(Modifier.horizontalScroll(rememberScrollState())) {
            Action(R.string.editor_reload) { vm.requestReload() }
            Action(R.string.editor_copy) { vm.saveCopy() }
        }
        BasicTextField(state = vm.text, readOnly = !vm.canEdit,
            inputTransformation = inputTransformation, scrollState = editorScroll,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp)
                .focusRequester(editorFocus).semantics { contentDescription = editorLabel },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Action(R.string.editor_undo, vm.canFormat && vm.text.undoState.canUndo) { vm.text.undoState.undo() }
            Action(R.string.editor_redo, vm.canFormat && vm.text.undoState.canRedo) { vm.text.undoState.redo() }
            Box {
                Action(R.string.editor_format, vm.canFormat) { formatMenu = true }
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
