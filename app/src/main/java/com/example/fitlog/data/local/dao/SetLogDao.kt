package com.example.fitlog.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.fitlog.data.local.entity.workout.SetLogEntity

/**
 * 组记录（[SetLogEntity]）的数据访问对象。
 */
@Dao
interface SetLogDao {
    /**
     * 插入一条组记录，若主键冲突则忽略。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(setLogEntity: SetLogEntity): Long

    /**
     * 批量插入组记录。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(setLogEntities: List<SetLogEntity>): List<Long>

    /**
     * 更新已有组记录。
     */
    @Update
    suspend fun update(setLogEntity: SetLogEntity)

    /**
     * 按主键局部更新重量/次数/组类型。
     *
     * 会话内逐键提交专用：[update] 是全列覆盖，要求构造完整实体（含
     * exerciseLogId/setNumber），而调用方（会话状态流投影）不持有这些列，
     * 全列覆盖会把关联列清成 0。定向 UPDATE 只改训练字段。
     */
    @Query("UPDATE set_logs SET weightKg = :weightKg, reps = :reps, setType = :setType WHERE id = :id")
    suspend fun updateById(id: Long, weightKg: Float, reps: Int, setType: String)

    /**
     * 按主键删除一组记录（DELETE 仅按主键匹配）。
     */
    @Query("DELETE FROM set_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * 按主键翻转组类型（WORKING ⇄ WARMUP）。
     *
     * 翻转语义收在 SQL 侧原子执行：UI 双击/流未及时重发时若以"当前值取反"
     * 提交，两次点击会写回同一值（卡在热身）；以 DB 当前值取反则幂等正确。
     */
    @Query(
        "UPDATE set_logs SET setType = " +
            "CASE WHEN setType = 'WORKING' THEN 'WARMUP' ELSE 'WORKING' END " +
            "WHERE id = :id",
    )
    suspend fun toggleTypeById(id: Long)

    /**
     * 删除指定组记录。
     */
    @Delete
    suspend fun delete(setLogEntity: SetLogEntity)

    /**
     * 根据动作记录 ID 查询所有组记录，按组号升序排列。
     */
    @Query("SELECT * FROM set_logs WHERE exerciseLogId = :exerciseLogId ORDER BY setNumber ASC")
    suspend fun getByExerciseLogId(exerciseLogId: Long): List<SetLogEntity>
}
