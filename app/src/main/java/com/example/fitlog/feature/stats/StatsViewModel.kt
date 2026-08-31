package com.example.fitlog.feature.stats

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitlog.data.repository.BodyMetricRepository
import com.example.fitlog.data.repository.WorkoutRepository
import com.example.fitlog.model.BodyMetric
import com.example.fitlog.model.Workout
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
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

    /**
     * 数据层异常通道：guard 捕获后写入，组装进 [StatsUiState.errorMessage]，
     * 弹窗关闭即清除。与 TodayViewModel.dataError 同模式——错误必须走独立通道，
     * 在流末端 catch 后发射错误态会终结整条流，弹窗将永远关不掉。
     */
    private val dataError = MutableStateFlow<String?>(null)

    /** 数据流降级包装：上游异常时写入 [dataError] 并发射 [fallback]，保证 combine 链存活。 */
    private fun <T> Flow<T>.guard(fallback: T): Flow<T> = catch { e ->
        dataError.value = e.message ?: "数据加载失败，请重试"
        emit(fallback)
    }

    private val today: LocalDate = LocalDate.now()

    /** 档位与其区间查询结果的原子对（防"档先切数后到"错帧）。 */
    private data class PeriodWorkouts(
        val period: StatsPeriod,
        val workouts: List<Workout>,
    )

    private val periodWorkouts = period.flatMapLatest { p ->
        val range = StatsChartDataBuilder.rangeOf(p, today)
        workoutRepository.getByDateRange(range.start, range.endInclusive)
            .map { workouts -> PeriodWorkouts(p, workouts) }
            .guard(PeriodWorkouts(p, emptyList()))
    }

    private val yearWorkouts = workoutRepository.getByDateRange(
        StatsHeatmapBuilder.windowStart(today),
        today,
    ).guard(emptyList())

    private val weightMetrics = bodyMetricRepository.getByDateRange(
        StatsWeightBuilder.windowStart(today),
        today,
    ).guard(emptyList())

    /** 页面 UI 状态流：三流组合 + 错误通道 → 纯函数装配。 */
    val uiState: StateFlow<StatsUiState> = combine(
        periodWorkouts,
        yearWorkouts,
        weightMetrics,
        dataError,
    ) { periodData, yearData, metrics, error ->
        StatsUiState(
            isLoading = false,
            period = periodData.period,
            chart = StatsChartDataBuilder.build(periodData.workouts, periodData.period, today),
            overview = StatsOverviewBuilder.build(periodData.workouts, periodData.period, today),
            heatmap = StatsHeatmapBuilder.build(yearData, today),
            weight = StatsWeightBuilder.build(metrics, today),
            errorMessage = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StatsUiState(),
    )

    /** 错误提示已展示，清除错误信息（独立通道，链路始终存活）。 */
    fun onErrorShown() {
        dataError.value = null
    }

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
            // 页面驻留可跨零点：预填与写入都以"当下"为准，不能用 VM 创建时固定的 today
            val now = LocalDate.now()
            val existing = runCatching {
                bodyMetricRepository.getByDateRange(now, now).map { it.firstOrNull() }.first()
            }.getOrElse { e ->
                Log.w(TAG, "读取今日体重失败，弹层以空值打开", e)
                null
            }
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
     *
     * 写路径取 [LocalDate.now] 而非 VM 创建时固定的 [today]：页面驻留跨零点后提交，
     * 固定值会把体重写到"昨天"的 date 主键上覆盖真实记录——显示层跨零点不刷新是
     * 可接受的 v1 取舍，写层不行。
     */
    fun onWeightSubmit() {
        val parsed = _weightSheetState.value.input.trim().toFloatOrNull()
        when {
            parsed == null -> _weightSheetState.update { it.copy(error = "请输入数字") }
            parsed !in 20f..300f ->
                _weightSheetState.update { it.copy(error = "体重需在 20–300 kg 之间") }
            else -> viewModelScope.launch {
                try {
                    bodyMetricRepository.upsert(
                        BodyMetric(date = LocalDate.now(), weightKg = parsed),
                    )
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

    private companion object {
        private const val TAG = "StatsViewModel"
    }
}
