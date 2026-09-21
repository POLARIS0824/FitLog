package com.example.fitlog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fitlog.ui.theme.FitLogTheme
import com.example.fitlog.ui.theme.FitLogShapes
import com.example.fitlog.ui.theme.fitLogColors
import java.time.LocalDate
import java.time.format.TextStyle
import androidx.compose.ui.platform.LocalLocale

/**
 * 单日训练状态枚举。
 */
enum class DayWorkoutStatus {
    /** 已完成训练（展现为高反差独立打卡实体胶囊） */
    COMPLETED,

    /** 今日待练（今天有训练安排且尚未打卡，等待开始） */
    PENDING,

    /** 休息日（过去未安排训练，或标记的休息日） */
    REST,

    /** 未来计划训练日（未来有安排但尚未到达） */
    PLANNED,

    /** 未来普通日/未安排 */
    FUTURE,
}

/**
 * 训练周历中的单日数据模型。
 *
 * @param date 日期
 * @param dayLabel 星期显示字符（如 "S", "M", "T" 或 "一", "二", "三"）
 * @param isToday 是否为今天
 * @param status 当天训练状态
 * @param workoutTitle 可选的训练日名称（如 "腿日 · 股四", "推日" 等）
 */
data class WeeklyTrackerDay(
    val date: LocalDate,
    val dayLabel: String,
    val isToday: Boolean = false,
    val status: DayWorkoutStatus = DayWorkoutStatus.REST,
    val workoutTitle: String? = null,
)

/**
 * 周训练追踪卡片 (Weekly Workout Tracker)：
 *
 * 遵循 **Material 3 Expressive (M3E)** 规范与极简现代健身美学全新打造。
 *
 * ## 设计语言与交互心智：
 * 1. **“完成即实体化”的胶囊打卡徽章 (Punch-out Workout Capsule)**：
 *    每一个已完成训练的日子（COMPLETED）都会实体化为一个独立的**高反差圆润立体胶囊**，
 *    内部嵌入微型圆形对勾徽章与白色星期字母，直观展现本周所有训练日的分布节奏。
 * 2. **极致对齐的几何韵律**：
 *    所有 7 天无论是否为胶囊形态，顶部的指示圆心（18dp 槽位）与底部的星期字母高度完全像素级对齐，
 *    消灭任何上下晃动的杂乱感。
 * 3. **M3 专属语义色彩系统 (M3E Semantic Theming)**：
 *    - 卡片容器：`MaterialTheme.fitLogColors.card`（纯净色阶底版）；
 *    - 打卡胶囊：`MaterialTheme.colorScheme.inverseSurface`；
 *      * 浅色模式下呈现深黑曜石高光质感（1:1 还原用户参考图）；
 *      * 深色模式下呈现高亮浮雕白玉质感；
 *    - 胶囊内徽章：`MaterialTheme.colorScheme.inverseOnSurface` 配反色微型 Check 对勾；
 *    - 未练/休息日：低对比度柔和表面色圆点；
 *    - 未来日期：优雅退火（透明度 0.30f）。
 * 4. **纯粹自信的顶栏排版**：
 *    采用紧凑无容器包袱的 `titleLarge` 粗体，右侧灵动内联火苗/哑铃图标与本周达成数。
 *
 * @param days 本周 7 天的数据列表
 * @param modifier 修饰符
 * @param completedCount 本周已完成训练次数，为 null 时自动统计 [days] 中 [DayWorkoutStatus.COMPLETED] 的天数
 * @param targetCount 本周目标训练次数（如 4 次）
 * @param title 卡片标题，默认为 "this week"
 * @param showTargetRatio 是否在右上角展示目标比率（如 "3/4"，false 则展示参考图风格的纯数字 "3"）
 * @param badgeIcon 计数图标，默认为火苗图标
 * @param onDayClick 点击单日槽位时的可选回调
 */
