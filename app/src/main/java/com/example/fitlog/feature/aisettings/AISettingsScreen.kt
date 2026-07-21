package com.example.fitlog.feature.aisettings

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
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

/**
 * 1. 容器层 (Stateful)
 * 绑定 Hilt ViewModel，处理生命周期感知的状态收集。
 */
@Composable
fun AISettingsRoute(
    modifier: Modifier = Modifier,
    viewModel: AISettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AISettingsScreen(
        uiState = uiState,
        onProviderSelected = viewModel::onProviderSelected,
        onApiKeyChange = viewModel::onApiKeyChange,
        onToggleApiKeyVisibility = viewModel::onToggleApiKeyVisibility,
        onModelChange = viewModel::onModelChange,
        onFetchModels = viewModel::onFetchModels,
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
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AISettingsScreen(
    uiState: AISettingsUiState,
    onProviderSelected: (ProviderType) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onToggleApiKeyVisibility: () -> Unit,
    onModelChange: (String) -> Unit,
    onFetchModels: (baseUrl: String, customEndpoint: String?) -> Unit,
    onSave: (AIProviderConfig) -> Unit,
    onErrorShown: () -> Unit,
    onSuccessShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showProviderSheet by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    val selectedType = uiState.provider.selectedType
    val spec = ProviderSpecs.of(selectedType)

    // 该类型已保存的配置（用于回填 Screen 本地表单字段）
    val savedConfig = uiState.provider.providers.firstOrNull { it.id == selectedType.name }

    // baseUrl 等字段不进 UiState，用"按 selectedType 重建"的 remember 做本地表单状态。
    // 切换 provider 时 key 变化 → 重新执行初始值计算 → 从已保存配置或默认值回填。
    var baseUrl by remember(selectedType) {
        mutableStateOf(savedConfig?.baseUrl ?: spec.defaultBaseUrl)
    }
    var customEndpoint by remember(selectedType) {
        mutableStateOf(savedConfig?.customEndpoint.orEmpty())
    }
    var apiVersion by remember(selectedType) {
        mutableStateOf(savedConfig?.apiVersion.orEmpty())
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = { Text("AI Configuration") },
                navigationIcon = {
                    // TODO: 返回 SettingsScreen（待导航框架接入后实现）
                    IconButton(onClick = { }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    // 展开时融入背景；滚动折叠时渐变为略深的托起色（插值自动完成）
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                // 点击输入框以外的区域时清除焦点，收起软键盘
                // （输入框自身会消费点击事件，不会误触发）
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                }
                // 键盘弹出时顶起内容（edge-to-edge 下 adjustResize 被忽略，需自行消费 IME inset）。
                // 注意顺序：imePadding 必须在 verticalScroll 外层——滚动视口高度随键盘收缩，
                // 聚焦的输入框才会自动滚到键盘上方；放在滚动之后只是给内容末尾加空白，无效。
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                baseUrl = baseUrl,
                onBaseUrlChange = { baseUrl = it },
                customEndpoint = customEndpoint,
                onCustomEndpointChange = { customEndpoint = it },
                apiVersion = apiVersion,
                onApiVersionChange = { apiVersion = it },
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
                    onFetchModels(baseUrl, customEndpoint.ifBlank { null })
                },
            )

            // 保存按钮在页面底部：provider + 凭据 + 模型是同一条记录，一次保存原子完成
            Button(
                onClick = {
                    onSave(
                        AIProviderConfig(
                            id = selectedType.name,
                            name = spec.displayName,
                            type = selectedType,
                            baseUrl = baseUrl.trim(),
                            apiKey = uiState.apiKey.apiKey.trim(),
                            model = uiState.model.selectedModel.trim(),
                            customEndpoint = customEndpoint.trim().ifBlank { null },
                            apiVersion = apiVersion.trim().ifBlank { null },
                            isPreset = true, // 每类型一条的内置槽位配置
                        )
                    )
                },
                enabled = uiState.apiKey.apiKey.isNotBlank() &&
                    uiState.model.selectedModel.isNotBlank() &&
                    baseUrl.isNotBlank(),
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

    // 保存成功提示：successMessage 出现时弹出 Snackbar，展示完毕后清除
    LaunchedEffect(uiState.ui.successMessage) {
        uiState.ui.successMessage?.let {
            snackbarHostState.showSnackbar(it)
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

/** 区块标签 */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

/** 统一的卡片容器：圆角 28dp、无阴影、白卡（比背景亮一档）。 */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.cardElevation(0.dp),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

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
        elevation = CardDefaults.cardElevation(0.dp),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
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

            // 末尾：展开示能
//            Icon(
//                Icons.Default.ExpandMore,
//                contentDescription = "展开选择",
//                tint = MaterialTheme.colorScheme.onSurfaceVariant,
//            )
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
                supportingContent = { Text(if (configured) "已配置" else "未配置") },
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
                label = { Text("自定义 Endpoint（完整 URL 或路径）") },
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
 * 模型选择卡片：推荐 chips + 手动输入兜底 + 拉取列表。
 *
 * chips 数据源：已拉取列表优先，否则用 spec 内置推荐值——离线/拉取失败都有得选。
 */
@OptIn(ExperimentalLayoutApi::class)
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

        val chipModels = model.availableModels.ifEmpty { spec.recommendedModels }
        if (chipModels.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chipModels.forEach { m ->
                    FilterChip(
                        selected = model.selectedModel == m,
                        onClick = { onModelChange(m) },
                        label = { Text(m) },
                    )
                }
            }
        }

        OutlinedTextField(
            value = model.selectedModel,
            onValueChange = onModelChange,
            label = { Text("模型名称") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (spec.supportsModelFetch) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = onFetchModels,
                    enabled = apiKeyReady && !model.isLoading,
                ) {
                    Text("拉取可用模型列表")
                }
                if (model.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
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
            test = TestState(),
            ui = UiState(),
        ),
        onProviderSelected = {},
        onApiKeyChange = {},
        onToggleApiKeyVisibility = {},
        onModelChange = {},
        onFetchModels = { _, _ -> },
        onSave = {},
        onErrorShown = {},
        onSuccessShown = {},
    )
}
