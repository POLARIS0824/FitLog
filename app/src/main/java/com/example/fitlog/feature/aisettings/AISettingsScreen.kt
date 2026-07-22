package com.example.fitlog.feature.aisettings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Snackbar
import kotlinx.coroutines.delay
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitlog.model.ai.AIProviderConfig
import com.example.fitlog.model.ai.ProviderType
import com.example.fitlog.ui.components.SectionLabel
import com.example.fitlog.ui.components.SettingsCard
import com.example.fitlog.ui.components.SubpageIndicator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

/**
 * 1. 容器层 (Stateful)
 * 绑定 Hilt ViewModel，处理生命周期感知的状态收集。
 */
@Composable
fun AISettingsRoute(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: AISettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AISettingsScreen(
        uiState = uiState,
        onBack = onBack,
        onProviderSelected = viewModel::onProviderSelected,
        onApiKeyChange = viewModel::onApiKeyChange,
        onToggleApiKeyVisibility = viewModel::onToggleApiKeyVisibility,
        onModelChange = viewModel::onModelChange,
        onBaseUrlChange = viewModel::onBaseUrlChange,
        onCustomEndpointChange = viewModel::onCustomEndpointChange,
        onApiVersionChange = viewModel::onApiVersionChange,
        onFetchModels = viewModel::onFetchModels,
        onFetchResultShown = viewModel::onFetchResultShown,
        onTestConnection = viewModel::onTestConnection,
        onTestResultShown = viewModel::onTestResultShown,
        onSave = viewModel::onSave,
        onErrorShown = viewModel::onErrorShown,
        onSuccessShown = viewModel::onSuccessShown,
        modifier = modifier,
    )
}