@Composable
fun WeeklyWorkoutTracker(
    days: List<WeeklyTrackerDay>,
    modifier: Modifier = Modifier,
    completedCount: Int? = null,
    targetCount: Int = 4,
    title: String = "this week",
    showTargetRatio: Boolean = false,
    badgeIcon: ImageVector = Icons.Default.Whatshot,
    onDayClick: ((WeeklyTrackerDay) -> Unit)? = null,
) {
    val actualCompletedCount = completedCount ?: days.count { it.status == DayWorkoutStatus.COMPLETED }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = FitLogShapes.Card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.fitLogColors.card,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            // ── 顶部头部栏：纯粹粗体标题 + 灵动计数 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )

                // 纯净内联计数（火苗 + 数字）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        imageVector = badgeIcon,
                        contentDescription = null,
                        tint = if (actualCompletedCount > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        },
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = if (showTargetRatio && targetCount > 0) {
                            "$actualCompletedCount/$targetCount"
                        } else {
                            "$actualCompletedCount"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ── 周历 7 天槽位行 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                days.forEach { day ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        WeeklyDaySlot(
                            day = day,
                            onClick = onDayClick?.let { { it(day) } },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 单日槽位统一渲染组件：
 * 保证所有 7 天无论是否处于高亮胶囊态，顶部圆点与底部字母在 Y 轴上绝对对齐。
 */
@Composable
private fun WeeklyDaySlot(
    day: WeeklyTrackerDay,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // 无障碍描述用本地化完整星期名（dayLabel 是单字符展示符，英文下 "周S"
    // 对 TalkBack 无意义）；dayLabel 仅供视觉展示
    val weekdayName = day.date.dayOfWeek.getDisplayName(TextStyle.FULL, LocalLocale.current.platformLocale)
    val a11yDesc = "${if (day.isToday) "今天 " else ""}$weekdayName, ${describeStatus(day.status)}"

    // 是否需要呈现为胶囊实体：
    // 1. 已完成打卡 (COMPLETED)
    // 2. 今天且有待办任务 (isToday && PENDING)
    val showCapsule = day.status == DayWorkoutStatus.COMPLETED || (day.isToday && day.status == DayWorkoutStatus.PENDING)

    val capsuleColor = MaterialTheme.colorScheme.inverseSurface
    val capsuleContentColor = MaterialTheme.colorScheme.inverseOnSurface

    val isFuture = day.status == DayWorkoutStatus.FUTURE || day.status == DayWorkoutStatus.PLANNED
    val textAlpha = if (isFuture) 0.30f else 1.0f

    // 触控槽填满 weight 单元（≈47dp，接近 M3 48dp 最小触控目标）；
    // 视觉胶囊保持 36dp 不变（内层 Box）
    Box(
        modifier = modifier
            .height(68.dp)
            .semantics {
                role = Role.Button
                contentDescription = a11yDesc
            }
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .fillMaxHeight()
                .then(
                    if (showCapsule) {
                        Modifier
                            .clip(CircleShape)
                            .background(capsuleColor)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
            // ── 顶部状态指示区 (固定 18dp 槽位，确保基线绝对水平对齐) ──
            Box(
                modifier = Modifier.size(18.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    // 1. 已完成训练：高对比度圆形徽章 + 微型对勾
                    day.status == DayWorkoutStatus.COMPLETED -> {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(capsuleContentColor),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = capsuleColor,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }

                    // 2. 今天待练态：胶囊内的微光等待圈
                    day.isToday && day.status == DayWorkoutStatus.PENDING -> {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(capsuleContentColor.copy(alpha = 0.25f)),
                        )
                    }

                    // 3. 未来计划训练日：细线轮廓环
                    day.status == DayWorkoutStatus.PLANNED -> {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .border(
                                    width = 1.5.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                    shape = CircleShape,
                                ),
                        )
                    }

                    // 4. 普通未安排日 / 休息日：柔和实心圆点
                    day.status == DayWorkoutStatus.REST -> {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        )
                    }

                    // 5. 未来普通日：极淡低透明度圆点
                    else -> {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f)),
                        )
                    }
                }
            }

            // ── 底部星期字母 ──
            Text(
                text = day.dayLabel,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                ),
                color = if (showCapsule) {
                    capsuleContentColor
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = textAlpha)
                },
            )
        }
        }
    }
}

private fun describeStatus(status: DayWorkoutStatus): String = when (status) {
    DayWorkoutStatus.COMPLETED -> "已完成训练"
    DayWorkoutStatus.PENDING -> "今日待练"
    DayWorkoutStatus.REST -> "休息日"
    DayWorkoutStatus.PLANNED -> "计划训练日"
    DayWorkoutStatus.FUTURE -> "未安排"
}

// ─────────────────────────────────────────────────────────────
// 预览与效果展示
// ─────────────────────────────────────────────────────────────

