package com.example.fitlog.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.fitlog.R

/** Editorial heading for scrolling pages; the app Scaffold owns the system insets. */
@Composable
internal fun FitLogPageHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backEnabled: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack, enabled = backEnabled,
                shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.cd_back))
            }
            Spacer(Modifier.weight(1f))
            actions()
        }
        Text(title, style = MaterialTheme.typography.headlineLargeEmphasized,
            modifier = Modifier.semantics { heading() })
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun FitLogSectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMediumEmphasized,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp).semantics { heading() })
}

@Composable
internal fun FitLogNotice(text: String, modifier: Modifier = Modifier, error: Boolean = false) {
    Surface(
        modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(if (error) R.drawable.warning_24px else R.drawable.list_alt_24px), null)
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
}
