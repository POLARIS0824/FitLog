package com.example.fitlog.di

import com.example.fitlog.data.remote.AIApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
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
     * 提供 AI 请求共用的 [Json] 序列化配置。
     *
     * - ignoreUnknownKeys：响应中未建模的字段（如 provider 私有扩展字段）直接忽略
     * - explicitNulls = false：null 字段不输出——否则 tools=null / content=null 会被
     *   序列化进请求体，DeepSeek 等 provider 对 "tools": null 直接报 400
     *
     * Retrofit 与 AgentOrchestrator（解析 tool 参数 JSON）共用同一实例。
     */
    @Provides
    @Singleton
    @Named("ai")
    fun provideAiJson(): Json {
        return Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }

    /**
     * 提供用于 AI 请求的 [Retrofit] 实例。
     *
     * baseUrl 使用占位符，实际请求地址通过 [@Url] 动态传入。
     */
    @Provides
    @Singleton
    @Named("ai")
    fun provideAIRetrofit(@Named("ai") json: Json): Retrofit {
        // OkHttp 拦截器——在开发和调试阶段，把请求和响应的完整内容打印到 Logcat
        // TODO: 发布时关闭，防止 API KEY 泄露
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            // LLM 非流式响应经常需要几十秒，默认 10s 读超时会误杀正常请求
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
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
