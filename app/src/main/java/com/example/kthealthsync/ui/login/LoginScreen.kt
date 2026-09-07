package com.example.kthealthsync.ui.login

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kthealthsync.DebugLogger
import com.example.kthealthsync.LoginRequest
import com.example.kthealthsync.PreferencesManager
import com.example.kthealthsync.RetrofitClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.security.MessageDigest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    prefs: PreferencesManager,
    onBack: () -> Unit,
    onLoginSuccess: (String, String, String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var manualCookie by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val savedEmail = prefs.emailFlow.first()
        val savedHash = prefs.passwordHashFlow.first()
        if (savedEmail.isNotEmpty() && savedHash.isNotEmpty()) {
            email = savedEmail
            DebugLogger.addActionLog("LoginScreen opened: found saved credentials for $savedEmail")
        } else {
            DebugLogger.addActionLog("LoginScreen opened: no saved credentials found.")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log in to Kalorické Tabulky") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (errorMessage.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF330000))) {
                    Text(errorMessage, color = Color.Red, modifier = Modifier.padding(16.dp))
                }
            }

            Text("Native Login (Recommended)", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Fast and reliable. Use the email and password associated with your account.", color = Color.Gray, fontSize = 14.sp)

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )

            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        errorMessage = "Please enter email and password"
                        return@Button
                    }
                    isLoading = true
                    errorMessage = ""
                    coroutineScope.launch {
                        try {
                            val md5Password = MessageDigest.getInstance("MD5")
                                .digest(password.toByteArray())
                                .joinToString("") { "%02x".format(it) }

                            val request = LoginRequest(email, md5Password)
                            val response = RetrofitClient.instance.login(request)
                            val body = response.body()

                            if (response.isSuccessful && body != null) {
                                if (body.code != null && body.code != 0) {
                                    errorMessage = body.message ?: "Login failed (Code ${body.code})"
                                    DebugLogger.addActionLog("Native Login Failed: $errorMessage")
                                } else {
                                    val cookieHeaders = response.headers().values("Set-Cookie")
                                    val jsessionCookie = cookieHeaders.firstOrNull { it.contains("JSESSIONID") }
                                    if (jsessionCookie != null) {
                                        val cookieValue = jsessionCookie.substringBefore(";")
                                        onLoginSuccess(cookieValue, email, md5Password)
                                    } else {
                                        errorMessage = "Login succeeded but no session cookie received."
                                    }
                                }
                            } else {
                                errorMessage = "HTTP ${response.code()}"
                                DebugLogger.addActionLog("Native Login HTTP Error: $errorMessage")
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            errorMessage = e.message ?: "Network error"
                            DebugLogger.addActionLog("Native Login Exception: $errorMessage")
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Text("Log In")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text("Alternative: Manual Cookie", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("If you use Google/Facebook Sign-In, you can copy your JSESSIONID cookie from your PC browser (DevTools) and paste it here.", color = Color.Gray, fontSize = 14.sp)

            OutlinedTextField(
                value = manualCookie,
                onValueChange = { manualCookie = it },
                label = { Text("Cookie String") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    if (manualCookie.isNotBlank()) {
                        onLoginSuccess(manualCookie, "", "")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Cookie")
            }
        }
    }
}
