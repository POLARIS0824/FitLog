package com.example.fitlog.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Gemini 风格的流动渐变背景容器组件（与业务无关的通用视觉组件）。
 *
 * 利用 [androidx.compose.animation.core.InfiniteTransition] 驱动多组色彩斑斓的
 * 动态径向渐变 (Radial Gradient Blobs) 在二维平面上作周期性平滑运动与膨胀收缩，
 * 结合 Material3 表面遮罩，打造类似于 Gemini App 界面中生动流畅的
 * Ambient Color Flow 效果。
 *
 * ## 帧消耗门控
 *
 * 动画仅在宿主生命周期 RESUMED 时挂载：Navigation3 转场 / 预测式返回手势 /
 * 浮层覆盖期间 entry 生命周期被压到 RESUMED 之下，此时把动画从组合中移除，
 * [androidx.compose.animation.core.InfiniteTransition] 无活跃动画即停止帧回调。
 * （稳态下被压栈的 entry 会整体移出组合；此门控覆盖瞬态窗口并防御未来 OverlayScene。）
 *
 * @param modifier 外部 Modifier 修饰符
 * @param isDarkTheme 当前是否处于深色主题模式
 * @param content 卡片内部内容布局
 */
@Composable
fun GeminiFlowingGradientBackground(
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable BoxScope.() -> Unit,
) {
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val isResumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

    val infiniteTransition = rememberInfiniteTransition(label = "GeminiGradientAnimation")

    val time by if (isResumed) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 10000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "time",
        )
    } else {
        // 非 RESUMED：动画移出组合，帧回调停止；恢复时从 0f 重启，10s 环境渐变下不可感知
        remember { mutableStateOf(0f) }
    }

    val color1 = if (isDarkTheme) Color(0xFF3865A8) else Color(0xFFA8C7FA) // 柔和天空蓝
    val color2 = if (isDarkTheme) Color(0xFF6B4EA2) else Color(0xFFD0BCFF) // 梦幻紫
    val color3 = if (isDarkTheme) Color(0xFF9E4870) else Color(0xFFFFD8E4) // 浪漫粉桃
    val color4 = if (isDarkTheme) Color(0xFF7C6D20) else Color(0xFFFFE088) // 暖阳明黄

    val surfaceContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(surfaceContainerColor)
            .drawBehind {
                val width = size.width
                val height = size.height

                if (width <= 0f || height <= 0f) return@drawBehind

                // Blob 1: 天空蓝 (左上 ↔ 中间)
                val c1X = width * (0.35f + 0.25f * sin(time))
                val c1Y = height * (0.3f + 0.2f * cos(time * 0.8f))
                val r1 = max(width, height) * (0.55f + 0.1f * sin(time * 1.2f))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color1.copy(alpha = 0.55f), Color.Transparent),
                        center = Offset(c1X, c1Y),
                        radius = r1,
                    ),
                    center = Offset(c1X, c1Y),
                    radius = r1,
                )

                // Blob 2: 梦幻紫 (右上 ↔ 右下)
                val c2X = width * (0.75f + 0.2f * cos(time * 1.1f))
                val c2Y = height * (0.6f + 0.25f * sin(time * 0.9f))
                val r2 = max(width, height) * (0.5f + 0.1f * cos(time))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color2.copy(alpha = 0.5f), Color.Transparent),
                        center = Offset(c2X, c2Y),
                        radius = r2,
                    ),
                    center = Offset(c2X, c2Y),
                    radius = r2,
                )

                // Blob 3: 暖黄 (左下 ↔ 中上)
                val c3X = width * (0.4f + 0.3f * cos(time * 0.7f))
                val c3Y = height * (0.8f + 0.15f * sin(time * 1.3f))
                val r3 = max(width, height) * (0.45f + 0.1f * sin(time * 0.8f))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color4.copy(alpha = 0.45f), Color.Transparent),
                        center = Offset(c3X, c3Y),
                        radius = r3,
                    ),
                    center = Offset(c3X, c3Y),
                    radius = r3,
                )

                // Blob 4: 浪漫粉桃 (中心浮动)
                val c4X = width * (0.6f + 0.25f * sin(time * 1.3f))
                val c4Y = height * (0.25f + 0.2f * cos(time * 1.1f))
                val r4 = max(width, height) * (0.4f + 0.08f * cos(time * 1.4f))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color3.copy(alpha = 0.4f), Color.Transparent),
                        center = Offset(c4X, c4Y),
                        radius = r4,
                    ),
                    center = Offset(c4X, c4Y),
                    radius = r4,
                )

                // 柔和半透明渐变罩层，确保前景文字在所有背景位置均具备极佳对比度
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            surfaceContainerColor.copy(alpha = 0.35f),
                            surfaceContainerColor.copy(alpha = 0.65f),
                        ),
                    ),
                )
            },
    ) {
        content()
    }
}
