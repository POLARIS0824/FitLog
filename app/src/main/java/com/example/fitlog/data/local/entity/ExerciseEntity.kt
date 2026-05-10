package com.example.fitlog.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.fitlog.domain.model.Difficulty
import com.example.fitlog.domain.model.Equipment
import com.example.fitlog.domain.model.ExerciseCategory
import com.example.fitlog.domain.model.Force
import com.example.fitlog.domain.model.MovementPattern
import com.example.fitlog.domain.model.MuscleGroup
import com.example.fitlog.domain.model.PrimaryMuscle

/**
 * 动作库的数据库实体。
 *
 * 存储系统内置动作和用户自定义动作的标准化定义，
 * 用于训练日志中的动作选择、AI 理解、分类筛选和跨版本兼容。
 *
 * @property id 业务标识，kebab-case 唯一语义标识，如 "barbell-bench-press"。
 *     与数据层的自增主键无关，用于 JSON 导入导出和 AI 上下文。
 * @property name 动作名称，如 "杠铃卧推"
 * @property primaryMuscle 主要目标肌群大类
 * @property secondaryMuscles 次要参与的具体肌群列表
 * @property movementPattern 动作模式，如水平推、垂直拉、蹲等
 * @property force 力学方向
 * @property difficulty 建议难度等级
 * @property isCompound 是否为复合动作（多关节参与）
 * @property isCustom 是否为用户自定义动作，系统内置为 false
 * @property equipment 所需器械类型，可选
 * @property category 训练类型分类
 * @property description 动作简要说明，可选
 * @property instructions 分步指导列表
 */
@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey
    val id: String = "",
    val name: String = "",
    val primaryMuscle: PrimaryMuscle? = null,
    val secondaryMuscles: List<MuscleGroup> = emptyList(),
    val movementPattern: MovementPattern? = null,
    val force: Force? = null,
    val difficulty: Difficulty? = null,
    val isCompound: Boolean = false,
    val isCustom: Boolean = false,
    val equipment: Equipment? = null,
    val category: ExerciseCategory = ExerciseCategory.STRENGTH,
    val description: String? = null,
    val instructions: List<String> = emptyList(),
)