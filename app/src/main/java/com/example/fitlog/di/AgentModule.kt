package com.example.fitlog.di

import com.example.fitlog.data.agent.ChatCompletionClient
import com.example.fitlog.data.agent.tools.GetExerciseHistoryTool
import com.example.fitlog.data.agent.tools.GetNextPlannedSessionTool
import com.example.fitlog.data.agent.tools.GetUserProfileTool
import com.example.fitlog.data.agent.tools.GetWorkoutDetailTool
import com.example.fitlog.data.agent.tools.ListRecentWorkoutsTool
import com.example.fitlog.data.agent.tools.ListWorkoutPlansTool
import com.example.fitlog.data.agent.tools.SearchExercisesTool
import com.example.fitlog.data.repository.AIChatRepository
import com.example.fitlog.model.ai.AgentTool
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/**
 * Agent 相关依赖的 Hilt Module。
 *
 * - [ChatCompletionClient]：agent loop 的 LLM 端口，由 [AIChatRepository] 实现
 * - [AgentTool] 集合：通过 multibinding 注册全部工具，
 * 新增工具时在此追加一个 @Binds @IntoSet 方法即可
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AgentModule {

    /**
     * 将 [AIChatRepository] 绑定为 agent loop 使用的 [ChatCompletionClient]。
     */
    @Binds
    @Singleton
    abstract fun bindChatCompletionClient(impl: AIChatRepository): ChatCompletionClient

    @Binds
    @IntoSet
    abstract fun bindGetUserProfileTool(impl: GetUserProfileTool): AgentTool

    @Binds
    @IntoSet
    abstract fun bindListRecentWorkoutsTool(impl: ListRecentWorkoutsTool): AgentTool

    @Binds
    @IntoSet
    abstract fun bindGetWorkoutDetailTool(impl: GetWorkoutDetailTool): AgentTool

    @Binds
    @IntoSet
    abstract fun bindGetExerciseHistoryTool(impl: GetExerciseHistoryTool): AgentTool

    @Binds
    @IntoSet
    abstract fun bindSearchExercisesTool(impl: SearchExercisesTool): AgentTool

    @Binds
    @IntoSet
    abstract fun bindListWorkoutPlansTool(impl: ListWorkoutPlansTool): AgentTool

    @Binds
    @IntoSet
    abstract fun bindGetNextPlannedSessionTool(impl: GetNextPlannedSessionTool): AgentTool
}
