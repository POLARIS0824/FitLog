package com.example.fitlog.data.analysis.adapter

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import okhttp3.RequestBody
import okio.BufferedSink

/** Process-owned client survives Activity recreation; request credentials are never client defaults. */
internal val sharedAiHttpClient: HttpClient by lazy { createAiHttpClient() }

/** Share one client at application scope. Tests supply MockEngine using the same configuration. */
internal fun createAiHttpClient(
    engine: HttpClientEngine = OkHttp.create {
        config {
            retryOnConnectionFailure(false)
            followRedirects(false)
            followSslRedirects(false)
            addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                // OkHttp retries a GET on 503 + Retry-After: 0 despite connection retries being off.
                // This client never schedules retries; keep the status for the caller's manual retry UI.
                if (response.code == 503) response.newBuilder().removeHeader("Retry-After").build()
                else response
            }
            addInterceptor { chain ->
                val request = chain.request()
                val body = request.body
                if (body == null) {
                    chain.proceed(request)
                } else {
                    // OkHttp can follow up a 503 + Retry-After: 0 even with connection retries off.
                    val oneShotBody = object : RequestBody() {
                        override fun contentType() = body.contentType()
                        override fun contentLength() = body.contentLength()
                        override fun writeTo(sink: BufferedSink) = body.writeTo(sink)
                        override fun isOneShot() = true
                    }
                    chain.proceed(request.newBuilder().method(request.method, oneShotBody).build())
                }
            }
        }
    },
): HttpClient = HttpClient(engine) {
    // A retry can incur another charge; redirects can send a diary to a different destination.
    followRedirects = false
    expectSuccess = false
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        requestTimeoutMillis = 120_000
        socketTimeoutMillis = 120_000
    }
    // Do not install HTTP logging: both credentials and diary text are private.
}
