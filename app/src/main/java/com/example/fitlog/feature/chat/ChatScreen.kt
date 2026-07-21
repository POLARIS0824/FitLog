package com.example.fitlog.feature.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
        onInputChange = { viewModel.onInputChange(it) },
        onSend = { viewModel.send() },
    )
}

@Composable
fun ChatScreen(
    uiState: ChatUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {

        // ── 字段 1: messages → 消息列表 ──
        LazyColumn(Modifier.weight(1f)) {
            items(uiState.messages) { msg ->
                // 气泡：role=="user" 靠右一种颜色，否则靠左另一种颜色
                MessageBubble(msg)
            }
            // ── 字段 2: isSending → 列表末尾的"AI 正在输入..." ──
            if (uiState.isSending) {
                item { Text("AI 正在思考…") }
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

@Composable
fun MessageBubble(x0: ChatMessage) {
    TODO("Not yet implemented")
}