@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.example.fitlog.feature.vault.components

import com.example.fitlog.feature.vault.VaultSetupUiState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import com.example.fitlog.R
import com.example.fitlog.data.vault.VaultAccessStatus
import com.example.fitlog.data.vault.VaultFolderInfo
import com.example.fitlog.ui.components.FitLogLoadingIndicator
import com.example.fitlog.ui.theme.FitLogContainment
import com.example.fitlog.ui.theme.FitLogTheme

internal enum class SetupMode { Loading, LoadFailed, Candidate, Current, Empty }

// ======================== 头部 ========================

@Composable
internal fun SetupHeader(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.vault_setup_title),
            style = MaterialTheme.typography.headlineLargeEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )

        Spacer(modifier = Modifier.height(FitLogTheme.spacing.m))

        Text(
            text = stringResource(R.string.vault_setup_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ======================== 内容区 ========================

@Composable
internal fun LoadingBody(loading: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = FitLogTheme.spacing.xxxl),
        contentAlignment = Alignment.Center,
    ) {
        FitLogLoadingIndicator(loading) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LoadingIndicator()
                Spacer(modifier = Modifier.height(FitLogTheme.spacing.l))
                Text(
                    text = stringResource(R.string.vault_setup_loading_config),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 统一的文件夹卡片：pending = true 表示“待确认”的新选择，false 表示“当前”正在使用的文件夹。 */
@Composable
internal fun VaultCard(
    folder: VaultFolderInfo,
    pending: Boolean,
    modifier: Modifier = Modifier,
    previous: VaultFolderInfo? = null,
) {
    val palette = vaultCardPalette(pending = pending)
    val spacing = FitLogTheme.spacing

    Card(
        colors = CardDefaults.cardColors(
            containerColor = palette.container,
            contentColor = palette.content,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(spacing.l),
            verticalArrangement = Arrangement.spacedBy(spacing.m),
        ) {
            Surface(
                color = palette.pillContainer,
                contentColor = palette.pillContent,
                shape = FitLogContainment.nestedShape,
            ) {
                Text(
                    text = stringResource(
                        if (pending) R.string.vault_label_pending else R.string.vault_label_current
                    ),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    modifier = Modifier.padding(horizontal = spacing.m, vertical = spacing.xs),
                )
            }

            Text(
                text = folder.displayName?.ifBlank { null }
                    ?: stringResource(R.string.vault_folder_unnamed),
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = palette.content,
            )

            StatusChip(
                status = folder.accessStatus,
                okContainer = palette.chipContainer,
                okContent = palette.chipContent,
            )

            if (previous != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = stringResource(R.string.settings_ui_previous_folder,
                        previous.displayName?.ifBlank { null } ?: stringResource(R.string.vault_folder_unnamed)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.content,
                )
            }
        }
    }
}

/** 权限状态胶囊：颜色 + 图标/字符占位 + 文案。 */
@Composable
internal fun StatusChip(
    status: VaultAccessStatus,
    modifier: Modifier = Modifier,
    okContainer: Color = FitLogContainment.nestedColor,
    okContent: Color = MaterialTheme.colorScheme.onSurface,
) {
    val container: Color
    val content: Color
    val iconRes: Int = when (status) {
        VaultAccessStatus.Readable -> {
            container = okContainer
            content = okContent
            R.drawable.lock_24px
        }
        else -> {
            container = MaterialTheme.colorScheme.errorContainer
            content = MaterialTheme.colorScheme.onErrorContainer
            R.drawable.warning_24px
        }
    }

    Surface(
        color = container,
        contentColor = content,
        shape = FitLogContainment.nestedShape,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = AssistChipDefaults.Height)
                .padding(start = FitLogTheme.spacing.s, end = FitLogTheme.spacing.l),
            horizontalArrangement = Arrangement.spacedBy(FitLogTheme.spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
            Text(
                text = stringResource(capabilityStringRes(status)),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** 错误 / 提示横幅，可选带重试按钮。 */
@Composable
internal fun MessageBanner(
    text: String,
    modifier: Modifier = Modifier,
    showRetry: Boolean = false,
    onRetryClick: () -> Unit = {},
) {
    // An error status card: errorContainer has a job here; the shape stays the card default.
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(FitLogTheme.spacing.l)) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(FitLogTheme.spacing.m),
            ) {
                Icon(
                    painter = painterResource(R.drawable.warning_24px),
                    contentDescription = null,
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            if (showRetry) {
                TextButton(
                    onClick = onRetryClick,
                    modifier = Modifier.align(Alignment.End), shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer),
                ) {
                    Text(stringResource(R.string.vault_setup_btn_retry))
                }
            }
        }
    }
}

// ======================== 底部操作区 ========================

@Composable
internal fun SetupActions(
    mode: SetupMode,
    uiState: VaultSetupUiState,
    onChooseFolderClick: () -> Unit,
    onConfirmClick: () -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val idle = !uiState.isSaving && !uiState.isChecking

    when (mode) {
        SetupMode.Loading -> Unit

        SetupMode.LoadFailed -> SetupPrimaryButton(
            text = stringResource(R.string.vault_setup_btn_retry),
            onClick = onRetryClick,
            modifier = modifier.fillMaxWidth(),
        )

        SetupMode.Empty -> SetupPrimaryButton(
            text = stringResource(
                if (uiState.isChecking) R.string.vault_setup_checking
                else R.string.vault_setup_btn_choose
            ),
            onClick = onChooseFolderClick,
            enabled = idle,
            busy = uiState.isChecking,
            modifier = modifier.fillMaxWidth(),
        )

        SetupMode.Candidate -> SetupButtonPair(
            primaryText = stringResource(
                if (uiState.isSaving) R.string.vault_setup_saving
                else if (uiState.isChecking) R.string.vault_setup_checking
                else R.string.vault_setup_btn_confirm
            ),
            onPrimaryClick = onConfirmClick,
            primaryEnabled = uiState.canConfirm,
            primaryBusy = !idle,
            secondaryText = stringResource(R.string.vault_setup_btn_reselect),
            onSecondaryClick = onChooseFolderClick,
            secondaryEnabled = idle,
            modifier = modifier,
        )

        SetupMode.Current -> SetupButtonPair(
            primaryText = stringResource(
                if (uiState.isChecking) R.string.vault_setup_checking
                else R.string.vault_setup_btn_continue
            ),
            onPrimaryClick = onConfirmClick,
            primaryEnabled = uiState.canConfirm,
            primaryBusy = uiState.isChecking,
            secondaryText = stringResource(
                if (uiState.currentVault?.accessStatus == VaultAccessStatus.NeedsReauthorization) {
                    R.string.vault_setup_btn_reauthorize
                } else {
                    R.string.vault_setup_btn_change
                }
            ),
            onSecondaryClick = onChooseFolderClick,
            secondaryEnabled = idle,
            modifier = modifier,
        )
    }
}

/** Keep action labels on one line; stack when both measured labels cannot fit. */
@Composable
private fun SetupButtonPair(
    primaryText: String,
    onPrimaryClick: () -> Unit,
    primaryEnabled: Boolean,
    primaryBusy: Boolean,
    secondaryText: String,
    onSecondaryClick: () -> Unit,
    secondaryEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val height = ButtonDefaults.MediumContainerHeight
    val style = ButtonDefaults.textStyleFor(height)
    val padding = ButtonDefaults.contentPaddingFor(height)
    val layoutDirection = LocalLayoutDirection.current
    val horizontalPadding = padding.calculateLeftPadding(layoutDirection) + padding.calculateRightPadding(layoutDirection)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val spacing = FitLogTheme.spacing
    val progressSize = FitLogTheme.dimens.vault.buttonProgressSize
    val primaryMinimum = with(density) { measurer.measure(primaryText, style, maxLines = 1).size.width.toDp() } +
        horizontalPadding + spacing.s + progressSize
    val secondaryMinimum = with(density) { measurer.measure(secondaryText, style, maxLines = 1).size.width.toDp() } +
        horizontalPadding
    val secondary: @Composable (Modifier) -> Unit = { buttonModifier ->
        FilledTonalButton(
            onClick = onSecondaryClick,
            enabled = secondaryEnabled,
            shapes = ButtonDefaults.shapesFor(height),
            contentPadding = padding,
            modifier = buttonModifier.heightIn(min = height),
        ) {
            Text(secondaryText, style = style, maxLines = 1, softWrap = false)
        }
    }
    val primary: @Composable (Modifier) -> Unit = { buttonModifier ->
        SetupPrimaryButton(
            text = primaryText,
            onClick = onPrimaryClick,
            enabled = primaryEnabled,
            busy = primaryBusy,
            modifier = buttonModifier,
        )
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth < primaryMinimum + secondaryMinimum + spacing.s) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                primary(Modifier.fillMaxWidth())
                secondary(Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.s), verticalAlignment = Alignment.CenterVertically) {
                secondary(Modifier.width(secondaryMinimum))
                primary(Modifier.weight(1f))
            }
        }
    }
}

