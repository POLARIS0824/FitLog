package com.example.fitlog.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SubpageIndicator() {
    CircularWavyProgressIndicator(
        modifier = Modifier
            .padding(start = 12.dp)
            .size(32.dp),
    )
}