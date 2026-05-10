package com.example.fitlog.di

import com.example.fitlog.data.remote.AIApi
import com.example.fitlog.data.repository.AIChatRepositoryImpl
import com.example.fitlog.domain.repository.AIChatRepository
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import javax.inject.Named
import javax.inject.Singleton

/**
 * 提供 AI 网络层依赖的 Hilt Module。
 */
@Module
@InstallIn(SingletonComponent::class)
object AIModule {

    /**
     * 提供用于 AI 请求的 [Retrofit] 实例。
     *
     * baseUrl 使用占位符，实际请求地址通过 [@Url] 动态传入。
     */
    @Provides
    @Singleton
    @Named("ai")
    fun provideAIRetrofit(): Retrofit {
        val json = Json { ignoreUnknownKeys = true }
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        return Retrofit.Builder()
            .baseUrl("https://placeholder.invalid/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    /**
     * 提供 [AIApi] 实例。
     */
    @Provides
    @Singleton
    fun provideAIApi(@Named("ai") retrofit: Retrofit): AIApi {
        return retrofit.create(AIApi::class.java)
    }
}

/**
 * 绑定 AI 聊天仓库实现。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AIRepositoryModule {

    @Binds
    abstract fun bindAIChatRepository(
        impl: AIChatRepositoryImpl,
    ): AIChatRepository
}
