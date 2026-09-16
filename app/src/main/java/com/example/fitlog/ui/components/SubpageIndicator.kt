package com.example.fitlog.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 子页面 / 次级加载指示器（Material 3 Expressive 风格的环形波浪进度菊花）。
 *
 * @param modifier 外部修饰符，默认包含 12dp 左侧外边距与 32dp 尺寸。外部可自定义传入尺寸以用于紧凑列表或内联状态。
 */
@Composable
fun SubpageIndicator(
    modifier: Modifier = Modifier
        .padding(start = 12.dp)
        .size(32.dp),
) {
    CircularWavyProgressIndicator(
        modifier = modifier,
    )
}