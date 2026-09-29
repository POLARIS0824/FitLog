package com.example.fitlog.log

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.fitlog.R
import com.example.fitlog.data.vault.*
import kotlinx.coroutines.CancellationException

@Composable
fun LogScreen(preferences: VaultPreferences, documents: MarkdownDocuments,
              onOpen: (String, String) -> Unit, onConnect: () -> Unit, modifier: Modifier = Modifier) {
    var refresh by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var vault by remember { mutableStateOf<String?>(null) }
    var scan by remember { mutableStateOf<MarkdownScan?>(null) }
    var error by remember { mutableStateOf<Int?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    LaunchedEffect(refresh) {
        loading = true; scan = null; error = null; vault = null
        try {
            when (val config = preferences.getVaultConfig()) {
                is VaultConfigState.Configured -> { vault = config.uri.toString(); scan = documents.scan(config.uri.toString()) }
                is VaultConfigState.Failed -> error = R.string.vault_error_load_config_failed
                else -> Unit
            }
        } catch (e: Exception) { if (e is CancellationException) throw e; error = R.string.log_failed }
        finally { loading = false }
    }
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row {
            TextButton(onClick = { refresh++ }, enabled = !loading) { Text(stringResource(R.string.log_refresh)) }
            TextButton(onClick = onConnect) { Text(stringResource(R.string.log_connect)) }
        }
        if (loading) CircularProgressIndicator()
        error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        if (!loading && error == null && vault == null) Text(stringResource(R.string.log_no_vault))
        scan?.let { result ->
            if (result.partial) Text(stringResource(R.string.log_partial))
            if (result.files.isEmpty()) Text(stringResource(R.string.log_empty))
            LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {
                items(result.files, key = { it.uri }) { file ->
                    ListItem(headlineContent = { Text(file.name) }, supportingContent = { Text(file.path) },
                        modifier = Modifier.clickable { vault?.let { onOpen(it, file.uri) } })
                }
            }
        }
    }
}