/**
 * 2. 纯 UI 展示层 (Stateless)
 *
 * 单配置页：页面只服务"当前选中的那一个服务商"，
 * 切换服务商通过底部弹层完成，不加号、无列表。
 *
 * 顶栏为 Google 风格双标题：顶行常驻小标题（父级板块 Settings），大标题置于滚动内容顶部；
 * 滚动时大标题自然没入不透明的顶栏之下，顶行标题按滚动进度交叉淡化为本页标题
 * （pinned TopAppBar + graphicsLayer alpha 联动滚动进度）；
 * 滚动停止在半折叠态时自动吸附到最近的稳定态（对齐 M3 顶栏内置的 snap 行为）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AISettingsScreen(
    uiState: AISettingsUiState,
    onBack: () -> Unit,
    onProviderSelected: (ProviderType) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onToggleApiKeyVisibility: () -> Unit,
    onModelChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onCustomEndpointChange: (String) -> Unit,
    onApiVersionChange: (String) -> Unit,
    onFetchModels: (baseUrl: String, customEndpoint: String?) -> Unit,
    onFetchResultShown: () -> Unit,
    onTestConnection: () -> Unit,
    onTestResultShown: () -> Unit,
    onSave: (AIProviderConfig) -> Unit,
    onErrorShown: () -> Unit,
    onSuccessShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollState = rememberScrollState()
    var showProviderSheet by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val stackedSnackbarHostState = rememberStackedSnackbarHostState()

    val density = LocalDensity.current
    val extraSpacingPx = remember(density) { with(density) { 12.dp.roundToPx() } }

    // 双标题切换进度：0 = 完全展开（显示父级板块标题 Settings），1 = 大标题刚好完全滚入顶栏之下。
    // 使用大标题 Header 容器高度加上底部 12.dp 间距，作为完全滚出顶栏的准确吸附阈值（确保大标题 100% 隐藏无残留）。
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val titleFraction by remember {
        derivedStateOf {
            if (headerHeightPx <= 0) 0f
            else (scrollState.value.toFloat() / headerHeightPx.toFloat()).coerceIn(0f, 1f)
        }
    }

    // 吸附效果：手势/惯性滚动停止后，若大标题处于半折叠的中间态，自动平滑吸附到最近的稳定边界（0 或 headerHeightPx）。
    LaunchedEffect(scrollState, headerHeightPx) {
        snapshotFlow { scrollState.isScrollInProgress }
            .collect { inProgress ->
                if (inProgress) return@collect
                val currentScroll = scrollState.value
                if (headerHeightPx > 0 && currentScroll in 1 until headerHeightPx) {
                    val target = if (currentScroll < headerHeightPx / 2) 0 else headerHeightPx
                    try {
                        scrollState.animateScrollTo(
                            value = target,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    } catch (e: CancellationException) {
                        // 吸附动画被用户新的手势打断，属正常交互
                    }
                }
            }
    }

    val selectedType = uiState.provider.selectedType
    val spec = ProviderSpecs.of(selectedType)

    // 该类型已保存的配置（用于 ProviderCard 展示配置状态）
    val savedConfig = uiState.provider.providers.firstOrNull { it.id == selectedType.name }

    // 顶栏背景色随滚动进度在 surfaceContainerLow (展开) 与 surfaceContainer (折叠) 之间平滑过渡
    val topAppBarContainerColor = androidx.compose.ui.graphics.lerp(
        MaterialTheme.colorScheme.surfaceContainerLow,
        MaterialTheme.colorScheme.surfaceContainer,
        titleFraction
    )

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        snackbarHost = { StackedSnackbarHost(hostState = stackedSnackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    // 双标题 Material 3 轴向共享过渡：
                    // 展开时显示父级板块（Settings），大标题滚出后向上淡出；
                    // 本页标题（AI Configuration）从下方向上淡入顶栏，实现双标题自然切换。
                    Box(contentAlignment = Alignment.CenterStart) {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.graphicsLayer {
                                alpha = 1f - titleFraction
                                translationY = -titleFraction * 12.dp.toPx()
                            },
                        )
                        Text(
                            text = "AI Configuration",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.graphicsLayer {
                                alpha = titleFraction
                                translationY = (1f - titleFraction) * 12.dp.toPx()
                            },
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topAppBarContainerColor,
                    scrolledContainerColor = topAppBarContainerColor,
                ),
                scrollBehavior = scrollBehavior
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                // 点击输入框以外的区域时清除焦点，收起软键盘
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                }
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 大标题 Header：包含 top/bottom 内边距及 12.dp 间距，整体测量高度作为完全滚出顶栏的准确吸附阈值
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 8.dp, bottom = 4.dp)
                    .onSizeChanged { size ->
                        headerHeightPx = size.height + extraSpacingPx
                    }
            ) {
                Text(
                    text = "AI Configuration",
                    style = MaterialTheme.typography.headlineMedium,
                )
            }

            if (uiState.ui.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            SectionLabel("AI Provider")
            ProviderCard(
                spec = spec,
                savedConfig = savedConfig,
                onClick = { showProviderSheet = true },
            )

            SectionLabel("Credentials")
            CredentialsCard(
                spec = spec,
                uiState = uiState,
                baseUrl = uiState.endpoint.baseUrl,
                onBaseUrlChange = onBaseUrlChange,
                customEndpoint = uiState.endpoint.customEndpoint,
                onCustomEndpointChange = onCustomEndpointChange,
                apiVersion = uiState.endpoint.apiVersion,
                onApiVersionChange = onApiVersionChange,
                onApiKeyChange = onApiKeyChange,
                onToggleApiKeyVisibility = onToggleApiKeyVisibility,
            )

            SectionLabel("Model")
            ModelCard(
                spec = spec,
                model = uiState.model,
                apiKeyReady = uiState.apiKey.apiKey.isNotBlank(),
                onModelChange = onModelChange,
                onFetchModels = {
                    onFetchModels(
                        uiState.endpoint.baseUrl,
                        uiState.endpoint.customEndpoint.ifBlank { null },
                    )
                },
            )

            SectionLabel("Test")
            TestCard(
                test = uiState.test,
                formReady = uiState.apiKey.apiKey.isNotBlank() &&
                    uiState.model.selectedModel.isNotBlank() &&
                    uiState.endpoint.baseUrl.isNotBlank(),
                onTest = onTestConnection,
            )

            // 保存按钮在页面底部：provider + 凭据 + 模型是同一条记录，一次保存原子完成
            Button(
                onClick = {
                    onSave(
                        AIProviderConfig(
                            id = selectedType.name,
                            name = spec.displayName,
                            type = selectedType,
                            baseUrl = uiState.endpoint.baseUrl.trim(),
                            apiKey = uiState.apiKey.apiKey.trim(),
                            model = uiState.model.selectedModel.trim(),
                            customEndpoint = uiState.endpoint.customEndpoint.trim().ifBlank { null },
                            apiVersion = uiState.endpoint.apiVersion.trim().ifBlank { null },
                            isPreset = true, // 每类型一条的内置槽位配置
                        )
                    )
                },
                enabled = uiState.apiKey.apiKey.isNotBlank() &&
                    uiState.model.selectedModel.isNotBlank() &&
                    uiState.endpoint.baseUrl.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                Text("保存")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Provider 选择弹层
    if (showProviderSheet) {
        ProviderPickerSheet(
            providers = uiState.provider.providers,
            selectedType = selectedType,
            onSelect = {
                onProviderSelected(it)
                showProviderSheet = false
            },
            onDismiss = { showProviderSheet = false },
        )
    }

    // 模型列表拉取结果：fetchResult 出现时弹出 StackedSnackbar，展示完毕后清除
    LaunchedEffect(uiState.model.fetchResult) {
        uiState.model.fetchResult.takeIf { it.isNotEmpty() }?.let {
            stackedSnackbarHostState.showSnackbar(it)
            onFetchResultShown()
        }
    }

    // 连接测试结果：lastResult 出现时弹出 StackedSnackbar，展示完毕后清除
    LaunchedEffect(uiState.test.lastResult) {
        uiState.test.lastResult.takeIf { it.isNotEmpty() }?.let {
            stackedSnackbarHostState.showSnackbar(it)
            onTestResultShown()
        }
    }

    // 保存成功提示：successMessage 出现时弹出 StackedSnackbar，展示完毕后清除
    LaunchedEffect(uiState.ui.successMessage) {
        uiState.ui.successMessage?.let {
            stackedSnackbarHostState.showSnackbar(it)
            onSuccessShown()
        }
    }

    // 错误提示
    uiState.ui.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = onErrorShown,
            confirmButton = { TextButton(onClick = onErrorShown) { Text("知道了") } },
            title = { Text("出错了") },
            text = { Text(message) },
        )
    }
}

// ──────────────────────────────────────
// 通用小组件
// ──────────────────────────────────────

/**
 * 服务商图标。
 *
 * 优先展示 [ProviderSpec.logoRes] 品牌 logo（导入资源到 `res/drawable` 后
 * 在 [ProviderSpecs] 中填入资源 ID 即全局生效，本函数无需再改）；
 * 未配置 logo 时回退到内置 tonal 图标。
 *
 * 注意：品牌 logo 用 [Image] 原色渲染，**不做 tint**（保持品牌色）。
 */
