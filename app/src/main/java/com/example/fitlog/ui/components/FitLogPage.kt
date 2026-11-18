package com.example.fitlog.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.example.fitlog.R
import com.example.fitlog.ui.theme.FitLogTheme

@Composable
internal fun FitLogSectionTitle(text: String) {
    val spacing = FitLogTheme.spacing
    Text(text, style = MaterialTheme.typography.titleMediumEmphasized,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = spacing.xs, top = spacing.s, bottom = spacing.s).semantics { heading() })
}

/** Info notices are low-emphasis outlined cards; errors are a status, so they take the error container. */
@Composable
internal fun FitLogNotice(text: String, modifier: Modifier = Modifier, error: Boolean = false) {
    val spacing = FitLogTheme.spacing
    val body: @Composable ColumnScope.() -> Unit = {
        Row(Modifier.padding(spacing.l), horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
            Icon(painterResource(if (error) R.drawable.warning_24px else R.drawable.list_alt_24px), null,
                tint = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
    if (error) Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ), content = body)
    else OutlinedCard(modifier.fillMaxWidth(), content = body)
}
