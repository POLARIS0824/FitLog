package com.example.fitlog.feature.today

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.fitlog.model.ai.CoachAction
import com.example.fitlog.ui.components.GeminiFlowingGradientBackground
import com.example.fitlog.ui.theme.FitLogTheme

/**
 * Coach Insight 卡片：左侧为 Coach 圆形图标，右侧垂直排列标签、问候、观察、建议与动作按钮槽。
 *
 * 整体布局采用左右双栏模式 (Row)，右侧包含：
 * 1. Coach 标签行（AI 生成/加载中显示 "AI Coach" + 波浪进度，规则版显示 "Coach"）
 * 2. 问候语（规则硬编码，按时段 + 用户名）
 * 3. 观察文案（规则版为训练摘要；AI 版为教练观察，Crossfade 替换）
 * 4. 训练建议 (加粗主色)
 * 5. 动作按钮槽：仅 [CoachAction.START_WORKOUT] 显示"开始训练"，其余无按钮
 *
 * 背景基于 [GeminiFlowingGradientBackground] 渲染 Gemini 风格流动渐变。
 *
 * @param insight Coach 建议状态数据（规则版底版 + AI 替换，见 [CoachInsightState]）
 * @param onStartWorkoutClick 点击“开始训练”按钮时的回调
 * @param modifier 修饰符
 */
@Composable
internal fun CoachInsightCard(
    insight: CoachInsightState,
    onStartWorkoutClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    GeminiFlowingGradientBackground(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // 左侧：仅在左上角放置一个 AI Coach 圆形图标
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(42.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // 右侧：全部文字与按钮在 Column 内垂直对齐
            Column(
                modifier = Modifier.weight(1f),
            ) {
                // 1. Coach 标签行：AI 生成/加载中显示 "AI Coach"，规则版显示 "Coach"；
                //    AI 请求进行时在标签旁展示小号波浪进度（内容区保持规则版文案）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (insight.isAiGenerated || insight.isAiLoading) "AI Coach" else "Coach",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (insight.isAiLoading) {
                        CircularWavyProgressIndicator(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(16.dp),
                        )
                    }
                }

                // 2. 问候语 (紧贴 Coach 标签，间距 2dp)
                Text(
                    text = "${insight.greeting} 👋",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                )

                if (insight.isAvailable) {
                    // 3+4. 观察与建议：AI 内容到达时 Crossfade 替换规则版文案
                    Crossfade(
                        targetState = insight.observation to insight.recommendation,
                        label = "coachInsightContent",
                    ) { (observation, recommendation) ->
                        Column {
                            // 3. 基于最近训练的观察
                            Text(
                                text = observation,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // 4. 高亮显示的教练针对性建议
                            Text(
                                text = recommendation,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    // 5. 动作按钮槽：仅建议训练时显示"开始训练"；建议休息等场景无按钮
                    if (insight.action == CoachAction.START_WORKOUT) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onStartWorkoutClick,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "开始训练",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "完成首次训练或选择一套计划后，这里会生成你的专属训练建议",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Coach 卡 · AI 加载态：规则版文案先上屏，"AI Coach" 标签旁波浪进度提示生成中。 */
@Preview(showBackground = true)
@Composable
private fun CoachInsightCardAiLoadingPreview() {
    FitLogTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            CoachInsightCard(
                insight = CoachInsightState(
                    greeting = "下午好，Polaris",
                    observation = "本周已练 2/3 次 · 距上次训练 1 天",
                    recommendation = "下一课：腿日 · 股四头后侧链",
                    action = CoachAction.START_WORKOUT,
                    isAiLoading = true,
                    isAvailable = true,
                ),
            )
        }
    }
}

/** Coach 卡 · AI 建议休息态：label 为 "AI Coach"，无按钮。 */
@Preview(showBackground = true)
@Composable
private fun CoachInsightCardRestPreview() {
    FitLogTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            CoachInsightCard(
                insight = CoachInsightState(
                    greeting = "下午好，Polaris",
                    observation = "昨天练了腿，今天身体需要恢复",
                    recommendation = "建议 30 分钟散步 + 10 分钟拉伸放松",
                    action = CoachAction.REST,
                    isAiGenerated = true,
                    isAvailable = true,
                ),
            )
        }
    }
}
