package com.example.fitlog.feature.chat

import android.content.Context
import android.speech.RecognizerIntent
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitlog.model.ai.AgentStep
import com.example.fitlog.model.ai.ChatThreadMessage
import com.example.fitlog.ui.components.StackedSnackbarHost
import com.example.fitlog.util.findActivity
import com.example.fitlog.ui.components.rememberStackedSnackbarHostState
import com.example.fitlog.ui.theme.fitLogColors

/**
 * AI 教练对话页容器层：绑定 [ChatViewModel]，收集状态并转发事件。
 *
 * ViewModel 挂在 Activity 级 ViewModelStore（而非 nav entry）：切 tab 清栈销毁
 * entry 时，进行中的 Agent 运行不被取消（中断会在会话里留悬空 tool_call，虽已有
 * 请求装配层自愈，但能不中断就不中断），输入到一半的草稿也不丢失。
 */
@Composable
fun ChatRoute(
    /** 非空时进入即预填输入框（一次性消费，重复重组不重放） */
    prefill: String? = null,
    modifier: Modifier = Modifier,
) {
    val activity = LocalContext.current.findActivity()
    val viewModel: ChatViewModel = if (activity != null) {
        hiltViewModel(activity)
    } else {
        hiltViewModel()
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 外部预填（如 Today「AI 分析」卡的分析请求）：一次性消费
    LaunchedEffect(prefill) {
        prefill?.let(viewModel::applyPrefill)
    }

    // 语音输入：系统识别器（RecognizerIntent）+ 结果追加回填。
    // 设备无识别服务时启动失败 → 一次性错误提示，不静默
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val text = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!text.isNullOrBlank()) {
            viewModel.onVoiceInputResult(text)
        } else {
            viewModel.onVoiceInputUnavailable()
        }
    }
    val onVoiceInput: () -> Unit = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "对 AI 教练说点什么")
        }
        runCatching { voiceLauncher.launch(intent) }
            .onFailure { viewModel.onVoiceInputUnavailable() }
    }

    ChatScreen(
        uiState = uiState,
        onInputChange = viewModel::onInputChange,
        onSend = viewModel::send,
        onStop = viewModel::stopRun,
        onErrorShown = viewModel::onErrorShown,
        onConfirm = viewModel::respondToConfirmation,
        onClearChat = viewModel::onClearChat,
        onVoiceInput = onVoiceInput,
        modifier = modifier,
    )
}

