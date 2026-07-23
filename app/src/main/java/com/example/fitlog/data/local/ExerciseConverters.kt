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
        if (value.isEmpty()) emptyList() else value.split(",").map { Muscle.valueOf(it) }

    // ── BodyPart ──

    @TypeConverter
    fun fromBodyPart(value: BodyPart): String = value.name

    @TypeConverter
    fun toBodyPart(value: String): BodyPart = BodyPart.valueOf(value)

    // ── Equipment ──

    @TypeConverter
    fun fromEquipment(value: Equipment?): String? = value?.name

    @TypeConverter
    fun toEquipment(value: String?): Equipment? = value?.let { Equipment.valueOf(it) }

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
