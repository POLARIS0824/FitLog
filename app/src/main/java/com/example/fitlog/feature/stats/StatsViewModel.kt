package com.example.fitlog.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitlog.data.repository.BodyMetricRepository
import com.example.fitlog.data.repository.WorkoutRepository
import com.example.fitlog.model.BodyMetric
import com.example.fitlog.model.Workout
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Stats 统计页 ViewModel。
 *
 * ## 状态装配
 *
 * 三条数据流组合为单一 [uiState]：
 *
 * - **周期流**：档位 [period] 经 [flatMapLatest] 驱动容量图表 + 概览网格的取数区间
 *   （快速连点末次档位胜出；档位与查询结果包装为原子对 [PeriodWorkouts]，
 *   杜绝"档先切、数后到"的瞬态错帧，同 TodayViewModel ModeHistory 先例）
 * - **热力图流**：固定 53 周窗口（[StatsHeatmapBuilder.windowStart]），独立于档位
 * - **体重流**：固定 90 天窗口（[StatsWeightBuilder.windowStart]），独立于档位
 *
 * 聚合计算全部委托纯函数 builder（[StatsChartDataBuilder] / [StatsOverviewBuilder] /
 * [StatsHeatmapBuilder] / [StatsWeightBuilder]），本类只做装配与事件分发。
 *
 * 注意：不挂种子门——seeder 只写 exercises/plans 两张表，不碰本页读取的
 * workouts/body_metrics，门只会白拖首装首帧（偏离 TodayViewModel 惯例的刻意取舍）；
 * [today] 在 ViewModel 创建时固定，跨零点不刷新（同 TodayViewModel 的 v1 取舍）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val bodyMetricRepository: BodyMetricRepository,
) : ViewModel() {

    /** 本地 UI 事件态：当前选中的周期档位。 */
    private val period = MutableStateFlow(StatsPeriod.WEEK)

    private val today: LocalDate = LocalDate.now()

    /** 档位与其区间查询结果的原子对（防"档先切数后到"错帧）。 */
    private data class PeriodWorkouts(
        val period: StatsPeriod,
        val workouts: List<Workout>,
    )

    private val periodWorkouts = period.flatMapLatest { p ->
        val range = StatsChartDataBuilder.rangeOf(p, today)
        workoutRepository.getByDateRange(range.start, range.endInclusive).map { workouts ->
            PeriodWorkouts(p, workouts)
        }
    }

    private val yearWorkouts = workoutRepository.getByDateRange(
        StatsHeatmapBuilder.windowStart(today),
        today,
    )

    private val weightMetrics = bodyMetricRepository.getByDateRange(
        StatsWeightBuilder.windowStart(today),
        today,
    )

    /** 页面 UI 状态流：三流组合 → 纯函数装配。 */
    val uiState: StateFlow<StatsUiState> = combine(
        periodWorkouts,
        yearWorkouts,
        weightMetrics,
    ) { periodData, yearData, metrics ->
        StatsUiState(
            isLoading = false,
            period = periodData.period,
            chart = StatsChartDataBuilder.build(periodData.workouts, periodData.period, today),
            overview = StatsOverviewBuilder.build(periodData.workouts, periodData.period, today),
            heatmap = StatsHeatmapBuilder.build(yearData, today),
            weight = StatsWeightBuilder.build(metrics, today),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StatsUiState(),
    )

    // ── 体重录入弹层（表单状态在 ViewModel；弹层显隐是 Screen 的 UI transient） ──

    private val _weightSheetState = MutableStateFlow(WeightSheetState())

    /** 体重弹层表单状态（与 [uiState] 平级暴露，同 TodayViewModel.allPlans 先例）。 */
    val weightSheetState: StateFlow<WeightSheetState> = _weightSheetState.asStateFlow()

    /** 切换统计周期档位。 */
    fun onPeriodSelected(period: StatsPeriod) = this.period.update { period }

    /**
     * 弹层打开：一次性读取今日记录预填输入框（体现按天 upsert 语义）。
     * 不用 stateIn 常驻流——`.value` 不构成订阅，WhileSubscribed 下上游永不启动。
     */
    fun onWeightSheetOpened() {
        viewModelScope.launch {
            val existing = bodyMetricRepository.getByDateRange(today, today)
                .map { it.firstOrNull() }
                .first()
            _weightSheetState.update {
                it.copy(input = existing?.weightKg?.toString().orEmpty(), error = null)
            }
        }
    }

    /** 输入变更：更新原始字符串并清除错误（保存时才解析，同 ProfileViewModel 先例）。 */
    fun onWeightInputChange(value: String) =
        _weightSheetState.update { it.copy(input = value, error = null) }

    /**
     * 提交体重：校验通过后按天 upsert 并发出 [WeightSheetState.savedTick] 信号。
     * 校验失败/写库失败只更新 error，不动 savedTick（Screen 不关弹层）。
     */
    fun onWeightSubmit() {
        val parsed = _weightSheetState.value.input.trim().toFloatOrNull()
        when {
            parsed == null -> _weightSheetState.update { it.copy(error = "请输入数字") }
            parsed !in 20f..300f ->
                _weightSheetState.update { it.copy(error = "体重需在 20–300 kg 之间") }
            else -> viewModelScope.launch {
                try {
                    bodyMetricRepository.upsert(BodyMetric(date = today, weightKg = parsed))
                    _weightSheetState.update {
                        it.copy(input = "", error = null, savedTick = it.savedTick + 1)
                    }
                } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _weightSheetState.update { it.copy(error = "保存失败，请重试") }
                }
            }
        }
    }

    /** 弹层关闭：清空表单与错误；savedTick 保留单调性（再次打开不算新保存）。 */
    fun onWeightSheetDismissed() =
        _weightSheetState.update { it.copy(input = "", error = null) }
}
