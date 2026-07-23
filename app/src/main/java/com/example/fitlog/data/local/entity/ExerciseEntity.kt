package com.example.fitlog.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.fitlog.model.BodyPart
import com.example.fitlog.model.Equipment
import com.example.fitlog.model.Muscle

/**
 * 动作库的数据库实体。
 *
 * 存储系统内置动作和用户自定义动作的标准化定义，
 * 用于训练日志中的动作选择、AI 理解、分类筛选和跨版本兼容。
 *
 * @property id 业务标识，kebab-case 唯一语义标识，如 "barbell-bench-press"。
 *     与数据层的自增主键无关，用于 JSON 导入导出和 AI 上下文。
 * @property name 动作名称，如 "Barbell Bench Press"
 * @property primaryMuscles 主要目标肌群列表
 * @property secondaryMuscles 次要参与的肌群列表
 * @property isCompound 是否为复合动作（多关节参与）
 * @property isCustom 是否为用户自定义动作，系统内置为 false
 * @property equipment 所需器械类型，可选
 * @property bodyPart 身体部位分类
 * @property description 动作简要说明（中文）
 * @property instructions 分步指导列表（中文）
 * @property imageUrl 缩略图路径
 * @property gifUrl GIF 动图 URL
 */
@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey
    val id: String = "",
    val name: String = "",
    val primaryMuscles: List<Muscle> = emptyList(),
    val secondaryMuscles: List<Muscle> = emptyList(),
    val isCompound: Boolean = false,
    val isCustom: Boolean = false,
    val equipment: Equipment? = null,
    val bodyPart: BodyPart = BodyPart.CHEST,
    val description: String? = null,
    val instructions: List<String> = emptyList(),
    val imageUrl: String? = null,
    val gifUrl: String? = null,
)
