package com.example.fitlog.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.fitlog.data.local.entity.ExerciseEntity

/**
 * [ExerciseEntity] 的数据访问对象。
 *
 * 提供动作库的基本增删改查，支持按分类、肌群和自定义标识筛选。
 */
@Dao
interface ExerciseDao {

    /**
     * 插入一条动作记录。若 [id] 冲突则替换。
     *
     * @param exercise 待插入的动作实体
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(exercise: ExerciseEntity)

    /**
     * 更新已有动作记录。
     *
     * @param exercise 待更新的动作实体
     */
    @Update
    suspend fun update(exercise: ExerciseEntity)

    /**
     * 删除指定动作记录。
     *
     * @param exercise 待删除的动作实体
     */
    @Delete
    suspend fun delete(exercise: ExerciseEntity)

    /**
     * 根据 ID 查询动作。
     *
     * @param id 动作业务标识
     * @return 匹配的动作实体，若不存在则返回 null
     */
    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: String): ExerciseEntity?

    /**
     * 查询所有动作记录。
     *
     * @return 动作实体列表
     */
    @Query("SELECT * FROM exercises ORDER BY name ASC")
    suspend fun getAll(): List<ExerciseEntity>

    /**
     * 按训练类型查询动作。
     *
     * @param category 训练类型名称
     * @return 匹配的动作实体列表
     */
    @Query("SELECT * FROM exercises WHERE category = :category ORDER BY name ASC")
    suspend fun getByCategory(category: String): List<ExerciseEntity>

    /**
     * 按主要肌群查询动作。
     *
     * @param muscle 肌群枚举名称
     * @return 匹配的动作实体列表
     */
    @Query("SELECT * FROM exercises WHERE primaryMuscle = :muscle ORDER BY name ASC")
    suspend fun getByPrimaryMuscle(muscle: String): List<ExerciseEntity>

    /**
     * 查询用户自定义动作。
     *
     * @return 用户自定义动作实体列表
     */
    @Query("SELECT * FROM exercises WHERE isCustom = 1 ORDER BY name ASC")
    suspend fun getCustomExercises(): List<ExerciseEntity>

    /**
     * 搜索动作名称（模糊匹配）。
     *
     * @param query 搜索关键词
     * @return 名称包含关键词的动作实体列表
     */
    @Query("SELECT * FROM exercises WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    suspend fun searchByName(query: String): List<ExerciseEntity>
}
