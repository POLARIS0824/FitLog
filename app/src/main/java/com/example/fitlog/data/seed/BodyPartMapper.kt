package com.example.fitlog.data.seed

import com.example.fitlog.model.BodyPart

/**
 * 将 exercises-dataset 中的身体部位字符串映射为 [BodyPart] 枚举。
 */
object BodyPartMapper {

    /**
     * 将数据集原始 body_part 字符串映射为 [BodyPart]。
     *
     * @param raw 数据集中的身体部位名称（如 "chest"、"upper legs"、"waist"）
     * @return 对应的 [BodyPart] 枚举值
     */
    fun map(raw: String): BodyPart = when (raw.lowercase().trim()) {
        "chest" -> BodyPart.CHEST
        "back" -> BodyPart.BACK
        "shoulders" -> BodyPart.SHOULDERS
        "upper arms" -> BodyPart.UPPER_ARMS
        "lower arms" -> BodyPart.LOWER_ARMS
        "upper legs" -> BodyPart.UPPER_LEGS
        "lower legs" -> BodyPart.LOWER_LEGS
        "waist" -> BodyPart.WAIST
        "neck" -> BodyPart.NECK
        "cardio" -> BodyPart.CARDIO
        else -> BodyPart.CHEST // fallback
    }
}
