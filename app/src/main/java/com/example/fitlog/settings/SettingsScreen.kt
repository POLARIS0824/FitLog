package com.example.fitlog.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fitlog.R
import com.example.fitlog.ui.components.FitLogPageHeader
import com.example.fitlog.ui.components.FitLogSectionTitle

/** Keep the settings overview small; AI configuration has its own form and persistence. */
@Composable
internal fun SettingsScreen(onAiSettings: () -> Unit, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 640.dp).fillMaxSize(),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                FitLogPageHeader(stringResource(R.string.settings_title),
                    stringResource(R.string.settings_description), onBack)
            }
            item {
                FitLogSectionTitle(stringResource(R.string.settings_analysis_heading))
                SegmentedListItem(
                    onClick = onAiSettings,
                    shapes = ListItemDefaults.segmentedShapes(0, 1),
                    leadingContent = {
                        Box(
                            Modifier.size(48.dp).background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.large),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(painterResource(R.drawable.auto_awesome_24px), contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    },
                    supportingContent = {
                        Text(stringResource(R.string.settings_ai_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                ) {
                    Text(stringResource(R.string.ai_settings_title), style = MaterialTheme.typography.titleMediumEmphasized)
                }
            }
        }
    }
}
