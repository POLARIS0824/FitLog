package com.example.fitlog.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.fitlog.feature.today.TodayScreen

@Preview(locale = "en", widthDp = 412, heightDp = 892, showBackground = true)
@Composable
private fun TodayPreview() {
    FitLogPreview { TodayScreen({}, {}, {}, {}) }
}
