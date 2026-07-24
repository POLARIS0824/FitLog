package com.example.fitlog.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitlog.model.ai.ChatMessage

@Composable
fun ChatRoute(
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ChatScreen(
        uiState = uiState,
        onInputChange = viewModel::onInputChange,
        onSend = viewModel::send,
        modifier = modifier,
    )
}

@Composable
fun ChatScreen(
    uiState: ChatUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(Modifier.fillMaxSize()) {

        // ── 字段 1: messages → 消息列表 ──
        LazyColumn(Modifier.weight(1f)) {
            items(uiState.messages) { msg ->
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
    // ── 字段 4: errorMessage → Snackbar（先不做也行，可以只打印） ──
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