@Composable
private fun ProviderIcon(spec: ProviderSpec, size: Dp) {
    val logoRes = spec.logoRes
    if (logoRes != null) {
        // 品牌 logo 分支：原图渲染。
        // 品牌 logo 通常撑满整个画布（不像 Material 图标自带内边距），
        // 因此按 0.7 系数缩小渲染，视觉大小与内置图标对齐；想再调就改这个系数。
        // 如需圆形裁切自行追加 .clip(CircleShape)；如需圆形底色自行追加 .background(...)。
        Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(logoRes),
                contentDescription = spec.displayName,
                modifier = Modifier.size(size * 0.7f),
            )
        }
        return
    }

    // 回退分支：内置图标（tint 色由动态取色的三对 tonal 色轮换）
    val chips = listOf(
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer,
    )
    val fg = chips[spec.type.ordinal % chips.size].second
    val icon = when (spec.type) {
        ProviderType.AZURE -> Icons.Default.Cloud
        ProviderType.CUSTOM -> Icons.Default.Tune
        else -> Icons.Default.AutoAwesome
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = fg)
    }
}

// ──────────────────────────────────────
// Provider 区块
// ──────────────────────────────────────

/**
 * Provider 卡片：横向 Row 布局。
 *
 * 左侧为服务商 tonal 圆形图标，右侧为文字列（字段名 / 当前服务商 / 配置状态），
 * 末尾的 ▾ 图标暗示点击可从底部展开选择弹层。整卡可点。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderCard(
    spec: ProviderSpec,
    savedConfig: AIProviderConfig?,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左：服务商图标（导入品牌 logo 后在 ProviderSpecs 填 logoRes 即自动生效）
            ProviderIcon(spec = spec, size = 48.dp)

            // 右：文字列
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    spec.displayName,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = if (savedConfig != null) {
                        "已配置 · ${savedConfig.model}"
                    } else {
                        "尚未配置 API Key"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (savedConfig != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/** Provider 选择弹层：列出全部协议类型，标注已配置状态。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderPickerSheet(
    providers: List<AIProviderConfig>,
    selectedType: ProviderType,
    onSelect: (ProviderType) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "Provider",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        ProviderType.entries.forEach { type ->
            val spec = ProviderSpecs.of(type)
            val configured = providers.any { it.id == type.name && it.apiKey.isNotBlank() }
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { ProviderIcon(spec = spec, size = 40.dp) },
                headlineContent = { Text(spec.displayName) },
                supportingContent = { Text(
                    text = if (configured) "已配置" else "未配置",
                    color = if (configured) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ) },
                trailingContent = {
                    if (type == selectedType) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "当前选中",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                modifier = Modifier.clickable { onSelect(type) },
            )
        }
        Spacer(modifier = Modifier.height(32.dp)) // 避开底部手势区
    }
}

// ──────────────────────────────────────
// Credentials 区块（随 ProviderSpec 变化）
// ──────────────────────────────────────

@Composable
private fun CredentialsCard(
    spec: ProviderSpec,
    uiState: AISettingsUiState,
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    customEndpoint: String,
    onCustomEndpointChange: (String) -> Unit,
    apiVersion: String,
    onApiVersionChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onToggleApiKeyVisibility: () -> Unit,
) {
    SettingsCard {
        Text(spec.keyLabel, style = MaterialTheme.typography.titleMedium)
        if (spec.helpUrl.isNotEmpty()) {
            HelpLink(url = spec.helpUrl)
        }
        OutlinedTextField(
            value = uiState.apiKey.apiKey,
            onValueChange = onApiKeyChange,
            label = { Text("API Key") },
            singleLine = true,
            visualTransformation = if (uiState.apiKey.showApiKey) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = onToggleApiKeyVisibility) {
                    Icon(
                        imageVector = if (uiState.apiKey.showApiKey) {
                            Icons.Default.VisibilityOff
                        } else {
                            Icons.Default.Visibility
                        },
                        contentDescription = "显示/隐藏 API Key",
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = baseUrl,
            onValueChange = onBaseUrlChange,
            label = { Text("Base URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (spec.needsCustomEndpoint) {
            OutlinedTextField(
                value = customEndpoint,
                onValueChange = onCustomEndpointChange,
                label = { Text("自定义 Endpoint") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (spec.needsApiVersion) {
            OutlinedTextField(
                value = apiVersion,
                onValueChange = onApiVersionChange,
                label = { Text("API Version") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** "获取地址"帮助链接：说明文字普通色，URL 用 primary + 下划线，点击打开浏览器。 */
