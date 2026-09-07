package com.example.kthealthsync

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import com.example.kthealthsync.sync.HealthConnectManager
import com.example.kthealthsync.ui.dashboard.DashboardScreen
import com.example.kthealthsync.ui.dashboard.DashboardUiState
import com.example.kthealthsync.ui.dashboard.DashboardViewModel
import com.example.kthealthsync.ui.debug.DebugLogsScreen
import com.example.kthealthsync.ui.login.LoginScreen
import com.example.kthealthsync.ui.search.SearchScreen
import com.example.kthealthsync.ui.settings.SettingsScreen
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {

    private lateinit var prefs: PreferencesManager
    private val healthConnectManager by lazy { HealthConnectManager(this) }
    private val dashboardViewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferencesManager(this)

        val requestPermissionActivityContract = PermissionController.createRequestPermissionResultContract()
        val requestPermissions = registerForActivityResult(requestPermissionActivityContract) { granted ->
            if (granted.containsAll(healthConnectManager.permissions)) {
                Toast.makeText(this, "Permissions granted!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permissions denied", Toast.LENGTH_SHORT).show()
            }
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF64FFDA),
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E)
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var currentScreen by remember { mutableStateOf("dashboard") }
                    var preselectedMealTimeGuid by remember { mutableStateOf<String?>(null) }
                    val selectedDate by dashboardViewModel.selectedDate.collectAsState()
                    val dashboardUiState by dashboardViewModel.uiState.collectAsState()

                    BackHandler(enabled = currentScreen != "dashboard") {
                        when (currentScreen) {
                            "login" -> currentScreen = "settings"
                            "search" -> {
                                preselectedMealTimeGuid = null
                                currentScreen = "dashboard"
                            }
                            else -> currentScreen = "dashboard"
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        // Dashboard remains continuously mounted to preserve scroll position and spoiler states
                        DashboardScreen(
                            viewModel = dashboardViewModel,
                            onSettingsClick = { currentScreen = "settings" },
                            onDebugClick = { currentScreen = "debug" },
                            onAddFoodClick = { mealTimeGuid ->
                                preselectedMealTimeGuid = mealTimeGuid
                                currentScreen = "search"
                            }
                        )

                        AnimatedVisibility(
                            visible = currentScreen == "search",
                            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                        ) {
                            val formattedDate = selectedDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                            val summary = (dashboardUiState as? DashboardUiState.Success)?.summary
                            SearchScreen(
                                date = formattedDate,
                                preselectedMealTimeGuid = preselectedMealTimeGuid,
                                currentKcal = summary?.kcal ?: 0.0,
                                currentProtein = summary?.protein ?: 0.0,
                                currentCarbs = summary?.carb ?: 0.0,
                                currentFat = summary?.fat ?: 0.0,
                                currentFiber = summary?.fiber ?: 0.0,
                                currentSugar = summary?.sugar ?: 0.0,
                                currentSalt = summary?.salt ?: 0.0,
                                onBack = {
                                    preselectedMealTimeGuid = null
                                    currentScreen = "dashboard"
                                },
                                onFoodAdded = { addedInfo ->
                                    preselectedMealTimeGuid = null
                                    dashboardViewModel.addEntryOptimistic(
                                        title = addedInfo.title,
                                        kcal = addedInfo.kcal,
                                        protein = addedInfo.protein,
                                        carbs = addedInfo.carbs,
                                        fat = addedInfo.fat,
                                        fiber = addedInfo.fiber,
                                        sugar = addedInfo.sugar,
                                        salt = addedInfo.salt,
                                        unitStr = addedInfo.unitStr,
                                        mealTimeId = addedInfo.mealTimeId
                                    )
                                    dashboardViewModel.refresh(silent = true)
                                    currentScreen = "dashboard"
                                }
                            )
                        }

                        AnimatedVisibility(
                            visible = currentScreen == "settings",
                            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                        ) {
                            SettingsScreen(
                                onBack = { currentScreen = "dashboard" },
                                onRequestPermissions = {
                                    if (healthConnectManager.isAvailable()) {
                                        requestPermissions.launch(healthConnectManager.permissions)
                                    } else {
                                        Toast.makeText(this@MainActivity, "Health Connect is not available on this device", Toast.LENGTH_LONG).show()
                                    }
                                },
                                onLoginClick = { currentScreen = "login" }
                            )
                        }

                        AnimatedVisibility(
                            visible = currentScreen == "debug",
                            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                        ) {
                            DebugLogsScreen(onBack = { currentScreen = "dashboard" })
                        }

                        AnimatedVisibility(
                            visible = currentScreen == "login",
                            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                        ) {
                            LoginScreen(
                                prefs = prefs,
                                onBack = { currentScreen = "settings" },
                                onLoginSuccess = { cookieStr, email, md5Password ->
                                    lifecycleScope.launch {
                                        prefs.saveCookie(cookieStr)
                                        prefs.saveCredentials(email, md5Password)
                                        DebugLogger.addActionLog("Cookie and Credentials saved via Login")
                                        dashboardViewModel.refresh()
                                        currentScreen = "settings"
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
