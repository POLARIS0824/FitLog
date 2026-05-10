package com.example.myfitness.domain.model

/**
 * 动作（Exercise）目录条目，描述一个可供训练使用的标准或自定义动作。
 *
 * @property id 动作唯一标识，建议与 [ExerciseLog] 通过外键关联
 * @property name 动作名称，如 "杠铃卧推"
 * @property primaryMuscle 主要目标肌群大类，用于筛选和分类
 * @property secondaryMuscles 次要参与的具体肌群，可为空或多组
 * @property movementPattern 动作模式，如水平推、垂直拉、蹲等
 * @property difficulty 建议难度等级
 * @property isCompound 是否为复合动作（多关节参与）
 * @property isCustom 是否为用户自定义动作，系统内置为 false
 * @property equipment 所需器械类型，可选
 * @property description 动作说明、要点提示或常见错误，可选
 */
data class Exercise(
    val id: Long,
    val name: String,
    val primaryMuscle: PrimaryMuscle,
    val secondaryMuscles: List<MuscleGroup> = emptyList(),
    val movementPattern: MovementPattern,
    val difficulty: Difficulty,
    val isCompound: Boolean,
    val isCustom: Boolean,
    val equipment: Equipment? = null,
    val description: String? = null,
)

/**
 * 主要目标肌群大类，用于动作列表的粗粒度筛选与分组。
 */
enum class PrimaryMuscle {
    CHEST,
    BACK,
    SHOULDERS,
    ARMS,
    LEGS,
    CORE,
    FULL_BODY,
}

/**
 * 具体肌肉/肌群枚举，用于精细化的动作分析、训练统计和疲劳管理。
 *
 * 注意：这里和 [PrimaryMuscle] 是“具体 vs 大类”的关系，不是重复。
 * 例如 "引体向上" 的 [primaryMuscle] 是 [BACK]，而次要肌群可以包含
 * [MuscleGroup.LATISSIMUS_DORSI]、[MuscleGroup.BICEPS_BRACHII] 等。
 */
enum class MuscleGroup {
    // 胸部
    PECTORALIS_MAJOR_UPPER,
    PECTORALIS_MAJOR_LOWER,
    PECTORALIS_MAJOR_MIDDLE,

    // 背部
    LATISSIMUS_DORSI,
    RHOMBOIDS,
    TRAPEZIUS_UPPER,
    TRAPEZIUS_LOWER,
    ERECTOR_SPINAE,

    // 肩部
    DELTOID_FRONT,
    DELTOID_SIDE,
    DELTOID_REAR,

    // 手臂
    BICEPS_BRACHII,
    BICEPS_BRACHIALIS,
    TRICEPS,
    BRACHIORADIALIS,
    FOREARM_FLEXORS,
    FOREARM_EXTENSORS,

    // 腿部
    QUADRICEPS,
    HAMSTRINGS,
    GLUTEUS_MAXIMUS,
    GLUTEUS_MEDIUS,
    CALVES_GASTROCNEMIUS,
    CALVES_SOLEUS,
    HIP_ABDUCTORS,
    HIP_ADDUCTORS,

    // 核心
    RECTUS_ABDOMINIS,
    OBLIQUES,
    TRANSVERSE_ABDOMINIS,

    // 全身/有氧
    FULL_BODY,
}

/**
 * 动作模式（Movement Pattern），基于功能性训练的分类体系。
 * 便于根据训练目标（如“今天练推”）快速组合动作。
 */
enum class MovementPattern {
    /** 水平推，如平板卧推 */
    HORIZONTAL_PUSH,

    /** 水平拉，如坐姿划船 */
    HORIZONTAL_PULL,

    /** 垂直推，如推举 */
    VERTICAL_PUSH,

    /** 垂直拉，如引体向上 */
    VERTICAL_PULL,

    /** 蹲类，如深蹲、前蹲 */
    SQUAT,

    /** 铰链类，如硬拉、罗马尼亚硬拉 */
    HINGE,

    /** 弓步/单腿类，如箭步蹲、保加利亚分腿蹲 */
    LUNGE,

    /** 旋转或抗旋转，如俄罗斯转体、Pallof Press */
    ROTATION,

    /** 负重行走/农夫行走 */
    CARRY,

    /** 单关节孤立动作，如二头弯举、腿屈伸 */
    ISOLATION,

    /** 有氧或代谢类训练 */
    CARDIO,

    /** 奥林匹克举重或其衍生动作 */
    OLYMPIC_LIFT,
}

/**
 * 动作难度等级。
 */
enum class Difficulty {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED,
}

/**
 * 所需器械类型，用于健身房筛选和居家训练替代建议。
 */
enum class Equipment {
    BARBELL,
    DUMBBELL,
    KETTLEBELL,
    CABLE,
    MACHINE,
    SMITH_MACHINE,
    BODYWEIGHT,
    RESISTANCE_BAND,
    NONE,
}
