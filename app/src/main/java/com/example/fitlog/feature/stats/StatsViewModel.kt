package com.example.fitlog.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitlog.data.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import javax.inject.Inject

/**
 * Stats 统计页 ViewModel。
 *
 * ## 状态装配
 *
 * 周期档位 [period] 驱动取数区间：切换档位经 [flatMapLatest] 取消旧查询、
 * 订阅新区间（快速连点时末次档位胜出，图表直接形变到最终档）；
 * 档位不变时的数据变化（新增/编辑训练）由 Room 响应式重发，桶 id 不变，
 * AnimatedBarChart 原地变形。聚合计算委托纯函数 [StatsChartDataBuilder]。
 *
 * 注意：[today] 在 ViewModel 创建时固定，跨零点不刷新（同 TodayViewModel 的 v1 取舍）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
) : ViewModel() {

    /** 本地 UI 事件态：当前选中的周期档位。 */
    private val period = MutableStateFlow(StatsPeriod.WEEK)

    private val today: LocalDate = LocalDate.now()

    /** 页面 UI 状态流：档位 → 区间查询 → Kotlin 聚合 → 图表状态。 */
    val uiState: StateFlow<StatsUiState> = period.flatMapLatest { p ->
        val range = StatsChartDataBuilder.rangeOf(p, today)
        workoutRepository.getByDateRange(range.start, range.endInclusive).map { workouts ->
            StatsUiState(
                isLoading = false,
                period = p,
                chart = StatsChartDataBuilder.build(workouts, p, today),
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StatsUiState(),
    )

    /** 切换统计周期档位。 */
    fun onPeriodSelected(period: StatsPeriod) = this.period.update { period }
}
