package com.example.fitlog.feature.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitlog.model.ExerciseLog
import com.example.fitlog.model.SetLog
import com.example.fitlog.model.Workout
import com.example.fitlog.ui.components.FitLogCard
import com.example.fitlog.ui.components.chart.AnimatedBarChart
import com.example.fitlog.ui.theme.FitLogTheme
import java.time.LocalDate

/**
 * 1. 容器层 (Stateful)
 * 绑定 Hilt ViewModel，处理生命周期感知的状态收集。
 *
 * @param onBack 返回上一页
 */
@Composable
fun StatsRoute(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    StatsScreen(
        uiState = uiState,
        onPeriodSelected = viewModel::onPeriodSelected,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * 2. 纯 UI 展示层 (Stateless)
 *
 * Stats 统计页：周期档位切换 + 日均容量摘要 + 动画柱状图。
 * 图表为通用组件 [AnimatedBarChart]，本页只负责聚合喂数（[StatsChartDataBuilder]），
 * 周期切换/数据更新的插值动画由图表组件内部状态机完成。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    uiState: StatsUiState,
    onPeriodSelected: (StatsPeriod) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "统计", style = MaterialTheme.typography.titleLarge)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            // 加载占位：顶部加载条（同 Today/AISettings 的 isLoading 呈现）
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PeriodSelector(
                    selected = uiState.period,
                    onSelected = onPeriodSelected,
                )
                VolumeChartCard(chart = uiState.chart)
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * 周期档位切换：M3 Expressive 分段按钮（自带选中形变动画、涟漪与无障碍）。
 * 标签均为 1-2 字短文案，无挤压问题（与 WeekProgressSection 的长文案 FilterChip 取舍相反）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodSelector(
    selected: StatsPeriod,
    onSelected: (StatsPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        StatsPeriod.entries.forEachIndexed { index, period ->
            SegmentedButton(
                selected = period == selected,
                onClick = { onSelected(period) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = StatsPeriod.entries.size,
                ),
            ) {
                Text(text = period.label)
            }
        }
    }
}

/**
 * 容量图表卡片：日均摘要头（标签 + 大字数值 + 区间文案）+ 动画柱状图。
 * 无数据时图表位置换占位文案（硬切：无柱无形可变，见 StatsChartState.hasData）。
 */
@Composable
private fun VolumeChartCard(
    chart: StatsChartState,
    modifier: Modifier = Modifier,
) {
    FitLogCard(modifier = modifier) {
        Text(
            text = "日均容量",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = chart.averageVolumeText,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = chart.rangeText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (chart.hasData) {
            AnimatedBarChart(
                data = chart.chartData,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                valueFormatter = StatsChartDataBuilder::formatAxisValue,
                barColor = ChartBarColor,
                goalLineColor = ChartGoalLineColor,
                contentDescription = "训练容量柱状图，${chart.rangeText}，日均 ${chart.averageVolumeText}",
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "该时段暂无训练数据",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 图表柱色/目标线色：参考 Samsung Health 的 teal（动态取色下的硬编码配色，
 * 先例见 MetricCard.kt 的 Steps 卡片 0xFFB2F5EA/0xFF004D40）。
 */
private val ChartBarColor = Color(0xFF2BB5A0)
private val ChartGoalLineColor = Color(0xFF004D40)

// ──────────────────────────────────────
// 3. 预览层（mock 训练日志走真实 Builder 聚合，所见即生产口径）
// ──────────────────────────────────────

/** 造一条指定容量的训练日志：单动作单组，重量×次数 = [volumeKg]。 */
private fun previewWorkout(date: LocalDate, volumeKg: Float): Workout = Workout(
    id = 0L,
    userId = 0L,
    date = date,
    exercises = listOf(
        ExerciseLog(
            name = "预览动作",
            sets = listOf(SetLog(weightKg = volumeKg / 10, reps = 10)),
        ),
    ),
    feelings = null,
)

/** 周视图预览：7 天内 4 次训练。 */
@Preview(showBackground = true)
@Composable
private fun StatsScreenWeekPreview() {
    val today = LocalDate.now()
    val workouts = listOf(
        previewWorkout(today.minusDays(5), 800f),
        previewWorkout(today.minusDays(3), 1250f),
        previewWorkout(today.minusDays(1), 1500f),
        previewWorkout(today, 600f),
    )
    FitLogTheme {
        StatsScreen(
            uiState = StatsUiState(
                isLoading = false,
                period = StatsPeriod.WEEK,
                chart = StatsChartDataBuilder.build(workouts, StatsPeriod.WEEK, today),
            ),
            onPeriodSelected = {},
            onBack = {},
        )
    }
}

/** 月视图预览：30 根柱 + x 标签抽稀。 */
@Preview(showBackground = true)
@Composable
private fun StatsScreenMonthPreview() {
    val today = LocalDate.now()
    val workouts = (0..11).map { i ->
        previewWorkout(today.minusDays((i * 2 + 1).toLong()), (600 + i * 130).toFloat())
    }
    FitLogTheme {
        StatsScreen(
            uiState = StatsUiState(
                isLoading = false,
                period = StatsPeriod.MONTH,
                chart = StatsChartDataBuilder.build(workouts, StatsPeriod.MONTH, today),
            ),
            onPeriodSelected = {},
            onBack = {},
        )
    }
}

/** 空态预览：图表位置展示占位文案。 */
@Preview(showBackground = true)
@Composable
private fun StatsScreenEmptyPreview() {
    val today = LocalDate.now()
    FitLogTheme {
        StatsScreen(
            uiState = StatsUiState(
                isLoading = false,
                chart = StatsChartDataBuilder.build(emptyList(), StatsPeriod.WEEK, today),
            ),
            onPeriodSelected = {},
            onBack = {},
        )
    }
}

/** 加载态预览：仅 TopBar + 顶部加载条。 */
@Preview(showBackground = true)
@Composable
private fun StatsScreenLoadingPreview() {
    FitLogTheme {
        StatsScreen(
            uiState = StatsUiState(isLoading = true),
            onPeriodSelected = {},
            onBack = {},
        )
    }
}
