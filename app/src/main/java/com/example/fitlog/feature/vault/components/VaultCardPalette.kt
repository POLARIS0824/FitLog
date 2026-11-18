package com.example.fitlog.feature.vault.components

import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.fitlog.ui.theme.FitLogContainment

/**
 * Color roles of a vault card. A pending choice is the user's selection (`secondaryContainer`);
 * the current folder is a plain filled card whose label is a status (`tertiaryContainer`). The
 * readable-status tile is a nested tile on either card. The screen's one strong fill stays the
 * primary action button.
 */
internal data class VaultCardPalette(
    val container: Color,
    val content: Color,
    val pillContainer: Color,
    val pillContent: Color,
    val chipContainer: Color,
    val chipContent: Color,
)

@Composable
internal fun vaultCardPalette(pending: Boolean): VaultCardPalette {
    val c = MaterialTheme.colorScheme
    return if (pending) {
        VaultCardPalette(
            container = c.secondaryContainer,
            content = c.onSecondaryContainer,
            pillContainer = c.secondary,
            pillContent = c.onSecondary,
            chipContainer = FitLogContainment.nestedColor,
            chipContent = c.onSurface,
        )
    } else {
        val card = CardDefaults.cardColors()
        VaultCardPalette(
            container = card.containerColor,
            content = card.contentColor,
            pillContainer = c.tertiaryContainer,
            pillContent = c.onTertiaryContainer,
            chipContainer = FitLogContainment.nestedColor,
            chipContent = c.onSurface,
        )
    }
}
