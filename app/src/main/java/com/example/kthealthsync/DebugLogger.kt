package com.example.kthealthsync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class LogEntry(
    val timestamp: String,
    val isNetwork: Boolean,
    val message: String,
    val method: String? = null,
    val url: String? = null,
    val requestHeaders: String? = null,
    val responseCode: Int? = null,
    val responseHeaders: String? = null,
    val responseBody: String? = null
)

object DebugLogger {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    fun addActionLog(message: String) {
        val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        val entry = LogEntry(
            timestamp = LocalDateTime.now().format(formatter),
            isNetwork = false,
            message = message
        )
        addEntry(entry)
    }

    fun addNetworkLog(
        method: String,
        url: String,
        requestHeaders: String,
        responseCode: Int,
        responseHeaders: String,
        responseBody: String
    ) {
        val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        val entry = LogEntry(
            timestamp = LocalDateTime.now().format(formatter),
            isNetwork = true,
            message = "HTTP $responseCode $method $url",
            method = method,
            url = url,
            requestHeaders = requestHeaders,
            responseCode = responseCode,
            responseHeaders = responseHeaders,
            responseBody = responseBody
        )
        addEntry(entry)
    }

    private fun addEntry(entry: LogEntry) {
        val currentLogs = _logs.value.toMutableList()
        currentLogs.add(0, entry)
        if (currentLogs.size > 100) {
            currentLogs.removeAt(currentLogs.size - 1)
        }
        _logs.value = currentLogs
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}
