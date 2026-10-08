package com.example.fitlog.ui.preview

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.fitlog.ui.theme.FitLogTheme

/** Common phone configurations for page previews, with deterministic theme colors. */
@Preview(name = "English · Light", locale = "en", widthDp = 412, heightDp = 892, showBackground = true)
@Preview(name = "English · Dark", locale = "en", widthDp = 412, heightDp = 892, showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "中文 · 浅色", locale = "zh", widthDp = 412, heightDp = 892, showBackground = true)
@Preview(name = "中文 · 深色", locale = "zh", widthDp = 412, heightDp = 892, showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
annotation class FitLogPreviews

@Composable
internal fun FitLogPreview(content: @Composable () -> Unit) {
    FitLogTheme(dynamicColor = false) {
        Surface(Modifier.fillMaxSize(), content = content)
    }
}
