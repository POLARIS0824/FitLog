package com.example.fitlog.feature.aisettings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitlog.model.ai.AIProviderConfig
import com.example.fitlog.model.ai.ProviderType
import java.util.UUID

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
        onAddNew = viewModel::onAddNew,
        onEdit = viewModel::onEdit,
        onDelete = viewModel::onDelete,
        onSetActive = viewModel::onSetActive,
        onEditorDismiss = viewModel::onEditorDismiss,
        onApiKeyChange = viewModel::onApiKeyChange,
        onToggleApiKeyVisibility = viewModel::onToggleApiKeyVisibility,
        onModelChange = viewModel::onModelChange,
        onSave = viewModel::onSave,
        onErrorShown = viewModel::onErrorShown,
        modifier = modifier,
    )
}

/**
 * 2. 纯 UI 展示层 (Stateless)
 * 不直接依赖任何 ViewModel 或 Hilt。
 */
//@OptIn(ExperimentalMaterial3Api::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AISettingsScreen(
    uiState: AISettingsUiState,
    onAddNew: () -> Unit,
    onEdit: (AIProviderConfig) -> Unit,
    onDelete: (AIProviderConfig) -> Unit,
    onSetActive: (String) -> Unit,
    onEditorDismiss: () -> Unit,
    onApiKeyChange: (String) -> Unit,
    onToggleApiKeyVisibility: () -> Unit,
    onModelChange: (String) -> Unit,
    onSave: (AIProviderConfig) -> Unit,
    onErrorShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        topBar = {
            LargeTopAppBar(
                title = { Text("AI Configuration") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        // Floating Action Button ?
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (uiState.ui.isLoading) {
                // 在屏幕中间显示一个 Contained loading indicator

            }
        }
    }




//    Scaffold(
//        modifier = modifier,
//        topBar = { TopAppBar(title = { Text("AI 服务商") }) },
//        floatingActionButton = {
//            FloatingActionButton(onClick = onAddNew) {
//                Icon(Icons.Default.Add, contentDescription = "新增服务商")
//            }
//        },
//    ) { innerPadding ->
//        Column(
//            modifier = Modifier
//                .padding(innerPadding)
//                .fillMaxSize()
//        ) {
//            if (uiState.ui.isLoading) {
//                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
//            }
//            if (uiState.provider.providers.isEmpty()) {
//                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
//                    Text("还没有 AI 服务商配置，点击右下角添加")
//                }
//            } else {
//                LazyColumn(modifier = Modifier.fillMaxSize()) {
//                    items(uiState.provider.providers, key = { it.id }) { config ->
//                        ProviderItem(
//                            config = config,
//                            isActive = config.id == uiState.provider.activeProviderId,
//                            onSetActive = onSetActive,
//                            onEdit = onEdit,
//                            onDelete = onDelete,
//                        )
//                        HorizontalDivider()
//                    }
//                }
//            }
//        }
//    }
//
//    // 新增 / 编辑对话框
//    if (uiState.provider.showEditor) {
//        ProviderEditorDialog(
//            uiState = uiState,
//            onDismiss = onEditorDismiss,
//            onApiKeyChange = onApiKeyChange,
//            onToggleApiKeyVisibility = onToggleApiKeyVisibility,
//            onModelChange = onModelChange,
//            onSave = onSave,
//        )
//    }
//
//    // 错误提示
//    uiState.ui.errorMessage?.let { message ->
//        AlertDialog(
//            onDismissRequest = onErrorShown,
//            confirmButton = { TextButton(onClick = onErrorShown) { Text("知道了") } },
//            title = { Text("出错了") },
//            text = { Text(message) },
//        )
//    }
}

/**
 * 单个服务商配置列表项。
 *
 * 左侧单选按钮表示"当前激活"，右侧提供编辑与删除入口。
 * 预设配置不显示删除按钮（与仓库层约定一致：是否可删由 UI 控制）。
 */
