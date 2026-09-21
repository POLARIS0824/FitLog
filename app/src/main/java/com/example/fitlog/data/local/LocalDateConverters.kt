package com.example.fitlog.data.local

import androidx.room.TypeConverter
import com.example.fitlog.util.log.FitLog
import java.time.LocalDate

/**
 * [LocalDate] 的 Room TypeConverter（ISO-8601 文本双向转换）。
 */
class LocalDateConverters {

    @TypeConverter
    fun fromLocalDate(date: LocalDate?): String? {
        return date?.toString()
    }

    /**
     * 解码容错：日期列（workouts.date / body_metrics.date 等）均为非空字段，
     * 坏字符串若直接抛 DateTimeParseException 会击穿整条查询 Flow
     * （Today/Stats 主链路永久中断，同 PlanConverters 防"坏一行炸订阅"的动机）。
     * 降级为 epoch 远端日期：该行存活且不落入近期统计窗口，Log.w 留痕。
     */
    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? {
        if (value == null) return null
        return runCatching { LocalDate.parse(value) }
            .onFailure {
                FitLog.w("LocalDateConverters", "日期解析失败，降级为 epoch：$value", it)
            }
            .getOrDefault(EPOCH_FALLBACK)
    }

    companion object {
        /**
         * 降级使用的 Epoch 日期（1970-01-01）。
         * 注意：[LocalDate.EPOCH] 在 Android 上需要 API 34+（Java 9+），
         * 在 minSdk=26 环境下使用会因字段缺失抛出 NoSuchFieldError。
         * 此处使用 [LocalDate.of] 保证在 minSdk 26 下的安全与等价语义。
         */
        private val EPOCH_FALLBACK: LocalDate = LocalDate.of(1970, 1, 1)
    }
}
