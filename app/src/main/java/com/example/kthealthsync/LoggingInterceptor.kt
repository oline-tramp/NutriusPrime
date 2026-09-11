package com.example.kthealthsync

import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Response

class LoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestHeaders = safeHeaders(request.headers)
        try {
            val response = chain.proceed(request)
            DebugLogger.addNetworkLog(
                method = request.method,
                url = request.url.toString(),
                requestHeaders = requestHeaders,
                responseCode = response.code,
                responseHeaders = safeHeaders(response.headers),
                responseBody = response.peekBody(1000).string()
            )
            return response
        } catch (e: Exception) {
            DebugLogger.addNetworkLog(
                method = request.method,
                url = request.url.toString(),
                requestHeaders = requestHeaders,
                responseCode = -1,
                responseHeaders = "",
                responseBody = "Exception: ${e.message}"
            )
            throw e
        }
    }

    private fun safeHeaders(headers: Headers): String = buildString {
        // Only diagnostics headers are useful here. Never retain credentials in DebugLogger.
        val allowed = setOf("content-type", "content-length", "accept", "user-agent", "date")
        for (index in 0 until headers.size) {
            val name = headers.name(index)
            val value = if (name.lowercase(java.util.Locale.ROOT) in allowed) {
                headers.value(index)
            } else "[REDACTED]"
            append(name).append(": ").append(value).append('\n')
        }
    }
}
