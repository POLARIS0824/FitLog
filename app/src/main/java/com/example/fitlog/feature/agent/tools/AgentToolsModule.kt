package com.example.fitlog.feature.agent.tools

import com.google.adk.kt.tools.BaseTool
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Agent 工具集的 Hilt 装配模块。
 *
 * ADK 的 KSP 处理器（google-adk-kotlin-processor）会为 [FitnessTools] 中每个
 * `@Tool` 注解的函数生成 `XxxTool` 包装类（构造参数为宿主实例 `instance`），
 * 例如 [FitnessTools.getUserProfile] → `GetUserProfileTool`。
 *
 * 本模块集中实例化全部生成类并聚合为 [List] 提供给 [AgentEngine]。
 * 生成类名以 KSP 输出为准（首字母大写 + "Tool"），若编译报类找不到，
 * 检查 build/generated/ksp 下实际生成的文件名。
 */
@Module
@InstallIn(SingletonComponent::class)
object AgentToolsModule {

    /**
     * 提供 agent 可用的全部工具实例。
     *
     * @param tools 宿主 [FitnessTools]（构造注入各 Repository）
     * @return 按注册顺序排列的工具列表
     */
    @Provides
    @Singleton
    fun provideAgentTools(tools: FitnessTools): List<BaseTool> = listOf(
        GetUserProfileTool(tools),
        GetRecentWorkoutsTool(tools),
        GetWorkoutDetailTool(tools),
        GetActivePlanTool(tools),
        GetAllPlansTool(tools),
        GetBodyMetricsTool(tools),
        SearchExercisesTool(tools),
        GetExerciseStatsTool(tools),
        GetWeeklySummaryTool(tools),
        LogBodyWeightTool(tools),
        SetActivePlanTool(tools),
    )
}
