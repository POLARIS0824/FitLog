package com.example.fitlog.data.seed

import com.example.fitlog.model.Muscle

/**
 * 将 exercises-dataset 中的肌肉名称字符串映射为 [Muscle] 枚举。
 *
 * 数据集命名不统一（target 用缩写如 "quads"，muscle_group 用全称如 "quadriceps"），
 * 此映射表统一归一化到 20 个功能肌群。
 */
object MuscleMapper {

    /**
     * 将数据集原始肌肉字符串映射为 [Muscle]。
     *
     * @param raw 数据集中的肌肉名称（如 "quads"、"pectorals"、"upper back"）
     * @return 对应的 [Muscle] 枚举值，无法映射时返回 null
     */
    fun map(raw: String): Muscle? = when (raw.lowercase().trim()) {
        // 胸
        "pectorals", "chest", "upper chest", "serratus anterior" -> Muscle.CHEST
        // 肩
        "delts", "deltoids", "shoulders", "rear deltoids", "rotator cuff" -> Muscle.SHOULDERS
        // 三头
        "triceps" -> Muscle.TRICEPS
        // 背阔肌
        "lats", "latissimus dorsi" -> Muscle.LATS
        // 上背
        "upper back", "rhomboids", "back" -> Muscle.UPPER_BACK
        // 斜方肌
        "traps", "trapezius" -> Muscle.TRAPS
        // 二头
        "biceps", "brachialis" -> Muscle.BICEPS
        // 前臂
        "forearms", "wrists", "wrist flexors", "wrist extensors",
        "grip muscles", "hands" -> Muscle.FOREARMS
        // 股四头
        "quads", "quadriceps" -> Muscle.QUADRICEPS
        // 腘绳肌
        "hamstrings" -> Muscle.HAMSTRINGS
        // 臀
        "glutes" -> Muscle.GLUTES
        // 小腿
        "calves", "soleus", "ankles", "ankle stabilizers", "feet", "shins" -> Muscle.CALVES
        // 髋屈肌
        "hip flexors" -> Muscle.HIP_FLEXORS
        // 内收肌
        "adductors", "inner thighs", "groin" -> Muscle.ADDUCTORS
        // 外展肌
        "abductors" -> Muscle.ABDUCTORS
        // 核心
        "abs", "abdominals", "core", "obliques", "lower abs" -> Muscle.CORE
        // 下背
        "lower back", "spine" -> Muscle.LOWER_BACK
        // 颈
        "neck", "sternocleidomastoid", "levator scapulae" -> Muscle.NECK
        // 心肺
        "cardiovascular system" -> Muscle.CARDIO
        else -> null
    }
}
