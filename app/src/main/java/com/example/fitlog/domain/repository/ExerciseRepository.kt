package com.example.fitlog.domain.repository

import com.example.fitlog.domain.model.Exercise

/**
 * 动作库（Exercise）仓库接口。
 *
 * 提供对标准动作及用户自定义动作的查询能力，
 * 供训练日志中的动作选择、AI 推荐和动作库浏览使用。
 */
interface ExerciseRepository {

    /**
     * 获取所有动作记录（含系统内置和用户自定义），按名称升序排列。
     *
     * @return [Exercise] 列表
     */
    suspend fun getAll(): List<Exercise>
}
