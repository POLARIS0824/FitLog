package com.example.fitlog.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitlog.data.repository.ExerciseRepository
import com.example.fitlog.data.repository.UserProfileRepository
import com.example.fitlog.data.repository.WorkoutPlanRepository
import com.example.fitlog.data.repository.WorkoutRepository
import com.example.fitlog.data.seed.SeedOrchestrator
import com.example.fitlog.model.Exercise
import com.example.fitlog.model.PlannedSession
import com.example.fitlog.model.Workout
import com.example.fitlog.model.WorkoutPlan
import com.example.fitlog.model.user.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/**
 * Today 主页 ViewModel。
 *
 * ## 状态装配
 *
 * 页面状态由数据层 Flow（Room/DataStore 响应式）与本地 UI 事件 Flow 组合而成：
 * 五元 `combine` 出数据快照（含 [ModeHistory] 原子对），
 * 再与三元 extras（latestWorkout / profile / catalog）汇合，
 * 最终调用三个纯函数（[CoachInsightBuilder]、[WeekProgressCalculator]、[TodayPlanAssembler]）
 * 组装 [TodayUiState]，自身不含业务计算。
 *
 * ## 首发门控
 *
 * 用户资料与动作目录是**无默认值的冷 Flow**（`flow { emit(suspendLoad()) }`）：
 * `combine` 首次发射必须等全部上游首发，因此**第一帧即包含真实 profile/catalog**，
 * 消除"默认值占位 → 真实值"的多段跳变；加载失败经 `catch` 降级（匿名 / 空目录），
 * 不拖垮整条链（否则 uiState 永远停在 isLoading 的 initialValue）。
 *
 * 另有一道**种子门**：[SeedOrchestrator.completed] 未置位前 uiState 不首发
 * （停留在加载占位）——Splash 只等外观偏好即放行，首装/升级的种子耗时由
 * 本页加载条承接，避免种子在收集期间写库导致内容中途翻转。
 *
 * ## 注意
 *
 * - 动作库由种子填充、基本静态，自定义动作新增后 Today 不实时刷新（v1 取舍）。
 * - `today`/`weekStart` 在 ViewModel 创建时固定，跨零点不刷新（v1 取舍）。
 * - `WhileSubscribed(5000)` 重启会重新查询 profile/catalog
 *   （附带收益：设置页改名后返回 Today 超 5s 问候语会刷新——预期行为，勿当 bug 修回）。
 */
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val workoutPlanRepository: WorkoutPlanRepository,
    private val userProfileRepository: UserProfileRepository,
    private val exerciseRepository: ExerciseRepository,
    private val seedOrchestrator: SeedOrchestrator,
) : ViewModel() {

    // ── 本地 UI 事件态 ──
    private val displayMode = MutableStateFlow(WeekProgressDisplayMode.SPLIT)
    private val uiFlow = MutableStateFlow(UiState())

    // ── 一次性加载：无默认值冷 Flow，combine 首发即真实值 ──

    /** 用户资料：失败降级匿名（null）。 */
    private val profileFlow: Flow<UserProfile?> = flow {
        emit(userProfileRepository.getFirst())
    }.catch { emit(null) }

    /** 动作目录：失败降级空目录。 */
    private val catalogFlow: Flow<List<Exercise>> = flow {
        emit(exerciseRepository.getAll())
    }.catch { emit(emptyList()) }

    // ── 数据层响应式流 ──
    private val today: LocalDate = LocalDate.now()
    private val weekStart: LocalDate = today.with(DayOfWeek.MONDAY)

    private val weekWorkouts = workoutRepository.getByDateRange(weekStart, today)
    private val todayWorkouts = workoutRepository.getByDate(today)
    private val latestWorkout = workoutRepository.getLatest()
    private val activePlan = workoutPlanRepository.activePlan

    @OptIn(ExperimentalCoroutinesApi::class)
    private val nextSession = activePlan.flatMapLatest { plan ->
        if (plan == null) {
            flowOf(null)
        } else {
            workoutPlanRepository.getNextIncompleteSession(plan.id)
        }
    }

    /**
     * 展示模式与其对应历史数据的**原子对**。
     *
     * 全历史训练（3 级 relation 全量联查，观察三张表）仅 VOLUME_PR 模式的
     * PR 对比需要，其余模式不订阅；切换模式时 mode 与 history 同帧到达，
     * 避免"模式先切、历史后到"的瞬态错帧。
     */
    private data class ModeHistory(
        val mode: WeekProgressDisplayMode,
        val history: List<Workout>,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val modeHistory: Flow<ModeHistory> = displayMode.flatMapLatest { mode ->
        val source = if (mode == WeekProgressDisplayMode.VOLUME_PR) {
            workoutRepository.getWorkouts()
        } else {
            flowOf(emptyList())
        }
        source.map { ModeHistory(mode, it) }
    }

    /** 数据层五元快照（combine 单次最多 5 个 Flow 的一手组合）。 */
    private data class TodaySnapshot(
        val weekWorkouts: List<Workout>,
        val todayWorkouts: List<Workout>,
        val modeHistory: ModeHistory,
        val activePlan: WorkoutPlan?,
        val nextSession: PlannedSession?,
    )

    /** 快照之外的补充材料：最近训练 + 一次性加载的资料与目录。 */
    private data class TodayExtras(
        val latestWorkout: Workout?,
        val profile: UserProfile?,
        val catalog: List<Exercise>,
    )

    /** combine 链的中间态：组装 [TodayUiState] 的全部材料。 */
    private data class TodayMaterials(
        val snapshot: TodaySnapshot,
        val latestWorkout: Workout?,
        val displayMode: WeekProgressDisplayMode,
        val profile: UserProfile?,
        val catalog: List<Exercise>,
    )

    /** 种子门：种子完成前不发射（只放行一次 true，之后恒透传）。 */
    private val seedGate: Flow<Boolean> = seedOrchestrator.completed.filter { it }

    /** 页面 UI 状态流，由数据层 Flow 与本地事件 Flow 组合而成。 */
    val uiState: StateFlow<TodayUiState> = combine(
        weekWorkouts, todayWorkouts, modeHistory, activePlan, nextSession, ::TodaySnapshot,
    ).combine(
        combine(latestWorkout, profileFlow, catalogFlow, ::TodayExtras),
    ) { snapshot, extras ->
        assemble(
            TodayMaterials(
                snapshot = snapshot,
                latestWorkout = extras.latestWorkout,
                displayMode = snapshot.modeHistory.mode,
                profile = extras.profile,
                catalog = extras.catalog,
            ),
        )
    }.combine(uiFlow) { state, ui ->
        state.copy(uiState = ui)
    }.combine(seedGate) { state, _ ->
        state
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TodayUiState(
            coachInsight = CoachInsightState(),
            weekProgress = WeekProgressState(),
            todayPlan = TodayPlanState(),
            uiState = UiState(isLoading = true),
        ),
    )

    /** 计划选择弹层的数据源（与 uiState 平级暴露）。 */
    val allPlans: StateFlow<List<WorkoutPlan>> = workoutPlanRepository.getAllPlansFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    // ──────────────────────────────────────
    // 组装（只调用纯函数，不含业务计算）
    // ──────────────────────────────────────

    private fun assemble(materials: TodayMaterials): TodayUiState {
        val snapshot = materials.snapshot
        val weekTarget = snapshot.activePlan?.sessionsPerWeek ?: 4
        val weekCompleted = snapshot.weekWorkouts.size

        val todayPlan = TodayPlanAssembler.assemble(
            activePlan = snapshot.activePlan,
            nextSession = snapshot.nextSession,
            todayWorkouts = snapshot.todayWorkouts,
        )

        val coachInsight = CoachInsightBuilder.build(
            profile = materials.profile,
            weekCompleted = weekCompleted,
            weekTarget = weekTarget,
            latestWorkout = materials.latestWorkout,
            nextSession = snapshot.nextSession,
            // 自由训练（无计划）今日有记录同样视为已完成
            todayCompleted = todayPlan.status == PlanStatus.COMPLETED ||
                snapshot.todayWorkouts.isNotEmpty(),
            hasActivePlan = snapshot.activePlan != null,
            today = today,
            hour = LocalTime.now().hour,
        )

        val weekProgress = WeekProgressState(
            completedWorkouts = weekCompleted,
            targetWorkouts = weekTarget,
            displayMode = materials.displayMode,
            items = WeekProgressCalculator.calculate(
                mode = materials.displayMode,
                weekWorkouts = snapshot.weekWorkouts,
                allWorkouts = snapshot.modeHistory.history,
                activePlan = snapshot.activePlan,
                catalog = materials.catalog,
                weekStart = weekStart,
            ),
            statusText = when {
                weekCompleted >= weekTarget -> "Great job!"
                weekCompleted == 0 -> "这周还没开始"
                else -> "继续加油！"
            },
        )

        return TodayUiState(
            coachInsight = coachInsight,
            weekProgress = weekProgress,
            todayPlan = todayPlan,
            uiState = UiState(),
        )
    }

    // ──────────────────────────────────────
    // 事件
    // ──────────────────────────────────────

    /** 切换本周进度的展示模式。 */
    fun onDisplayModeSelected(mode: WeekProgressDisplayMode) = displayMode.update { mode }

    /** 在计划选择弹层中选中一套计划（设为当前激活计划）。 */
    fun onPlanSelected(planId: String) {
        viewModelScope.launch {
            workoutPlanRepository.setActivePlanId(planId)
        }
    }

    /** 错误提示已展示，清除错误信息。 */
    fun onErrorShown() = uiFlow.update { it.copy(errorMessage = null) }
}
