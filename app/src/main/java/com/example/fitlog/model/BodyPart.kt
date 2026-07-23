package com.example.fitlog.model

/**
 * 身体部位枚举，用于动作库的 UI 分组和筛选。
 *
 * 与数据集（hasaneyldrm/exercises-dataset）的 body_part 字段一一对应，
 * 替代原有的 ExerciseCategory（力量/有氧/拉伸分类）。
 */
enum class BodyPart {
    /** 胸部 */
    CHEST,
    /** 背部 */
    BACK,
    /** 肩部 */
    SHOULDERS,
    /** 上臂 */
    UPPER_ARMS,
    /** 前臂 */
    LOWER_ARMS,
    /** 大腿/臀部 */
    UPPER_LEGS,
    /** 小腿 */
    LOWER_LEGS,
    /** 腰腹 */
    WAIST,
    /** 颈部 */
    NECK,
    /** 有氧/心肺 */
    CARDIO,
}