@Composable
private fun HelpLink(url: String) {
    val uriHandler = LocalUriHandler.current
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                append("获取地址：")
            }
            withStyle(
                SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                )
            ) {
                append(url)
            }
        },
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.clickable { uriHandler.openUri(url) },
    )
}

// ──────────────────────────────────────
// Model 区块
// ──────────────────────────────────────

/**
 * 模型选择卡片：可编辑下拉输入框 + 拉取列表。
 *
 * 下拉数据源：已拉取的 availableModels 优先，未拉取时回落到 spec 内置推荐值。
 * 输入框始终可手动输入，下拉只是快捷填充，拉取失败也不会被卡死。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelCard(
    spec: ProviderSpec,
    model: ModelState,
    apiKeyReady: Boolean,
    onModelChange: (String) -> Unit,
    onFetchModels: () -> Unit,
) {
    SettingsCard {
        Text("模型选择", style = MaterialTheme.typography.titleMedium)
        Text(
            "为 ${spec.displayName} 选择模型，或手动输入",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 可编辑下拉框：输入框手动输入，右侧箭头展开模型列表
        var expanded by remember { mutableStateOf(false) }
        val options = model.availableModels.ifEmpty { spec.recommendedModels }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = model.selectedModel,
                onValueChange = onModelChange,
                label = { Text("模型名称") },
                singleLine = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    // SecondaryEditable：点输入区正常聚焦弹键盘；点右侧箭头只展开列表，不弹键盘
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            if (options.isNotEmpty()) {
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                onModelChange(option)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }

        if (spec.supportsModelFetch) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(
                    onClick = onFetchModels,
                    enabled = apiKeyReady && !model.isLoading,
                ) {
                    Text("拉取可用模型列表")
                }
                if (model.isLoading) {
                    SubpageIndicator()
                }
            }
            if (!apiKeyReady) {
                Text(
                    "填写 API Key 后可拉取",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}



// ──────────────────────────────────────
// Test 区块
// ──────────────────────────────────────

/**
 * 连接测试卡片：用当前表单配置发一条最小消息，验证全链路可用。
 *
 * [TestState.isTesting] 驱动按钮旁加载菊花；结果通过 Snackbar 一次性展示
 * （见 Screen 中的 LaunchedEffect），卡片内不保留结果文本。
 */
