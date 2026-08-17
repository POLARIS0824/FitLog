package com.example.fitlog.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitlog.model.ai.ChatMessage
import com.example.fitlog.ui.components.StackedSnackbarHost
import com.example.fitlog.ui.components.rememberStackedSnackbarHostState

/**
 * AI 教练对话页容器层：绑定 [ChatViewModel]，收集状态并转发事件。
 *
 * @param onBack 返回上一页回调（Navigation3 回退栈语义：调用方执行 removeLastOrNull）
 * @param modifier 修饰符
 */
@Composable
fun ChatRoute(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ChatScreen(
        uiState = uiState,
        onBack = onBack,
        onInputChange = viewModel::onInputChange,
        onSend = viewModel::send,
        onErrorShown = viewModel::onErrorShown,
        modifier = modifier,
    )
}

/**
 * AI 教练对话页纯 UI 层。
 *
 * 布局：顶栏（返回 + 标题）→ 消息列表 → 底部输入栏；
 * 错误提示经 [StackedSnackbarHost] 叠加在底部展示，展示完毕后回调 [onErrorShown]
 * 清除一次性错误状态（与全局 StackedSnackbar 用法一致）。
 *
 * @param uiState 对话状态
 * @param onBack 返回上一页回调
 * @param onInputChange 输入框文本变化事件
 * @param onSend 发送按钮点击事件
 * @param onErrorShown 错误提示展示完毕回调
 * @param modifier 修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    uiState: ChatUiState,
    onBack: () -> Unit = {},
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onErrorShown: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val stackedSnackbarHostState = rememberStackedSnackbarHostState()
    val listState = rememberLazyListState()

    // 错误提示：errorMessage 出现时弹出 StackedSnackbar，展示完毕后清除一次性错误状态
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            stackedSnackbarHostState.showSnackbar(it)
            onErrorShown()
        }
    }

    // 新消息/发送中指示出现时自动滚动到底部，保证 AI 回复对用户可见
    // （否则用户向上翻过历史后，回复与"AI 正在思考…"都渲染在屏幕外）
    LaunchedEffect(uiState.messages.size, uiState.isSending) {
        val lastIndex = uiState.messages.size - 1 + if (uiState.isSending) 1 else 0
        if (lastIndex >= 0) {
            listState.animateScrollToItem(lastIndex)
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {

            // ── 顶栏：返回 + 标题 ──
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "AI 教练",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            )

            // ── 字段 1: messages → 消息列表 ──
            LazyColumn(Modifier.weight(1f), state = listState) {
                items(uiState.messages, key = { it.id }) { msg ->
                    if (msg.role == "user") {
                        UserMessageBubble(msg)
                    } else {
                        // AI 回复不使用气泡，直接纯文本展示
                        SelectionContainer {
                            Text(
                                text = msg.content,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
                // ── 字段 2: isSending → 列表末尾的"AI 正在输入..." ──
                if (uiState.isSending) {
                    item {
                        Text(
                            text = "AI 正在思考…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            // ── 字段 3: input → 底部输入栏 ──
            Row {
                TextField(
                    value = uiState.input,
                    onValueChange = onInputChange,   // ← 事件转发
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onSend, enabled = !uiState.isSending) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
                }
            }
        }

        // ── 字段 4: errorMessage → 底部叠加 StackedSnackbar ──
        StackedSnackbarHost(
            hostState = stackedSnackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * 用户消息气泡：右对齐、primary 配色圆角气泡。
 *
 * AI 回复不使用气泡（见 [ChatScreen] 消息列表分支）。
 */
@Composable
fun UserMessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 4.dp,
            ),
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}
