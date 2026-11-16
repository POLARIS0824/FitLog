package com.example.fitlog.ui.preview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.fitlog.ui.theme.FitLogTheme

@Composable
internal fun FitLogPreview(content: @Composable () -> Unit) {
    FitLogTheme(dynamicColor = false) {
        Surface(Modifier.fillMaxSize(), content = content)
    }
}
