package com.example.fitlog.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * App 级形状 token（卡片圆角统一口径）。
 *
 * 此前三种卡片圆角并存（24/22/18dp），收敛为两档：
 * - [Card]：大卡片（FitLogCard / 指标大卡 / 渐变背景 / 打卡胶囊卡片等容器层）；
 * - [CardSmall]：小卡片（SmallMetricCard 等紧凑容器）。
 *
 * 对话框/底部弹层等组件继续使用 MaterialTheme 默认形状（extraLarge 28dp 等），
 * 不在此处覆盖全局 Shapes，避免影响未显式指定形状的组件默认值。
 */
object FitLogShapes {

    /** 大卡片圆角。 */
    val Card = RoundedCornerShape(22.dp)

    /** 小卡片（紧凑容器）圆角。 */
    val CardSmall = RoundedCornerShape(18.dp)
}
