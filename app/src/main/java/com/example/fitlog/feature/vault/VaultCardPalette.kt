package com.example.fitlog.feature.vault

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 全局卡片配色切换锚点。
 * 在此处修改枚举值即可切换全局卡片风格。
 */
var ActiveVaultCardStyle: VaultCardStyle = VaultCardStyle.PrimaryTonal

enum class VaultCardStyle {
    PrimaryTonal,
    SecondaryTonal,
    TertiaryTonal,
    StateAware,
    Outlined,
}

data class VaultCardPalette(
    val container: Color,
    val content: Color,
    val pillContainer: Color,
    val pillContent: Color,
    val chipContainer: Color,
    val chipContent: Color,
    val border: BorderStroke? = null,
)

@Composable
fun vaultCardPalette(
    pending: Boolean,
    style: VaultCardStyle = ActiveVaultCardStyle,
): VaultCardPalette {
    val c = MaterialTheme.colorScheme
    return when (style) {
        VaultCardStyle.PrimaryTonal -> VaultCardPalette(
            container = c.primaryContainer,
            content = c.onPrimaryContainer,
            pillContainer = c.primary,
            pillContent = c.onPrimary,
            chipContainer = c.surface,
            chipContent = c.onSurface,
        )

        VaultCardStyle.SecondaryTonal -> VaultCardPalette(
            container = c.secondaryContainer,
            content = c.onSecondaryContainer,
            pillContainer = c.secondary,
            pillContent = c.onSecondary,
            chipContainer = c.surface,
            chipContent = c.onSurface,
        )

        VaultCardStyle.TertiaryTonal -> VaultCardPalette(
            container = c.tertiaryContainer,
            content = c.onTertiaryContainer,
            pillContainer = c.tertiary,
            pillContent = c.onTertiary,
            chipContainer = c.surface,
            chipContent = c.onSurface,
        )

        VaultCardStyle.StateAware -> if (pending) {
            VaultCardPalette(
                container = c.primaryContainer,
                content = c.onPrimaryContainer,
                pillContainer = c.primary,
                pillContent = c.onPrimary,
                chipContainer = c.surface,
                chipContent = c.onSurface,
            )
        } else {
            VaultCardPalette(
                container = c.surfaceContainerHigh,
                content = c.onSurface,
                pillContainer = c.secondaryContainer,
                pillContent = c.onSecondaryContainer,
                chipContainer = c.surfaceContainerHighest,
                chipContent = c.onSurface,
            )
        }

        VaultCardStyle.Outlined -> VaultCardPalette(
            container = c.surface,
            content = c.onSurface,
            pillContainer = if (pending) c.primary else c.secondaryContainer,
            pillContent = if (pending) c.onPrimary else c.onSecondaryContainer,
            chipContainer = c.surfaceContainerHighest,
            chipContent = c.onSurface,
            border = BorderStroke(
                width = if (pending) 2.dp else 1.dp,
                color = if (pending) c.primary else c.outlineVariant,
            ),
        )
    }
}
