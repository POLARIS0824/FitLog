package com.example.fitlog.feature.log

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.fitlog.model.source.IndexedScan
import com.example.fitlog.ui.preview.FitLogPreview
import com.example.fitlog.ui.preview.PreviewDiary

@Preview(name = "Markdown · 320dp · 200%", widthDp = 320, heightDp = 1100, fontScale = 2f, locale = "en")
@Composable
internal fun LogLargeTextReviewPreview() {
    FitLogPreview {
        val file = PreviewDiary.file
        LogContent(LogUiState(vault = PreviewDiary.VAULT_URI, files = listOf(file), visibleFiles = listOf(file),
            scanStatus = IndexedScan.COMPLETE, pendingCount = 2, statuses = mapOf(file.path to LogDiaryStatus.PENDING)),
            LogActions(), { _, _ -> }, {}, {})
    }
}