@Composable
private fun TestCard(
    test: TestState,
    formReady: Boolean,
    onTest: () -> Unit,
) {
    SettingsCard {
        Text("连接测试", style = MaterialTheme.typography.titleMedium)
        Text(
            "用当前配置发一条测试消息",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(
                onClick = onTest,
                enabled = formReady && !test.isTesting,
            ) {
                Text("测试连接")
            }
            if (test.isTesting) {
                SubpageIndicator()
            }
        }
    }
}

/**
 * 3. 预览层
 * 无需模拟数据库和 Hilt 容器，可自由 mock 各种状态，即时预览界面。
 */
@Preview(showBackground = true)
@Composable
private fun AISettingsScreenPreview() {
    AISettingsScreen(
        uiState = AISettingsUiState(
            provider = ProviderState(
                providers = listOf(
                    AIProviderConfig(
                        id = "DEEPSEEK",
                        name = "DeepSeek",
                        type = ProviderType.DEEPSEEK,
                        baseUrl = "https://api.deepseek.com",
                        apiKey = "sk-xxx",
                        model = "deepseek-chat",
                        isPreset = true,
                    ),
                ),
                activeProviderId = "DEEPSEEK",
                selectedType = ProviderType.DEEPSEEK,
            ),
            apiKey = ApiKeyState(apiKey = "sk-xxx"),
            model = ModelState(selectedModel = "deepseek-chat"),
            endpoint = EndpointState(),
            test = TestState(),
            ui = UiState(),
        ),
        onBack = {},
        onProviderSelected = {},
        onApiKeyChange = {},
        onToggleApiKeyVisibility = {},
        onModelChange = {},
        onBaseUrlChange = {},
        onCustomEndpointChange = {},
        onApiVersionChange = {},
        onFetchModels = { _, _ -> },
        onFetchResultShown = {},
        onTestConnection = {},
        onTestResultShown = {},
        onSave = {},
        onErrorShown = {},
        onSuccessShown = {},
    )
}

// ──────────────────────────────────────
// 堆叠式 Snackbar 组件
// ──────────────────────────────────────

