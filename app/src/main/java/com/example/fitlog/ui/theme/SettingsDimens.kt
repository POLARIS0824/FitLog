package com.example.fitlog.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Settings, AI settings and appearance. */
@Immutable
class SettingsDimens {
    /** Round container behind an AI connection's icon; matches the settings entry icon box. */
    val connectionIconContainer: Dp = 48.dp

    /** A provider logo drawn inside [connectionIconContainer]. */
    val connectionLogoSize: Dp = 32.dp
}
