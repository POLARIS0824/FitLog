package com.example.fitlog.data.seed

import com.example.fitlog.model.Equipment

/**
 * 将 exercises-dataset 中的器械名称字符串映射为 [Equipment] 枚举。
 *
 * 数据集有 28 种器械类型，归一化为 19 个枚举值。
 */
object EquipmentMapper {

    /**
     * 将数据集原始器械字符串映射为 [Equipment]。
     *
     * @param raw 数据集中的器械名称（如 "barbell"、"body weight"、"leverage machine"）
     * @return 对应的 [Equipment] 枚举值，无法映射时返回 [Equipment.OTHER]
     */
    fun map(raw: String): Equipment = when (raw.lowercase().trim()) {
        "barbell", "olympic barbell" -> Equipment.BARBELL
        "dumbbell" -> Equipment.DUMBBELL
        "ez barbell" -> Equipment.EZ_BAR
        "cable" -> Equipment.CABLE
        "leverage machine", "sled machine", "hammer" -> Equipment.MACHINE
        "smith machine" -> Equipment.SMITH_MACHINE
        "body weight" -> Equipment.BODYWEIGHT
        "kettlebell" -> Equipment.KETTLEBELL
        "band", "resistance band" -> Equipment.RESISTANCE_BAND
        "medicine ball" -> Equipment.MEDICINE_BALL
        "stability ball" -> Equipment.STABILITY_BALL
        "bosu ball" -> Equipment.BOSU_BALL
        "rope" -> Equipment.ROPE
        "roller", "wheel roller", "foam roll" -> Equipment.ROLLER
        "assisted" -> Equipment.ASSISTED
        "weighted" -> Equipment.WEIGHTED
        "trap bar" -> Equipment.TRAP_BAR
        "stationary bike", "elliptical machine", "stepmill machine",
        "skierg machine", "upper body ergometer", "tire" -> Equipment.CARDIO_MACHINE
        else -> Equipment.OTHER
    }
}
