package com.example.myfitness.ai.di

import com.example.myfitness.ai.config.AiConfig
import com.example.myfitness.ai.remote.AiApi
import com.example.myfitness.ai.repository.AiRepository
import com.example.myfitness.ai.repository.AiRepositoryImpl
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
import javax.inject.Singleton

/**
 * AI 网络层及 Repository 的 Hilt 依赖注入模块。
 */
@Module
@InstallIn(SingletonComponent::class)
object AiNetworkModule {

    /**
     * 提供 AI 配置。MVP 阶段使用默认值，请在 [AiConfig.apiKey] 中填入实际密钥。
     */
    @Provides
    @Singleton
    fun provideAiConfig(): AiConfig {
        return AiConfig(
            baseUrl = "https://api.openai.com/",
            apiKey = "", // TODO: 填入你的 API key
            model = "gpt-4o-mini",
        )
    }

    @Provides
    @Singleton
    fun provideJson(): Json {
        return Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(config: AiConfig): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer ${config.apiKey}")
                    .addHeader("Content-Type", "application/json")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                },
            )
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        config: AiConfig,
        okHttpClient: OkHttpClient,
        json: Json,
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(config.baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideAiApi(retrofit: Retrofit): AiApi {
        return retrofit.create(AiApi::class.java)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AiRepositoryModule {

    @Binds
    abstract fun bindAiRepository(
        impl: AiRepositoryImpl,
    ): AiRepository
}
