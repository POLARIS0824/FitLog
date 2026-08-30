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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitlog.model.BodyMetric
import com.example.fitlog.model.ExerciseLog
import com.example.fitlog.model.SetLog
import com.example.fitlog.model.Workout
import com.example.fitlog.ui.components.FitLogCard
import com.example.fitlog.ui.components.MetricChart
import com.example.fitlog.ui.components.MetricChartCard
import com.example.fitlog.ui.components.MetricChartCardGrid
import com.example.fitlog.ui.components.MetricChartCardState
import com.example.fitlog.ui.components.SectionLabel
import com.example.fitlog.ui.components.chart.AnimatedBarChart
import com.example.fitlog.ui.components.chart.ContributionHeatmap
import com.example.fitlog.ui.components.chart.MiniLineStyle
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
    val weightSheetState by viewModel.weightSheetState.collectAsStateWithLifecycle()
    StatsScreen(
        uiState = uiState,
        weightSheetState = weightSheetState,
        onPeriodSelected = viewModel::onPeriodSelected,
        onWeightSheetOpened = viewModel::onWeightSheetOpened,
        onWeightInputChange = viewModel::onWeightInputChange,
        onWeightSubmit = viewModel::onWeightSubmit,
        onWeightSheetDismissed = viewModel::onWeightSheetDismissed,
        onErrorShown = viewModel::onErrorShown,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * 2. 纯 UI 展示层 (Stateless)
 *
 * Stats 统计页，自上而下：
 * 周期档位（仅驱动「训练」两区）→ 容量图表（hero）→ 概览网格 →
 * 「坚持度」53 周热力图 → 「身体」体重折线卡（点击开录入弹层）。
 *
 * 图表均为通用组件（[AnimatedBarChart] / [ContributionHeatmap] / [MetricChartCard]），
 * 本页只负责聚合喂数（builder 四件套），动画由组件内部状态机完成。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    uiState: StatsUiState,
    weightSheetState: WeightSheetState,
    onPeriodSelected: (StatsPeriod) -> Unit,
    onWeightSheetOpened: () -> Unit,
    onWeightInputChange: (String) -> Unit,
    onWeightSubmit: () -> Unit,
    onWeightSheetDismissed: () -> Unit,
    onErrorShown: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var showWeightSheet by remember { mutableStateOf(false) }

    // 保存成功信号：savedTick 单调递增，>0 即关弹层（再次打开不会误触发）
    LaunchedEffect(weightSheetState.savedTick) {
        if (weightSheetState.savedTick > 0) showWeightSheet = false
    }

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
                OverviewGrid(overview = uiState.overview)
                SectionLabel("坚持度")
                HeatmapCard(heatmap = uiState.heatmap)
                SectionLabel("身体")
                WeightCard(
                    weight = uiState.weight,
                    onClick = {
                        onWeightSheetOpened()
                        showWeightSheet = true
                    },
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showWeightSheet) {
        WeightRecordSheet(
            state = weightSheetState,
            onInputChange = onWeightInputChange,
            onSubmit = onWeightSubmit,
            onDismiss = {
                showWeightSheet = false
                onWeightSheetDismissed()
            },
        )
    }

    // 错误提示（数据层异常经独立通道上屏，关闭即清除）
    uiState.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = onErrorShown,
            confirmButton = { TextButton(onClick = onErrorShown) { Text("知道了") } },
            title = { Text("出错了") },
            text = { Text(message) },
        )
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
 * 柱色/目标线色走 AnimatedBarChart 默认（primary/tertiary，dynamic color 合规）。
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
 * 概览网格：2×2 指标卡（次数/总容量/平均单次/正式组数），随周期档位联动。
 * 纯数字卡不带迷你图（上方 hero 图已展示趋势形状）；accent 留 null 取主题 primary。
 */
@Composable
private fun OverviewGrid(
    overview: StatsOverviewState,
    modifier: Modifier = Modifier,
) {
    MetricChartCardGrid(
        cards = overview.items.map { item ->
            MetricChartCardState(title = item.title, valueText = item.valueText)
        },
        modifier = modifier,
    )
}

/**
 * 坚持度卡片：头部摘要（训练天数 + 最长连续）+ 53 周贡献热力图。
 * 全空时热力图仍渲染空网格——「快开始第一节训练」的邀请，优于占位文案。
 */