/**
 * 堆叠式 Snackbar 数据项。
 *
 * @property id 唯一标识符
 * @property message 提示内容
 * @property actionLabel 按钮操作文案（可选）
 * @property isVisible 是否可见（控制淡出与折叠动画）
 */
data class StackedSnackbarItem(
    val id: Long,
    val message: String,
    val actionLabel: String? = null,
    val isVisible: Boolean = true,
)

/**
 * 堆叠式 Snackbar 状态管理器。
 *
 * 支持多个 Snackbar 同时存活与并存显示，新消息弹出时按队列压入，
 * 消失时通过 key + 垂直折叠动画促使下方 Snackbar 平滑回归正常位置。
 */
class StackedSnackbarHostState {
    var items by mutableStateOf<List<StackedSnackbarItem>>(emptyList())
        private set

    private var nextId = 0L

    /**
     * 弹出一条新的 Snackbar。
     *
     * @param message 提示文案
     * @param actionLabel 操作按钮文案
     */
    fun showSnackbar(
        message: String,
        actionLabel: String? = null,
    ) {
        val id = ++nextId
        val item = StackedSnackbarItem(id = id, message = message, actionLabel = actionLabel)
        items = items + item
    }

    /**
     * 触发指定 ID 的 Snackbar 淡出/折叠动画。
     *
     * @param id Snackbar 标识符
     */
    fun dismiss(id: Long) {
        items = items.map {
            if (it.id == id) it.copy(isVisible = false) else it
        }
    }

    /**
     * 从数据列表中彻底移除已完成动画的 Snackbar。
     *
     * @param id Snackbar 标识符
     */
    fun remove(id: Long) {
        items = items.filterNot { it.id == id }
    }
}

/**
 * 创建并记住 [StackedSnackbarHostState] 实例。
 */
@Composable
fun rememberStackedSnackbarHostState(): StackedSnackbarHostState {
    return remember { StackedSnackbarHostState() }
}

/**
 * 堆叠式 Snackbar 宿主组件。
 *
 * 允许多个 Snackbar 在重叠时间内同时展示。按倒序在 Column 中排列，
 * 保证最先发出的 Snackbar 处在最底部（正常 Snackbar 锚定位置）。
 * 当先发出的 Snackbar 消失时，利用 [shrinkVertically] (Alignment.Bottom)
 * 使后发出的 Snackbar 平滑平移归位到底部。
 *
 * @param hostState 状态管理器
 * @param modifier 布局修饰符
 */
@Composable
fun StackedSnackbarHost(
    hostState: StackedSnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 倒序排列：使得最早发出的 (Item 1) 处于 Column 最底部（正常 Snackbar 位置），
        // 后发出的 (Item 2) 叠在 Item 1 上方。
        // 当 Item 1 消失折叠时，Item 2 会平滑下落补位到最底部正常位置。
        hostState.items.reversed().forEach { item ->
            key(item.id) {
                LaunchedEffect(item.id) {
                    delay(4000L)
                    hostState.dismiss(item.id)
                }

                LaunchedEffect(item.isVisible) {
                    if (!item.isVisible) {
                        delay(300L)
                        hostState.remove(item.id)
                    }
                }

                AnimatedVisibility(
                    visible = item.isVisible,
                    enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                            slideInVertically(
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                initialOffsetY = { it }
                            ),
                    exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                           shrinkVertically(
                               animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                               shrinkTowards = Alignment.Bottom
                           ) +
                           slideOutVertically(
                               animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                               targetOffsetY = { it }
                           ),
                ) {
                    Snackbar(
                        modifier = Modifier.padding(vertical = 2.dp),
                        action = item.actionLabel?.let { action ->
                            {
                                TextButton(onClick = { hostState.dismiss(item.id) }) {
                                    Text(action)
                                }
                            }
                        },
                        dismissAction = {
                            IconButton(onClick = { hostState.dismiss(item.id) }) {
                                Icon(Icons.Default.Close, contentDescription = "关闭")
                            }
                        }
                    ) {
                        Text(item.message)
                    }
                }
            }
        }
    }
}