/**
 * AI 教练对话页纯 UI 层。
 *
 * 布局：顶栏（标题 + 清空对话）→ 消息列表 → 底部胶囊输入栏；
 * 错误提示经 [StackedSnackbarHost] 叠加在底部展示，展示完毕后回调 [onErrorShown]
 * 清除一次性错误状态（与全局 StackedSnackbar 用法一致）。
 *
 * @param uiState 对话状态
 * @param onInputChange 输入框文本变化事件
 * @param onSend 发送按钮点击事件
 * @param onStop 停止按钮点击事件（生成期间取消进行中的运行）
 * @param onErrorShown 错误提示展示完毕回调
 * @param onConfirm 工具确认请求回调（参数为是否同意；同意才真正执行写操作）
 * @param onClearChat 清空对话事件（删除持久化历史并重置 UI）
 * @param onVoiceInput 发起语音识别回调（结果经系统识别器回填输入框）
 * @param modifier 修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    uiState: ChatUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit = {},
    onErrorShown: () -> Unit = {},
    onConfirm: (Boolean) -> Unit = {},
    onClearChat: () -> Unit = {},
    onVoiceInput: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val stackedSnackbarHostState = rememberStackedSnackbarHostState()
    val listState = rememberLazyListState()
    // 清空对话需二次确认：操作不可逆；会话历史删除前会归档为长期记忆，教练仍可引用历史要点
    // （rememberSaveable：旋转重建后弹层不静默消失）
    var showClearDialog by rememberSaveable { mutableStateOf(false) }

    // 错误提示：errorMessage 出现时弹出 StackedSnackbar，展示完毕后清除一次性错误状态
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            stackedSnackbarHostState.showSnackbar(it)
            onErrorShown()
        }
    }

    // 新消息/时间线步骤追加/键盘弹出时自动滚动到底部，保证 AI 回复与最新步骤
    // 对用户可见（否则用户向上翻过历史后，回复与进行中的时间线都渲染在屏幕外）。
    // 仅当用户已接近底部时才跟随：生成期间每个新步骤都会触发本效果，
    // 无条件滚动会把正在回看历史的用户反复拽回底部
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LaunchedEffect(
        uiState.messages.size,
        uiState.activeRun != null,
        uiState.activeRun?.steps?.size,
        imeVisible,
    ) {
        val lastIndex = uiState.messages.lastIndex + if (uiState.activeRun != null) 1 else 0
        if (lastIndex >= 0) {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val nearBottom = !listState.canScrollForward || lastVisibleIndex >= lastIndex - 1
            if (nearBottom) {
                // 带 offset 滚动：运行中的时间线卡片可高于视口（展开态无内部滚动），
                // 顶部对齐会让卡片底部的新步骤永远留在视口外；大 offset 把卡片
                // 底部带进视口，短卡片时被列表末尾 clamp，效果等同滚到底
                listState.animateScrollToItem(lastIndex, 2_000)
            }
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

            // ── 字段 3: input → Material 3 Expressive 胶囊输入栏 ──
            // imePadding：edge-to-edge 下根 Scaffold 未消费键盘 insets，必须自行避让，
            // 否则键盘弹出会遮挡输入框
            ChatInputBar(
                input = uiState.input,
                isSending = uiState.isSending,
                onInputChange = onInputChange,
                onSend = onSend,
                onStop = onStop,
                onVoiceInputClick = onVoiceInput,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .imePadding(),
            )
        }

        // ── 字段 4: errorMessage → 底部叠加 StackedSnackbar ──
        StackedSnackbarHost(
            hostState = stackedSnackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
                .padding(bottom = 76.dp),
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
fun UserMessageBubble(message: ChatThreadMessage) {
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

/**
 * Material 3 Expressive 风格的胶囊药丸输入栏。
 *
 * 包含：
 * - 左侧 "+" 扩展操作按钮（预留附件/快捷操作）
 * - 中间多行自适应文本输入区（带 "问问 AI 教练..." 占位提示）
 * - 右侧语音输入按钮（预留语音转文字）
 * - 最右侧圆形强调按钮（空闲态展示 Live 对话图标、输入态切换为发送按钮、生成态切换为停止按钮）
 *
 * @param input 当前输入的文本内容
 * @param isSending 是否正在生成回复中
 * @param onInputChange 输入内容变更回调
 * @param onSend 发送消息回调
 * @param onStop 停止生成回调
 * @param onVoiceInputClick 点击麦克风按钮回调（发起系统语音识别）
 * @param modifier 修饰符
 */
@Composable
fun ChatInputBar(
    input: String,
    isSending: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onVoiceInputClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ── 中间文本输入区 ──
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (input.isEmpty()) {
                    Text(
                        text = "问问 AI 教练...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
                BasicTextField(
                    value = input,
                    onValueChange = onInputChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Default,
                    ),
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ── 右侧麦克风语音按钮 ──
            // 不显式收窄尺寸：M3 IconButton 默认 40dp 视觉 + 48dp 最小触控目标
            IconButton(onClick = onVoiceInputClick) {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = "语音输入",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(Modifier.width(2.dp))

            // ── 最右侧圆形强调操作按钮（发送 / 停止；空输入时禁用）──
            val hasInput = input.isNotBlank()
            val actionState = when {
                isSending -> InputActionState.STOP
                else -> InputActionState.SEND
            }

            FilledIconButton(
                onClick = {
                    when (actionState) {
                        InputActionState.STOP -> onStop()
                        InputActionState.SEND -> onSend()
                    }
                },
                enabled = isSending || hasInput,
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                // 不显式收窄尺寸：默认 40dp 视觉 + 48dp 最小触控目标
            ) {
                AnimatedContent(
                    targetState = actionState,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(150)) togetherWith
                            fadeOut(animationSpec = tween(150))
                    },
                    label = "InputActionTransition",
                ) { target ->
                    when (target) {
                        InputActionState.STOP -> {
                            Icon(
                                imageVector = Icons.Filled.Stop,
                                contentDescription = "停止生成",
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        InputActionState.SEND -> {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "发送",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 底部操作按钮的三态定义。
 */
private enum class InputActionState {
    SEND,
    STOP,
}