@Composable
private fun ProviderItem(
    config: AIProviderConfig,
    isActive: Boolean,
    onSetActive: (String) -> Unit,
    onEdit: (AIProviderConfig) -> Unit,
    onDelete: (AIProviderConfig) -> Unit,
) {
    ListItem(
        leadingContent = {
            RadioButton(selected = isActive, onClick = { onSetActive(config.id) })
        },
        headlineContent = { Text(config.name) },
        supportingContent = { Text("${config.type.name} · ${config.model}") },
        trailingContent = {
            Row {
                IconButton(onClick = { onEdit(config) }) {
                    Icon(Icons.Default.Edit, contentDescription = "编辑")
                }
                if (!config.isPreset) {
                    IconButton(onClick = { onDelete(config) }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除")
                    }
                }
            }
        },
    )
}

/**
 * 新增 / 编辑服务商的对话框。
 *
 * name / baseUrl / type 等纯表单字段由本对话框本地维护；
 * apiKey 与 model 走 [AISettingsUiState]（ViewModel），
 * 以便后续支持"拉取可用模型列表"等异步能力。
 */
@Composable
private fun ProviderEditorDialog(
    uiState: AISettingsUiState,
    onDismiss: () -> Unit,
    onApiKeyChange: (String) -> Unit,
    onToggleApiKeyVisibility: () -> Unit,
    onModelChange: (String) -> Unit,
    onSave: (AIProviderConfig) -> Unit,
) {
    val editing = uiState.provider.editing

    var name by remember { mutableStateOf(editing?.name ?: "") }
    var type by remember { mutableStateOf(editing?.type ?: ProviderType.CUSTOM) }
    var baseUrl by remember { mutableStateOf(editing?.baseUrl ?: "") }
    var customEndpoint by remember { mutableStateOf(editing?.customEndpoint ?: "") }
    var apiVersion by remember { mutableStateOf(editing?.apiVersion ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "新增服务商" else "编辑服务商") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                // 类型选择（最简下拉）
                var typeExpanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(
                        onClick = { typeExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("类型: ${type.name}")
                    }
                    DropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false },
                    ) {
                        ProviderType.entries.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t.name) },
                                onClick = { type = t; typeExpanded = false },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
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
                    value = uiState.model.selectedModel,
                    onValueChange = onModelChange,
                    label = { Text("模型") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (type == ProviderType.CUSTOM) {
                    OutlinedTextField(
                        value = customEndpoint,
                        onValueChange = { customEndpoint = it },
                        label = { Text("自定义 Endpoint（完整 URL 或路径）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (type == ProviderType.AZURE) {
                    OutlinedTextField(
                        value = apiVersion,
                        onValueChange = { apiVersion = it },
                        label = { Text("API Version") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && baseUrl.isNotBlank() &&
                    uiState.apiKey.apiKey.isNotBlank() && uiState.model.selectedModel.isNotBlank(),
                onClick = {
                    // 组装配置：编辑时保留原 id / isPreset，新建时生成随机 id
                    onSave(
                        AIProviderConfig(
                            id = editing?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            type = type,
                            baseUrl = baseUrl.trim(),
                            apiKey = uiState.apiKey.apiKey.trim(),
                            model = uiState.model.selectedModel.trim(),
                            customEndpoint = customEndpoint.trim().ifBlank { null },
                            apiVersion = apiVersion.trim().ifBlank { null },
                            isPreset = editing?.isPreset ?: false,
                        )
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
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
                        id = "1",
                        name = "DeepSeek",
                        type = ProviderType.DEEPSEEK,
                        baseUrl = "https://api.deepseek.com",
                        apiKey = "sk-xxx",
                        model = "deepseek-chat",
                        isPreset = true,
                    ),
                    AIProviderConfig(
                        id = "2",
                        name = "Moonshot",
                        type = ProviderType.MOONSHOT,
                        baseUrl = "https://api.moonshot.cn",
                        apiKey = "sk-yyy",
                        model = "kimi-k2",
                        isPreset = false,
                    ),
                ),
                activeProviderId = "1",
            ),
            apiKey = ApiKeyState(),
            model = ModelState(selectedModel = ""),
            test = TestState(),
            ui = UiState(),
        ),
        onAddNew = {},
        onEdit = {},
        onDelete = {},
        onSetActive = {},
        onEditorDismiss = {},
        onApiKeyChange = {},
        onToggleApiKeyVisibility = {},
        onModelChange = {},
        onSave = {},
        onErrorShown = {},
    )
}
