package com.example.kthealthsync

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Volume
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

fun parseNutrient(value: String?): Double {
    if (value == null) return 0.0
    val cleaned = value.replace("[^0-9,.-]".toRegex(), "").replace(",", ".")
    return cleaned.toDoubleOrNull() ?: 0.0
}

fun scaleNutrient(value: String?, scale: Double): String? {
    if (value == null) return null
    val parsed = parseNutrient(value)
    return String.format(java.util.Locale.US, "%.1f", parsed * scale)
}

object RecipeCache {
    var mealMap: Map<String, String> = emptyMap() // title -> guid
    var lastMealListFetch: Long = 0
    val recipeDetails = ConcurrentHashMap<String, RecipeData>()
    
    suspend fun getMealMap(cookie: String): Map<String, String> {
        val now = System.currentTimeMillis()
        if (now - lastMealListFetch > 60_000) { // 1 minute cache
            try {
                val resp = RetrofitClient.instance.getMealList(cookie)
                val map = mutableMapOf<String, String>()
                resp.data?.forEach { item ->
                    if (item.title != null && item.guid != null) {
                        map[item.title] = item.guid
                    }
                }
                mealMap = map
                lastMealListFetch = now
            } catch (e: Exception) {
                DebugLogger.addActionLog("Failed to fetch meal list: ${e.message}")
            }
        }
        return mealMap
    }
    
    suspend fun getRecipeDetail(guid: String, cookie: String): RecipeData? {
        if (recipeDetails.containsKey(guid)) return recipeDetails[guid]
        try {
            val resp = RetrofitClient.instance.getRecipeDetail(guid, cookie)
            resp.data?.let {
                recipeDetails[guid] = it
                return it
            }
        } catch (e: Exception) {
            DebugLogger.addActionLog("Failed to fetch recipe $guid: ${e.message}")
        }
        return null
    }
}

data class FoodBreakdown(val name: String, val amount: Double, val components: List<FoodBreakdown> = emptyList())

data class DailySummary(
    val kcal: Double, val protein: Double, val fat: Double, val carb: Double,
    val sugar: Double, val salt: Double, val water: Double, val fiber: Double, val date: String,
    val kcalBreakdown: List<FoodBreakdown> = emptyList(),
    val proteinBreakdown: List<FoodBreakdown> = emptyList(),
    val fatBreakdown: List<FoodBreakdown> = emptyList(),
    val carbBreakdown: List<FoodBreakdown> = emptyList(),
    val sugarBreakdown: List<FoodBreakdown> = emptyList(),
    val saltBreakdown: List<FoodBreakdown> = emptyList(),
    val waterBreakdown: List<FoodBreakdown> = emptyList(),
    val fiberBreakdown: List<FoodBreakdown> = emptyList()
)

fun aggregateMacro(
    foodstuffs: List<Foodstuff>,
    extractor: (Foodstuff) -> Double,
    withMeals: Boolean
): List<FoodBreakdown> {
    val result = mutableListOf<FoodBreakdown>()
    for (f in foodstuffs) {
        val fname = f.title ?: f.name ?: f.nazev ?: "Unknown"
        val value = extractor(f)
        val children = f.foodstuff ?: f.items ?: f.ingredients ?: f.components
        val isRecipe = f.isRecipe == true || !children.isNullOrEmpty()

        if (isRecipe && withMeals) {
            val childBreakdowns = children?.let { aggregateMacro(it, extractor, false) } ?: emptyList()
            if (value > 0 || childBreakdowns.isNotEmpty()) {
                result.add(FoodBreakdown(fname, value, childBreakdowns))
            }
        } else if (isRecipe && !withMeals) {
            if (children != null && children.isNotEmpty()) {
                result.addAll(aggregateMacro(children, extractor, false))
            } else {
                if (value > 0) result.add(FoodBreakdown(fname, value))
            }
        } else {
            if (value > 0) result.add(FoodBreakdown(fname, value))
        }
    }
    return mergeBreakdowns(result)
}

