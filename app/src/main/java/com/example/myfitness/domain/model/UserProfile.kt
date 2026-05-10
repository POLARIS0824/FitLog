package com.example.myfitness.domain.model

enum class Gender {
    MALE,
    FEMALE,
    OTHER
}

/**
 * 用户个性化信息
 */
data class UserProfile(
    val id: Long,
    val name: String,
    val age: Int?,
    val gender: Gender?,
    val height: Float?,
    val weight: Float?,
    val trainingLevel: TrainingLevel,
    val trainingGoal: TrainingGoal,
)

/**
 * 单个动作的训练水平子结构
 */
data class ExerciseTrainingLevel(
    val estimatedOneRMKg: Double?,   // 估算 1RM (kg)
    val relativeStrength: Double?,   // 相对力量 (1RM / 体重)
    val bestVolumeLoadKg: Double?    // 历史最佳单次训练容量 (kg)
)

/**
 * 整体训练水平，使用 Map 组织各项目
 */
data class TrainingLevel(
    val exercises: Map<String, ExerciseTrainingLevel>
)

/**
 * 训练目标
 */
enum class TrainingGoal {
    HYPERTROPHY, // 增肌
    FATLOSS,     // 减脂
    STRENGTH,    // 力量
    // TODO: FUNCTIONALITY
}