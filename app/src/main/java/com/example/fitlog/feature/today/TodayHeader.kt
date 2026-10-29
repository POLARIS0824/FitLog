package com.example.fitlog.feature.today

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.example.fitlog.R
import com.example.fitlog.ui.components.FitLogTopAppBar

@Composable
internal fun TodayHeader(onSettings: () -> Unit, date: String, scrollBehavior: TopAppBarScrollBehavior, maxContentWidth: Dp) {
    FitLogTopAppBar(stringResource(R.string.today_title), scrollBehavior, editorial = true, subtitle = date, maxContentWidth = maxContentWidth,
        actions = {
            FilledTonalIconButton(onClick = onSettings, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(R.drawable.settings_24px), stringResource(R.string.settings_title))
            }
        })
}
