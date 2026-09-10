package com.example.kthealthsync.ui.dashboard

import android.app.DatePickerDialog
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.example.kthealthsync.DebugLogger
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kthealthsync.DiaryResponse
import com.example.kthealthsync.Foodstuff
import com.example.kthealthsync.MealTime
import com.example.kthealthsync.domain.model.DailySummary
import com.example.kthealthsync.domain.model.FoodBreakdown
import com.example.kthealthsync.domain.model.LocalAppStrings
import com.example.kthealthsync.domain.model.ShareFormatType
import com.example.kthealthsync.domain.util.NutrientUtils
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onSettingsClick: () -> Unit,
    onDebugClick: () -> Unit,
    onAddFoodClick: (mealTimeGuid: String?) -> Unit,
    onScanBarcodeClick: (mealTimeGuid: String?) -> Unit = {}
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val selectedDate by viewModel.selectedDate.collectAsState()
    val withMeals by viewModel.withMeals.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val nutrientGoals by viewModel.nutrientGoals.collectAsState()

    val now = ZonedDateTime.now()
    val isToday = selectedDate.toLocalDate() == now.toLocalDate()
    val isYesterday = selectedDate.toLocalDate() == now.minusDays(1).toLocalDate()

    val dateDisplay = when {
        isToday -> strings.dateToday
        isYesterday -> strings.dateYesterday
        else -> selectedDate.format(DateTimeFormatter.ofPattern("EEE, MMM dd", strings.language.toLocale()))
    }
    val canGoForward = !isToday
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 40.dp.toPx() }
    var totalDragX by remember { mutableFloatStateOf(0f) }

    val clipboardManager = LocalClipboardManager.current
    var showPopupFor by remember { mutableStateOf<Pair<String, List<FoodBreakdown>>?>(null) }
    var showShareDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.prevDay() }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Day")
                        }
                        TextButton(onClick = {
                            val dialog = DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    val newDate = selectedDate
                                        .withYear(year)
                                        .withMonth(month + 1)
                                        .withDayOfMonth(dayOfMonth)
                                    viewModel.selectDate(newDate)
                                },
                                selectedDate.year,
                                selectedDate.monthValue - 1,
                                selectedDate.dayOfMonth
                            )
                            dialog.datePicker.maxDate = System.currentTimeMillis()
                            dialog.show()
                        }) {
                            Text(dateDisplay, color = Color.White, fontSize = 20.sp)
                        }
                        IconButton(
                            onClick = { viewModel.nextDay() },
                            enabled = canGoForward
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Next Day",
                                tint = if (canGoForward) Color.White else Color.Gray
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val state = uiState
                        if (state is DashboardUiState.Success) {
                            showShareDialog = true
                        } else {
                            Toast.makeText(context, strings.noDiaryEntries, Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = null)
                    }
                    IconButton(onClick = onDebugClick) {
                        Icon(Icons.Default.Info, contentDescription = "Debug")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = strings.settingsTitle)
                    }
                }
            )
        }
    ) { padding ->
        var selectedEntryIds by remember { mutableStateOf<Set<String>>(emptySet()) }
        var showMoveDialog by remember { mutableStateOf(false) }
        var showDeleteDialog by remember { mutableStateOf(false) }
        var singleMoveTargetId by remember { mutableStateOf<String?>(null) }
        var singleDeleteTargetId by remember { mutableStateOf<String?>(null) }
        var showCloneDialog by remember { mutableStateOf(false) }
        var singleCloneTargetFood by remember { mutableStateOf<Foodstuff?>(null) }
        var showEditDialog by remember { mutableStateOf(false) }
        var editTarget by remember { mutableStateOf<Pair<Foodstuff, String>?>(null) }

        val allValidFoods = remember(uiState) {
            (uiState as? DashboardUiState.Success)?.diaryResponse?.data?.times
                ?.flatMap { it.foodstuff ?: emptyList() } ?: emptyList()
        }
        val allValidIds = remember(allValidFoods) {
            allValidFoods.mapNotNull { it.id }.toSet()
        }
        LaunchedEffect(allValidIds) {
            selectedEntryIds = selectedEntryIds.intersect(allValidIds)
        }

        showPopupFor?.let { (title, breakdownList) ->
            AlertDialog(
                onDismissRequest = { showPopupFor = null },
                title = { Text(title) },
                text = {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        val total = breakdownList.sumOf { it.amount }
                        items(breakdownList) { item ->
                            RecursiveBreakdownItem(
                                item = item,
                                title = title,
                                grandTotal = total,
                                depth = 0,
                                onAddFood = { breakdown ->
                                    val food = breakdown.originalFood ?: allValidFoods.find { f ->
                                        (f.name != null && f.name.equals(breakdown.name, ignoreCase = true)) ||
                                        (f.title != null && f.title.equals(breakdown.name, ignoreCase = true))
                                    } ?: Foodstuff(
                                        name = breakdown.name,
                                        title = breakdown.name
                                    )
                                    singleCloneTargetFood = food
                                    showCloneDialog = true
                                }
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPopupFor = null }) { Text(strings.close) }
                }
            )
        }

        if (showShareDialog) {
            val state = uiState
            if (state is DashboardUiState.Success) {
                ShareOptionsDialog(
                    dateDisplay = dateDisplay,
                    onDismiss = { showShareDialog = false },
                    onOptionSelected = { format, copyOnly ->
                        val shareText = NutrientUtils.formatDailyShareText(
                            type = format,
                            dateStr = dateDisplay,
                            summary = state.summary,
                            goals = nutrientGoals,
                            diaryResponse = state.diaryResponse
                        )
                        if (copyOnly) {
                            clipboardManager.setText(AnnotatedString(shareText))
                            Toast.makeText(context, strings.saveSuccess, Toast.LENGTH_SHORT).show()
                        } else {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, shareText)
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, format.title)
                            context.startActivity(shareIntent)
                        }
                        showShareDialog = false
                    }
                )
            }
        }

        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(canGoForward) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (totalDragX > swipeThresholdPx) {
                                    viewModel.prevDay()
                                } else if (totalDragX < -swipeThresholdPx && canGoForward) {
                                    viewModel.nextDay()
                                }
                                totalDragX = 0f
                            },
                            onDragCancel = {
                                totalDragX = 0f
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                totalDragX += dragAmount
                            }
                        )
                    }
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = Color(0xFF1E1E1E),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color(0xFF2E2E2E)),
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(bottom = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(strings.withMeals, fontSize = 12.sp, color = Color(0xFFBBBBBB))
                        Spacer(modifier = Modifier.width(6.dp))
                        Switch(
                            checked = withMeals,
                            onCheckedChange = { viewModel.toggleWithMeals(it) },
                            modifier = Modifier.scale(0.75f)
                        )
                    }
                }

                when (val state = uiState) {
                    is DashboardUiState.Loading -> {
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is DashboardUiState.Error -> {
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(state.message, color = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { viewModel.refresh() }) {
                                    Text(strings.retry)
                                }
                            }
                        }
                    }
                    is DashboardUiState.Success -> {
                        val summary = state.summary
                        val totalMacros = summary.protein + summary.fat + summary.carb
                        val goalKcal = nutrientGoals.kcal

                        // Calculate progress sweep matching calorie goal
                        val progressRatio = if (goalKcal > 0) (summary.kcal / goalKcal).toFloat() else 1f
                        val totalSweep = (progressRatio * 360f).coerceIn(0f, 360f)

                        val protSweep = if (totalMacros > 0) ((summary.protein / totalMacros).toFloat() * totalSweep) else 0f
                        val fatSweep = if (totalMacros > 0) ((summary.fat / totalMacros).toFloat() * totalSweep) else 0f
                        val carbSweep = if (totalMacros > 0) ((summary.carb / totalMacros).toFloat() * totalSweep) else 0f

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(148.dp)
                                .clickable {
                                    if (summary.kcalBreakdown.isNotEmpty()) {
                                        showPopupFor = "${strings.energy} (${strings.kcalUnit})" to summary.kcalBreakdown
                                    }
                                }
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val strokeWidth = 18.dp.toPx()
                                // Background circular track
                                drawArc(
                                    color = Color(0xFF262626),
                                    startAngle = 0f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    style = Stroke(strokeWidth)
                                )

                                if (totalSweep > 0f) {
                                    if (totalMacros == 0.0) {
                                        drawArc(
                                            color = Color(0xFF81C784),
                                            startAngle = -90f,
                                            sweepAngle = totalSweep,
                                            useCenter = false,
                                            style = Stroke(strokeWidth)
                                        )
                                    } else {
                                        drawArc(Color(0xFFE57373), -90f, protSweep, false, style = Stroke(strokeWidth))
                                        drawArc(Color(0xFFFFD54F), -90f + protSweep, fatSweep, false, style = Stroke(strokeWidth))
                                        drawArc(Color(0xFF64B5F6), -90f + protSweep + fatSweep, carbSweep, false, style = Stroke(strokeWidth))
                                    }
                                }
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "${summary.kcal.toInt()}",
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                if (goalKcal > 0) {
                                    Text(
                                        text = "/ ${goalKcal.toInt()} ${strings.kcalUnit}",
                                        fontSize = 11.sp,
                                        color = Color(0xFFAAAAAA)
                                    )
                                    val kcalPercent = (summary.kcal / goalKcal * 100).toInt()
                                    Text(
                                        text = "$kcalPercent%",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (kcalPercent > 105) Color(0xFFFFB74D) else Color(0xFF81C784)
                                    )
                                } else {
                                    Text(
                                        text = strings.kcalUnit,
                                        fontSize = 12.sp,
                                        color = Color(0xFFAAAAAA)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Macros Row (3 cards with color-coded progress lines and percentages)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MacroItem(
                                name = strings.protein,
                                amount = summary.protein,
                                goal = nutrientGoals.protein,
                                color = Color(0xFFE57373),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (summary.proteinBreakdown.isNotEmpty()) showPopupFor = strings.protein to summary.proteinBreakdown
                            }
                            MacroItem(
                                name = strings.fat,
                                amount = summary.fat,
                                goal = nutrientGoals.fat,
                                color = Color(0xFFFFD54F),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (summary.fatBreakdown.isNotEmpty()) showPopupFor = strings.fat to summary.fatBreakdown
                            }
                            MacroItem(
                                name = strings.carbs,
                                amount = summary.carb,
                                goal = nutrientGoals.carbs,
                                color = Color(0xFF64B5F6),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (summary.carbBreakdown.isNotEmpty()) showPopupFor = strings.carbs to summary.carbBreakdown
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = Color(0xFF2A2A2A), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(10.dp))

                        // Micros Row 1 (4 items)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val waterLiters = summary.water / 1000.0
                            val rawWaterGoal = nutrientGoals.water
                            val waterGoalLiters = if (rawWaterGoal >= 50.0) rawWaterGoal / 1000.0 else rawWaterGoal
                            val waterStr = if (waterGoalLiters >= 0.5) String.format(Locale.US, "%.1f / %.1f L", waterLiters, waterGoalLiters) else String.format(Locale.US, "%.1f L", waterLiters)
                            val waterProgress = if (waterGoalLiters > 0.0) (waterLiters / waterGoalLiters).toFloat() else null
                            MicroItem(
                                icon = strings.water,
                                value = waterStr,
                                progress = waterProgress,
                                progressColor = Color(0xFF4FC3F7),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (summary.waterBreakdown.isNotEmpty()) showPopupFor = strings.water to summary.waterBreakdown
                            }

                            val fiberStr = if (nutrientGoals.fiber > 0) "${summary.fiber.toInt()} / ${nutrientGoals.fiber.toInt()} g" else "${summary.fiber.toInt()} g"
                            val fiberProgress = if (nutrientGoals.fiber > 0) (summary.fiber / nutrientGoals.fiber).toFloat() else null
                            MicroItem(
                                icon = strings.fiber,
                                value = fiberStr,
                                progress = fiberProgress,
                                progressColor = Color(0xFFAED581),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (summary.fiberBreakdown.isNotEmpty()) showPopupFor = strings.fiber to summary.fiberBreakdown
                            }

                            val sugarStr = if (nutrientGoals.sugar > 0) "${summary.sugar.toInt()} / ${nutrientGoals.sugar.toInt()} g" else "${summary.sugar.toInt()} g"
                            val sugarProgress = if (nutrientGoals.sugar > 0) (summary.sugar / nutrientGoals.sugar).toFloat() else null
                            val sugarColor = if (nutrientGoals.sugar > 0 && summary.sugar > nutrientGoals.sugar) Color(0xFFEF5350) else Color(0xFFFFB74D)
                            MicroItem(
                                icon = strings.sugar,
                                value = sugarStr,
                                progress = sugarProgress,
                                progressColor = sugarColor,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (summary.sugarBreakdown.isNotEmpty()) showPopupFor = strings.sugar to summary.sugarBreakdown
                            }

                            val saltStr = if (nutrientGoals.salt > 0) String.format(Locale.US, "%.1f / %.1f g", summary.salt, nutrientGoals.salt) else String.format(Locale.US, "%.1f g", summary.salt)
                            val saltProgress = if (nutrientGoals.salt > 0) (summary.salt / nutrientGoals.salt).toFloat() else null
                            val saltColor = if (nutrientGoals.salt > 0 && summary.salt > nutrientGoals.salt) Color(0xFFEF5350) else Color(0xFF90A4AE)
                            MicroItem(
                                icon = strings.salt,
                                value = saltStr,
                                progress = saltProgress,
                                progressColor = saltColor,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (summary.saltBreakdown.isNotEmpty()) showPopupFor = strings.salt to summary.saltBreakdown
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Micros Row 2 (4 items)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MicroItem(strings.veg, "${summary.vegGrams.toInt()} g", modifier = Modifier.weight(1f)) {
                                if (summary.vegBreakdown.isNotEmpty()) showPopupFor = strings.veg to summary.vegBreakdown
                            }
                            MicroItem(strings.fruit, "${summary.fruitGrams.toInt()} g", modifier = Modifier.weight(1f)) {
                                if (summary.fruitBreakdown.isNotEmpty()) showPopupFor = strings.fruit to summary.fruitBreakdown
                            }
                            val vegFruitTarget = if (nutrientGoals.vegFruit > 0) nutrientGoals.vegFruit else 400.0
                            val totalVegFruit = (summary.vegGrams + summary.fruitGrams).toInt()
                            val vfProgress = (totalVegFruit / vegFruitTarget).toFloat()
                            MicroItem(
                                icon = strings.vegFruitTotal,
                                value = "$totalVegFruit / ${vegFruitTarget.toInt()} g",
                                progress = vfProgress,
                                progressColor = Color(0xFF81C784),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (summary.vegFruitBreakdown.isNotEmpty()) showPopupFor = strings.vegFruitTotal to summary.vegFruitBreakdown
                            }
                            MicroItem(strings.supps, "${summary.supplementCount} ${strings.pcs}", modifier = Modifier.weight(1f)) {
                                if (summary.supplementBreakdown.isNotEmpty()) showPopupFor = strings.supps to summary.supplementBreakdown
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = Color(0xFF2C2C2C), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(10.dp))

                        DailyDiarySpoiler(
                            diaryResponse = state.diaryResponse,
                            selectedEntryIds = selectedEntryIds,
                            dateStr = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                            onToggleSelect = { id ->
                                selectedEntryIds = if (selectedEntryIds.contains(id)) selectedEntryIds - id else selectedEntryIds + id
                            },
                            onAddFoodClick = onAddFoodClick,
                            onScanBarcodeClick = onScanBarcodeClick,
                            onSingleMove = { id ->
                                singleMoveTargetId = id
                                showMoveDialog = true
                            },
                            onSingleClone = { food ->
                                singleCloneTargetFood = food
                                showCloneDialog = true
                            },
                            onSingleDelete = { id ->
                                singleDeleteTargetId = id
                                showDeleteDialog = true
                            },
                            onEditFood = { food, mealTimeId ->
                                editTarget = food to mealTimeId
                                showEditDialog = true
                            },
                            onMoveFoodToMeal = { id, targetMealTimeId ->
                                viewModel.moveEntries(setOf(id), targetMealTimeId, null)
                            },
                            onCloneFoodToMeal = { food, targetMealTimeId ->
                                viewModel.cloneEntries(listOf(food), targetMealTimeId, null) { success ->
                                    if (success) {
                                        Toast.makeText(context, strings.cloneSuccess, Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, strings.cloneError, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )

                        // Bottom spacer to ensure content isn't covered when bottom bar is displayed
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }

            // Fixed Overlap Floating Bottom Bar for Multi-Select
            AnimatedVisibility(
                visible = selectedEntryIds.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .navigationBarsPadding()
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)),
                    border = BorderStroke(1.dp, Color(0xFF383838)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = String.format(strings.selectedCount, selectedEntryIds.size),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            TextButton(
                                onClick = {
                                    selectedEntryIds = if (selectedEntryIds.size == allValidIds.size) emptySet() else allValidIds
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (selectedEntryIds.size == allValidIds.size) strings.deselectAll else String.format(strings.selectAll, allValidIds.size),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalButton(
                                onClick = {
                                    singleMoveTargetId = null
                                    showMoveDialog = true
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFF1E3A5F),
                                    contentColor = Color(0xFF64B5F6)
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = strings.moveAction,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(strings.moveAction, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            FilledTonalButton(
                                onClick = {
                                    singleCloneTargetFood = null
                                    showCloneDialog = true
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFF1E3F2E),
                                    contentColor = Color(0xFF81C784)
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = strings.cloneAction,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(strings.cloneAction, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            FilledTonalButton(
                                onClick = {
                                    singleDeleteTargetId = null
                                    showDeleteDialog = true
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFF4A1A1A),
                                    contentColor = Color(0xFFEF5350)
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = strings.deleteAction,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(strings.deleteAction, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        // Move Dialog
        if (showMoveDialog) {
            val targetIds = singleMoveTargetId?.let { setOf(it) } ?: selectedEntryIds
            if (targetIds.isNotEmpty()) {
                MoveEntriesDialog(
                    count = targetIds.size,
                    currentDate = selectedDate,
                    onDismiss = {
                        showMoveDialog = false
                        singleMoveTargetId = null
                    },
                    onConfirm = { targetTimeId, targetDateStr ->
                        viewModel.moveEntries(targetIds, targetTimeId, targetDateStr) { success ->
                            if (success) {
                                Toast.makeText(context, strings.moveSuccess, Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, strings.moveError, Toast.LENGTH_SHORT).show()
                            }
                        }
                        showMoveDialog = false
                        singleMoveTargetId = null
                        selectedEntryIds = emptySet()
                    }
                )
            }
        }

        // Clone Dialog
        if (showCloneDialog) {
            val targetFoods = singleCloneTargetFood?.let { listOf(it) } ?: allValidFoods.filter { selectedEntryIds.contains(it.id) }
            if (targetFoods.size == 1) {
                val food = targetFoods.first()
                val currentMealId = (uiState as? DashboardUiState.Success)?.diaryResponse?.data?.times?.find { time ->
                    time.foodstuff?.any {
                        (it.id != null && it.id == food.id) ||
                        (it.guid != null && it.guid == food.guid) ||
                        (it.guidRecipe != null && it.guidRecipe == food.guidRecipe) ||
                        (it.guidType != null && it.guidType == food.guidType) ||
                        (it.name != null && it.name.equals(food.name, ignoreCase = true)) ||
                        (it.title != null && it.title.equals(food.title, ignoreCase = true))
                    } == true
                }?.id ?: "1"

                EditFoodDialog(
                    food = food,
                    initialMealTimeId = currentMealId,
                    currentDate = selectedDate,
                    viewModel = viewModel,
                    isClone = true,
                    onDismiss = {
                        showCloneDialog = false
                        singleCloneTargetFood = null
                    },
                    onSuccess = {
                        Toast.makeText(context, strings.cloneSuccess, Toast.LENGTH_SHORT).show()
                        showCloneDialog = false
                        singleCloneTargetFood = null
                        selectedEntryIds = emptySet()
                        showPopupFor = null
                    }
                )
            } else if (targetFoods.size > 1) {
                CloneEntriesDialog(
                    count = targetFoods.size,
                    currentDate = selectedDate,
                    onDismiss = {
                        showCloneDialog = false
                        singleCloneTargetFood = null
                    },
                    onConfirm = { targetTimeId, targetDateStr ->
                        viewModel.cloneEntries(targetFoods, targetTimeId, targetDateStr) { success ->
                            if (success) {
                                Toast.makeText(context, strings.cloneSuccess, Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, strings.cloneError, Toast.LENGTH_SHORT).show()
                            }
                        }
                        showCloneDialog = false
                        singleCloneTargetFood = null
                        selectedEntryIds = emptySet()
                    }
                )
            }
        }

        // Edit Food Dialog
        if (showEditDialog) {
            editTarget?.let { (food, mealTimeId) ->
                EditFoodDialog(
                    food = food,
                    initialMealTimeId = mealTimeId,
                    currentDate = selectedDate,
                    viewModel = viewModel,
                    onDismiss = {
                        showEditDialog = false
                        editTarget = null
                    },
                    onSuccess = {
                        Toast.makeText(context, strings.saveSuccess, Toast.LENGTH_SHORT).show()
                        showEditDialog = false
                        editTarget = null
                    },
                    onDelete = {
                        val entryId = food.id
                        if (!entryId.isNullOrBlank()) {
                            viewModel.deleteEntries(setOf(entryId)) { success ->
                                if (success) {
                                    Toast.makeText(context, strings.deleteSuccess, Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, strings.deleteError, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        showEditDialog = false
                        editTarget = null
                    }
                )
            }
        }

        // Delete Confirmation Dialog
        if (showDeleteDialog) {
            val targetIds = singleDeleteTargetId?.let { setOf(it) } ?: selectedEntryIds
            if (targetIds.isNotEmpty()) {
                AlertDialog(
                    onDismissRequest = {
                        showDeleteDialog = false
                        singleDeleteTargetId = null
                    },
                    title = { Text(strings.deleteConfirmTitle) },
                    text = {
                        Text(
                            if (targetIds.size == 1) String.format(strings.deleteSingleConfirmText, "")
                            else String.format(strings.deleteMultiConfirmText, targetIds.size)
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.deleteEntries(targetIds) { success ->
                                    if (success) {
                                        Toast.makeText(context, strings.deleteSuccess, Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, strings.deleteError, Toast.LENGTH_SHORT).show()
                                    }
                                }
                                showDeleteDialog = false
                                singleDeleteTargetId = null
                                selectedEntryIds = emptySet()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))
                        ) {
                            Text(strings.deleteAction, color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showDeleteDialog = false
                            singleDeleteTargetId = null
                        }) {
                            Text(strings.cancel)
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacroItem(
    name: String,
    amount: Double,
    goal: Double,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val pct = if (goal > 0) (amount / goal * 100).toInt() else null
    val progress = if (goal > 0) (amount / goal).toFloat().coerceIn(0f, 1f) else 0f

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = name,
                    color = color,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (pct != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$pct%",
                        color = if (pct > 105) Color(0xFFFFB74D) else Color(0xFFB0BEC5),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = if (goal > 0) "${amount.toInt()} / ${goal.toInt()} g" else "${amount.toInt()} g",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(5.dp))

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF2E2E2E))
            ) {
                if (progress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(color)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MicroItem(
    icon: String,
    value: String,
    progress: Float? = null,
    progressColor: Color = Color(0xFF81C784),
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 7.dp, horizontal = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(icon, fontSize = 11.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontWeight = FontWeight.Bold,
                fontSize = 11.5.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (progress != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(Color(0xFF2E2E2E))
                ) {
                    if (progress > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .height(3.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(progressColor)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecursiveBreakdownItem(
    item: FoodBreakdown,
    title: String,
    grandTotal: Double,
    depth: Int = 0,
    onAddFood: ((FoodBreakdown) -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val pct = if (grandTotal > 0) (item.amount / grandTotal) * 100 else 0.0
    val isSupp = title.contains("Supplements") || title.contains("Supps")
    val unitStr = if (title == "Calories") "kcal" else if (title == "💧 Water") "ml" else if (isSupp) "pcs" else "g"
    val hasChildren = item.components.isNotEmpty()

    Column(modifier = Modifier.fillMaxWidth().padding(start = (depth * 16).dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .then(if (hasChildren) Modifier.clickable { expanded = !expanded } else Modifier),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f).padding(end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    val amountStr = if (isSupp) "${item.amount.toInt()} $unitStr" else "${String.format(Locale.US, "%.1f", item.amount)} $unitStr"
                    Text(amountStr, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("${String.format(Locale.US, "%.1f", pct)}%", fontSize = 12.sp, color = Color.Gray)
                }
                if (onAddFood != null) {
                    IconButton(
                        onClick = { onAddFood(item) },
                        modifier = Modifier
                            .padding(start = 2.dp)
                            .size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
        if (expanded && hasChildren) {
            item.components.forEach { child ->
                RecursiveBreakdownItem(child, title, grandTotal, depth + 1, onAddFood)
            }
        }
    }
}

@Composable
fun ShareOptionsDialog(
    dateDisplay: String,
    onDismiss: () -> Unit,
    onOptionSelected: (ShareFormatType, Boolean) -> Unit
) {
    val strings = LocalAppStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(strings.shareDayTitle, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(dateDisplay, fontSize = 13.sp, color = Color.Gray)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ShareFormatType.entries) { format ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOptionSelected(format, false) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = format.icon,
                                fontSize = 22.sp,
                                modifier = Modifier.padding(end = 10.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = format.title,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = format.subtitle,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 15.sp
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onOptionSelected(format, true) },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Text("📋", fontSize = 15.sp)
                                }
                                IconButton(
                                    onClick = { onOptionSelected(format, false) },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Share,
                                        contentDescription = "Share",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.close)
            }
        }
    )
}

data class DraggedFoodState(
    val food: Foodstuff,
    val sourceMealTimeId: String,
    val rootPointer: Offset
)

enum class DropAction { MOVE, CLONE }

@Composable
fun DailyDiarySpoiler(
    diaryResponse: DiaryResponse?,
    selectedEntryIds: Set<String>,
    dateStr: String = "",
    onToggleSelect: (String) -> Unit,
    onAddFoodClick: (mealTimeGuid: String?) -> Unit,
    onScanBarcodeClick: (mealTimeGuid: String?) -> Unit = {},
    onSingleMove: (String) -> Unit,
    onSingleClone: (Foodstuff) -> Unit,
    onSingleDelete: (String) -> Unit,
    onEditFood: (food: Foodstuff, mealTimeId: String) -> Unit,
    onMoveFoodToMeal: (foodId: String, targetMealTimeId: String) -> Unit,
    onCloneFoodToMeal: (food: Foodstuff, targetMealTimeId: String) -> Unit
) {
    val strings = LocalAppStrings.current
    var isDiaryExpanded by rememberSaveable(dateStr) { mutableStateOf(true) }
    var draggedFoodState by remember { mutableStateOf<DraggedFoodState?>(null) }
    val mealBounds = remember { mutableStateMapOf<String, Rect>() }
    var spoilerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val haptic = LocalHapticFeedback.current

    val mealTimes = diaryResponse?.data?.times ?: emptyList()
    val allFoods = remember(diaryResponse) {
        mealTimes.flatMap { it.foodstuff ?: emptyList() }
    }
    val totalKcal = remember(allFoods) {
        allFoods.sumOf { NutrientUtils.parseNutrient(it.energy) }
    }

    var prevTotalFoodCount by remember(dateStr) { mutableIntStateOf(allFoods.size) }
    LaunchedEffect(allFoods.size) {
        if (allFoods.size > prevTotalFoodCount) {
            isDiaryExpanded = true
        }
        prevTotalFoodCount = allFoods.size
    }

    val currentHoverInfo = remember(draggedFoodState?.rootPointer, mealBounds.toMap()) {
        val pointer = draggedFoodState?.rootPointer ?: return@remember null
        val entry = mealBounds.entries.find { (_, rect) -> rect.contains(pointer) } ?: return@remember null
        val mealId = entry.key
        val rect = entry.value
        val action = if (pointer.x < rect.center.x) DropAction.MOVE else DropAction.CLONE
        mealId to action
    }
    val hoveredMealId = currentHoverInfo?.first
    val currentDropAction = currentHoverInfo?.second

    LaunchedEffect(hoveredMealId, currentDropAction) {
        if (hoveredMealId != null) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { spoilerCoordinates = it }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
            border = BorderStroke(1.dp, Color(0xFF2C2C2C))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                // Spoiler Header Panel
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isDiaryExpanded = !isDiaryExpanded }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🍽️", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = strings.mealsHeader,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF2A2A2A)
                            ) {
                                Text(
                                    text = "${allFoods.size}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (allFoods.isNotEmpty()) {
                            Text(
                                text = String.format(strings.totalKcalFormat, totalKcal.toInt()),
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    // Barcode Scanner button
                    IconButton(
                        onClick = { onScanBarcodeClick(null) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = strings.scanBarcode,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    // Add button on the spoiler header (compact icon button)
                    IconButton(
                        onClick = { onAddFoodClick(null) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = strings.add,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    IconButton(
                        onClick = { isDiaryExpanded = !isDiaryExpanded },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (isDiaryExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color.LightGray
                        )
                    }
                }

                // Expanded content
                AnimatedVisibility(visible = isDiaryExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    ) {
                        if (mealTimes.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = strings.noDiaryEntries,
                                    color = Color.Gray,
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            fun handleDrop(source: String) {
                                val currentDrag = draggedFoodState
                                Log.d("NutriusDrag", "handleDrop called by $source: currentDrag=${currentDrag?.food?.title}")
                                DebugLogger.addActionLog("handleDrop ($source): food=${currentDrag?.food?.title}")

                                if (currentDrag != null) {
                                    val pointer = currentDrag.rootPointer
                                    val entry = mealBounds.entries.find { (_, rect) -> rect.contains(pointer) }
                                    val targetMeal = entry?.key ?: hoveredMealId
                                    val rect = entry?.value ?: mealBounds[targetMeal]
                                    val action = if (rect != null && pointer.x >= rect.center.x) DropAction.CLONE else (currentDropAction ?: DropAction.MOVE)

                                    Log.d("NutriusDrag", "handleDrop: targetMeal=$targetMeal, action=$action, source=${currentDrag.sourceMealTimeId}, pointer=$pointer")
                                    DebugLogger.addActionLog("Drop target: $targetMeal ($action), source: ${currentDrag.sourceMealTimeId}")

                                    if (!targetMeal.isNullOrBlank()) {
                                        if (action == DropAction.CLONE) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            Log.d("NutriusDrag", "Executing onCloneFoodToMeal(food=${currentDrag.food.title}, targetMealTimeId=$targetMeal)")
                                            DebugLogger.addActionLog("Cloning ${currentDrag.food.title} to meal $targetMeal via Drag & Drop")
                                            onCloneFoodToMeal(currentDrag.food, targetMeal)
                                        } else if (targetMeal != currentDrag.sourceMealTimeId) {
                                            val foodId = currentDrag.food.id
                                            if (!foodId.isNullOrBlank()) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                Log.d("NutriusDrag", "Executing onMoveFoodToMeal(foodId=$foodId, targetMealTimeId=$targetMeal)")
                                                DebugLogger.addActionLog("Moving ${currentDrag.food.title} to meal $targetMeal via Drag & Drop")
                                                onMoveFoodToMeal(foodId, targetMeal)
                                            } else {
                                                Log.w("NutriusDrag", "foodId is null or blank for ${currentDrag.food}")
                                                DebugLogger.addActionLog("Error: foodId is null or blank")
                                            }
                                        } else {
                                            Log.d("NutriusDrag", "Drop ignored or cancelled (targetMeal=$targetMeal, sourceMeal=${currentDrag.sourceMealTimeId})")
                                            DebugLogger.addActionLog("Drop ignored: target=$targetMeal == source=${currentDrag.sourceMealTimeId}")
                                        }
                                    }
                                }
                                draggedFoodState = null
                            }

                            mealTimes.forEachIndexed { index, mealTime ->
                                val mealTimeId = mealTime.id ?: (index + 1).toString()
                                val isDropTarget = hoveredMealId == mealTimeId
                                val dropAction = if (isDropTarget) currentDropAction else null

                                MealTimeGroup(
                                    mealTime = mealTime,
                                    selectedEntryIds = selectedEntryIds,
                                    dateStr = dateStr,
                                    onToggleSelect = onToggleSelect,
                                    onAddFoodClick = {
                                        onAddFoodClick(mealTime.id)
                                    },
                                    onScanBarcodeClick = {
                                        onScanBarcodeClick(mealTime.id)
                                    },
                                    onSingleMove = onSingleMove,
                                    onSingleClone = onSingleClone,
                                    onSingleDelete = onSingleDelete,
                                    onEditFood = { food ->
                                        onEditFood(food, mealTimeId)
                                    },
                                    isDropTarget = isDropTarget,
                                    dropAction = dropAction,
                                    draggedSourceMealId = draggedFoodState?.sourceMealTimeId,
                                    onPositioned = { rect ->
                                        mealBounds[mealTimeId] = rect
                                    },
                                    onStartDrag = { food, rootPointer ->
                                        Log.d("NutriusDrag", "onStartDrag: food=${food.title} (${food.id}), sourceMeal=$mealTimeId, pointer=$rootPointer")
                                        DebugLogger.addActionLog("Drag start: ${food.title}")
                                        draggedFoodState = DraggedFoodState(
                                            food = food,
                                            sourceMealTimeId = mealTimeId,
                                            rootPointer = rootPointer
                                        )
                                    },
                                    onDrag = { dragAmount ->
                                        draggedFoodState?.let { current ->
                                            draggedFoodState = current.copy(rootPointer = current.rootPointer + dragAmount)
                                        }
                                    },
                                    onDragEnd = { handleDrop("onDragEnd") },
                                    onDragCancel = { handleDrop("onDragCancel") },
                                    draggedFoodId = draggedFoodState?.food?.id
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }

        // Floating Ghost Card Overlay
        draggedFoodState?.let { dragInfo ->
            val spoilerRoot = spoilerCoordinates?.localToRoot(Offset.Zero) ?: Offset.Zero
            val localOffset = dragInfo.rootPointer - spoilerRoot
            val title = dragInfo.food.title ?: dragInfo.food.name ?: dragInfo.food.nazev ?: strings.unnamedFood
            val kcal = NutrientUtils.parseNutrient(dragInfo.food.energy).toInt()
            val isCloneAction = currentDropAction == DropAction.CLONE
            val isTargetValid = when {
                hoveredMealId == null -> false
                isCloneAction -> true
                hoveredMealId != dragInfo.sourceMealTimeId -> true
                else -> false
            }
            val accentColor = if (isCloneAction) Color(0xFF81C784) else Color(0xFF64B5F6)

            Box(
                modifier = Modifier
                    .zIndex(100f)
                    .graphicsLayer {
                        translationX = localOffset.x - 130.dp.toPx()
                        translationY = localOffset.y - 35.dp.toPx()
                        rotationZ = 3f
                        scaleX = 1.05f
                        scaleY = 1.05f
                        shadowElevation = 20.dp.toPx()
                    }
                    .width(260.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1E2638))
                    .border(
                        BorderStroke(
                            width = 1.5.dp,
                            color = if (isTargetValid) accentColor else Color(0xFF9E9E9E)
                        ),
                        RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isCloneAction) "📋" else "✨", fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "$kcal ${strings.kcalUnit}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFFD54F)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                !isTargetValid -> strings.dragToMealHint
                                isCloneAction -> strings.releaseToClone
                                else -> strings.releaseToMove
                            },
                            fontSize = 11.sp,
                            color = if (isTargetValid) accentColor else Color.LightGray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MealTimeGroup(
    mealTime: MealTime,
    selectedEntryIds: Set<String>,
    dateStr: String = "",
    onToggleSelect: (String) -> Unit,
    onAddFoodClick: () -> Unit,
    onScanBarcodeClick: () -> Unit = {},
    onSingleMove: (String) -> Unit,
    onSingleClone: (Foodstuff) -> Unit,
    onSingleDelete: (String) -> Unit,
    onEditFood: (Foodstuff) -> Unit,
    isDropTarget: Boolean,
    dropAction: DropAction?,
    draggedSourceMealId: String?,
    onPositioned: (Rect) -> Unit,
    onStartDrag: (food: Foodstuff, rootPointer: Offset) -> Unit,
    onDrag: (dragAmount: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    draggedFoodId: String?
) {
    val foods = mealTime.foodstuff ?: emptyList()
    var isExpanded by rememberSaveable(mealTime.id, dateStr) { mutableStateOf(foods.isNotEmpty()) }
    var prevFoodCount by remember(mealTime.id, dateStr) { mutableIntStateOf(foods.size) }

    LaunchedEffect(foods.size) {
        if (foods.size > prevFoodCount) {
            isExpanded = true
        } else if (foods.isEmpty() && prevFoodCount > 0) {
            isExpanded = false
        }
        prevFoodCount = foods.size
    }
    val strings = LocalAppStrings.current
    val mealKcal = remember(foods) {
        foods.sumOf { NutrientUtils.parseNutrient(it.energy) }
    }
    val mealDisplayName = remember(mealTime, strings) {
        NutrientUtils.getMealTimeDisplayName(mealTime, strings)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { onPositioned(it.boundsInRoot()) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDropTarget) Color(0xFF132238) else Color(0xFF141414)
        ),
        border = BorderStroke(
            width = if (isDropTarget) 2.dp else 1.dp,
            color = if (isDropTarget) (if (dropAction == DropAction.CLONE) Color(0xFF81C784) else Color(0xFF64B5F6)) else Color(0xFF222222)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Meal Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = mealDisplayName,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(${foods.size})",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    if (foods.isNotEmpty()) {
                        Text(
                            text = "${mealKcal.toInt()} ${strings.kcalUnit}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Per-meal Scanner Button
                IconButton(
                    onClick = onScanBarcodeClick,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "${strings.scanBarcode} $mealDisplayName",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(17.dp)
                    )
                }

                Spacer(modifier = Modifier.width(2.dp))

                // Per-meal Add Button
                IconButton(
                    onClick = onAddFoodClick,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "${strings.add} $mealDisplayName",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(2.dp))

                // Chevron
                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Dual drop indicators when an item is hovered over this meal
            AnimatedVisibility(visible = isDropTarget) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val isMoveHovered = dropAction == DropAction.MOVE
                    val isCloneHovered = dropAction == DropAction.CLONE
                    val canMoveHere = mealTime.id != draggedSourceMealId

                    // Left target: Move
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isMoveHovered && canMoveHere) Color(0xFF1565C0).copy(alpha = 0.5f)
                                else Color(0xFF1565C0).copy(alpha = 0.15f)
                            )
                            .border(
                                BorderStroke(
                                    if (isMoveHovered && canMoveHere) 1.5.dp else 0.5.dp,
                                    if (isMoveHovered && canMoveHere) Color(0xFF64B5F6) else Color(0xFF1565C0)
                                ),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📥", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (canMoveHere) strings.moveAction else strings.sameMealHint,
                                color = if (canMoveHere) Color(0xFF90CAF9) else Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = if (isMoveHovered && canMoveHere) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    // Right target: Clone
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isCloneHovered) Color(0xFF2E7D32).copy(alpha = 0.5f)
                                else Color(0xFF2E7D32).copy(alpha = 0.15f)
                            )
                            .border(
                                BorderStroke(
                                    if (isCloneHovered) 1.5.dp else 0.5.dp,
                                    if (isCloneHovered) Color(0xFF81C784) else Color(0xFF2E7D32)
                                ),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📋", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = strings.cloneAction,
                                color = Color(0xFFA5D6A7),
                                fontSize = 11.sp,
                                fontWeight = if (isCloneHovered) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // Foods List inside Meal
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    if (foods.isEmpty()) {
                        Text(
                            text = strings.noEntriesInMeal,
                            color = Color.DarkGray,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    } else {
                        foods.forEachIndexed { index, food ->
                            val foodId = food.id ?: ""
                            val isSelected = selectedEntryIds.contains(foodId)
                            val isBeingDragged = draggedFoodId == foodId

                            FoodEntryRow(
                                food = food,
                                isSelected = isSelected,
                                isBeingDragged = isBeingDragged,
                                onToggleSelect = {
                                    if (foodId.isNotBlank()) onToggleSelect(foodId)
                                },
                                onEdit = {
                                    onEditFood(food)
                                },
                                onMove = {
                                    if (foodId.isNotBlank()) onSingleMove(foodId)
                                },
                                onClone = {
                                    onSingleClone(food)
                                },
                                onDelete = {
                                    if (foodId.isNotBlank()) onSingleDelete(foodId)
                                },
                                onStartDrag = { rootPointer ->
                                    onStartDrag(food, rootPointer)
                                },
                                onDrag = onDrag,
                                onDragEnd = onDragEnd,
                                onDragCancel = onDragCancel
                            )

                            if (index < foods.lastIndex) {
                                HorizontalDivider(
                                    color = Color(0xFF1E1E1E),
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FoodEntryRow(
    food: Foodstuff,
    isSelected: Boolean,
    isBeingDragged: Boolean,
    onToggleSelect: () -> Unit,
    onEdit: () -> Unit,
    onMove: () -> Unit,
    onClone: () -> Unit,
    onDelete: () -> Unit,
    onStartDrag: (rootPointer: Offset) -> Unit,
    onDrag: (dragAmount: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    val strings = LocalAppStrings.current
    val title = food.title ?: food.name ?: food.nazev ?: strings.unnamedFood
    val energyKcal = NutrientUtils.parseNutrient(food.energy).toInt()
    val protein = NutrientUtils.parseNutrient(food.protein).toInt()
    val fat = NutrientUtils.parseNutrient(food.fat).toInt()
    val carbs = NutrientUtils.parseNutrient(food.carbohydrate).toInt()

    var handleCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val haptic = LocalHapticFeedback.current

    val currentOnStartDrag by rememberUpdatedState(onStartDrag)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)
    val currentOnMove by rememberUpdatedState(onMove)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = if (isBeingDragged) 0.25f else 1f
            }
            .padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Selection Checkbox
        Box(
            modifier = Modifier
                .size(32.dp)
                .clickable { onToggleSelect() },
            contentAlignment = Alignment.Center
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    checkmarkColor = Color.Black,
                    uncheckedColor = Color.Gray
                ),
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Food Title & Macros -> Clickable to Edit, with dashed underline on title
        val accentDotted = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
        var textLayoutResult by remember(title) { mutableStateOf<TextLayoutResult?>(null) }
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .clickable { onEdit() }
                .padding(vertical = 4.dp, horizontal = 4.dp)
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { textLayoutResult = it },
                modifier = Modifier.drawBehind {
                    val strokeWidth = 1.dp.toPx()
                    val pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 3f), 0f)
                    val layout = textLayoutResult
                    if (layout != null) {
                        for (i in 0 until layout.lineCount) {
                            val startX = layout.getLineLeft(i)
                            val endX = layout.getLineRight(i)
                            if (endX > startX) {
                                val y = layout.getLineBottom(i) - strokeWidth / 2
                                drawLine(
                                    color = accentDotted,
                                    start = Offset(startX, y),
                                    end = Offset(endX, y),
                                    strokeWidth = strokeWidth,
                                    pathEffect = pathEffect
                                )
                            }
                        }
                    } else {
                        val y = size.height - strokeWidth / 2
                        drawLine(
                            color = accentDotted,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = strokeWidth,
                            pathEffect = pathEffect
                        )
                    }
                }
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 3.dp)
            ) {
                if (!food.unit.isNullOrBlank()) {
                    Text(
                        text = food.unit,
                        fontSize = 11.sp,
                        color = Color(0xFF9E9E9E)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = "$energyKcal ${strings.kcalUnit}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFFD54F)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${strings.proteinShort}:$protein ${strings.fatShort}:$fat ${strings.carbsShort}:$carbs",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }
        }

        // Action Buttons: Drag handle (sandwich), Clone button, Delete button
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Drag Handle / Move Button
            val barColor = if (isBeingDragged) MaterialTheme.colorScheme.primary else Color(0xFF888888)
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .onGloballyPositioned { handleCoordinates = it }
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isBeingDragged) Color(0xFF1E3A5F) else Color.Transparent)
                    .pointerInput(food.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { localOffset ->
                                val rootStart = handleCoordinates?.localToRoot(localOffset) ?: Offset.Zero
                                Log.d("NutriusDrag", "FoodEntryRow onDragStart: food=${food.title}, rootStart=$rootStart")
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentOnStartDrag(rootStart)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                currentOnDrag(dragAmount)
                            },
                            onDragEnd = {
                                Log.d("NutriusDrag", "FoodEntryRow onDragEnd for food ${food.title}")
                                currentOnDragEnd()
                            },
                            onDragCancel = {
                                Log.d("NutriusDrag", "FoodEntryRow onDragCancel for food ${food.title}")
                                currentOnDragCancel()
                            }
                        )
                    }
                    .clickable { currentOnMove() },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(16.dp, 12.dp)) {
                    val barHeight = 2.dp.toPx()
                    val barSpacing = 3.dp.toPx()
                    val corner = CornerRadius(barHeight / 2f, barHeight / 2f)
                    for (i in 0..2) {
                        val y = i * (barHeight + barSpacing)
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(0f, y),
                            size = Size(size.width, barHeight),
                            cornerRadius = corner
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(2.dp))

            // Quick Clone Button
            IconButton(
                onClick = onClone,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = strings.cloneAction,
                    tint = Color(0xFF888888),
                    modifier = Modifier.size(15.dp)
                )
            }

            Spacer(modifier = Modifier.width(2.dp))

            // Delete Button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = strings.deleteAction,
                    tint = Color(0xFF888888),
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveEntriesDialog(
    count: Int,
    currentDate: ZonedDateTime,
    onDismiss: () -> Unit,
    onConfirm: (targetTimeId: String, targetDateStr: String?) -> Unit
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    var selectedMealTimeId by remember { mutableStateOf("1") }
    var mealExpanded by remember { mutableStateOf(false) }
    val mealOptions = remember(strings) { NutrientUtils.getStandardMealOptions(strings) }
    var targetDate by remember { mutableStateOf(currentDate.toLocalDate()) }
    val formatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (count == 1) strings.moveTitle else String.format(strings.moveMultiTitle, count),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(
                    text = strings.whereToMove,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Date selection chips
                Text(
                    text = targetDate.format(formatter),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = targetDate == currentDate.toLocalDate(),
                        onClick = { targetDate = currentDate.toLocalDate() },
                        label = { Text(strings.dateThisDay, fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = targetDate == LocalDate.now(),
                        onClick = { targetDate = LocalDate.now() },
                        label = { Text(strings.dateToday, fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = targetDate == LocalDate.now().minusDays(1),
                        onClick = { targetDate = LocalDate.now().minusDays(1) },
                        label = { Text(strings.dateYesterday, fontSize = 11.sp) }
                    )
                }

                TextButton(
                    onClick = {
                        val dialog = DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                targetDate = LocalDate.of(year, month + 1, dayOfMonth)
                            },
                            targetDate.year,
                            targetDate.monthValue - 1,
                            targetDate.dayOfMonth
                        )
                        dialog.show()
                    },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(strings.chooseAnotherDate, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFF2C2C2C))
                Spacer(modifier = Modifier.height(12.dp))

                // Meal Time Combobox
                ExposedDropdownMenuBox(
                    expanded = mealExpanded,
                    onExpandedChange = { mealExpanded = !mealExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = mealOptions.find { it.first == selectedMealTimeId }?.second ?: strings.chooseMeal,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(strings.mealTimeLabel, fontSize = 12.sp) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = mealExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color(0xFF444444)
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = mealExpanded,
                        onDismissRequest = { mealExpanded = false }
                    ) {
                        mealOptions.forEach { (id, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    selectedMealTimeId = id
                                    mealExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val dateStr = targetDate.format(formatter)
                    onConfirm(selectedMealTimeId, dateStr)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.Black
                )
            ) {
                Text("${strings.moveAction} ($count)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}