fun mergeBreakdowns(breakdowns: List<FoodBreakdown>): List<FoodBreakdown> {
    return breakdowns.groupBy { it.name }.map { (name, items) ->
        val totalAmount = items.sumOf { it.amount }.takeIf { it > 0 } ?: items.firstOrNull()?.amount ?: 0.0
        val allComponents = items.flatMap { it.components }
        val mergedComponents = if (allComponents.isNotEmpty()) mergeBreakdowns(allComponents) else emptyList()
        FoodBreakdown(name, totalAmount, mergedComponents)
    }.sortedByDescending { it.amount }
}

class MainActivity : ComponentActivity() {

    private lateinit var prefs: PreferencesManager
    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(this) }
    
    private val permissions = setOf(
        HealthPermission.getWritePermission(NutritionRecord::class),
        HealthPermission.getReadPermission(NutritionRecord::class),
        HealthPermission.getWritePermission(HydrationRecord::class),
        HealthPermission.getReadPermission(HydrationRecord::class)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferencesManager(this)

        val requestPermissionActivityContract = PermissionController.createRequestPermissionResultContract()
        val requestPermissions = registerForActivityResult(requestPermissionActivityContract) { granted ->
            if (granted.containsAll(permissions)) {
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
                    var summaryData by remember { mutableStateOf<DailySummary?>(null) }
                    var errorMessage by remember { mutableStateOf<String?>(null) }
                    var selectedDate by remember { mutableStateOf(ZonedDateTime.now().minusDays(1)) }
                    val cookie by prefs.cookieFlow.collectAsState(initial = "")
                    var rawDiaryResponse by remember { mutableStateOf<DiaryResponse?>(null) }
                    var withMeals by remember { mutableStateOf(false) }

                    LaunchedEffect(cookie, currentScreen, selectedDate) {
                        if (currentScreen == "dashboard") {
                            if (cookie.isEmpty()) {
                                summaryData = null
                                errorMessage = "Please log in in Settings first."
                                return@LaunchedEffect
                            }
                            
                            summaryData = null // Show loading state
                            errorMessage = null
                            val dateStr = selectedDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                            try {
                                DebugLogger.addActionLog("Fetching data from Kalorické Tabulky for $dateStr...")
                                val diaryResponse = RetrofitClient.instance.getDiaryWithAutoLogin(dateStr, prefs)
                                rawDiaryResponse = diaryResponse

                                var kcal = 0.0; var prot = 0.0; var fat = 0.0; var carb = 0.0
                                var sugar = 0.0; var salt = 0.0; var water = 0.0; var fiber = 0.0
                                val allFoodstuffs = diaryResponse.data?.times?.flatMap { it.foodstuff ?: emptyList() } ?: emptyList()

                                for (f in allFoodstuffs) {
                                    kcal += f.energy?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
                                    prot += f.protein?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
                                    fat += f.fat?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
                                    carb += f.carbohydrate?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
                                    sugar += f.sugar?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
                                    salt += f.salt?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
                                    water += f.water?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
                                    fiber += f.fiber?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
                                }
                                
                                val now = ZonedDateTime.now()
                                val isToday = selectedDate.toLocalDate() == now.toLocalDate()
                                if (!isToday && (kcal > 0 || prot > 0 || water > 0)) {
                                    val granted = healthConnectClient.permissionController.getGrantedPermissions()
                                    if (granted.containsAll(permissions)) {
                                        val endTime = selectedDate.withHour(23).withMinute(59).withSecond(0).toInstant()
                                        val startTime = endTime.minusSeconds(60)
                                        val zoneOffset = ZoneId.systemDefault().rules.getOffset(startTime)

                                        val records = mutableListOf<androidx.health.connect.client.records.Record>()
                                        records.add(NutritionRecord(
                                            startTime = startTime, endTime = endTime,
                                            startZoneOffset = zoneOffset, endZoneOffset = zoneOffset,
                                            energy = Energy.kilocalories(kcal), protein = Mass.grams(prot),
                                            totalFat = Mass.grams(fat), totalCarbohydrate = Mass.grams(carb),
                                            sugar = Mass.grams(sugar), sodium = Mass.grams(salt),
                                            dietaryFiber = Mass.grams(fiber),
                                            name = "KT Auto Import"
                                        ))
                                        if (water > 0.0) {
                                            records.add(HydrationRecord(
                                                startTime = startTime, endTime = endTime,
                                                startZoneOffset = zoneOffset, endZoneOffset = zoneOffset,
                                                volume = Volume.liters(water / 1000.0)
                                            ))
                                        }
                                        healthConnectClient.insertRecords(records)
                                        DebugLogger.addActionLog("Synced ${kcal.toInt()} kcal to Health Connect for $dateStr")
                                    } else {
                                        DebugLogger.addActionLog("Missing HC permissions for auto-sync on $dateStr")
                                    }
                                } else if (isToday) {
                                    DebugLogger.addActionLog("Fetched data for $dateStr but skipped HC sync (not midnight yet)")
                                } else {
                                    DebugLogger.addActionLog("No data found to sync for $dateStr")
                                }
                            } catch (e: Exception) {
                                val errorMsg = e.message ?: "Unknown error"
                                if (errorMsg.contains("302") || errorMsg.contains("401")) {
                                    prefs.saveCookie("") // clear it
                                    errorMessage = "Session expired. Please log in again."
                                } else {
                                    errorMessage = "Error: $errorMsg"
                                }
                                DebugLogger.addActionLog("Error loading $dateStr: $errorMsg")
                            }
                        }
                    }

                    LaunchedEffect(rawDiaryResponse, withMeals) {
                        val diaryResponse = rawDiaryResponse ?: return@LaunchedEffect
                        val dateStr = selectedDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                        
                        val cookie = prefs.cookieFlow.first()
                        val mealMap = RecipeCache.getMealMap(cookie)
                        val rawFoods = diaryResponse.data?.times?.flatMap { it.foodstuff ?: emptyList() } ?: emptyList()
                        DebugLogger.addActionLog("Fetched diary. Foods: ${rawFoods.size}, Custom meals cached: ${mealMap.size}")
                        
                        val allFoodstuffs = rawFoods.map { f ->
                            val name = f.title ?: f.name ?: f.nazev
                            // The diary item might not have a guid, so we match by title against the mealMap
                            val guid = f.guid ?: f.guidRecipe ?: mealMap[name]
                            if (guid != null && (mealMap.containsValue(guid) || mealMap.containsKey(name))) {
                                DebugLogger.addActionLog("Matched custom meal: $name (guid: $guid)")
                                val recipeData = RecipeCache.getRecipeDetail(guid, cookie)
                                if (recipeData != null) {
                                    DebugLogger.addActionLog("Fetched recipe data for $name, content size: ${recipeData.content?.size}")
                                    val recipeEnergy = parseNutrient(recipeData.energy)
                                    val loggedEnergy = parseNutrient(f.energy)
                                    val scale = if (recipeEnergy > 0) loggedEnergy / recipeEnergy else 1.0
                                    
                                    val components = recipeData.content?.mapNotNull { item ->
                                        val compFood = item.foodstuff
                                        if (compFood != null) {
                                            compFood.copy(
                                                protein = scaleNutrient(compFood.protein, scale),
                                                fat = scaleNutrient(compFood.fat, scale),
                                                carbohydrate = scaleNutrient(compFood.carbohydrate, scale),
                                                sugar = scaleNutrient(compFood.sugar, scale),
                                                salt = scaleNutrient(compFood.salt, scale),
                                                fiber = scaleNutrient(compFood.fiber, scale),
                                                water = scaleNutrient(compFood.water, scale),
                                                energy = scaleNutrient(compFood.energy, scale)
                                            )
                                        } else null
                                    }
                                    f.copy(components = components, isRecipe = true)
                                } else f
                            } else f
                        }
                        var kcal = 0.0; var prot = 0.0; var fat = 0.0; var carb = 0.0
                        var sugar = 0.0; var salt = 0.0; var water = 0.0; var fiber = 0.0

                        for (f in allFoodstuffs) {
                            kcal += f.energy?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
                            prot += f.protein?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
                            fat += f.fat?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
                            carb += f.carbohydrate?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
                            sugar += f.sugar?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
                            salt += f.salt?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
                            water += f.water?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
                            fiber += f.fiber?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
                        }

                        summaryData = DailySummary(
                            kcal, prot, fat, carb, sugar, salt, water, fiber, dateStr,
                            aggregateMacro(allFoodstuffs, { parseNutrient(it.energy) }, withMeals),
                            aggregateMacro(allFoodstuffs, { parseNutrient(it.protein) }, withMeals),
                            aggregateMacro(allFoodstuffs, { parseNutrient(it.fat) }, withMeals),
                            aggregateMacro(allFoodstuffs, { parseNutrient(it.carbohydrate) }, withMeals),
                            aggregateMacro(allFoodstuffs, { parseNutrient(it.sugar) }, withMeals),
                            aggregateMacro(allFoodstuffs, { parseNutrient(it.salt) }, withMeals),
                            aggregateMacro(allFoodstuffs, { parseNutrient(it.water) }, withMeals),
                            aggregateMacro(allFoodstuffs, { parseNutrient(it.fiber) }, withMeals)
                        )
                    }

                    if (currentScreen == "dashboard") {
                        DashboardScreen(
                            summary = summaryData,
                            errorMessage = errorMessage,
                            selectedDate = selectedDate,
                            onDateChange = { selectedDate = it },
                            withMeals = withMeals,
                            onWithMealsChange = { withMeals = it },
                            onSettingsClick = { currentScreen = "settings" },
                            onDebugClick = { currentScreen = "debug" },
                            onAddFoodClick = { currentScreen = "search" }
                        )
                    } else if (currentScreen == "search") {
                        val formattedDate = selectedDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                        SearchScreen(
                            currentKcal = summaryData?.kcal ?: 0.0,
                            currentProtein = summaryData?.protein ?: 0.0,
                            currentCarbs = summaryData?.carb ?: 0.0,
                            currentFat = summaryData?.fat ?: 0.0,
                            currentFiber = summaryData?.fiber ?: 0.0,
                            currentSugar = summaryData?.sugar ?: 0.0,
                            currentSalt = summaryData?.salt ?: 0.0,
                            date = formattedDate,
                            onBack = { currentScreen = "dashboard" },
                            onFoodAdded = { 
                                currentScreen = "dashboard"
                                // Force refresh by temporarily clearing the selected date and setting it back
                                val tempDate = selectedDate
                                selectedDate = tempDate.minusDays(1)
                                selectedDate = tempDate
                            }
                        )
                    } else if (currentScreen == "settings") {
                        SettingsScreen(
                            prefs = prefs,
                            onBack = { currentScreen = "dashboard" },
                            onRequestPermissions = { requestPermissions.launch(permissions) },
                            onScheduleWorker = { e, h, m -> scheduleWorker(e, h, m) },
                            onLoginClick = { currentScreen = "login" }
                        )
                    } else if (currentScreen == "debug") {
                        DebugLogsScreen(onBack = { currentScreen = "dashboard" })
                    } else if (currentScreen == "login") {
                        LoginScreen(
                            prefs = prefs,
                            onBack = { currentScreen = "settings" },
                            onLoginSuccess = { cookieStr, email, md5Password ->
                                lifecycleScope.launch { 
                                    prefs.saveCookie(cookieStr)
                                    prefs.saveCredentials(email, md5Password) 
                                }
                                DebugLogger.addActionLog("Cookie and Credentials saved via Login")
                                currentScreen = "settings"
                            }
                        )
                    }
                }
            }
        }
    }

    private fun scheduleWorker(enabled: Boolean, hour: Int, minute: Int) {
        val workManager = WorkManager.getInstance(this)
        if (!enabled) {
            workManager.cancelUniqueWork("KTSyncWorker")
            DebugLogger.addActionLog("Auto-sync disabled via Settings")
            return
        }
        
        DebugLogger.addActionLog("Auto-sync scheduled for $hour:$minute")
        val now = ZonedDateTime.now()
        var targetTime = now.with(LocalTime.of(hour, minute, 0, 0))
        if (!now.isBefore(targetTime)) targetTime = targetTime.plusDays(1)
        val initialDelay = Duration.between(now, targetTime).toMillis()

        val syncWork = PeriodicWorkRequestBuilder<SyncWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork("KTSyncWorker", ExistingPeriodicWorkPolicy.UPDATE, syncWork)
        Toast.makeText(this, "Scheduled for ${String.format("%02d:%02d", hour, minute)}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun RecursiveBreakdownItem(item: FoodBreakdown, title: String, grandTotal: Double, depth: Int = 0) {
    var expanded by remember { mutableStateOf(false) }
    val pct = if (grandTotal > 0) (item.amount / grandTotal) * 100 else 0.0
    val unitStr = if (title == "Calories") "kcal" else if (title == "💧 Water") "ml" else "g"
    val hasChildren = item.components.isNotEmpty()

    Column(modifier = Modifier.fillMaxWidth().padding(start = (depth * 16).dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .then(if (hasChildren) Modifier.clickable { expanded = !expanded } else Modifier),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (hasChildren) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Expand",
                        modifier = Modifier.size(20.dp).padding(end = 4.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.width(20.dp))
                }
                Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${String.format(java.util.Locale.US, "%.1f", item.amount)} $unitStr", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("${String.format(java.util.Locale.US, "%.1f", pct)}%", fontSize = 12.sp, color = Color.Gray)
            }
        }
        if (expanded && hasChildren) {
            item.components.forEach { child ->
                RecursiveBreakdownItem(child, title, grandTotal, depth + 1)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    summary: DailySummary?, 
    errorMessage: String?, 
    selectedDate: ZonedDateTime,
    onDateChange: (ZonedDateTime) -> Unit,
    withMeals: Boolean,
    onWithMealsChange: (Boolean) -> Unit,
    onSettingsClick: () -> Unit, 
    onDebugClick: () -> Unit,
    onAddFoodClick: () -> Unit
) {
    val context = LocalContext.current
    val now = ZonedDateTime.now()
    val isToday = selectedDate.toLocalDate() == now.toLocalDate()
    val isYesterday = selectedDate.toLocalDate() == now.minusDays(1).toLocalDate()
    
    val dateDisplay = when {
        isToday -> "Today"
        isYesterday -> "Yesterday"
        else -> selectedDate.format(DateTimeFormatter.ofPattern("EEE, MMM dd", java.util.Locale.getDefault()))
    }
    
    val canGoForward = !isToday

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onDateChange(selectedDate.minusDays(1)) }) {
                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Previous Day")
                        }
                        TextButton(onClick = {
                            val dialog = DatePickerDialog(context, { _, y, m, d ->
                                val newDate = ZonedDateTime.now().withYear(y).withMonth(m+1).withDayOfMonth(d)
                                if (!newDate.isAfter(now)) {
                                    onDateChange(newDate)
                                } else {
                                    Toast.makeText(context, "Cannot select future dates", Toast.LENGTH_SHORT).show()
                                }
                            }, selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth)
                            // Restrict to max today
                            dialog.datePicker.maxDate = System.currentTimeMillis()
                            dialog.show()
                        }) {
                            Text(dateDisplay, color = Color.White, fontSize = 20.sp)
                        }
                        IconButton(
                            onClick = { onDateChange(selectedDate.plusDays(1)) },
                            enabled = canGoForward
                        ) {
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next Day", tint = if (canGoForward) Color.White else Color.Gray)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onDebugClick) {
                        Icon(Icons.Default.Info, contentDescription = "Debug")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            androidx.compose.material3.FloatingActionButton(
                onClick = onAddFoodClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Food/Water")
            }
        }
    ) { padding ->
        var showPopupFor by remember { mutableStateOf<Pair<String, List<FoodBreakdown>>?>(null) }
        
        showPopupFor?.let { (title, breakdownList) ->
            AlertDialog(
                onDismissRequest = { showPopupFor = null },
                title = { Text(title) },
                text = {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        val total = breakdownList.sumOf { it.amount }
                        items(breakdownList) { item ->
                            RecursiveBreakdownItem(item, title, total, 0)
                        }
                    }
                },

                confirmButton = {
                    TextButton(onClick = { showPopupFor = null }) { Text("Close") }
                }
            )
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                Text("With meals")
                Spacer(modifier = Modifier.width(8.dp))
                Switch(checked = withMeals, onCheckedChange = onWithMealsChange)
            }

            if (errorMessage != null) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }

            if (summary == null) {
                CircularProgressIndicator(modifier = Modifier.padding(32.dp))
            } else {
                val safeSummary = summary

            val totalMacros = safeSummary.protein + safeSummary.fat + safeSummary.carb
            val protSweep = if (totalMacros > 0) ((safeSummary.protein / totalMacros) * 360).toFloat() else 0f
                val fatSweep = if (totalMacros > 0) ((safeSummary.fat / totalMacros) * 360).toFloat() else 0f
                val carbSweep = if (totalMacros > 0) ((safeSummary.carb / totalMacros) * 360).toFloat() else 0f
                
                val isLoading = summary == null
                val alpha = if (isLoading) 0.3f else 1f
                
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp).clickable(enabled = !isLoading) { 
                    if (safeSummary.kcalBreakdown.isNotEmpty()) showPopupFor = "Calories" to safeSummary.kcalBreakdown 
                }) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 30.dp.toPx()
                        if (isLoading || totalMacros == 0.0) {
                            drawArc(Color.DarkGray.copy(alpha = alpha), 0f, 360f, false, style = Stroke(strokeWidth))
                        } else {
                            drawArc(Color(0xFFE57373), -90f, protSweep, false, style = Stroke(strokeWidth, cap = StrokeCap.Round)) // Red = Prot
                            drawArc(Color(0xFFFFD54F), -90f + protSweep, fatSweep, false, style = Stroke(strokeWidth, cap = StrokeCap.Round)) // Yellow = Fat
                            drawArc(Color(0xFF64B5F6), -90f + protSweep + fatSweep, carbSweep, false, style = Stroke(strokeWidth, cap = StrokeCap.Round)) // Blue = Carb
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${safeSummary.kcal.toInt()}", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = alpha))
                        Text("kcal", fontSize = 14.sp, color = Color.White.copy(alpha = alpha))
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Macros Row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    val protColor = if (isLoading) Color.Gray else Color(0xFFE57373)
                    val fatColor = if (isLoading) Color.Gray else Color(0xFFFFD54F)
                    val carbColor = if (isLoading) Color.Gray else Color(0xFF64B5F6)
                    MacroItem("Protein", safeSummary.protein, protColor, alpha) {
                        if (!isLoading && safeSummary.proteinBreakdown.isNotEmpty()) showPopupFor = "Protein" to safeSummary.proteinBreakdown
                    }
                    MacroItem("Fat", safeSummary.fat, fatColor, alpha) {
                        if (!isLoading && safeSummary.fatBreakdown.isNotEmpty()) showPopupFor = "Fat" to safeSummary.fatBreakdown
                    }
                    MacroItem("Carbs", safeSummary.carb, carbColor, alpha) {
                        if (!isLoading && safeSummary.carbBreakdown.isNotEmpty()) showPopupFor = "Carbohydrates" to safeSummary.carbBreakdown
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                HorizontalDivider(color = Color.DarkGray)
                Spacer(modifier = Modifier.height(16.dp))
                
                // Micros Row 1
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    val waterLiters = safeSummary.water / 1000.0
                    MicroItem("💧 Water", String.format(java.util.Locale.US, "%.1f L", waterLiters), alpha) {
                        if (!isLoading && safeSummary.waterBreakdown.isNotEmpty()) showPopupFor = "💧 Water" to safeSummary.waterBreakdown
                    }
                    MicroItem("🌾 Fiber", "${safeSummary.fiber.toInt()} g", alpha) {
                        if (!isLoading && safeSummary.fiberBreakdown.isNotEmpty()) showPopupFor = "🌾 Fiber" to safeSummary.fiberBreakdown
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Micros Row 2
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MicroItem("🍬 Sugar", "${safeSummary.sugar.toInt()} g", alpha) {
                        if (!isLoading && safeSummary.sugarBreakdown.isNotEmpty()) showPopupFor = "🍬 Sugar" to safeSummary.sugarBreakdown
                    }
                    MicroItem("🧂 Salt", String.format(java.util.Locale.US, "%.1f g", safeSummary.salt), alpha) {
                        if (!isLoading && safeSummary.saltBreakdown.isNotEmpty()) showPopupFor = "🧂 Salt" to safeSummary.saltBreakdown
                    }
                }
            }
        }
    }
}

