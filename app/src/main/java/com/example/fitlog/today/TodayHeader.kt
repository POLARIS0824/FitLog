package com.example.fitlog.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.fitlog.R

@Composable
internal fun TodayHeader(onSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.today_title), modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineLargeEmphasized)
        FilledTonalIconButton(onClick = onSettings, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(R.drawable.settings_24px), contentDescription = stringResource(R.string.settings_title))
        }
    }
}

