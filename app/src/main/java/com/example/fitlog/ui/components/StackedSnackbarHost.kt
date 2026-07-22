package com.example.fitlog.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fitlog.R
import kotlinx.coroutines.delay

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
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close))
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
