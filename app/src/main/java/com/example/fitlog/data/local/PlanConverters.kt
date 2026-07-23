package com.example.fitlog.data.local

import androidx.room.TypeConverter
import com.example.fitlog.model.PlannedExerciseItem
import kotlinx.serialization.json.Json

/**
 * plan 相关实体的 Room TypeConverter。
 *
 * 负责 [PlannedExerciseItem] 列表与 JSON 字符串之间的双向转换。
 */
class PlanConverters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromPlannedExerciseItemList(value: List<PlannedExerciseItem>): String =
        json.encodeToString(value)

    @TypeConverter
    fun toPlannedExerciseItemList(value: String): List<PlannedExerciseItem> =
        if (value.isEmpty()) {
            emptyList()
        } else {
            json.decodeFromString(value)
        }
}
