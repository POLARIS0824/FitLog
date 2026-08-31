package com.example.fitlog.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.fitlog.data.local.entity.workout.WorkoutEntity
import com.example.fitlog.data.local.relation.WorkoutWithExerciseLogs
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 训练日（[WorkoutEntity]）的数据访问对象。
 */
@Dao
interface WorkoutDao {
    /**
     * 插入一条训练日记录，若主键冲突则忽略。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(workoutEntity: WorkoutEntity): Long

    /**
     * 更新已有训练日记录。
     *
     * @return 受影响行数；0 表示该 id 不存在（调用方必须检查，
     *   否则级联子行写入将以悬空外键触发 SQLiteConstraintException）
     */
    @Update
    suspend fun update(workoutEntity: WorkoutEntity): Int

    /**
     * 删除指定训练日记录。
     */
    @Delete
    suspend fun delete(workoutEntity: WorkoutEntity)

    /**
     * 按主键删除训练日记录（子行经外键 CASCADE 连带删除）。
     *
     * 训练执行流"放弃会话"入口：调用方只有 id（进行中的会话行由
     * [getInProgressWithDetails] 流式给出），无完整实体可走 [delete]。
     */
    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * 根据日期查询训练日记录。
     *
     * @param date LocalDate
     */
    @Query("SELECT * FROM workouts WHERE date = :date")
    fun getByDate(date: LocalDate): Flow<List<WorkoutEntity>>

    /**
     * 查询所有训练日记录，按日期降序排列。
     */
    @Query("SELECT * FROM workouts ORDER BY date DESC")
    fun getAll(): Flow<List<WorkoutEntity>>

    /**
     * 根据来源文件名查询训练日记录。
     *
     * 生产路径的导入去重由 `sourceFileName` 唯一索引 + IGNORE 策略保证
     * （应用层 check-then-insert 有 TOCTOU 窗口，已弃用）；本查询保留供
     * 测试断言与排障使用。
     */
    @Query("SELECT * FROM workouts WHERE sourceFileName = :fileName")
    suspend fun getBySourceFileName(fileName: String): WorkoutEntity?

    /**
     * 按主键查询单条训练（含动作与组）。
     *
     * Agent 工具（getWorkoutDetail 等）的定点取数入口，
     * 替代"全表三级加载后按 id 过滤"的 O(n) 写法。
     *
     * @param id 训练日数据库主键
     */
    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getByIdWithDetails(id: Long): WorkoutWithExerciseLogs?

    /**
     * 查询所有训练日记录及其关联的练习日志，按日期降序排列。
     * 使用 @Transaction 注解确保查询和关联数据的原子性
     */
    @Transaction
    @Query("SELECT * FROM workouts ORDER BY date DESC")
    fun getAllWithDetails(): Flow<List<WorkoutWithExerciseLogs>>

    /**
     * 根据日期查询训练日记录及其关联的练习日志。
     * 使用 @Transaction 注解确保查询和关联数据的原子性
     */
    @Transaction
    @Query("SELECT * FROM workouts WHERE date = :date")
    fun getByDateWithDetails(date: LocalDate): Flow<List<WorkoutWithExerciseLogs>>

    /**
     * 查询最近 N 条训练（含动作与组），按日期降序排列。
     * 使用 @Transaction 注解确保查询和关联数据的原子性。
     *
     * 注意：Do NOT 为"取最近一条"新增单实体查询——单实体映射会静默丢弃
     * exercises，下游"最近训练"的部位推导会失效；统一用本查询 LIMIT 1。
     *
     * @param limit 返回条数上限
     */
    @Transaction
    @Query("SELECT * FROM workouts ORDER BY date DESC, id DESC LIMIT :limit")
    fun getRecentWithDetails(limit: Int): Flow<List<WorkoutWithExerciseLogs>>

    /**
     * 放弃会话的条件删除：仅当该行仍处于进行中（endedAt 为空）时删除。
     *
     * 防止"结束落库后放弃"的双操作竞态把已保存的训练整体删掉。
     *
     * @return 删除的行数（0 = 会话已结束或不存在，放弃被拒绝）
     */
    @Query("DELETE FROM workouts WHERE id = :id AND endedAt IS NULL")
    suspend fun deleteInProgressById(id: Long): Int

    /**
     * 查询进行中的训练（startedAt 已写、endedAt 为空），含动作与组。
     *
     * 训练执行流以 DB 为会话状态源：进程死亡/页面销毁后，本查询仍是
     * 恢复入口（"继续训练"）。全库至多一条（启动会话前有防御），LIMIT 1 兜底。
     */
    @Transaction
    @Query(
        """
        SELECT * FROM workouts
        WHERE startedAt IS NOT NULL AND endedAt IS NULL
        ORDER BY startedAt DESC
        LIMIT 1
        """,
    )
    fun getInProgressWithDetails(): Flow<WorkoutWithExerciseLogs?>

    /**
     * 查询日期区间内的训练（含动作与组），按日期降序排列
     * （Today「本周概览」与 Stats 区间聚合取数；聚合在 Kotlin 端完成）。
     * 使用 @Transaction 注解确保查询和关联数据的原子性。
     *
     * @param from 起始日期（含）
     * @param to 结束日期（含）
     */
    @Transaction
    @Query("SELECT * FROM workouts WHERE date BETWEEN :from AND :to ORDER BY date DESC")
    fun getByDateRangeWithDetails(from: LocalDate, to: LocalDate): Flow<List<WorkoutWithExerciseLogs>>
}
