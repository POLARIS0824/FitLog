package com.example.fitlog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp

/**
 * 同色系圆形图标底：浅色圆 + 同色相深色图标。
 *
 * 三对 tonal 色（primary / secondary / tertiary 的 container 配对）
 * 由动态取色生成，按 [index] 轮换，保证相邻图标色相不同。
 *
 * @param icon 图标
 * @param index 轮换序号（同一列表中相邻项传递增 index 即可）
 * @param size 圆形槽位尺寸
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
    )
    val (bg, fg) = chips[index % chips.size]
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = fg)
    }
}
