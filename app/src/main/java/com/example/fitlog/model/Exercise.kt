package com.example.fitlog.model

/**
 * 动作（Exercise）目录条目，描述一个可供训练使用的标准或自定义动作。
 *
 * @property id 业务标识，kebab-case 唯一语义标识，如 "barbell-bench-press"，用于 AI 理解、
 *     JSON 导入导出、跨版本兼容。与数据层的自增主键无关。
 * @property name 动作名称，如 "杠铃卧推"
 * @property primaryMuscle 主要目标肌群大类，用于筛选和分类
 * @property secondaryMuscles 次要参与的具体肌群，可为空或多组
 * @property movementPattern 动作模式，如水平推、垂直拉、蹲等
 * @property force 力学方向，与动作模式互补，用于 AI 判断 "今天练推/拉"
 * @property difficulty 建议难度等级
 * @property isCompound 是否为复合动作（多关节参与）
 * @property isCustom 是否为用户自定义动作，系统内置为 false
 * @property equipment 所需器械类型，可选
 * @property category 训练类型，区分力量/有氧/拉伸等不同训练目的
 * @property description 动作简要说明，可选
 * @property instructions 分步指导，结构化为 AI 分析和语音播报提供原子步骤
 */
data class Exercise(
    val id: String,
    val name: String,
    val primaryMuscle: PrimaryMuscle?,
    val secondaryMuscles: List<MuscleGroup> = emptyList(),
    val movementPattern: MovementPattern?,
    val force: Force?,
    val difficulty: Difficulty?,
    val isCompound: Boolean,
    val isCustom: Boolean,
    val equipment: Equipment? = null,
    val category: ExerciseCategory,
    val description: String? = null,
    val instructions: List<String> = emptyList(),
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
 * 注意：这里和 [PrimaryMuscle] 是"具体 vs 大类"的关系，不是重复。
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
 * 便于根据训练目标（如"今天练推"）快速组合动作。
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
 * 力学方向，描述动作产生的力量方向。
 * 与 [MovementPattern] 互补：例如 "引体向上" 是 [VERTICAL_PULL] + [PULL]，
 * "农夫行走" 是 [CARRY] + [STATIC]。
 */
enum class Force {
    /** 推类，如卧推、肩推 */
    PUSH,

    /** 拉类，如划船、引体向上 */
    PULL,

    /** 静态支撑或等长收缩，如平板支撑、农夫行走 */
    STATIC,
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
 * 训练类型，用于区分力量训练、有氧、拉伸等不同训练目的。
 * 与 [PrimaryMuscle]（按身体部位分类）是不同维度。
 */
enum class ExerciseCategory {
    /** 力量训练，如深蹲、卧推 */
    STRENGTH,

    /** 有氧/心肺训练，如跑步、划船 */
    CARDIO,

    /** 拉伸/柔韧性训练，如静态拉伸 */
    STRETCHING,

    /** 关节活动度训练，如动态热身 */
    MOBILITY,

    /** 爆发力/增强式训练，如跳箱、药球抛掷 */
    PLYOMETRICS,
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
