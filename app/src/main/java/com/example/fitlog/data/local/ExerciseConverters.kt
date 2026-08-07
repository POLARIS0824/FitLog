package com.example.fitlog.data.local

import androidx.room.TypeConverter
import com.example.fitlog.model.BodyPart
import com.example.fitlog.model.Equipment
import com.example.fitlog.model.Muscle
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString


/**
 * [com.example.fitlog.data.local.entity.ExerciseEntity] 的 Room TypeConverter。
 *
 * 负责枚举和列表类型与数据库可存储的字符串之间的双向转换。
 */
class ExerciseConverters {
    private val json = Json { ignoreUnknownKeys = true }

    // ── Muscle 列表 ──

    @TypeConverter
    fun fromMuscleList(value: List<Muscle>): String =
        value.joinToString(",") { it.name }

    @TypeConverter
    fun toMuscleList(value: String): List<Muscle> =
        if (value.isEmpty()) {
            emptyList()
        } else {
            // 未知肌群名（如旧版本种子数据残留）直接跳过，不抛 valueOf 异常
            value.split(",").mapNotNull { raw ->
                Muscle.entries.firstOrNull { it.name == raw }
            }
        }

    // ── BodyPart ──

    @TypeConverter
    fun fromBodyPart(value: BodyPart): String = value.name

    @TypeConverter
    fun toBodyPart(value: String): BodyPart =
        // 未知部位名降级为实体默认值 CHEST（与 ExerciseEntity.bodyPart 默认值一致）
        BodyPart.entries.firstOrNull { it.name == value } ?: BodyPart.CHEST

    // ── Equipment ──

    @TypeConverter
    fun fromEquipment(value: Equipment?): String? = value?.name

    @TypeConverter
    fun toEquipment(value: String?): Equipment? =
        // 未知器械名降级为 null（Equipment 字段本身可空）
        value?.let { raw -> Equipment.entries.firstOrNull { it.name == raw } }

    // ── String 列表（JSON 序列化） ──

    @TypeConverter
    fun fromStringList(value: List<String>): String =
        json.encodeToString(value)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        if (value.isEmpty()) {
            emptyList()
        } else {
            try {
                json.decodeFromString<List<String>>(value)
            } catch (e: Exception) {
                value.split(" ")
            }
        }
}
