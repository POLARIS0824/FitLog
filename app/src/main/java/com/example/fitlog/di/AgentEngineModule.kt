package com.example.fitlog.di

import com.example.fitlog.feature.agent.engine.AgentEngine
import com.example.fitlog.feature.agent.engine.AgentEngineImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
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
}
