package com.example.fitlog.di

import com.example.fitlog.BuildConfig
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
     * 提供用于 AI 请求的 [Retrofit] 实例。
     *
     * baseUrl 使用占位符，实际请求地址通过 [@Url] 动态传入。
     */
    @Provides
    @Singleton
    @Named("ai")
    fun provideAIRetrofit(): Retrofit {
        val json = Json { ignoreUnknownKeys = true }

        // OkHttp 拦截器——仅调试构建打印完整请求/响应到 Logcat；
        // Release 关闭：BODY 会泄露 API Key 与 prompt 内容
        val logging = HttpLoggingInterceptor().apply {
            // BODY 级日志会原样打印认证头（明文 API Key），必须全部脱敏：
            // "Authorization" 覆盖 Bearer 系（OpenAI/DeepSeek/Moonshot/...），
            // "api-key" 是 Azure 的认证头，"x-api-key" 兜底常见网关变体
            redactHeader("Authorization")
            redactHeader("api-key")
            redactHeader("x-api-key")
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
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
