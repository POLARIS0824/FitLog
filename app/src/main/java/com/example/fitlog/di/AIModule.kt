package com.example.fitlog.di

import com.example.fitlog.data.remote.AIApi
import com.example.fitlog.util.log.AiNetworkLogger
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * 提供 AI 网络层依赖的 Hilt Module。
 */
@Module
@InstallIn(SingletonComponent::class) // 这些依赖在整个 App 生命周期只创建一次（单例）
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

        // AI 网络日志：元数据（方法/脱敏 URL/状态码/耗时）全构建落盘，
        // 请求/响应正文仅 debug 构建记录；认证头永不输出（详见 AiNetworkLogger）
        val logging = AiNetworkLogger()

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            // LLM 非流式响应经常需要几十秒，且 agent 路径带多轮工具调用大 prompt：
            // 60s 会误杀正常长回复（SocketTimeoutException 只会给用户笼统报错），
            // 放宽到 180s，长期方案是实现真流式（OpenAiCompatibleModel 已预留方向）
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
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
