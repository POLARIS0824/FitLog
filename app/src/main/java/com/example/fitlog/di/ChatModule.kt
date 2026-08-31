package com.example.fitlog.di

import com.example.fitlog.data.repository.ChatRepository
import com.example.fitlog.data.repository.RoomChatRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 聊天持久化的 Hilt 绑定：UI 层面向 [ChatRepository] 接口编程
 * （测试注入内存替身），生产绑定到 Room 实现（进程级单例）。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ChatModule {

    /** 绑定聊天仓库实现为单例。 */
    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: RoomChatRepository): ChatRepository
}
