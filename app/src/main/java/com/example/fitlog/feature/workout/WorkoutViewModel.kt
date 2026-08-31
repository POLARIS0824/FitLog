package com.example.fitlog.feature.workout

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitlog.data.repository.ExerciseRepository
import com.example.fitlog.data.repository.WorkoutPlanRepository
import com.example.fitlog.data.repository.WorkoutRepository
import com.example.fitlog.model.Exercise
import com.example.fitlog.model.PlannedSession
import com.example.fitlog.model.SetType
import com.example.fitlog.model.Workout
import com.example.fitlog.util.VolumeAggregator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 训练页的 ViewModel：训练记录列表 + 训练执行流（进行中会话）。
 *
 * ## 训练执行流
 *
 * 会话的**状态源是 DB**：启动会话插入 workouts 行（startedAt 已写、endedAt 空），
 * 此后加动作/录组逐条落库，[activeSession] 从
 * [WorkoutRepository.getInProgressWorkoutEntity] 投影——进程死亡或页面销毁后
 * 重进仍可恢复（Today「继续训练」/ 本页自动恢复），组数进度对 Today 卡片实时可见。
 *
 * 计划目标元数据（目标组数/次数区间）不在 workouts 表中：会话行经
 * `planSessionId` 列（v9 迁移新增，关联与会话行同生命周期）关联计划课次，
 * 投影时从计划库补齐目标后合并；计划课次被删除时降级为无目标展示。
 * 结束训练时按行内 planSessionId 回写课次完成标记，页面退出不丢。
 *
 * ## 结束会话
 *
 * [finishSession] 经 [WorkoutRepository.finishSession] 事务清洗落库
 * （剔除占位组与空动作），成功后把 workouts.id 回写计划课次的
 * completedWorkoutId——Today 卡片的 COMPLETED 状态与全 App isCountable
 * 口径的最终事实源。
 *
 * ## 记录列表
 *
 * 列表经 stateIn 订阅 Room Flow（库变更自动刷新），进行中的会话行
 * （startedAt 已写且 endedAt 为空）从列表中过滤——它由会话视图呈现。
 */
