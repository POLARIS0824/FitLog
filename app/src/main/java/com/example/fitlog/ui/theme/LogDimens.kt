package com.example.fitlog.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Log list, analysis panel and batch review. */
@Immutable
class LogDimens {
    /** Folder illustration in the empty Markdown state. */
    val emptyIllustrationSize: Dp = 48.dp

    /** Status glyph inline with a list item's supporting text; matches its line height. */
    val inlineStatusIconSize: Dp = 16.dp
}
