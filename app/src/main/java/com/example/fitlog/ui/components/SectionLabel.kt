package com.example.fitlog.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * 区块标签：设置页与子设置页各分组 Card 上方的分类标题。
 *
 * 遵循 Material Design 3 (M3) Subheader 布局与字阶规范：
 * 1. 字体：采用 [MaterialTheme.typography.titleMedium] 字阶与 [FontWeight.Medium] 字重，层次清晰。
 * 2. 配色：使用 [MaterialTheme.colorScheme.primary] 主调强调色，契合 Google 原生设置风格。
 * 3. 边距：遵循 M3 8dp 增量网格规则——左侧缩进 8.dp（配合页面外层 16.dp 边距对齐 24.dp 规范缩进）；
 *    顶部留白 16.dp，底部距离卡片留白 6.dp，具备通透的高级视觉留白。
 *
 * @param text 标签显示文本
 * @param modifier 修饰符
 */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 8.dp, top = 16.dp, bottom = 6.dp),
    )
}

/**
 * SectionLabel 布局预览
 */
@Preview(showBackground = true)
@Composable
private fun SectionLabelPreview() {
    SectionLabel(text = "Preview Section")
}

