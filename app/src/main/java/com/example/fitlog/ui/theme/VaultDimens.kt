package com.example.fitlog.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Vault setup and management. */
@Immutable
class VaultDimens {
    /** Progress indicator reserved next to the primary setup button label. */
    val buttonProgressSize: Dp = 16.dp

    /** Initial scale of the setup body when its state changes (spatial spring from the theme). */
    val setupBodyEnterScale: Float = 0.92f
}
