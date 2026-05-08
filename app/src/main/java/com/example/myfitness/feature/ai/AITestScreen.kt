package com.example.myfitness.feature.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.myfitness.domain.model.AIProviderConfig

/**
 * AI 调用链条测试页面。
 *
 * 支持：
 * - 添加多条 AI 提供商配置
 * - 查看已保存的配置列表（验证 Room 存储）
 * - 切换激活的配置（验证多 provider 选择）
 * - 使用当前激活配置发送测试消息
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AITestScreen(
    viewModel: AITestViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("https://api.ppio.com/openai") }
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("deepseek/deepseek-v4-pro") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 链路测试") },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ===== 新增配置 =====
            Text(
                text = "添加新配置",
                style = MaterialTheme.typography.titleMedium,
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("配置名称（如：PPIO、OpenAI）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Model") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Button(
                onClick = {
                    viewModel.addConfig(name, baseUrl, apiKey, model)
                    name = ""
                    apiKey = ""
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank()
                    && baseUrl.isNotBlank()
                    && apiKey.isNotBlank()
                    && model.isNotBlank(),
            ) {
                Text("保存配置到 Room")
            }

            // ===== 已保存配置列表 =====
            Text(
                text = "已保存的配置（共 ${uiState.configs.size} 条）",
                style = MaterialTheme.typography.titleMedium,
            )

            if (uiState.configs.isEmpty()) {
                Text(
                    text = "暂无配置，请先添加一条",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                uiState.configs.forEach { config ->
                    ConfigCard(
                        config = config,
                        isActive = config.id == uiState.activeId,
                        onActivate = { viewModel.setActiveConfig(config.id) },
                        onDelete = { viewModel.deleteConfig(config.id) },
                    )
                }
            }

            // ===== 测试当前激活配置 =====
            Button(
                onClick = { viewModel.testActiveConfig() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading && uiState.activeId != null,
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("使用当前激活配置发送测试")
                }
            }

            // ===== 结果展示 =====
            uiState.result?.let { result ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "AI 回复：",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = result,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            uiState.error?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "错误：",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 单条配置卡片。
 *
 * @param config 配置信息
 * @param isActive 是否为当前激活项
 * @param onActivate 点击激活回调
 * @param onDelete 点击删除回调
 */
@Composable
private fun ConfigCard(
    config: AIProviderConfig,
    isActive: Boolean,
    onActivate: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = isActive,
                    onClick = onActivate,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = config.name,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "${config.model} @ ${config.baseUrl}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "id: ${config.id}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onDelete) {
                    Text("删除")
                }
            }
        }
    }
}