/** Keep space for progress feedback so the label remains readable while saving. */
@Composable
private fun SetupPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    val height = ButtonDefaults.MediumContainerHeight
    val progressSize = FitLogTheme.dimens.vault.buttonProgressSize

    Button(
        onClick = onClick,
        enabled = enabled,
        shapes = ButtonDefaults.shapesFor(height),
        contentPadding = ButtonDefaults.contentPaddingFor(height),
        modifier = modifier.heightIn(min = height),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FitLogTheme.spacing.s, Alignment.CenterHorizontally)) {
            Text(
                text = text,
                style = ButtonDefaults.textStyleFor(height),
                maxLines = 1,
                softWrap = false,
            )
            Box(Modifier.size(progressSize), contentAlignment = Alignment.Center) {
                FitLogLoadingIndicator(busy) {
                    LoadingIndicator(Modifier.size(progressSize), color = LocalContentColor.current)
                }
            }
        }
    }
}

private fun capabilityStringRes(status: VaultAccessStatus): Int = when (status) {
    VaultAccessStatus.Readable -> R.string.vault_capability_readable
    VaultAccessStatus.NeedsReauthorization -> R.string.vault_capability_needs_reauthorization
    VaultAccessStatus.DirectoryUnavailable -> R.string.vault_capability_unavailable
    else -> R.string.vault_setup_error_access_failed
}