@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val workoutPlanRepository: WorkoutPlanRepository,
    private val exerciseRepository: ExerciseRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)

    /** 一次性操作提示（会话启动失败/结束失败等），Snackbar 展示后经 [onMessageShown] 清除。 */
    val message: StateFlow<String?> = _message.asStateFlow()

    /**
     * autoStart 一次性消费标记（SavedStateHandle 持久化）：导航参数
     * WorkoutKey(autoStart=true) 随回退栈持久化，旋转/进程恢复重组时
     * LaunchedEffect 会重放——消费后置位，避免"结束会话后旋转屏幕凭空
     * 创建幽灵会话"。
     */
    private var autoStartConsumed: Boolean
        get() = savedStateHandle[KEY_AUTO_START_CONSUMED] ?: false
        set(value) {
            savedStateHandle[KEY_AUTO_START_CONSUMED] = value
        }

    /** 计划课次 id → 课次对象缓存（目标元数据投影用，会话生命周期内最多几条）。 */
    private val planSessionCache = mutableMapOf<String, PlannedSession?>()

    /**
     * 会话写操作互斥锁：逐键提交的 updateSet 与 finishSession/discard 在
     * Room 的不同执行器上运行，无锁时"最后一键 + 立即结束"可能被结束事务
     * 读到旧值（最后一组丢失）。所有会话变更经此串行化。
     */
    private val sessionMutex = Mutex()

    /**
     * 页面 UI 状态流：Room 变更驱动（进行中会话行已过滤）。
     *
     * 异常按全项目 guard 约定降级：捕获后写一次性提示通道并发射空列表，
     * 不在流末端发射错误态终结链路——否则后续 DB 变化不再驱动 UI，直到
     * VM 重建（反模式见 TodayViewModel/StatsViewModel 的同款注释）。
     */
    val uiState: StateFlow<WorkoutUiState> = workoutRepository
        .getWorkouts()
        .map<List<Workout>, WorkoutUiState> { workouts ->
            // 进行中的会话行不出现在历史列表：它由会话视图呈现（endedAt == null
            // 但 exercises 非空的只能是执行流的会话行，导入存档行无 startedAt）
            WorkoutUiState.Success(workouts.filter { it.startedAt == null || it.endedAt != null })
        }
        .catch { e ->
            Log.w(TAG, "训练记录加载失败", e)
            _message.update { "训练记录加载失败，请重试" }
            emit(WorkoutUiState.Success(emptyList()))
        }
        .stateIn(
            scope = viewModelScope,
            // 前台订阅期间 Room 变更即刷新；无人订阅 5s 后停止监听（配置变更最佳实践）
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = WorkoutUiState.Loading,
        )

    /** 动作库目录（会话内"添加动作"选择器数据源；启动期已由 Seeder 灌库）。 */
    val exerciseCatalog: StateFlow<List<Exercise>> = kotlinx.coroutines.flow.flow {
        emit(exerciseRepository.getAll())
    }
        .catch { e ->
            Log.w(TAG, "动作库加载失败", e)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** 进行中的训练会话投影（null = 无会话，页面显示历史列表）。 */
    val activeSession: StateFlow<ActiveSession?> = workoutRepository
        .getInProgressWorkoutEntity()
        .map { relation -> relation?.let { buildActiveSession(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ──────────────────────────────────────
    // 会话控制
    // ──────────────────────────────────────

    /**
     * 进入页面即启动会话（Today「开始训练」的 autoStart 导航参数）。
     * [autoStartConsumed] 保证同一导航条目只消费一次。
     */
    fun maybeAutoStart() {
        if (autoStartConsumed) return
        autoStartConsumed = true
        startSession()
    }

    /**
     * 启动训练会话（已有进行中会话时为 no-op，天然实现"继续训练"）。
     *
     * 优先从激活计划的下一个未完成课次预填动作清单（Today「开始训练」主路径），
     * 无计划/全部完成时为自由训练。会话行与预填清单在单事务内落库，
     * planSessionId 直接写入 workouts 行——页面退出/进程死亡后关联不丢，
     * 结束训练仍能回写课次完成标记。
     */
    fun startSession() {
        viewModelScope.launch {
            sessionMutex.withLock {
                // 防重检查 fail-closed：查询异常按"已有会话"处理，
                // 否则异常吞成 false 可能产出第二条进行中行（旧行被列表过滤永久隐藏）
                if (runCatching { workoutRepository.hasInProgressWorkout() }.getOrDefault(true)) return@withLock
                try {
                    val planSession = runCatching {
                        val plan = workoutPlanRepository.activePlan.first()
                            ?: return@runCatching null
                        workoutPlanRepository.getNextIncompleteSession(plan.id).first()
                    }.getOrNull()
                    planSession?.let { planSessionCache[it.id] = it }

                    val workoutId = workoutRepository.createSessionWorkout(planSession)
                    if (workoutId == -1L) {
                        _message.update { "会话启动失败，请重试" }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "启动训练会话失败", e)
                    _message.update { "会话启动失败：${e.message}" }
                }
            }
        }
    }

    /** 结束会话：清洗落库 + 回写计划课次完成标记。[feelings] 可空。 */
    fun finishSession(feelings: String?) {
        val session = activeSession.value ?: return
        viewModelScope.launch {
            sessionMutex.withLock {
                try {
                    // 锁内存活复核：双击保存/保存后立即放弃时，第一次操作可能
                    // 已让会话终结，快照里的 workoutId 不能再动
                    val current = workoutRepository.getInProgressWorkoutEntity()
                        .first()?.workout
                    if (current == null || current.id != session.workoutId) return@withLock

                    // 返回键销毁 VM 会取消协程：落库段包 NonCancellable，
                    // 保证用户已确认的"保存"不因页面退出而静默回滚
                    val ended = withContext(NonCancellable) {
                        workoutRepository.finishSession(
                            workoutId = session.workoutId,
                            feelings = feelings?.trim()?.takeIf { it.isNotEmpty() },
                            endedAt = System.currentTimeMillis(),
                        )
                    }
                    if (!ended) {
                        _message.update { "还没有可保存的训练内容，请至少完成一组" }
                        return@withLock
                    }
                    session.planSessionId?.let { planSessionId ->
                        withContext(NonCancellable) {
                            runCatching {
                                workoutPlanRepository.markSessionCompleted(planSessionId, session.workoutId)
                            }.onFailure { Log.w(TAG, "回写课次完成标记失败", it) }
                        }
                    }
                    planSessionCache.remove(session.planSessionId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "结束训练会话失败", e)
                    _message.update { "保存失败：${e.message}" }
                }
            }
        }
    }

    /** 放弃会话：条件删除训练日行（动作与组级联清除），不回写计划完成标记。 */
    fun discardSession() {
        val session = activeSession.value ?: return
        viewModelScope.launch {
            sessionMutex.withLock {
                try {
                    // 锁内存活复核：拒绝删除已结束落库的训练（保存后立即放弃的竞态）
                    val current = workoutRepository.getInProgressWorkoutEntity()
                        .first()?.workout
                    if (current == null || current.id != session.workoutId) return@withLock
                    workoutRepository.discardSession(session.workoutId)
                    planSessionCache.remove(session.planSessionId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "放弃训练会话失败", e)
                    _message.update { "操作失败，请重试" }
                }
            }
        }
    }

    // ──────────────────────────────────────
    // 会话内容编辑（逐条落库，UI 由 activeSession 流自动刷新）
    // ──────────────────────────────────────

    /**
     * 添加动作并附一个占位组（0kg×0 次）：选择器只出动作库条目，
     * exerciseKey 必在 exercises 表——exercise_logs 外键对未知 key 会整体回滚。
     */
    fun addExercise(exercise: Exercise) {
        val session = activeSession.value ?: return
        if (session.exercises.any { it.exerciseKey == exercise.id }) return
        viewModelScope.launch {
            sessionMutex.withLock {
                try {
                    val logId = workoutRepository.addExerciseToSession(
                        workoutId = session.workoutId,
                        exerciseKey = exercise.id,
                        name = exercise.name,
                        sortOrder = session.exercises.size,
                    )
                    if (logId != -1L) {
                        workoutRepository.addSetToExercise(
                            exerciseLogId = logId,
                            setNumber = 1,
                            weightKg = 0f,
                            reps = 0,
                            setType = SetType.WORKING,
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "添加动作失败", e)
                    _message.update { "添加动作失败，请重试" }
                }
            }
        }
    }

    /** 移除动作（其组级联删除）。 */
    fun removeExercise(logId: Long) {
        viewModelScope.launch {
            sessionMutex.withLock {
                runCatching { workoutRepository.deleteSessionExercise(logId) }
                    .onFailure { Log.w(TAG, "移除动作失败", it) }
            }
        }
    }

    /** 为动作追加一组，默认值复制该动作的上一组（连续录入的常见路径）。 */
    fun addSet(exerciseLogId: Long) {
        val session = activeSession.value ?: return
        val exercise = session.exercises.firstOrNull { it.logId == exerciseLogId } ?: return
        val lastSet = exercise.sets.lastOrNull()
        viewModelScope.launch {
            sessionMutex.withLock {
                try {
                    workoutRepository.addSetToExercise(
                        exerciseLogId = exerciseLogId,
                        setNumber = exercise.sets.size + 1,
                        weightKg = lastSet?.weightKg ?: 0f,
                        reps = lastSet?.reps ?: 0,
                        setType = SetType.WORKING,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "添加组失败", e)
                    _message.update { "添加组失败，请重试" }
                }
            }
        }
    }

    /** 更新一组的重量/次数/组类型（UI 逐键提交）。 */
    fun updateSet(setId: Long, weightKg: Float, reps: Int, setType: SetType) {
        viewModelScope.launch {
            sessionMutex.withLock {
                runCatching {
                    workoutRepository.updateSessionSet(
                        setId = setId,
                        weightKg = weightKg.coerceAtLeast(0f),
                        reps = reps.coerceAtLeast(0),
                        setType = setType,
                    )
                }.onFailure { Log.w(TAG, "更新组失败", it) }
            }
        }
    }

    /** 翻转一组的组类型（WORKING ⇄ WARMUP，SQL 侧按 DB 当前值原子取反）。 */
    fun toggleSetType(setId: Long) {
        viewModelScope.launch {
            sessionMutex.withLock {
                runCatching { workoutRepository.toggleSessionSetType(setId) }
                    .onFailure { Log.w(TAG, "切换组类型失败", it) }
            }
        }
    }

    /** 删除一组。 */
    fun removeSet(setId: Long) {
        viewModelScope.launch {
            sessionMutex.withLock {
                runCatching { workoutRepository.deleteSessionSet(setId) }
                    .onFailure { Log.w(TAG, "删除组失败", it) }
            }
        }
    }

    /** 删除训练记录（失败仅记录日志，列表由 Room Flow 驱动，无需手动刷新）。 */
    fun deleteWorkout(workout: Workout) {
        viewModelScope.launch {
            try {
                workoutRepository.delete(workout)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "删除训练记录失败：${workout.date}", e)
            }
        }
    }

    /** 一次性提示已展示，清除。 */
    fun onMessageShown() = _message.update { null }

    // ──────────────────────────────────────
    // 投影
    // ──────────────────────────────────────

    /** relation → 会话投影：合并计划课次的目标元数据（课次已删则降级为无目标）。 */
    private suspend fun buildActiveSession(
        relation: com.example.fitlog.data.local.relation.WorkoutWithExerciseLogs,
    ): ActiveSession {
        // planSessionId 以 DB 行为事实源（v9 列）：页面退出/进程死亡后关联不丢，
        // 结束训练仍能按行内值回写课次完成标记
        val planSessionId = relation.workout.planSessionId
        val planSession = planSessionId?.let { id ->
            planSessionCache.getOrPut(id) {
                runCatching { workoutPlanRepository.getSessionById(id) }
                    .onFailure { Log.w(TAG, "读取计划课次失败：$id", it) }
                    .getOrNull()
            }
        }

        return ActiveSession(
            workoutId = relation.workout.id,
            startedAtMs = relation.workout.startedAt ?: System.currentTimeMillis(),
            planSessionId = planSessionId,
            planSessionName = planSession?.name,
            exercises = relation.exerciseLogs
                .sortedBy { it.exerciseLog.sortOrder }
                .map { log ->
                    val planItem = planSession?.exercises
                        ?.firstOrNull { it.exerciseKey == log.exerciseLog.exerciseKey }
                    ActiveSessionExercise(
                        logId = log.exerciseLog.id,
                        exerciseKey = log.exerciseLog.exerciseKey,
                        name = log.exerciseLog.name,
                        targetText = planItem?.toTargetText(),
                        sets = log.sets
                            .sortedBy { it.setNumber }
                            .map { set ->
                                ActiveSessionSet(
                                    id = set.id,
                                    weightKg = set.weightKg,
                                    reps = set.reps,
                                    setType = runCatching { SetType.valueOf(set.setType) }
                                        .getOrDefault(SetType.WORKING),
                                )
                            },
                    )
                },
        )
    }

    private companion object {
        const val TAG = "WorkoutViewModel"
        const val KEY_AUTO_START_CONSUMED = "workout_auto_start_consumed"
    }
}

/** 计划动作项 → 目标处方摘要（如 "4 组 × 8-10 次"；区间缺失时显示已给部分）。 */
private fun com.example.fitlog.model.PlannedExerciseItem.toTargetText(): String {
    val reps = when {
        targetRepsMin != null && targetRepsMax != null -> "$targetRepsMin-$targetRepsMax 次"
        targetRepsMin != null -> "≥$targetRepsMin 次"
        targetRepsMax != null -> "≤$targetRepsMax 次"
        else -> null
    }
    return listOfNotNull("${targetSets}组", reps).joinToString(" × ")
}
