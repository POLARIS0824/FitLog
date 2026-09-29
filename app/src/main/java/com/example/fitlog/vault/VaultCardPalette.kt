package com.example.fitlog.vault

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.fitlog.data.vault.VaultAccessStatus
import com.example.fitlog.data.vault.VaultFolderInfo
import com.example.fitlog.ui.theme.FitLogTheme

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

// ======================== 配色方案对比 Preview ========================

private val mockSampleFolder = VaultFolderInfo(
    uri = Uri.EMPTY,
    displayName = "FitLog Obsidian Vault",
    accessStatus = VaultAccessStatus.CanCreateFiles,
)

@Preview(name = "1. Primary Tonal", showBackground = true)
@Composable
private fun PreviewPrimaryTonal() {
    FitLogTheme {
        VaultCard(folder = mockSampleFolder, pending = true, createAfterSetup = false, style = VaultCardStyle.PrimaryTonal)
    }
}

@Preview(name = "2. Secondary Tonal", showBackground = true)
@Composable
private fun PreviewSecondaryTonal() {
    FitLogTheme {
        VaultCard(folder = mockSampleFolder, pending = true, createAfterSetup = false, style = VaultCardStyle.SecondaryTonal)
    }
}

@Preview(name = "3. Tertiary Tonal", showBackground = true)
@Composable
private fun PreviewTertiaryTonal() {
    FitLogTheme {
        VaultCard(folder = mockSampleFolder, pending = true, createAfterSetup = false, style = VaultCardStyle.TertiaryTonal)
    }
}

@Preview(name = "4. State Aware (Pending)", showBackground = true)
@Composable
private fun PreviewStateAwarePending() {
    FitLogTheme {
        VaultCard(folder = mockSampleFolder, pending = true, createAfterSetup = false, style = VaultCardStyle.StateAware)
    }
}

@Preview(name = "4. State Aware (Current)", showBackground = true)
@Composable
private fun PreviewStateAwareCurrent() {
    FitLogTheme {
        VaultCard(folder = mockSampleFolder, pending = false, createAfterSetup = false, style = VaultCardStyle.StateAware)
    }
}

@Preview(name = "5. Outlined (Pending)", showBackground = true)
@Composable
private fun PreviewOutlinedPending() {
    FitLogTheme {
        VaultCard(folder = mockSampleFolder, pending = true, createAfterSetup = false, style = VaultCardStyle.Outlined)
    }
}

@Preview(name = "5. Outlined (Current)", showBackground = true)
@Composable
private fun PreviewOutlinedCurrent() {
    FitLogTheme {
        VaultCard(folder = mockSampleFolder, pending = false, createAfterSetup = false, style = VaultCardStyle.Outlined)
    }
}

@Preview(name = "All 5 Styles Gallery", showBackground = true, heightDp = 1600)
@Composable
private fun PreviewAllStylesGallery() {
    FitLogTheme {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            VaultCardStyle.entries.forEach { style ->
                Text(
                    text = "方案: ${style.name}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                VaultCard(
                    folder = mockSampleFolder,
                    pending = true,
                    createAfterSetup = false,
                    style = style,
                )
            }
        }
    }
}
