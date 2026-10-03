package com.example.fitlog.data.analysis.adapter

import com.sun.net.httpserver.HttpServer
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AiHttpClientTest {
    @Test fun realOkHttpEngineNeverReplaysPostOnRetryOrRedirectResponses() = runTest {
        // Loopback HTTP isolates engine behavior. Production model addresses still require HTTPS.
        listOf(408, 503, 307).forEach { status ->
            val calls = AtomicInteger()
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/") { exchange ->
                calls.incrementAndGet()
                exchange.requestBody.use { it.readBytes() }
                exchange.responseHeaders.add("Retry-After", "0")
                exchange.responseHeaders.add("Location", "/redirected")
                val response = "not accepted".toByteArray()
                exchange.sendResponseHeaders(status, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
            server.start()
            try {
                createAiHttpClient().use { client ->
                    val response = client.post("http://127.0.0.1:${server.address.port}/") {
                        setBody(TextContent("test diary", ContentType.Application.Json))
                    }
                    assertEquals(status, response.status.value)
                    assertEquals(1, calls.get())
                }
            } finally {
                server.stop(0)
            }
        }
    }
}
