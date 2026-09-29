@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.example.fitlog.vault

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fitlog.R
import com.example.fitlog.data.vault.VaultAccessStatus
import com.example.fitlog.data.vault.VaultFolderInfo
import com.example.fitlog.data.vault.VaultPreferences
import com.example.fitlog.data.vault.VaultRepository
import com.example.fitlog.navigation.FitLogRoute
import com.example.fitlog.ui.theme.FitLogTheme

/**
 * 页面接入层：负责系统选择器、生命周期感知的状态收集与导航衔接。
 */
@Composable
fun VaultSetupRoute(
    route: FitLogRoute.VaultSetup,
    vaultPreferences: VaultPreferences,
    vaultRepository: VaultRepository,
    onSetupCompleted: (FitLogRoute.VaultSetup) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: VaultSetupViewModel = viewModel(
        factory = VaultSetupViewModel.Factory(
            createAfterSetup = route.createAfterSetup,
            requestId = route.requestId,
            vaultPreferences = vaultPreferences,
            vaultRepository = vaultRepository,
        )
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val vaultPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        viewModel.onFolderPicked(uri)
    }

    LaunchedEffect(uiState.completedResult) {
        val result = uiState.completedResult ?: return@LaunchedEffect
        if (result.requestId == route.requestId) {
            viewModel.onCompletionConsumed()
            onSetupCompleted(route)
        }
    }

    BackHandler {
        if (viewModel.onBack()) {
            onBack()
        }
    }

    VaultSetupScreen(
        uiState = uiState,
        onChooseFolderClick = {
            viewModel.onSelectingFolder()
            vaultPicker.launch(null)
        },
        onConfirmClick = viewModel::confirm,
        onRetryClick = viewModel::retry,
        modifier = modifier,
    )
}

/** 页面主体内容所处的阶段，用于驱动内容切换动画与底部操作区。 */
private enum class SetupMode { Loading, LoadFailed, Candidate, Current, Empty }

/**
 * 纯展示组件：根据 uiState 显示对应状态内容并分发操作回调。
 *
 * 布局：左对齐的大标题头部 + 可滚动的内容区 + 固定在底部的操作区。
 */
@Composable
fun VaultSetupScreen(
    uiState: VaultSetupUiState,
    onChooseFolderClick: () -> Unit,
    onConfirmClick: () -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme

    val mode = when {
        uiState.configState == VaultConfigUiState.Loading -> SetupMode.Loading
        uiState.configState is VaultConfigUiState.Failed -> SetupMode.LoadFailed
        uiState.hasCandidate -> SetupMode.Candidate
        uiState.currentVault != null -> SetupMode.Current
        else -> SetupMode.Empty
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            SetupHeader()

            Spacer(modifier = Modifier.height(32.dp))

            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    (fadeIn(motion.defaultEffectsSpec()) +
                            scaleIn(initialScale = 0.92f, animationSpec = motion.defaultSpatialSpec())) togetherWith
                            fadeOut(motion.fastEffectsSpec())
                },
                label = "vaultSetupBody",
            ) { target ->
                when (target) {
                    SetupMode.Loading -> LoadingBody()

                    SetupMode.LoadFailed -> MessageBanner(
                        text = stringResource(R.string.vault_error_load_config_failed),
                    )

                    SetupMode.Candidate -> uiState.candidateVault?.let { candidate ->
                        VaultCard(
                            folder = candidate,
                            pending = true,
                            createAfterSetup = uiState.createAfterSetup,
                            previous = uiState.currentVault,
                        )
                    }

                    SetupMode.Current -> uiState.currentVault?.let { current ->
                        VaultCard(
                            folder = current,
                            pending = false,
                            createAfterSetup = uiState.createAfterSetup,
                        )
                    }

                    SetupMode.Empty -> Unit
                }
            }

            uiState.errorResId?.let { resId ->
                Spacer(modifier = Modifier.height(16.dp))
                MessageBanner(
                    text = stringResource(resId),
                    showRetry = uiState.stage == VaultOperationStage.Idle &&
                            (uiState.hasCandidate || uiState.currentVault != null),
                    onRetryClick = onRetryClick,
                )
            }
        }

        if (mode != SetupMode.Loading) {
            Spacer(modifier = Modifier.height(16.dp))
            SetupActions(
                mode = mode,
                uiState = uiState,
                onChooseFolderClick = onChooseFolderClick,
                onConfirmClick = onConfirmClick,
                onRetryClick = onRetryClick,
            )
        }
    }
}

// ======================== 头部 ========================