@Composable
fun MacroItem(name: String, amount: Double, color: Color, alpha: Float = 1f, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Text(name, color = color.copy(alpha = alpha), fontWeight = FontWeight.Bold)
        Text("${amount.toInt()} g", color = Color.White.copy(alpha = alpha))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MicroItem(icon: String, value: String, alpha: Float = 1f, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E).copy(alpha = alpha)),
        modifier = Modifier.width(100.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(icon, fontSize = 16.sp, color = Color.White.copy(alpha = alpha))
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White.copy(alpha = alpha))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefs: PreferencesManager,
    onBack: () -> Unit,
    onRequestPermissions: () -> Unit,
    onScheduleWorker: (Boolean, Int, Int) -> Unit,
    onLoginClick: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    
    val cookie by prefs.cookieFlow.collectAsState(initial = "")
    val syncHour by prefs.syncHourFlow.collectAsState(initial = 6)
    val syncMinute by prefs.syncMinuteFlow.collectAsState(initial = 0)
    val autoSyncEnabled by prefs.autoSyncEnabledFlow.collectAsState(initial = false)

    val timePickerDialog = android.app.TimePickerDialog(
        context,
        { _, hourOfDay, minuteOfHour ->
            coroutineScope.launch { prefs.saveSyncTime(hourOfDay, minuteOfHour) }
            onScheduleWorker(autoSyncEnabled, hourOfDay, minuteOfHour)
            DebugLogger.addActionLog("Sync time changed to ${String.format(java.util.Locale.US, "%02d:%02d", hourOfDay, minuteOfHour)}")
        },
        syncHour,
        syncMinute,
        true // 24-hour format
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            SettingSectionHeader("Authentication")
            SettingItem(
                icon = Icons.Default.Info,
                title = "Kalorické Tabulky Login",
                subtitle = if (cookie.isEmpty()) "Not logged in. Tap to log in." else "Logged in. Tap to re-login if sync fails.",
                onClick = onLoginClick
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFF2A2A2A))
            
            SettingSectionHeader("Background Synchronization")
            SettingItem(
                icon = Icons.Default.Settings,
                title = "Enable Auto-Sync",
                subtitle = "Automatically fetch yesterday's data in the background.",
                onClick = { 
                    val newValue = !autoSyncEnabled
                    coroutineScope.launch { prefs.setAutoSync(newValue) }
                    onScheduleWorker(newValue, syncHour, syncMinute)
                },
                trailingContent = {
                    Switch(
                        checked = autoSyncEnabled,
                        onCheckedChange = { checked ->
                            coroutineScope.launch { prefs.setAutoSync(checked) }
                            onScheduleWorker(checked, syncHour, syncMinute)
                        }
                    )
                }
            )
            
            if (autoSyncEnabled) {
                SettingItem(
                    icon = Icons.Default.Info,
                    title = "Sync Time",
                    subtitle = "Current time: ${String.format(java.util.Locale.US, "%02d:%02d", syncHour, syncMinute)}.\nTap to choose when the background sync should run.",
                    onClick = { timePickerDialog.show() }
                )
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFF2A2A2A))
            
            SettingSectionHeader("System")
            SettingItem(
                icon = Icons.Default.Info,
                title = "Health Connect Permissions",
                subtitle = "Review or grant permissions required to write data to Health Connect.",
                onClick = onRequestPermissions
            )
        }
    }
}

@Composable
fun SettingSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailingContent: @Composable (() -> Unit)? = null
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color.LightGray)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Spacer(modifier = Modifier.height(4.dp))
                Text(subtitle, fontSize = 13.sp, color = Color.Gray, lineHeight = 18.sp)
            }
            if (trailingContent != null) {
                Spacer(modifier = Modifier.width(16.dp))
                trailingContent()
            }
        }
    }
}

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
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
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
                    Button(onClick = { DebugLogger.clearLogs() }) { Text("Clear") }
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
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            if (log.isNetwork) {
                                Text("${log.timestamp} | ${log.method} ${log.url}", fontWeight = FontWeight.Bold, color = Color.Cyan, modifier = Modifier.weight(1f))
                            } else {
                                Text("${log.timestamp} | ${log.message}", fontWeight = FontWeight.Bold, color = Color(0xFFFFA726), modifier = Modifier.weight(1f))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(prefs: PreferencesManager, onBack: () -> Unit, onLoginSuccess: (String, String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var manualCookie by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

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
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
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
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
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
                            // Kalorické Tabulky requires the password to be MD5 hashed client-side
                            val md5Password = java.security.MessageDigest.getInstance("MD5")
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
                                    // Extract JSESSIONID cookie from headers
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
