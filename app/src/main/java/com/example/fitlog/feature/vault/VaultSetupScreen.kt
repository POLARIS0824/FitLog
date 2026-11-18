@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.example.fitlog.feature.vault

import com.example.fitlog.feature.vault.components.LoadingBody
import com.example.fitlog.feature.vault.components.MessageBanner
import com.example.fitlog.feature.vault.components.SetupActions
import com.example.fitlog.feature.vault.components.SetupHeader
import com.example.fitlog.feature.vault.components.SetupMode
import com.example.fitlog.feature.vault.components.VaultCard
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fitlog.R
import com.example.fitlog.feature.vault.navigation.VaultSetupNavKey
import com.example.fitlog.ui.components.FitLogBackGuard
import com.example.fitlog.ui.theme.FitLogTheme
import com.example.fitlog.ui.theme.fitLogPagePadding

/**
 * 页面接入层：负责系统选择器、生命周期感知的状态收集与导航衔接。
 */
@Composable
fun VaultSetupRoute(
    route: VaultSetupNavKey,
    factory: androidx.lifecycle.ViewModelProvider.Factory,
    onSetupCompleted: (VaultSetupNavKey) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onVaultConnected: suspend (String) -> Unit = {},
) {
    val viewModel: VaultSetupViewModel = viewModel(
        factory = factory
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
            uiState.targetVault?.uri?.toString()?.let { onVaultConnected(it) }
            viewModel.onCompletionConsumed()
            onSetupCompleted(route)
        }
    }

    FitLogBackGuard(enabled = uiState.configState is VaultConfigUiState.Loading || uiState.stage != VaultOperationStage.Idle) {
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
    val spacing = FitLogTheme.spacing
    val enterScale = FitLogTheme.dimens.vault.setupBodyEnterScale

    val mode = when {
        uiState.configState == VaultConfigUiState.Loading -> SetupMode.Loading
        uiState.configState is VaultConfigUiState.Failed -> SetupMode.LoadFailed
        uiState.hasCandidate -> SetupMode.Candidate
        uiState.currentVault != null -> SetupMode.Current
        else -> SetupMode.Empty
    }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = FitLogTheme.dimens.page.contentWidth)
                .fillMaxSize()
                .padding(horizontal = fitLogPagePadding(), vertical = spacing.xl),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                SetupHeader()

                Spacer(modifier = Modifier.height(spacing.xxl))

                AnimatedContent(
                    targetState = mode,
                    transitionSpec = {
                        (fadeIn(motion.defaultEffectsSpec()) +
                                scaleIn(initialScale = enterScale, animationSpec = motion.defaultSpatialSpec())) togetherWith
                                fadeOut(motion.fastEffectsSpec())
                    },
                    label = "vaultSetupBody",
                ) { target ->
                    when (target) {
                        SetupMode.Loading -> LoadingBody(loading = mode == SetupMode.Loading)

                        SetupMode.LoadFailed -> MessageBanner(
                            text = stringResource(R.string.vault_error_load_config_failed),
                        )

                        SetupMode.Candidate -> uiState.candidateVault?.let { candidate ->
                            VaultCard(
                                folder = candidate,
                                pending = true,
                                previous = uiState.currentVault,
                            )
                        }

                        SetupMode.Current -> uiState.currentVault?.let { current ->
                            VaultCard(
                                folder = current,
                                pending = false,
                            )
                        }

                        SetupMode.Empty -> Unit
                    }
                }

                uiState.errorResId?.let { resId ->
                    Spacer(modifier = Modifier.height(spacing.l))
                    MessageBanner(
                        text = stringResource(resId),
                        showRetry = uiState.stage == VaultOperationStage.Idle &&
                                (uiState.hasCandidate || uiState.currentVault != null),
                        onRetryClick = onRetryClick,
                    )
                }
            }

            if (mode != SetupMode.Loading) {
                Spacer(modifier = Modifier.height(spacing.l))
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
}
