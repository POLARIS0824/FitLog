package com.example.fitlog.model

/**
 * 功能肌群枚举，用于 AI 教练的训练分析、计划生成和恢复追踪。
 *
 * 粒度选择原则：按功能区分而非解剖学细分。
 * LLM 本身理解解剖学，枚举的作用是数据标注和 Room 筛选，不是教学。
 */
enum class Muscle {
    // ── 上肢推 ──
    /** 胸部（含上中下胸，由动作角度区分） */
    CHEST,
    /** 肩部（含前中后束，由动作类型区分） */
    SHOULDERS,
    /** 肱三头肌 */
    TRICEPS,

    // ── 上肢拉 ──
    /** 背阔肌（垂直拉：引体、高位下拉） */
    LATS,
    /** 上背/菱形肌（水平拉：划船） */
    UPPER_BACK,
    /** 斜方肌 */
    TRAPS,
    /** 肱二头肌（含肱肌） */
    BICEPS,
    /** 前臂/握力 */
    FOREARMS,

    // ── 下肢 ──
    /** 股四头肌 */
    QUADRICEPS,
    /** 腘绳肌 */
    HAMSTRINGS,
    /** 臀部 */
    GLUTES,
    /** 小腿 */
    CALVES,
    /** 髋屈肌 */
    HIP_FLEXORS,
    /** 内收肌 */
    ADDUCTORS,
    /** 外展肌 */
    ABDUCTORS,

    // ── 核心 ──
    /** 核心（腹直肌 + 腹斜肌） */
    CORE,
    /** 下背/竖脊肌 */
    LOWER_BACK,

    // ── 其他 ──
    /** 颈部 */
    NECK,
    /** 心肺 */
    CARDIO,
}
