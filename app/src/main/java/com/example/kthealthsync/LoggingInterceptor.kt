package com.example.kthealthsync

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody

class LoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()
        val method = request.method
        val requestHeaders = request.headers.toString()

        var responseCode = 0
        var responseHeaders = ""
        var responseBodyString = ""

        try {
            val response = chain.proceed(request)
            responseCode = response.code
            responseHeaders = response.headers.toString()
            
            val body = response.body
            if (body != null) {
                responseBodyString = body.string()
                
                // Log it
                DebugLogger.addNetworkLog(
                    method = method,
                    url = url,
                    requestHeaders = requestHeaders,
                    responseCode = responseCode,
                    responseHeaders = responseHeaders,
                    responseBody = responseBodyString.take(1000) // Log max 1000 chars of body
                )

                // Re-create the response because body string() consumes it
                val newBody = ResponseBody.create(body.contentType(), responseBodyString)
                return response.newBuilder().body(newBody).build()
            } else {
                DebugLogger.addNetworkLog(
                    method = method,
                    url = url,
                    requestHeaders = requestHeaders,
                    responseCode = responseCode,
                    responseHeaders = responseHeaders,
                    responseBody = "null body"
                )
                return response
            }
        } catch (e: Exception) {
            DebugLogger.addNetworkLog(
                method = method,
                url = url,
                requestHeaders = requestHeaders,
                responseCode = -1,
                responseHeaders = "",
                responseBody = "Exception: ${e.message}"
            )
            throw e
        }
    }
}
