package com.example.fitlog.data.local

import android.util.Log
import androidx.room.TypeConverter
import com.example.fitlog.model.PlannedExerciseItem
import kotlinx.serialization.json.Json

/**
 * plan 相关实体的 Room TypeConverter。
 *
 * 负责 [PlannedExerciseItem] 列表与 JSON 字符串之间的双向转换。
 *
 * 解码容错：坏一行数据不能炸掉整条 Room Flow（getAllPlansWithDetailsFlow
 * 等订阅将永久死亡）。decode 失败时记录警告并降级为空列表——计划可重灌，
 * 订阅链路不能死。
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
            runCatching { json.decodeFromString<List<PlannedExerciseItem>>(value) }
                .onFailure {
                    Log.w(
                        "PlanConverters",
                        "planned_sessions.exercises JSON 解析失败，降级为空列表：$value",
                        it,
                    )
                }
                .getOrDefault(emptyList())
        }
}
