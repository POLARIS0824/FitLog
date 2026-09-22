package com.example.fitlog.feature.today

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
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
import com.example.fitlog.ui.components.GeminiFlowingGradientBackground
import com.example.fitlog.ui.theme.FitLogTheme

/**
 * Today 的 Coach Insight 主卡。流动光晕背景保留，整卡进入教练对话，避免与训练主卡重复动作。
 */
@Composable
internal fun CoachInsightCard(
    insight: CoachInsightState,
    onCoachClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    GeminiFlowingGradientBackground(
        modifier = modifier.clickable(onClick = onCoachClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (insight.isAiGenerated || insight.isAiLoading) "AI COACH INSIGHT" else "COACH INSIGHT",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (insight.isAiLoading) {
                        CircularWavyProgressIndicator(
                            modifier = Modifier.padding(start = 8.dp).size(16.dp),
                        )
                    }
                }

                Text(
                    text = "${insight.greeting} 👋",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )

                if (insight.isAvailable) {
                    Crossfade(
                        targetState = insight.observation to insight.recommendation,
                        label = "coachInsightContent",
                    ) { (observation, recommendation) ->
                        Column {
                            Text(
                                text = observation,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = recommendation,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                } else {
                    Text(
                        text = "完成首次训练或选择一套计划后，这里会生成你的专属训练建议",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(
                    modifier = Modifier.padding(top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "和教练聊聊",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CoachInsightCardPreview() {
    FitLogTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            CoachInsightCard(
                insight = CoachInsightState(
                    greeting = "下午好，Polaris",
                    observation = "本周已练 2/3 次 · 距上次训练 1 天",
                    recommendation = "下一课：腿日 · 股四头后侧链",
                    isAiGenerated = true,
                    isAvailable = true,
                ),
            )
        }
    }
}