package com.example.fitlog.data.repository

import androidx.room.withTransaction
import com.example.fitlog.data.local.AppDatabase
import com.example.fitlog.data.local.dao.ExerciseLogDao
import com.example.fitlog.data.local.dao.SetLogDao
import com.example.fitlog.data.local.dao.WorkoutDao
import com.example.fitlog.data.local.entity.workout.ExerciseLogEntity
import com.example.fitlog.data.local.entity.workout.SetLogEntity
import com.example.fitlog.data.local.relation.WorkoutWithExerciseLogs
import com.example.fitlog.data.mapper.toEntity
import com.example.fitlog.data.mapper.toModel
import com.example.fitlog.model.SetType
import com.example.fitlog.model.Workout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

/**
 * 训练日志仓库。
 *
 * 协调 [WorkoutDao]、[ExerciseLogDao] 和 [SetLogDao]，
 * 通过 [androidx.room.withTransaction] 完成 3 层训练日志（Workout → ExerciseLog → SetLog）
 * 的事务级联存储、删除以及联表查询聚合。
 */
class WorkoutRepository @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val exerciseLogDao: ExerciseLogDao,
    private val setLogDao: SetLogDao,
    private val db: AppDatabase,
) {
    /**
     * 事务级联插入完整训练日志。
     *
     * 依次写入 workouts → exercise_logs → set_logs：
     * 父行插入返回的自增主键作为子行的外键。
     * 任一环节失败则整体回滚。
     *
     * @param workout 完整训练日志（含动作与组）
     * @return 新插入训练日的数据库主键；头行主键冲突或 [Workout.sourceFileName]
     *     命中唯一索引（同源文件已导入，IGNORE 策略）时返回 -1
     *     （此时子行一律不写，避免以 -1 为外键触发约束异常）
     */
    suspend fun insert(workout: Workout): Long = db.withTransaction {
        val workoutId = workoutDao.insert(workout.toEntity())
        if (workoutId == -1L) return@withTransaction -1L
        insertChildren(workoutId, workout)
        workoutId
    }

    /**
     * 事务级联更新：更新父行后删除旧子行并重新插入。
     *
     * set_logs 由 exercise_logs 的外键 CASCADE 连带删除，无需显式清理。
     *
     * @param workout 完整训练日志（id 必须已存在）
     * @throws IllegalStateException 当 [Workout.id] 在库中不存在时抛出——
     *   此时若继续写子行会以悬空外键触发 SQLiteConstraintException
     */
    suspend fun update(workout: Workout) = db.withTransaction {
        val updatedRows = workoutDao.update(workout.toEntity())
        check(updatedRows > 0) { "Workout id=${workout.id} 不存在，无法更新" }
        exerciseLogDao.deleteByWorkoutId(workout.id)
        insertChildren(workout.id, workout)
    }

    /**
     * 级联插入动作与组（须在事务内调用）。
     */
    private suspend fun insertChildren(workoutId: Long, workout: Workout) {
        workout.exercises.forEachIndexed { index, exerciseLog ->
            val exerciseLogId = exerciseLogDao.insert(
                exerciseLog.toEntity(workoutId = workoutId, sortOrder = index),
            )
            val setEntities = exerciseLog.sets.mapIndexed { setIndex, setLog ->
                setLog.toEntity(exerciseLogId = exerciseLogId, setNumber = setIndex + 1)
            }
            setLogDao.insertAll(setEntities)
        }
    }

    /**
     * 按主键取单条完整训练日志（Agent 定点查询入口）。
     *
     * @param id 训练日数据库主键
     */
    suspend fun getById(id: Long): Workout? =
        workoutDao.getByIdWithDetails(id)?.toModel()

    suspend fun delete(workout: Workout) = workoutDao.delete(workout.toEntity())

    fun getByDate(date: LocalDate) = workoutDao.getByDateWithDetails(date).map { list ->
        list.map { it.toModel() }
    }

    fun getWorkouts(): Flow<List<Workout>> {
        return workoutDao.getAllWithDetails().map { list ->
            list.map { it.toModel() }
        }
    }

    /**
     * 观察最近一次训练（Today「最近训练」卡片）。
     *
     * 必须走级联查询：单实体映射会静默丢弃 exercises，导致
     * WeekProgressCalculator.resolveLastSessionName 的主导部位推导不可达，
     * 最近训练永远显示"自由训练"。
     */
    fun getLatest(): Flow<Workout?> = workoutDao.getRecentWithDetails(1).map { list ->
        list.firstOrNull()?.toModel()
    }

    /**
     * 观察最近 N 条完整训练日志（Today「最近训练」列表）。
     *
     * @param limit 返回条数上限
     */
    fun getRecentWithDetails(limit: Int): Flow<List<Workout>> =
        workoutDao.getRecentWithDetails(limit).map { list ->
            list.map { it.toModel() }
        }

    /**
     * 观察日期区间内的完整训练日志（Today「本周概览」与 Stats 区间聚合）。
     *
     * @param from 起始日期（含）
     * @param to 结束日期（含）
     */
    fun getByDateRange(from: LocalDate, to: LocalDate): Flow<List<Workout>> =
        workoutDao.getByDateRangeWithDetails(from, to).map { list ->
            list.map { it.toModel() }
        }

    // ──────────────────────────────────────
    // 训练执行流（进行中会话）— 状态源是 DB 而非内存：
    // 进程死亡/页面销毁后重启仍可恢复，Today 卡片的 IN_PROGRESS 分支同源
    // ──────────────────────────────────────

    /**
     * 事务化创建会话并预填计划动作清单（各附一个占位组 0kg×0 次）。
     *
     * 单事务保证"半初始化会话"不可见：workout 行与全部预填行要么同时
     * 存在，要么都不存在（此前逐条落库的窗口期内投影会出现无组动作行，
     * 且中途失败会留下进行中会话与"启动失败"提示并存的矛盾）。
     *
     * @param planSession 来源计划课次（null = 自由训练，不预填动作）
     * @return 新会话的 workouts 主键；写入失败返回 -1
     */
    suspend fun createSessionWorkout(planSession: com.example.fitlog.model.PlannedSession?): Long =
        db.withTransaction {
            val workoutId = workoutDao.insert(
                Workout(
                    id = 0,
                    userId = 0,
                    date = LocalDate.now(),
                    exercises = emptyList(),
                    feelings = null,
                    startedAt = System.currentTimeMillis(),
                    endedAt = null,
                    planSessionId = planSession?.id,
                ).toEntity(),
            )
            if (workoutId == -1L) return@withTransaction -1L

            planSession?.exercises
                ?.sortedBy { it.order }
                ?.forEachIndexed { index, item ->
                    val logId = exerciseLogDao.insert(
                        ExerciseLogEntity(
                            workoutId = workoutId,
                            exerciseKey = item.exerciseKey,
                            name = item.exerciseName ?: item.exerciseKey,
                            sortOrder = index,
                        ),
                    )
                    if (logId != -1L) {
                        setLogDao.insert(
                            SetLogEntity(
                                exerciseLogId = logId,
                                setNumber = 1,
                                weightKg = 0f,
                                reps = 0,
                                setType = SetType.WORKING.name,
                            ),
                        )
                    }
                }
            workoutId
        }

    /**
     * 观察进行中的训练（startedAt 已写、endedAt 为空）。
     *
     * 刻意返回 relation 包装而非 domain [Workout]：会话内的组编辑
     * （updateSet/deleteSet）需要 exerciseLog/setLog 的数据库主键，
     * domain 模型不含 id。仅限训练执行流使用。
     */
    fun getInProgressWorkoutEntity(): Flow<WorkoutWithExerciseLogs?> =
        workoutDao.getInProgressWithDetails()

    /** 进行中会话是否已存在（启动新会话的防御检查）。 */
    suspend fun hasInProgressWorkout(): Boolean =
        workoutDao.getInProgressWithDetails().map { it != null }.first()

    /**
     * 向进行中会话添加动作（不预建组）。
     *
     * @param exerciseKey 动作库 id；调用方必须保证其存在于 exercises 表
     *   （exercise_logs 外键对未知 key 会触发约束异常整体回滚），
     *   计划种子与动作选择器均已校验
     * @param sortOrder 动作排序序号
     * @return 新插入动作记录的主键
     */
    suspend fun addExerciseToSession(
        workoutId: Long,
        exerciseKey: String?,
        name: String,
        sortOrder: Int,
    ): Long = db.withTransaction {
        exerciseLogDao.insert(
            ExerciseLogEntity(workoutId = workoutId, exerciseKey = exerciseKey, name = name, sortOrder = sortOrder),
        )
    }

    /**
     * 向动作记录追加一组（会话内"添加一组"，默认值由调用方按上一组复制）。
     *
     * @return 新插入组记录的主键
     */
    suspend fun addSetToExercise(
        exerciseLogId: Long,
        setNumber: Int,
        weightKg: Float,
        reps: Int,
        setType: SetType,
    ): Long = setLogDao.insert(
        SetLogEntity(
            exerciseLogId = exerciseLogId,
            setNumber = setNumber,
            weightKg = weightKg,
            reps = reps,
            setType = setType.name,
        ),
    )

    /** 更新会话内一组的重量/次数/组类型（逐键提交，频率高故走定向 UPDATE 而非全列覆盖）。 */
    suspend fun updateSessionSet(setId: Long, weightKg: Float, reps: Int, setType: SetType) {
        setLogDao.updateById(id = setId, weightKg = weightKg, reps = reps, setType = setType.name)
    }

    /** 翻转会话内一组的组类型（WORKING ⇄ WARMUP，SQL 侧原子取反）。 */
    suspend fun toggleSessionSetType(setId: Long) {
        setLogDao.toggleTypeById(setId)
    }

    /** 删除会话内一组。 */
    suspend fun deleteSessionSet(setId: Long) {
        setLogDao.deleteById(setId)
    }

    /** 删除会话内一个动作（其组经外键 CASCADE 连带删除）。 */
    suspend fun deleteSessionExercise(exerciseLogId: Long) {
        exerciseLogDao.deleteById(exerciseLogId)
    }

    /**
     * 结束会话：清洗无效数据、写 endedAt 与感受，训练正式落库。
     *
     * 清洗规则：reps ≤ 0 的组剔除（占位行）；清洗后无任何有效组的动作剔除；
     * 清洗后不存在任何有效动作时结束失败（返回 false，会话保持进行中）。
     * 已结束的行直接拒绝（双击"保存"的第二击不改写 endedAt）。
     *
     * @return true 结束成功；false 无有效训练内容或会话已结束，不能结束
     */
    suspend fun finishSession(workoutId: Long, feelings: String?, endedAt: Long): Boolean =
        db.withTransaction {
            val relation = workoutDao.getByIdWithDetails(workoutId)
                ?: return@withTransaction false
            if (relation.workout.endedAt != null) return@withTransaction false
            val cleaned = relation.exerciseLogs
                .sortedBy { it.exerciseLog.sortOrder }
                .mapNotNull { log ->
                    // @Relation 无 ORDER BY：按持久化组号显式排序后再清洗重排，
                    // 保证重插后的 setNumber 连续且与录入顺序一致
                    log to log.sets.sortedBy { it.setNumber }.filter { it.reps > 0 }
                }
                .filter { (_, sets) -> sets.isNotEmpty() }
            if (cleaned.isEmpty()) return@withTransaction false

            // 删除旧子行并按清洗结果重插（组号重新连续编号），再补 endedAt/feelings
            exerciseLogDao.deleteByWorkoutId(workoutId)
            cleaned.forEachIndexed { index, (log, sets) ->
                val logId = exerciseLogDao.insert(
                    ExerciseLogEntity(
                        workoutId = workoutId,
                        exerciseKey = log.exerciseLog.exerciseKey,
                        name = log.exerciseLog.name,
                        sortOrder = index,
                    ),
                )
                setLogDao.insertAll(
                    sets.mapIndexed { setIndex, set ->
                        SetLogEntity(
                            exerciseLogId = logId,
                            setNumber = setIndex + 1,
                            weightKg = set.weightKg,
                            reps = set.reps,
                            setType = set.setType,
                        )
                    },
                )
            }
            workoutDao.update(relation.workout.copy(feelings = feelings, endedAt = endedAt))
            true
        }

    /**
     * 放弃会话：条件删除训练日行（动作与组经外键 CASCADE 连带清除）。
     *
     * @return true 放弃成功；false 会话已结束（拒绝删除已保存的训练）或不存在
     */
    suspend fun discardSession(workoutId: Long): Boolean =
        workoutDao.deleteInProgressById(workoutId) > 0
}