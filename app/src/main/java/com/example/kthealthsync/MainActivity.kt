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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.kthealthsync.domain.model.AppLanguage
import com.example.kthealthsync.domain.model.LocalAppStrings
import com.example.kthealthsync.domain.model.getAppStrings
import com.example.kthealthsync.sync.HealthConnectManager
import com.example.kthealthsync.ui.dashboard.DashboardScreen
import com.example.kthealthsync.ui.dashboard.DashboardUiState
import com.example.kthealthsync.ui.dashboard.DashboardViewModel
import com.example.kthealthsync.ui.debug.DebugLogsScreen
import com.example.kthealthsync.ui.login.LoginScreen
import com.example.kthealthsync.ui.search.SearchViewModel
import com.example.kthealthsync.ui.search.SearchScreen
import com.example.kthealthsync.ui.settings.SettingsScreen
import com.example.kthealthsync.domain.update.AppUpdateManager
import com.example.kthealthsync.domain.update.AppUpdateState
import com.example.kthealthsync.ui.update.AppUpdatePrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.example.kthealthsync.data.repository.KalorickeRepository
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {

    companion object {
        const val ACTION_ADD_FOOD = "com.example.kthealthsync.action.ADD_FOOD"
        const val ACTION_SCAN_BARCODE = "com.example.kthealthsync.action.SCAN_BARCODE"
        const val ACTION_VIEW_TODAY = "com.example.kthealthsync.action.VIEW_TODAY"
    }

    private lateinit var prefs: PreferencesManager
    private val healthConnectManager by lazy { HealthConnectManager(this) }
    private val searchViewModel: SearchViewModel by viewModels()
    private val dashboardViewModel: DashboardViewModel by viewModels()
    private var isFirstLaunch = true
    private val pendingIntentAction = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    override fun onResume() {
        super.onResume()
        if (!isFirstLaunch) {
            dashboardViewModel.refresh(silent = true)
        } else {
            isFirstLaunch = false
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val cookie = prefs.cookieFlow.first()
            if (cookie.isNotBlank()) {
                val repo = KalorickeRepository(prefs = prefs)
                try {
                    val frequent = repo.getFrequentFoodstuff(limit = 15)
                    val favorites = repo.getFavoriteFoodstuff(limit = 15)
                    val combined = (frequent + favorites).distinctBy { item -> item.id ?: item.url }
                    com.example.kthealthsync.wear.WearSyncManager(applicationContext).syncDataToWatch(cookie, combined)
                } catch (_: Exception) {}
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        val action = intent?.action
        if (action == ACTION_ADD_FOOD || action == ACTION_SCAN_BARCODE || action == ACTION_VIEW_TODAY) {
            pendingIntentAction.value = action
            intent.action = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.parseColor("#121212")
        window.navigationBarColor = android.graphics.Color.parseColor("#121212")
        prefs = PreferencesManager(this)
        handleIntent(intent)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                searchViewModel.refreshMyMealsWhileActive()
            }
        }

        val requestPermissionActivityContract = PermissionController.createRequestPermissionResultContract()
        val requestPermissions = registerForActivityResult(requestPermissionActivityContract) { granted ->
            if (granted.containsAll(healthConnectManager.permissions)) {
                Toast.makeText(this, "Permissions granted!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permissions denied", Toast.LENGTH_SHORT).show()
            }
        }

        val requestNotificationPermission = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                dashboardViewModel.refresh(silent = true)
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val currentLanguage by prefs.resolvedLanguageFlow.collectAsState(initial = AppLanguage.RU)
            val appStrings = remember(currentLanguage) { getAppStrings(currentLanguage) }

            CompositionLocalProvider(LocalAppStrings provides appStrings) {
                MaterialTheme(
                    colorScheme = darkColorScheme(
                        primary = Color(0xFF64FFDA),
                        background = Color(0xFF121212),
                        surface = Color(0xFF1E1E1E)
                    )
                ) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    Surface(modifier = Modifier.fillMaxSize()) {
                        var currentScreen by remember { mutableStateOf("dashboard") }
                        var preselectedMealTimeGuid by remember { mutableStateOf<String?>(null) }
                        var initialSearchBarcode by remember { mutableStateOf<String?>(null) }
                        val selectedDate by dashboardViewModel.selectedDate.collectAsState()
                        val dashboardUiState by dashboardViewModel.uiState.collectAsState()

                        val updateManager = remember { AppUpdateManager.getInstance(context) }
                        val updateState by updateManager.updateState.collectAsState()
                        val showUpdatePrompt by updateManager.showPrompt.collectAsState()
                        val downloadState by updateManager.downloadState.collectAsState()

                        LaunchedEffect(Unit) {
                            updateManager.checkForUpdates(force = false)
                        }

                        val pendingAction by pendingIntentAction.collectAsState()
                        LaunchedEffect(pendingAction) {
                            when (pendingAction) {
                                ACTION_VIEW_TODAY -> {
                                    dashboardViewModel.selectDate(java.time.ZonedDateTime.now())
                                    currentScreen = "dashboard"
                                    pendingIntentAction.value = null
                                }
                                ACTION_ADD_FOOD -> {
                                    dashboardViewModel.selectDate(java.time.ZonedDateTime.now())
                                    preselectedMealTimeGuid = null
                                    initialSearchBarcode = null
                                    currentScreen = "search"
                                    pendingIntentAction.value = null
                                }
                                ACTION_SCAN_BARCODE -> {
                                    dashboardViewModel.selectDate(java.time.ZonedDateTime.now())
                                    pendingIntentAction.value = null
                                    com.example.kthealthsync.domain.util.BarcodeScannerHelper.scanBarcode(
                                        context = context,
                                        onSuccess = { barcode ->
                                            preselectedMealTimeGuid = null
                                            initialSearchBarcode = barcode
                                            currentScreen = "search"
                                        },
                                        onFailure = { e ->
                                            Toast.makeText(context, "${appStrings.scanError}: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }

                        BackHandler(enabled = currentScreen != "dashboard") {
                            when (currentScreen) {
                                "login" -> currentScreen = "settings"
                                "search" -> {
                                    preselectedMealTimeGuid = null
                                    initialSearchBarcode = null
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
                                    initialSearchBarcode = null
                                    currentScreen = "search"
                                },
                                onScanBarcodeClick = { mealTimeGuid ->
                                    com.example.kthealthsync.domain.util.BarcodeScannerHelper.scanBarcode(
                                        context = context,
                                        onSuccess = { barcode ->
                                            preselectedMealTimeGuid = mealTimeGuid
                                            initialSearchBarcode = barcode
                                            currentScreen = "search"
                                        },
                                        onFailure = { e ->
                                            Toast.makeText(context, "${appStrings.scanError}: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    )
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
                                    viewModel = searchViewModel,
                                    date = formattedDate,
                                    preselectedMealTimeGuid = preselectedMealTimeGuid,
                                    initialBarcode = initialSearchBarcode,
                                    currentKcal = summary?.kcal ?: 0.0,
                                    currentProtein = summary?.protein ?: 0.0,
                                    currentCarbs = summary?.carb ?: 0.0,
                                    currentFat = summary?.fat ?: 0.0,
                                    currentFiber = summary?.fiber ?: 0.0,
                                    currentSugar = summary?.sugar ?: 0.0,
                                    currentSalt = summary?.salt ?: 0.0,
                                    onBack = {
                                        preselectedMealTimeGuid = null
                                        initialSearchBarcode = null
                                        currentScreen = "dashboard"
                                    },
                                    onFoodAdded = { addedInfo ->
                                        preselectedMealTimeGuid = null
                                        initialSearchBarcode = null
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

                        val availableUpdate = updateState as? AppUpdateState.Available
                        if (availableUpdate != null) {
                            AppUpdatePrompt(
                                visible = showUpdatePrompt && currentScreen == "dashboard",
                                version = availableUpdate.displayVersion,
                                downloadState = downloadState,
                                onUpdateClick = {
                                    val currentDl = downloadState
                                    if (currentDl is com.example.kthealthsync.domain.update.DownloadState.ReadyToInstall) {
                                        updateManager.installApk(context, currentDl.apkFile)
                                    } else {
                                        updateManager.downloadAndInstall(context, availableUpdate.downloadUrl, availableUpdate.displayVersion)
                                    }
                                },
                                onDismissClick = {
                                    updateManager.dismissPrompt()
                                },
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }
                    }
                }
            }
        }
    }
    }
}
