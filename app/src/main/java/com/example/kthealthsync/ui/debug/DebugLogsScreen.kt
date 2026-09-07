package com.example.kthealthsync.ui.debug

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kthealthsync.DebugLogger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugLogsScreen(onBack: () -> Unit) {
    val logs by DebugLogger.logs.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug Logs") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val logString = logs.joinToString("\n\n") { log ->
                            if (log.isNetwork) {
                                "${log.timestamp} | ${log.method} ${log.url}\nCode: ${log.responseCode}\nReqHeaders: ${log.requestHeaders}\nBody: ${log.responseBody}"
                            } else {
                                "${log.timestamp} | ${log.message}"
                            }
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, logString)
                        }
                        context.startActivity(Intent.createChooser(intent, "Export Logs"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Export")
                    }
                    Button(onClick = { DebugLogger.clearLogs() }) {
                        Text("Clear")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            logs.forEach { log ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (log.isNetwork) {
                                Text(
                                    "${log.timestamp} | ${log.method} ${log.url}",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Cyan,
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Text(
                                    "${log.timestamp} | ${log.message}",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFA726),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            IconButton(onClick = {
                                val text = if (log.isNetwork) {
                                    "${log.timestamp} | ${log.method} ${log.url}\nCode: ${log.responseCode}\nReqHeaders: ${log.requestHeaders}\nBody: ${log.responseBody}"
                                } else {
                                    "${log.timestamp} | ${log.message}"
                                }
                                clipboardManager.setText(AnnotatedString(text))
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            }) {
                                Text("COPY", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (log.isNetwork) {
                            val codeColor = if (log.responseCode != null && log.responseCode in 200..299) Color.Green else Color.Red
                            Text("Response: ${log.responseCode}", color = codeColor)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text("Req Headers: ${log.requestHeaders}", fontSize = 10.sp, color = Color.Gray)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text("Res Headers: ${log.responseHeaders}", fontSize = 10.sp, color = Color.Gray)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text("Body (first 1000 chars):", fontSize = 10.sp, color = Color.LightGray)
                            Text(log.responseBody ?: "null", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
