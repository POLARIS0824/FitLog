package com.example.fitlog.feature.settings

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.fitlog.data.settings.AppearancePreferences
import com.example.fitlog.ui.preview.FitLogPreview
import com.example.fitlog.ui.preview.FitLogPreviews

/** Small-window and large-text checks alongside the shared bilingual light/dark previews. */
@FitLogPreviews
@Preview(name = "English · 320dp · 200%", locale = "en", widthDp = 320, heightDp = 740, fontScale = 2f,
    showBackground = true)
@Preview(name = "中文 · 320dp · 200% · 深色", locale = "zh", widthDp = 320, heightDp = 740, fontScale = 2f,
    uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
internal fun SettingsUiReviewAppearance() {
    FitLogPreview {
        AppearanceContent(AppearancePreferences(), true, false, null, {}, {}, {}, {}, dynamicColorSupported = true)
    }
}
