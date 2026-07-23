package com.example.fitlog.model

/**
 * 动作（Exercise）目录条目，描述一个可供训练使用的标准或自定义动作。
 *
 * @property id 业务标识，kebab-case 唯一语义标识，如 "barbell-bench-press"，用于 AI 理解、
 *     JSON 导入导出、跨版本兼容。与数据层的自增主键无关。
 * @property name 动作名称，如 "Barbell Bench Press"
 * @property primaryMuscles 主要目标肌群列表（1-2个），用于筛选、统计和 AI 分析
 * @property secondaryMuscles 次要参与的肌群列表
 * @property isCompound 是否为复合动作（多关节参与），从肌肉数量推导
 * @property isCustom 是否为用户自定义动作，系统内置为 false
 * @property equipment 所需器械类型，可选
 * @property bodyPart 身体部位分类，用于 UI 分组和筛选
 * @property description 动作简要说明（中文）
 * @property instructions 分步指导（中文），结构化为 AI 分析和语音播报提供原子步骤
 * @property imageUrl 缩略图路径（assets 本地路径或网络 URL）
 * @property gifUrl GIF 动图 URL（网络加载）
 */
data class Exercise(
    val id: String,
    val name: String,
    val primaryMuscles: List<Muscle>,
    val secondaryMuscles: List<Muscle> = emptyList(),
    val isCompound: Boolean = false,
    val isCustom: Boolean = false,
    val equipment: Equipment? = null,
    val bodyPart: BodyPart,
    val description: String? = null,
    val instructions: List<String> = emptyList(),
    val imageUrl: String? = null,
    val gifUrl: String? = null,
)

/**
 * 所需器械类型，用于健身房筛选和居家训练替代建议。
 *
 * 覆盖 exercises-dataset 的 28 种器械类型。
 */
enum class Equipment {
    /** 杠铃（含奥林匹克杠铃） */
    BARBELL,

    /** 哑铃 */
    DUMBBELL,

    /** 曲杆杠铃（EZ bar） */
    EZ_BAR,

    /** 绳索/拉力器 */
    CABLE,

    /** 器械（杠杆机、雪橇机等） */
    MACHINE,

    /** 史密斯机 */
    SMITH_MACHINE,

    /** 自重 */
    BODYWEIGHT,

    /** 壶铃 */
    KETTLEBELL,

    /** 阻力带/弹力带 */
    RESISTANCE_BAND,

    /** 药球 */
    MEDICINE_BALL,

    /** 稳定球/瑜伽球 */
    STABILITY_BALL,

    /** 波速球 */
    BOSU_BALL,

    /** 绳索（攀爬绳等） */
    ROPE,

    /** 滚筒/泡沫轴 */
    ROLLER,

    /** 辅助器械（引体辅助机等） */
    ASSISTED,

    /** 负重（负重背心等） */
    WEIGHTED,

    /** 六角杠铃 */
    TRAP_BAR,

    /** 有氧器械（跑步机、椭圆机、划船机等） */
    CARDIO_MACHINE,

    /** 其他/未分类 */
    OTHER,
}
