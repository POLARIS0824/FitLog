package com.example.fitlog.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitlog.ui.components.StackedSnackbarHost
import com.example.fitlog.ui.components.rememberStackedSnackbarHostState
import com.example.fitlog.ui.theme.FitLogTheme

/**
 * 1. 容器层
 *
 * 从 [ChatViewModel] 收集状态并转发事件，导航回调由上层（NavDisplay）注入。
 *
 * @param onNavigateToSettings 跳转设置回调
 * @param onNavigateToAISettings 跳转 AI 配置回调（未配置服务商时的引导）
 * @param modifier 修饰符
 * @param viewModel 对话 ViewModel
 */
@Composable
fun ChatRoute(
    onNavigateToSettings: () -> Unit = {},
    onNavigateToAISettings: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ChatScreen(
        uiState = uiState,
        onInputChange = viewModel::onInputChange,
        onSend = viewModel::send,
        onErrorShown = viewModel::dismissError,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToAISettings = onNavigateToAISettings,
        modifier = modifier,
    )
}

/**
 * 2. 纯 UI 展示层
 *
 * App 根页面：消息列表（reverseLayout 贴底）+ 底部输入栏。
 * 一次性错误经 [com.example.fitlog.ui.components.StackedSnackbarHost] 展示后立即回调清除。
 *
 * @param uiState 对话 UI 状态
 * @param onInputChange 输入框文本变化
 * @param onSend 发送
 * @param onErrorShown 一次性错误已展示
 * @param onNavigateToSettings 跳转设置
 * @param onNavigateToAISettings 跳转 AI 配置
 * @param modifier 修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    uiState: ChatUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onErrorShown: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAISettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = rememberStackedSnackbarHostState()

    // 一次性错误事件：展示 → 立即清除
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onErrorShown()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        snackbarHost = { StackedSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("FitLog Coach") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // 未配置 AI 服务商时的引导条
            if (uiState.hasActiveProvider == false) {
                ProviderSetupBanner(onNavigateToAISettings = onNavigateToAISettings)
            }

            MessageList(
                items = uiState.items,
                phase = uiState.phase,
                onSuggestionClick = { suggestion ->
                    onInputChange(suggestion)
                    onSend()
                },
                modifier = Modifier.weight(1f),
            )

            ChatInputBar(
                input = uiState.input,
                enabled = uiState.phase == ChatPhase.IDLE,
                onInputChange = onInputChange,
                onSend = onSend,
            )
        }
    }
}

// ───────────────────────────── 私有组件 ─────────────────────────────

/**
 * 未配置 AI 服务商时的顶部引导条。
 */
@Composable
private fun ProviderSetupBanner(
    onNavigateToAISettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "配置 AI 服务商后开始对话",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f),
            )
            SuggestionChip(
                onClick = onNavigateToAISettings,
                label = { Text("去配置") },
            )
        }
    }
}

/**
 * 消息列表：reverseLayout 使新消息贴底、键盘弹起时视图自然上移。
 * 空态展示欢迎语与建议问题。
 */
@Composable
private fun MessageList(
    items: List<ChatListItem>,
    phase: ChatPhase,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) {
        EmptyState(onSuggestionClick = onSuggestionClick, modifier = modifier)
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items.asReversed(), key = { it.id }) { item ->
            when (item) {
                is ChatListItem.UserMessage -> UserBubble(item.text)
                is ChatListItem.AssistantMessage -> AssistantBubble(item.text)
                is ChatListItem.ToolCallItem -> ToolCallChip(item)
                is ChatListItem.ErrorItem -> ErrorBubble(item.message)
            }
        }
    }
}

/**
 * 空态：欢迎语 + 3 个建议问题 chip。
 */
@Composable
private fun EmptyState(
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "我是你的 AI 健身教练",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "可以问我关于你的训练记录、计划和动作的问题",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        listOf("我最近练了什么？", "我卧推最高多少？", "下次训练该练什么？").forEach { suggestion ->
            SuggestionChip(
                onClick = { onSuggestionClick(suggestion) },
                label = { Text(suggestion) },
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
    }
}

/**
 * 用户消息气泡：右对齐、primaryContainer、不对称圆角。
 */
@Composable
private fun UserBubble(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .widthIn(max = 300.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(
                topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 4.dp,
            ),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

/**
 * AI 消息气泡：左对齐、surfaceContainerHighest。
 */
@Composable
private fun AssistantBubble(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .widthIn(max = 300.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(
                topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 20.dp,
            ),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

/**
 * tool 调用指示条：执行中显示波浪进度条，结束后显示对勾/叉。
 */
@Composable
private fun ToolCallChip(item: ChatListItem.ToolCallItem, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (item.status) {
            ChatListItem.ToolCallItem.Status.RUNNING -> CircularWavyProgressIndicator(
                modifier = Modifier.size(18.dp),
            )

            ChatListItem.ToolCallItem.Status.SUCCESS -> Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )

            ChatListItem.ToolCallItem.Status.FAILED -> Icon(
                Icons.Default.Close,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.size(8.dp))
        Text(
            text = toolDisplayName(item.toolName) + if (item.status == ChatListItem.ToolCallItem.Status.RUNNING) "…" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 错误提示气泡。
 */
@Composable
private fun ErrorBubble(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.align(Alignment.CenterStart),
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

/**
 * 底部输入栏：文本框 + 发送按钮，随键盘上移（imePadding）。
 */
@Composable
private fun ChatInputBar(
    input: String,
    enabled: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .imePadding()
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("问问你的教练…") },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )
            Spacer(Modifier.size(8.dp))
            IconButton(onClick = onSend, enabled = enabled && input.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
            }
        }
    }
}

// ───────────────────────────── Preview ─────────────────────────────

@Preview(showBackground = true)
@Composable
private fun ChatScreenPreview() {
    FitLogTheme {
        ChatScreen(
            uiState = ChatUiState(
                items = listOf(
                    ChatListItem.UserMessage(text = "我最近练了什么？"),
                    ChatListItem.ToolCallItem(
                        id = "call_1",
                        toolName = "list_recent_workouts",
                        status = ChatListItem.ToolCallItem.Status.SUCCESS,
                    ),
                    ChatListItem.ToolCallItem(
                        id = "call_2",
                        toolName = "get_exercise_history",
                        status = ChatListItem.ToolCallItem.Status.RUNNING,
                    ),
                    ChatListItem.AssistantMessage(
                        text = "你最近练了 3 次：周一胸+三头，周三背，周五腿。卧推最高 80kg。",
                    ),
                ),
                phase = ChatPhase.EXECUTING_TOOL,
                hasActiveProvider = false,
            ),
            onInputChange = {},
            onSend = {},
            onErrorShown = {},
            onNavigateToSettings = {},
            onNavigateToAISettings = {},
        )
    }
}
