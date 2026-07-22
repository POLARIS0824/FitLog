package com.example.fitlog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 同色系圆形图标底：浅色圆 + 同色相深色图标。
 *
 * 遵循 Material Expressive 规范，包含 5 组 tonal 色槽（primary / secondary / tertiary / surfaceVariant / errorContainer），
 * 由动态取色生成，按 [index] 轮换，保证相邻图标色相丰富且互不冲突。
 *
 * @param icon 图标
 * @param index 轮换序号（同一列表中相邻项传递增 index 即可）
 * @param size 圆形槽位尺寸
 * @param modifier 修饰符
 */
@Composable
fun TonalIcon(
    icon: ImageVector,
    index: Int,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val chips = listOf(
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer,
        MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant,
        MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer,
    )
    val (bg, fg) = chips[(index % chips.size + chips.size) % chips.size]
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(size * 0.54f),
        )
    }
}

@Preview
@Composable
fun TonalIconPreview() {
    TonalIcon(
        icon = Icons.AutoMirrored.Filled.Send,
        index = 0,
        size = 40.dp
    )
}