@Composable
private fun SetupHeader(modifier: Modifier = Modifier) {
    // Expressive 形状：9 瓣 Cookie，作为页面的视觉锚点
    val cookieShape = MaterialShapes.Cookie9Sided.toShape()

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, cookieShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.folder_open_24px),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(44.dp),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.vault_setup_title),
            style = MaterialTheme.typography.headlineLargeEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.vault_setup_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ======================== 内容区 ========================

@Composable
private fun LoadingBody(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LoadingIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.vault_setup_loading_config),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 统一的文件夹卡片：pending = true 表示“待确认”的新选择，false 表示“当前”正在使用的文件夹。 */
@Composable
internal fun VaultCard(
    folder: VaultFolderInfo,
    pending: Boolean,
    createAfterSetup: Boolean,
    modifier: Modifier = Modifier,
    previous: VaultFolderInfo? = null,
    style: VaultCardStyle = ActiveVaultCardStyle,
) {
    val palette = vaultCardPalette(pending = pending, style = style)

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = palette.container,
            contentColor = palette.content,
        ),
        border = palette.border,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                color = palette.pillContainer,
                contentColor = palette.pillContent,
                shape = CircleShape,
            ) {
                Text(
                    text = stringResource(
                        if (pending) R.string.vault_label_pending else R.string.vault_label_current
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            Text(
                text = folder.displayName?.ifBlank { null }
                    ?: stringResource(R.string.vault_folder_unnamed),
                style = MaterialTheme.typography.headlineSmall,
                color = palette.content,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            StatusChip(
                status = folder.accessStatus,
                okContainer = palette.chipContainer,
                okContent = palette.chipContent,
            )

            if (createAfterSetup && folder.accessStatus == VaultAccessStatus.ReadOnly) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        text = stringResource(R.string.vault_setup_read_only_add_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            if (previous != null) {
                HorizontalDivider(color = palette.content.copy(alpha = 0.16f))
                Text(
                    text = stringResource(R.string.vault_label_current) + ": " +
                            (previous.displayName?.ifBlank { null }
                                ?: stringResource(R.string.vault_folder_unnamed)),
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.content.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
    okContainer: Color = MaterialTheme.colorScheme.primaryContainer,
    okContent: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    val container: Color
    val content: Color
    val iconRes: Int = when (status) {
        VaultAccessStatus.CanCreateFiles -> {
            container = okContainer
            content = okContent
            R.drawable.check_24px
        }
        VaultAccessStatus.ReadOnly -> {
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
        shape = CircleShape,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(capabilityStringRes(status)),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** 错误 / 提示横幅，可选带重试按钮。 */
@Composable
private fun MessageBanner(
    text: String,
    modifier: Modifier = Modifier,
    showRetry: Boolean = false,
    onRetryClick: () -> Unit = {},
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    painter = painterResource(R.drawable.warning_24px),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            if (showRetry) {
                TextButton(
                    onClick = onRetryClick,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.vault_setup_btn_retry))
                }
            }
        }
    }
}

// ======================== 底部操作区 ========================

@Composable
private fun SetupActions(
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

/** Expressive 连接式按钮组：主操作略宽，次操作为 Tonal。 */
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

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SetupPrimaryButton(
            text = primaryText,
            onClick = onPrimaryClick,
            enabled = primaryEnabled,
            busy = primaryBusy,
            shape = ButtonGroupDefaults.connectedLeadingButtonShape,
            modifier = Modifier.weight(1.4f),
        )

        FilledTonalButton(
            onClick = onSecondaryClick,
            enabled = secondaryEnabled,
            shape = ButtonGroupDefaults.connectedTrailingButtonShape,
            contentPadding = ButtonDefaults.contentPaddingFor(height),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = height),
        ) {
            Text(
                text = secondaryText,
                style = ButtonDefaults.textStyleFor(height),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 主按钮：Medium 尺寸，忙碌时在文字前显示 Expressive 的 LoadingIndicator。 */
@Composable
private fun SetupPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    shape: androidx.compose.ui.graphics.Shape = ButtonDefaults.shape,
) {
    val height = ButtonDefaults.MediumContainerHeight

    Button(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        contentPadding = ButtonDefaults.contentPaddingFor(height),
        modifier = modifier.heightIn(min = height),
    ) {
        if (busy) {
            LoadingIndicator(
                modifier = Modifier.size(24.dp),
                color = LocalContentColor.current,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = ButtonDefaults.textStyleFor(height),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun capabilityStringRes(status: VaultAccessStatus): Int = when (status) {
    VaultAccessStatus.CanCreateFiles -> R.string.vault_capability_can_create
    VaultAccessStatus.ReadOnly -> R.string.vault_capability_read_only
    VaultAccessStatus.NeedsReauthorization -> R.string.vault_capability_needs_reauthorization
    VaultAccessStatus.DirectoryUnavailable -> R.string.vault_capability_unavailable
    else -> R.string.vault_setup_error_access_failed
}

// ======================== Previews ========================

@Preview(name = "Loading", showBackground = true)
@Composable
private fun VaultSetupLoadingPreview() {
    FitLogTheme {
        VaultSetupScreen(
            uiState = VaultSetupUiState(configState = VaultConfigUiState.Loading),
            onChooseFolderClick = {},
            onConfirmClick = {},
            onRetryClick = {},
        )
    }
}

@Preview(name = "Not Configured", showBackground = true)
@Composable
private fun VaultSetupNotConfiguredPreview() {
    FitLogTheme {
        VaultSetupScreen(
            uiState = VaultSetupUiState(
                configState = VaultConfigUiState.NotConfigured,
            ),
            onChooseFolderClick = {},
            onConfirmClick = {},
            onRetryClick = {},
        )
    }
}

@Preview(name = "Configured - Can Create", showBackground = true)
@Composable
private fun VaultSetupConfiguredPreview() {
    FitLogTheme {
        VaultSetupScreen(
            uiState = VaultSetupUiState(
                configState = VaultConfigUiState.Configured(
                    VaultFolderInfo(
                        uri = Uri.EMPTY,
                        displayName = "My Workouts",
                        accessStatus = VaultAccessStatus.CanCreateFiles,
                    )
                ),
            ),
            onChooseFolderClick = {},
            onConfirmClick = {},
            onRetryClick = {},
        )
    }
}

@Preview(name = "Candidate Selected - Pending Connect", showBackground = true)
@Composable
private fun VaultSetupCandidatePreview() {
    FitLogTheme {
        VaultSetupScreen(
            uiState = VaultSetupUiState(
                configState = VaultConfigUiState.Configured(
                    VaultFolderInfo(
                        uri = Uri.EMPTY,
                        displayName = "Old Vault",
                        accessStatus = VaultAccessStatus.CanCreateFiles,
                    )
                ),
                candidateVault = VaultFolderInfo(
                    uri = Uri.EMPTY,
                    displayName = "New Obsidian Notes",
                    accessStatus = VaultAccessStatus.CanCreateFiles,
                ),
            ),
            onChooseFolderClick = {},
            onConfirmClick = {},
            onRetryClick = {},
        )
    }
}

@Preview(name = "Candidate ReadOnly for Add", showBackground = true)
@Composable
private fun VaultSetupReadOnlyAddPreview() {
    FitLogTheme {
        VaultSetupScreen(
            uiState = VaultSetupUiState(
                createAfterSetup = true,
                configState = VaultConfigUiState.NotConfigured,
                candidateVault = VaultFolderInfo(
                    uri = Uri.EMPTY,
                    displayName = "Read-Only Archive",
                    accessStatus = VaultAccessStatus.ReadOnly,
                ),
                errorResId = R.string.vault_error_read_only,
            ),
            onChooseFolderClick = {},
            onConfirmClick = {},
            onRetryClick = {},
        )
    }
}

@Preview(name = "Checking Folder", showBackground = true)
@Composable
private fun VaultSetupCheckingPreview() {
    FitLogTheme {
        VaultSetupScreen(
            uiState = VaultSetupUiState(
                stage = VaultOperationStage.Checking,
                candidateVault = VaultFolderInfo(
                    uri = Uri.EMPTY,
                    displayName = "Fitness Logs",
                    accessStatus = VaultAccessStatus.CanCreateFiles,
                ),
            ),
            onChooseFolderClick = {},
            onConfirmClick = {},
            onRetryClick = {},
        )
    }
}

@Preview(name = "Saving Folder", showBackground = true)
@Composable
private fun VaultSetupSavingPreview() {
    FitLogTheme {
        VaultSetupScreen(
            uiState = VaultSetupUiState(
                stage = VaultOperationStage.Saving,
                candidateVault = VaultFolderInfo(
                    uri = Uri.EMPTY,
                    displayName = "Fitness Logs",
                    accessStatus = VaultAccessStatus.CanCreateFiles,
                ),
            ),
            onChooseFolderClick = {},
            onConfirmClick = {},
            onRetryClick = {},
        )
    }
}

@Preview(name = "Save Error - Retry Available", showBackground = true)
@Composable
private fun VaultSetupErrorPreview() {
    FitLogTheme {
        VaultSetupScreen(
            uiState = VaultSetupUiState(
                candidateVault = VaultFolderInfo(
                    uri = Uri.EMPTY,
                    displayName = "Fitness Logs",
                    accessStatus = VaultAccessStatus.CanCreateFiles,
                ),
                errorResId = R.string.vault_error_save_config_failed,
            ),
            onChooseFolderClick = {},
            onConfirmClick = {},
            onRetryClick = {},
        )
    }
}