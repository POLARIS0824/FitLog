package com.example.fitlog.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitlog.data.repository.CoachInsightRepository
import com.example.fitlog.data.repository.ExerciseRepository
import com.example.fitlog.data.repository.UserProfileRepository
import com.example.fitlog.data.repository.WorkoutPlanRepository
import com.example.fitlog.data.repository.WorkoutRepository
import com.example.fitlog.data.seed.SeedOrchestrator
import com.example.fitlog.model.Exercise
import com.example.fitlog.model.PlannedSession
import com.example.fitlog.model.Workout
import com.example.fitlog.model.WorkoutPlan
import com.example.fitlog.model.ai.CoachInsight
import com.example.fitlog.model.ai.CoachInsightContext
import com.example.fitlog.model.user.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
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
 * 再与四元 extras（prevWeekWorkouts / latestWorkout / profile / catalog）汇合，
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
 * ## AI 增强
 *
 * 规则版 Coach Insight 由组装链同步产出（即时上屏）；AI 版走独立的增强链：
 * 材料流按 [CoachInsightContext.fingerprint] 去重 → 指纹变化才请求 AI
 * （同一天训练状态未变时命中 DataStore 缓存，零网络）→ 返回后替换卡片的
 * 观察/建议/动作标签。未配置服务商、无网络、解析失败均**静默回退规则版**。
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
    private val coachInsightRepository: CoachInsightRepository,
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
    private val prevWeekWorkouts = workoutRepository.getByDateRange(weekStart.minusDays(7), weekStart.minusDays(1))
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

    private val allWorkouts = workoutRepository.getWorkouts()

    /** 数据层五元快照（combine 单次最多 5 个 Flow 的一手组合）。 */
    private data class TodaySnapshot(
        val weekWorkouts: List<Workout>,
        val todayWorkouts: List<Workout>,
        val allWorkouts: List<Workout>,
        val activePlan: WorkoutPlan?,
        val nextSession: PlannedSession?,
    )

    /** 快照之外的补充材料：最近训练 + 上周记录（环比基线）+ 一次性加载的资料与目录。 */
    private data class TodayExtras(
        val latestWorkout: Workout?,
        val prevWeekWorkouts: List<Workout>,
        val profile: UserProfile?,
        val catalog: List<Exercise>,
    )

    /** combine 链的中间态：组装 [TodayUiState] 的全部材料。 */
    private data class TodayMaterials(
        val snapshot: TodaySnapshot,
        val prevWeekWorkouts: List<Workout>,
        val latestWorkout: Workout?,
        val displayMode: WeekProgressDisplayMode,
        val profile: UserProfile?,
        val catalog: List<Exercise>,
    )

    /** 种子门：种子完成前不发射（只放行一次 true，之后恒透传）。 */
    private val seedGate: Flow<Boolean> = seedOrchestrator.completed.filter { it }

    /**
     * 共享材料流：组装链与 AI 增强链的共同上游。
     * `shareIn` 避免两条订阅链各自触发 Room/DataStore 查询。
     */
    private val sharedMaterials = combine(
        weekWorkouts, todayWorkouts, allWorkouts, activePlan, nextSession, ::TodaySnapshot,
    ).combine(
        combine(latestWorkout, prevWeekWorkouts, profileFlow, catalogFlow, ::TodayExtras),
    ) { snapshot, extras ->
        TodayMaterials(
            snapshot = snapshot,
            prevWeekWorkouts = extras.prevWeekWorkouts,
            latestWorkout = extras.latestWorkout,
            displayMode = displayMode.value,
            profile = extras.profile,
            catalog = extras.catalog,
        )
    }.combine(displayMode) { materials, mode ->
        materials.copy(displayMode = mode)
    }.shareIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        replay = 1,
    )

    /** AI 增强阶段（Coach Insight 卡片的内容来源状态机）。 */
    private sealed interface AiPhase {
        /** 不尝试 AI（无训练数据、无计划或未配置服务商）：保持规则版，无加载态 */
        data object Hidden : AiPhase

        /** AI 请求/缓存读取进行中：规则版已上屏，label 旁显示加载 */
        data object Loading : AiPhase

        /** AI 已返回：insight 为 null 表示失败（静默保持规则版） */
        data class Ready(val insight: CoachInsight?) : AiPhase
    }

    /**
     * AI 增强链：材料 → 上下文 → 按指纹去重 → 请求 AI。
     *
     * [distinctUntilChangedBy] 以指纹为键：滑动 Pager 等无关材料变化不会触发请求；
     * [flatMapLatest] 保证指纹再变时取消在途请求。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val aiPhaseFlow: Flow<AiPhase> = sharedMaterials
        .map { materials -> materials.toCoachInsightContext() }
        .distinctUntilChangedBy { context -> context.fingerprint() }
        .flatMapLatest { context ->
            flow {
                val eligible = context.activePlan != null || context.recentWorkouts.isNotEmpty()
                if (!eligible || !coachInsightRepository.aiAvailable.first()) {
                    emit(AiPhase.Hidden)
                    return@flow
                }
                emit(AiPhase.Loading)
                val insight = coachInsightRepository.getAiInsight(context).getOrNull()
                emit(AiPhase.Ready(insight))
            }
        }

    /** 页面 UI 状态流，由数据层 Flow 与本地事件 Flow 组合而成。 */
    val uiState: StateFlow<TodayUiState> = combine(
        sharedMaterials.map { materials -> assemble(materials) },
        aiPhaseFlow,
        uiFlow,
    ) { state, aiPhase, ui ->
        state.copy(
            coachInsight = mergeAiPhase(state.coachInsight, aiPhase),
            uiState = ui,
        )
    }.combine(seedGate) { state, _ ->
        state
    }
        // 任一 Room 流异常不能击穿整条链：否则 stateIn 停在 initialValue（isLoading=true），
        // 页面永久卡加载。降级为错误态，errorMessage 通道由 TodayScreen 的 AlertDialog 消费
        .catch { e ->
            emit(
                TodayUiState(
                    coachInsight = CoachInsightState(),
                    weekProgress = WeekProgressState(),
                    todayPlan = TodayPlanState(),
                    uiState = UiState(errorMessage = e.message ?: "数据加载失败，请重试"),
                ),
            )
        }
        .stateIn(
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
        // 导入的表头记录（无动作明细）不计入完成数：它是"那天练过"的存档证明，
        // 不是一次可计数的结构化训练（否则导入历史会让本周次数/今日完成虚增）
        val weekCompleted = snapshot.weekWorkouts.count { it.exercises.isNotEmpty() }

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
            // 自由训练（无计划）今日有记录同样视为已完成（同样要求有动作明细）
            todayCompleted = todayPlan.status == PlanStatus.COMPLETED ||
                snapshot.todayWorkouts.any { it.exercises.isNotEmpty() },
            hasActivePlan = snapshot.activePlan != null,
            today = today,
            hour = LocalTime.now().hour,
        )

        // 预先计算好所有模式的 ProgressItemState，让 Pager 左右切页时数据即刻就位（0ms 零延迟）
        val itemsMap = WeekProgressDisplayMode.entries.associateWith { mode ->
            WeekProgressCalculator.calculate(
                mode = mode,
                weekWorkouts = snapshot.weekWorkouts,
                prevWeekWorkouts = materials.prevWeekWorkouts,
                allWorkouts = snapshot.allWorkouts,
                activePlan = snapshot.activePlan,
                nextSession = snapshot.nextSession,
                latestWorkout = materials.latestWorkout,
                targetWorkouts = weekTarget,
                catalog = materials.catalog,
                weekStart = weekStart,
            )
        }

        val weekProgress = WeekProgressState(
            completedWorkouts = weekCompleted,
            targetWorkouts = weekTarget,
            displayMode = materials.displayMode,
            items = itemsMap[materials.displayMode] ?: emptyList(),
            itemsMap = itemsMap,
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
    // AI 增强（Coach Insight）
    // ──────────────────────────────────────

    /** 今日训练是否已完成：计划课次完成，或自由训练今日有记录（与规则版口径一致）。 */
    private fun TodayMaterials.isTodayCompleted(): Boolean {
        val planCompleted = TodayPlanAssembler.assemble(
            activePlan = snapshot.activePlan,
            nextSession = snapshot.nextSession,
            todayWorkouts = snapshot.todayWorkouts,
        ).status == PlanStatus.COMPLETED
        return planCompleted || snapshot.todayWorkouts.isNotEmpty()
    }

    /** 材料 → AI 上下文（最近训练取全量记录按日期倒序前 3 条，含组详情）。 */
    private fun TodayMaterials.toCoachInsightContext(): CoachInsightContext =
        CoachInsightContext(
            profile = profile,
            weekCompleted = snapshot.weekWorkouts.size,
            weekTarget = snapshot.activePlan?.sessionsPerWeek ?: 4,
            todayCompleted = isTodayCompleted(),
            activePlan = snapshot.activePlan,
            nextSession = snapshot.nextSession,
            recentWorkouts = snapshot.allWorkouts
                .sortedWith(compareByDescending<Workout> { it.date }.thenByDescending { it.id })
                .take(3),
            catalog = catalog,
            today = today,
        )

    /**
     * 把 AI 阶段合并进规则版卡片状态：
     * AI 内容到达后替换观察/建议/动作标签；失败仅清加载态，规则版原样保留。
     */
    private fun mergeAiPhase(base: CoachInsightState, phase: AiPhase): CoachInsightState =
        when (phase) {
            AiPhase.Hidden -> base.copy(isAiLoading = false)
            AiPhase.Loading -> base.copy(isAiLoading = true)
            is AiPhase.Ready -> phase.insight?.let { ai ->
                base.copy(
                    observation = ai.observation,
                    recommendation = ai.recommendation,
                    action = ai.action,
                    isAiGenerated = true,
                    isAiLoading = false,
                )
            } ?: base.copy(isAiLoading = false)
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
