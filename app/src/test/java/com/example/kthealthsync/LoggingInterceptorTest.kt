package com.example.kthealthsync

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

class LoggingInterceptorTest {
    @Before fun clearLogs() = DebugLogger.clearLogs()

    @Test fun credentialsAreRedactedBeforeStorageAndResponseIsNotConsumed() {
        val body = "response ".repeat(1000)
        val client = OkHttpClient.Builder()
            .addInterceptor(LoggingInterceptor())
            .addInterceptor { chain ->
                assertEquals("session-secret", chain.request().header("Cookie"))
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK")
                    .addHeader("sEt-CoOkIe", "new-session-secret")
                    .addHeader("Content-Type", "text/plain")
                    .body(body.toResponseBody()).build()
            }.build()
        val request = Request.Builder().url("https://example.test/diary")
            .header("Cookie", "session-secret")
            .header("Authorization", "Bearer auth-secret")
            .header("X-Api-Key", "api-secret").build()

        client.newCall(request).execute().use { assertEquals(body, it.body!!.string()) }
        val entry = DebugLogger.logs.value.single()
        for (secret in listOf("session-secret", "new-session-secret", "auth-secret", "api-secret")) {
            assertFalse(entry.toString().contains(secret))
        }
        assertTrue(entry.requestHeaders!!.contains("[REDACTED]"))
        assertTrue(entry.responseHeaders!!.contains("text/plain"))
        assertEquals(1000, entry.responseBody!!.length)
    }

    @Test fun failureLogsAlsoRedactCredentials() {
        val client = OkHttpClient.Builder().addInterceptor(LoggingInterceptor())
            .addInterceptor { throw IOException("Disconnected") }.build()
        val request = Request.Builder().url("https://example.test/diary")
            .header("Cookie", "secret-cookie").build()
        assertThrows(IOException::class.java) { client.newCall(request).execute() }
        assertFalse(DebugLogger.logs.value.toString().contains("secret-cookie"))
        assertEquals(-1, DebugLogger.logs.value.single().responseCode)
    }
}
