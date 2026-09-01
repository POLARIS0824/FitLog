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
     * @return 对应的 [BodyPart] 枚举值；未知值返回 null——调用方按"丢弃该条目"
     *   处理（与 [MuscleMapper] 同策略）。此前静默 fallback 到 CHEST 会把未知
     *   部位的动作错记进胸部，污染部位维度的全部统计、筛选与 AI 上下文
     */
    fun map(raw: String): BodyPart? = when (raw.lowercase().trim()) {
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
        else -> null // 未知值：交由调用方丢弃并留痕（配合种子投放对账日志）
    }
}
