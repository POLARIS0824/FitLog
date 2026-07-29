package com.example.fitlog.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.fitlog.ui.theme.FitLogTheme

/**
 * 目标进度轨道：全宽圆角 pill，填色比例 = 当前值 / 目标值。
 *
 * 这是**卡片级**的目标指示（[com.example.fitlog.ui.components.chart.ChartData.goalLine]
 * 细参考线之外的第二种语义）：位置固定在卡片布局中，不随数据区间移动；
 * 常与「剩余量」状态 pill 搭配（如填 65% + "1,815 cal left"）。
 * 无数据时传 0f 呈现为空轨道。
 *
 * 实现为双层 Box（外层轨道底 + 内层宽度比例填色，两层都裁 CircleShape，
 * 防止小进度时填色露出方角）；进度变化经 [animateFloatAsState] 平滑过渡——
 * 动画初值即目标值，静态 Preview 天然渲染定形态。
 *
 * @param progress 进度（自动收敛到 0..1）
 * @param modifier 修饰符
 * @param fillColor 填色（已完成部分）颜色
 * @param trackColor 轨道底色（默认填色的 15% 透明版）
 * @param height 轨道高度
 * @param animationSpec 进度动画规格
 */
@Composable
fun GoalTrack(
    progress: Float,
    modifier: Modifier = Modifier,
    fillColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = fillColor.copy(alpha = 0.15f),
    height: Dp = 8.dp,
    animationSpec: AnimationSpec<Float> = tween(450, easing = FastOutSlowInEasing),
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = animationSpec,
        label = "goalTrackProgress",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(animatedProgress)
                .clip(CircleShape)
                .background(fillColor),
        )
    }
}

// ──────────────────────────────────────
// 预览
// ──────────────────────────────────────

/** 进度三态：0%（空轨道/无数据形态）、65%、100%。 */
@Preview(showBackground = true)
@Composable
private fun GoalTrackPreview() {
    FitLogTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GoalTrack(progress = 0f, fillColor = Color(0xFF2BB5A0))
            GoalTrack(progress = 0.65f, fillColor = Color(0xFF2BB5A0))
            GoalTrack(progress = 1f, fillColor = Color(0xFF2BB5A0))
        }
    }
}

/** 暗色主题形态。 */
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GoalTrackDarkPreview() {
    FitLogTheme {
        GoalTrack(
            progress = 0.65f,
            fillColor = Color(0xFF2BB5A0),
            modifier = Modifier.padding(16.dp),
        )
    }
}
