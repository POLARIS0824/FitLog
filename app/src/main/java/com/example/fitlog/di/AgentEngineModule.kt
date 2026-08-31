package com.example.fitlog.di

import android.content.Context
import com.example.fitlog.feature.agent.engine.AgentEngine
import com.example.fitlog.feature.agent.engine.AgentEngineImpl
import com.example.fitlog.feature.agent.engine.FaultTolerantMemoryService
import com.google.adk.kt.memory.MemoryService
import com.google.adk.kt.memory.appsearch.AppSearchMemoryService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Agent 引擎的 Hilt 绑定：UI 层面向 [AgentEngine] 接口编程，
 * 默认绑定到 [AgentEngineImpl]（单例：内部持有可跨消息复用的 runner 与会话服务）。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AgentEngineModule {

    /** 绑定引擎实现为单例。 */
    @Binds
    @Singleton
    abstract fun bindAgentEngine(impl: AgentEngineImpl): AgentEngine

    companion object {

        /**
         * 长期记忆服务：ADK [MemoryService] 的 AppSearch 持久化实现（数据不出设备）。
         *
         * 进程级单例，跨 runner 重建共享——切换 AI 服务商不丢记忆；AppSearch 会话
         * 惰性打开，AutoCloseable 但与 RoomSessionService 同模式随进程存活，不显式 close。
         */
        @Provides
        @Singleton
        fun provideMemoryService(
            @ApplicationContext context: Context,
        ): MemoryService = FaultTolerantMemoryService(AppSearchMemoryService.fromContext(context))
    }
}
