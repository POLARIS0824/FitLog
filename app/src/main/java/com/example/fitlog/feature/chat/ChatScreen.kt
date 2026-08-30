package com.example.fitlog.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitlog.ui.components.StackedSnackbarHost
import com.example.fitlog.ui.components.rememberStackedSnackbarHostState
import com.example.fitlog.ui.theme.fitLogColors

/**
 * AI 教练对话页容器层：绑定 [ChatViewModel]，收集状态并转发事件。
 *
 * @param modifier 修饰符
 */
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
        onErrorShown = viewModel::onErrorShown,
        onConfirm = viewModel::respondToConfirmation,
        onClearChat = viewModel::onClearChat,
        modifier = modifier,
    )
}

/**
 * AI 教练对话页纯 UI 层。
 *
 * 布局：顶栏（标题 + 清空对话）→ 消息列表 → 底部输入栏；
 * 错误提示经 [StackedSnackbarHost] 叠加在底部展示，展示完毕后回调 [onErrorShown]
 * 清除一次性错误状态（与全局 StackedSnackbar 用法一致）。
 *
 * @param uiState 对话状态
 * @param onInputChange 输入框文本变化事件
 * @param onSend 发送按钮点击事件
 * @param onErrorShown 错误提示展示完毕回调
 * @param onConfirm 工具确认请求回调（参数为是否同意；同意才真正执行写操作）
 * @param onClearChat 清空对话事件（删除持久化历史并重置 UI）
 * @param modifier 修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    uiState: ChatUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onErrorShown: () -> Unit = {},
    onConfirm: (Boolean) -> Unit = {},
    onClearChat: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val stackedSnackbarHostState = rememberStackedSnackbarHostState()
    val listState = rememberLazyListState()
    // 清空对话需二次确认：操作不可逆；会话历史删除前会归档为长期记忆，教练仍可引用历史要点
    var showClearDialog by remember { mutableStateOf(false) }

    // 错误提示：errorMessage 出现时弹出 StackedSnackbar，展示完毕后清除一次性错误状态
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            stackedSnackbarHostState.showSnackbar(it)
            onErrorShown()
        }
    }

    // 新消息/时间线步骤追加时自动滚动到底部，保证 AI 回复与最新步骤对用户可见
    // （否则用户向上翻过历史后，回复与进行中的时间线都渲染在屏幕外）
    LaunchedEffect(
        uiState.messages.size,
        uiState.activeRun != null,
        uiState.activeRun?.steps?.size,
    ) {
        val lastIndex = uiState.messages.lastIndex + if (uiState.activeRun != null) 1 else 0
        if (lastIndex >= 0) {
            listState.animateScrollToItem(lastIndex)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.fitLogColors.pageBackground),
    ) {
        Column(Modifier.fillMaxSize()) {

            // ── 顶栏：标题 + 清空对话 ──
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "AI 教练",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = {
                    IconButton(
                        onClick = { showClearDialog = true },
                        enabled = uiState.messages.isNotEmpty() && !uiState.isSending,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "清空对话",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.fitLogColors.pageBackground,
                    scrolledContainerColor = MaterialTheme.fitLogColors.pageBackground,
                ),
            )

            // ── 字段 1: messages → 消息列表 ──
            LazyColumn(Modifier.weight(1f), state = listState) {
                items(uiState.messages, key = { it.id }) { msg ->
                    if (msg.role == "user") {
                        UserMessageBubble(msg)
                    } else {
                        // AI 回复 = 过程时间线卡片（有步骤时）+ 无气泡纯文本
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            if (msg.steps.isNotEmpty()) {
                                AgentProcessCard(
                                    steps = msg.steps,
                                    isRunning = false,
                                    elapsedMs = msg.durationMs ?: 0L,
                                    initiallyExpanded = false,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                            }
                            SelectionContainer {
                                Text(
                                    text = msg.content,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
                }
                // ── 字段 2: activeRun → 列表末尾展开的执行过程时间线 ──
                uiState.activeRun?.let { run ->
                    item(key = "active_run") {
                        AgentProcessCard(
                            steps = run.steps,
                            isRunning = true,
                            awaitingConfirmation = run.awaitingConfirmation,
                            elapsedMs = run.activeMs,
                            initiallyExpanded = true,
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

    // ── 字段 5: pendingConfirmation → 工具写操作确认对话框 ──
    uiState.pendingConfirmation?.let { pending ->
        ToolConfirmationDialog(
            pending = pending,
            onConfirm = { onConfirm(true) },
            onDismiss = { onConfirm(false) },
        )
    }

    // ── 清空对话二次确认 ──
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空对话？") },
            text = {
                Text("将删除全部聊天记录与当前会话上下文；此前对话的要点会归档为长期记忆，供教练后续参考。操作不可恢复。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        onClearChat()
                    },
                ) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}

/**
 * 工具写操作确认对话框。
 *
 * agent 想执行需要确认的工具（记体重 / 切计划）时展示；同意才真正执行，
 * 拒绝则向模型回传拒绝结果（模型会向用户解释）。参数展示尽量可读：
 * 已知字段中文化（体重/计划），未知字段原样展示。
 *
 * @param pending 待确认的工具调用信息
 * @param onConfirm 点击"允许"回调
 * @param onDismiss 点击"拒绝"或对话框外关闭回调
 */
@Composable
private fun ToolConfirmationDialog(
    pending: PendingConfirmation,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("允许 AI 执行此操作？") },
        text = {
            Column {
                Text(
                    text = "工具：" + AgentStepFormatter.toolLabel(pending.toolName),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(8.dp))
                // 参数摘要：已知字段中文化，其余原样展示
                val argLines = pending.args.entries.map { (k, v) ->
                    val label = when (k) {
                        "weightKg" -> "体重（kg）"
                        "planId" -> "计划 ID"
                        else -> k
                    }
                    "$label：$v"
                }
                if (argLines.isNotEmpty()) {
                    Text(
                        text = argLines.joinToString("\n"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("允许")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("拒绝")
            }
        },
    )
}

/**
 * 用户消息气泡：右对齐、primary 配色圆角气泡。
 *
 * AI 回复不使用气泡（见 [ChatScreen] 消息列表分支）。
 */
@Composable
fun UserMessageBubble(message: ChatUiMessage) {
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
