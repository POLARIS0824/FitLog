package com.example.fitlog.data.seed

import kotlinx.serialization.Serializable

/**
 * 从 res/raw/exercises.json 反序列化的种子数据 DTO。
 *
 * 与 hasaneyldrm/exercises-dataset 预处理后的 JSON 字段一一对应。
 */
@Serializable
data class ExerciseSeedData(
    /** 数据集数字 ID，如 "0001" */
    val id: String,
    /** 动作英文名称，如 "barbell bench front squat" */
    val name: String,
    /** 身体部位，如 "upper legs" */
    val body_part: String,
    /** 器械类型，如 "barbell" */
    val equipment: String,
    /** 目标肌肉，如 "quads" */
    val target: String,
    /** 协同肌群，如 "hamstrings" */
    val muscle_group: String,
    /** 次要肌肉列表 */
    val secondary_muscles: List<String>,
    /** 全文本指导（按语言代码索引，仅保留 zh） */
    val instructions: Map<String, String>,
    /** 分步指导（按语言代码索引，仅保留 zh） */
    val instruction_steps: Map<String, List<String>>,
    /** 缩略图路径，如 "images/0024-Y7YcmIJ.jpg" */
    val image: String,
    /** GIF 动图路径，如 "videos/0024-Y7YcmIJ.gif" */
    val gif_url: String,
)