@Composable
private fun HeatmapCard(
    heatmap: StatsHeatmapState,
    modifier: Modifier = Modifier,
) {
    FitLogCard(modifier = modifier) {
        Text(
            text = if (heatmap.trainedDays > 0) {
                "近一年训练 ${heatmap.trainedDays} 天 · 最长连续 ${heatmap.longestStreak} 天"
            } else {
                "近一年暂无训练记录"
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ContributionHeatmap(
            values = heatmap.values,
            // 显式锚定网格末端：组件默认 LocalDate.now() 实时求值，与数据窗口
            // （VM 创建时固定的 today）跨零点后发散，旧一周数据会从图上静默消失
            endDate = heatmap.endDate,
            weekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日"),
            contentDescription = "过去一年训练热力图，${heatmap.trainedDays} 天有训练，" +
                "最长连续 ${heatmap.longestStreak} 天",
        )
    }
}

/**
 * 体重卡片：最新值 + 环比 pill + 迷你折线；整卡点击开录入弹层。
 * 空态为 Dotted 点状平线 + outline 灰调（MetricChartCard 空态惯例），
 * statusText 承担「点击记录体重」的可发现性提示。
 */
@Composable
private fun WeightCard(
    weight: StatsWeightState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MetricChartCard(
        state = MetricChartCardState(
            title = "体重",
            valueText = weight.valueText,
            chart = MetricChart.Line(
                data = weight.chartData,
                style = if (weight.hasData) MiniLineStyle.Solid else MiniLineStyle.Dotted,
                lineWidth = if (weight.hasData) 2.dp else 3.dp,
            ),
            xLabels = weight.xLabels,
            statusText = weight.deltaText ?: "点击记录体重",
            accentColor = if (weight.hasData) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.outline
            },
        ),
        onClick = onClick,
        chartHeight = 96.dp,
        modifier = modifier,
    )
}

/**
 * 体重录入弹层：ModalBottomSheet 包 [WeightSheetContent]（内容独立便于预览）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeightRecordSheet(
    state: WeightSheetState,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        WeightSheetContent(
            state = state,
            onInputChange = onInputChange,
            onSubmit = onSubmit,
        )
    }
}

/**
 * 体重录入内容：标题 + 按天覆盖说明 + 数字输入 + 保存按钮。
 * 非法输入经 isError/supportingText 内联提示，不关弹层。
 */
@Composable
private fun WeightSheetContent(
    state: WeightSheetState,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "记录体重", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "记录今天的体重，重复记录会覆盖",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.input,
            onValueChange = onInputChange,
            label = { Text("体重 (kg)") },
            singleLine = true,
            isError = state.error != null,
            supportingText = state.error?.let { error -> { Text(error) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("保存")
        }
        Spacer(modifier = Modifier.height(32.dp)) // 避开底部手势区
    }
}

// ──────────────────────────────────────
// 3. 预览层（mock 数据走真实 Builder 聚合，所见即生产口径）
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

/** 近一年的伪随机训练历史：约 55% 的日子有训练（热力图/年档预览用）。 */
private fun previewYearWorkouts(today: LocalDate): List<Workout> =
    (0L..364L).mapNotNull { i ->
        val date = today.minusDays(i)
        val roll = (date.toEpochDay() * 37 % 100).toFloat() / 100f
        if (roll < 0.55f) {
            previewWorkout(date, 500f + (date.toEpochDay() * 13 % 1500).toFloat())
        } else {
            null
        }
    }

/** 近两个月的体重记录：缓降趋势（体重卡预览用）。 */
private fun previewBodyMetrics(today: LocalDate): List<BodyMetric> =
    (0L..59L step 3).map { i ->
        BodyMetric(date = today.minusDays(59 - i), weightKg = 75.5f - i * 0.05f)
    }

/** 组装全 section 预览状态：四区都走真实 builder。 */
private fun previewUiState(period: StatsPeriod, today: LocalDate): StatsUiState {
    val yearWorkouts = previewYearWorkouts(today)
    return StatsUiState(
        isLoading = false,
        period = period,
        chart = StatsChartDataBuilder.build(yearWorkouts, period, today),
        overview = StatsOverviewBuilder.build(yearWorkouts, period, today),
        heatmap = StatsHeatmapBuilder.build(yearWorkouts, today),
        weight = StatsWeightBuilder.build(previewBodyMetrics(today), today),
    )
}

/** 周视图预览：全 section 有数据。 */
@Preview(showBackground = true)
@Composable
private fun StatsScreenWeekPreview() {
    val today = LocalDate.now()
    FitLogTheme {
        StatsScreen(
            uiState = previewUiState(StatsPeriod.WEEK, today),
            weightSheetState = WeightSheetState(),
            onPeriodSelected = {},
            onWeightSheetOpened = {},
            onWeightInputChange = {},
            onWeightSubmit = {},
            onWeightSheetDismissed = {},
            onBack = {},
        )
    }
}

/** 年视图预览：12 根月柱 + 全年热力图。 */
@Preview(showBackground = true)
@Composable
private fun StatsScreenYearPreview() {
    val today = LocalDate.now()
    FitLogTheme {
        StatsScreen(
            uiState = previewUiState(StatsPeriod.YEAR, today),
            weightSheetState = WeightSheetState(),
            onPeriodSelected = {},
            onWeightSheetOpened = {},
            onWeightInputChange = {},
            onWeightSubmit = {},
            onWeightSheetDismissed = {},
            onBack = {},
        )
    }
}

/** 空态预览：零值概览 + 空热力图 + Dotted 体重卡。 */
@Preview(showBackground = true)
@Composable
private fun StatsScreenEmptyPreview() {
    val today = LocalDate.now()
    FitLogTheme {
        StatsScreen(
            uiState = StatsUiState(
                isLoading = false,
                chart = StatsChartDataBuilder.build(emptyList(), StatsPeriod.WEEK, today),
                overview = StatsOverviewBuilder.build(emptyList(), StatsPeriod.WEEK, today),
                heatmap = StatsHeatmapBuilder.build(emptyList(), today),
                weight = StatsWeightBuilder.build(emptyList(), today),
            ),
            weightSheetState = WeightSheetState(),
            onPeriodSelected = {},
            onWeightSheetOpened = {},
            onWeightInputChange = {},
            onWeightSubmit = {},
            onWeightSheetDismissed = {},
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
            weightSheetState = WeightSheetState(),
            onPeriodSelected = {},
            onWeightSheetOpened = {},
            onWeightInputChange = {},
            onWeightSubmit = {},
            onWeightSheetDismissed = {},
            onBack = {},
        )
    }
}

/** 体重录入内容预览：含校验错误态。 */
@Preview(showBackground = true)
@Composable
private fun WeightSheetContentPreview() {
    FitLogTheme {
        Column {
            WeightSheetContent(
                state = WeightSheetState(input = "74.5"),
                onInputChange = {},
                onSubmit = {},
            )
            WeightSheetContent(
                state = WeightSheetState(input = "abc", error = "请输入数字"),
                onInputChange = {},
                onSubmit = {},
            )
        }
    }
}
