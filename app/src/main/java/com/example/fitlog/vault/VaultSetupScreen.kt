package com.example.fitlog.vault

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fitlog.R

@Composable
fun VaultSetupScreen(
    onVaultSelected: (Uri) -> Unit,
    busy: Boolean,
    saving: Boolean,
    errorResId: Int?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    var errorMessageResId by remember {
        mutableStateOf<Int?>(null)
    }

    val vaultPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }

        val flags =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        runCatching {
            try {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: SecurityException) {
                // Read-only providers can still be connected for browsing.
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }.onSuccess {
            errorMessageResId = null
            onVaultSelected(uri)
        }.onFailure {
            errorMessageResId = R.string.vault_setup_error_access_failed
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.vault_setup_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        Text(
            text = stringResource(R.string.vault_setup_description),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )

        Button(
            onClick = {
                vaultPicker.launch(null)
            },
            enabled = !busy,
            modifier = Modifier.padding(top = 24.dp),
        ) {
            Text(stringResource(
                if (saving) R.string.vault_setup_saving
                else if (busy) R.string.vault_setup_checking
                else R.string.vault_setup_btn_choose
            ))
        }

        (errorMessageResId ?: errorResId)?.let { resId ->
            Text(
                text = stringResource(resId),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
