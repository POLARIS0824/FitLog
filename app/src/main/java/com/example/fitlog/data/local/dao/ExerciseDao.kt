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
 * 提供动作库的基本增删改查，支持按身体部位、肌群和自定义标识筛选。
 */
@Dao
interface ExerciseDao {

    /**
     * 插入一条动作记录。若 [ExerciseEntity.id] 冲突则替换。
     *
     * @param exercise 待插入的动作实体
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(exercise: ExerciseEntity)

    /**
     * 批量插入动作记录。若 ID 冲突则替换。
     *
     * @param exercises 待插入的动作实体列表
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exercises: List<ExerciseEntity>)

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
     * 按身体部位查询动作。
     *
     * @param bodyPart 身体部位枚举名称
     * @return 匹配的动作实体列表
     */
    @Query("SELECT * FROM exercises WHERE bodyPart = :bodyPart ORDER BY name ASC")
    suspend fun getByBodyPart(bodyPart: String): List<ExerciseEntity>

    /**
     * 按主要肌群查询动作（LIKE 模糊匹配逗号分隔的枚举值）。
     *
     * @param muscle 肌群枚举名称
     * @return 匹配的动作实体列表
     */
    @Query("SELECT * FROM exercises WHERE primaryMuscles LIKE '%' || :muscle || '%' ORDER BY name ASC")
    suspend fun getByMuscle(muscle: String): List<ExerciseEntity>

    /**
     * 查询用户自定义动作。
     *
     * @return 用户自定义动作实体列表
     */
    @Query("SELECT * FROM exercises WHERE isCustom = 1 ORDER BY name ASC")
    suspend fun getCustomExercises(): List<ExerciseEntity>

    /**
     * 根据名称精确查询动作。
     *
     * @param name 动作名称
     * @return 匹配的动作实体，若不存在则返回 null
     */
    @Query("SELECT * FROM exercises WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): ExerciseEntity?

    /**
     * 搜索动作名称（模糊匹配）。
     *
     * @param query 搜索关键词
     * @return 名称包含关键词的动作实体列表
     */
    @Query("SELECT * FROM exercises WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    suspend fun searchByName(query: String): List<ExerciseEntity>

    /**
     * 查询动作总数，用于判断种子数据是否已导入。
     *
     * @return 动作记录总数
     */
    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun getCount(): Int
}