/**
 * 对应用户最新上传参考图的单字母测试数据：
 * - S M（休息）
 * - T W T（周二、周三、周四已练！各形成独立打卡胶囊！）
 * - F S（未来柔和弱化）
 */
private fun mockThreeDaysCompletedData(): List<WeeklyTrackerDay> {
    val labels = listOf("S", "M", "T", "W", "T", "F", "S")
    return labels.mapIndexed { index, label ->
        val status = when (index) {
            2, 3, 4 -> DayWorkoutStatus.COMPLETED // 周二(T)、周三(W)、周四(T) 完成训练！
            5, 6 -> DayWorkoutStatus.FUTURE       // 周五(F)、周六(S) 未来
            else -> DayWorkoutStatus.REST         // 周日(S)、周一(M) 休息
        }
        WeeklyTrackerDay(
            date = LocalDate.now(),
            dayLabel = label,
            isToday = index == 4, // 周四为今天
            status = status,
        )
    }
}

/**
 * 【1:1 还原用户参考图 2】浅色模式 · 3 天打卡胶囊 (T, W, T)
 */
@Preview(name = "Image 2 Match - Light (3 Completed)", showBackground = true)
@Composable
private fun WeeklyWorkoutTrackerImage2MatchLightPreview() {
    FitLogTheme(darkTheme = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            WeeklyWorkoutTracker(
                title = "this week",
                days = mockThreeDaysCompletedData(),
            )
        }
    }
}

/**
 * 【深色模式 · 3 天打卡胶囊】深色背景下的浮雕白玉胶囊
 */
@Preview(name = "Image 2 Match - Dark (3 Completed)", showBackground = true, backgroundColor = 0xFF141218)
@Composable
private fun WeeklyWorkoutTrackerImage2MatchDarkPreview() {
    FitLogTheme(darkTheme = true) {
        Box(modifier = Modifier.padding(16.dp)) {
            WeeklyWorkoutTracker(
                title = "this week",
                days = mockThreeDaysCompletedData(),
            )
        }
    }
}

/**
 * 【单日打卡 + 今天待练态 (还原参考图 1)】周四今天待练，火苗计数 1
 */
@Preview(name = "Image 1 Match - Dark (1 Completed + Today Pending)", showBackground = true, backgroundColor = 0xFF141218)
@Composable
private fun WeeklyWorkoutTrackerImage1MatchDarkPreview() {
    FitLogTheme(darkTheme = true) {
        val labels = listOf("S", "M", "T", "W", "T", "F", "S")
        val days = labels.mapIndexed { index, label ->
            WeeklyTrackerDay(
                date = LocalDate.now(),
                dayLabel = label,
                isToday = index == 4, // 周四
                status = when (index) {
                    1 -> DayWorkoutStatus.COMPLETED // 周一完成
                    4 -> DayWorkoutStatus.PENDING   // 周四今天待练
                    5, 6 -> DayWorkoutStatus.FUTURE // 未来
                    else -> DayWorkoutStatus.REST
                },
            )
        }
        Box(modifier = Modifier.padding(16.dp)) {
            WeeklyWorkoutTracker(
                title = "this week",
                days = days,
            )
        }
    }
}

/**
 * 【中文星期排版 · 目标比率 2/4】中文风格适配
 */
@Preview(name = "Chinese Style - With Target Ratio", showBackground = true)
@Composable
private fun WeeklyWorkoutTrackerChineseStylePreview() {
    FitLogTheme(darkTheme = false) {
        val labels = listOf("一", "二", "三", "四", "五", "六", "日")
        val days = labels.mapIndexed { index, label ->
            WeeklyTrackerDay(
                date = LocalDate.now(),
                dayLabel = label,
                isToday = index == 3, // 周四
                status = when (index) {
                    0, 2 -> DayWorkoutStatus.COMPLETED // 周一、周三完成打卡
                    3 -> DayWorkoutStatus.PENDING      // 周四待练
                    4 -> DayWorkoutStatus.PLANNED      // 周五计划练
                    else -> DayWorkoutStatus.REST
                },
            )
        }
        Box(modifier = Modifier.padding(16.dp)) {
            WeeklyWorkoutTracker(
                title = "本周训练",
                targetCount = 4,
                showTargetRatio = true,
                days = days,
            )
        }
    }
}
