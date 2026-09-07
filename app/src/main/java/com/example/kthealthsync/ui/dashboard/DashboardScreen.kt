package com.example.kthealthsync.ui.dashboard

import android.app.DatePickerDialog
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.example.kthealthsync.domain.model.DailySummary
import com.example.kthealthsync.domain.model.FoodBreakdown
import com.example.kthealthsync.domain.model.ShareFormatType
import com.example.kthealthsync.domain.util.NutrientUtils
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onSettingsClick: () -> Unit,
    onDebugClick: () -> Unit,
    onAddFoodClick: () -> Unit
) {
    val context = LocalContext.current
    val selectedDate by viewModel.selectedDate.collectAsState()
    val withMeals by viewModel.withMeals.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val nutrientGoals by viewModel.nutrientGoals.collectAsState()

    val now = ZonedDateTime.now()
    val isToday = selectedDate.toLocalDate() == now.toLocalDate()
    val isYesterday = selectedDate.toLocalDate() == now.minusDays(1).toLocalDate()

    val dateDisplay = when {
        isToday -> "Today"
        isYesterday -> "Yesterday"
        else -> selectedDate.format(DateTimeFormatter.ofPattern("EEE, MMM dd", Locale.getDefault()))
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
                            Toast.makeText(context, "Нет данных за выбранный день", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Поделиться данными дня")
                    }
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
            FloatingActionButton(
                onClick = onAddFoodClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Food/Water")
            }
        }
    ) { padding ->
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
                            Toast.makeText(context, "Скопировано в буфер обмена!", Toast.LENGTH_SHORT).show()
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

        Column(
            modifier = Modifier
                .padding(padding)
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Text("With meals")
                Spacer(modifier = Modifier.width(8.dp))
                Switch(checked = withMeals, onCheckedChange = { viewModel.toggleWithMeals(it) })
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
                                Text("Retry")
                            }
                        }
                    }
                }
                is DashboardUiState.Success -> {
                    val summary = state.summary
                    val totalMacros = summary.protein + summary.fat + summary.carb
                    val protSweep = if (totalMacros > 0) ((summary.protein / totalMacros) * 360).toFloat() else 0f
                    val fatSweep = if (totalMacros > 0) ((summary.fat / totalMacros) * 360).toFloat() else 0f
                    val carbSweep = if (totalMacros > 0) ((summary.carb / totalMacros) * 360).toFloat() else 0f

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(200.dp)
                            .clickable {
                                if (summary.kcalBreakdown.isNotEmpty()) {
                                    showPopupFor = "Calories" to summary.kcalBreakdown
                                }
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 30.dp.toPx()
                            if (totalMacros == 0.0) {
                                drawArc(Color.DarkGray, 0f, 360f, false, style = Stroke(strokeWidth))
                            } else {
                                drawArc(Color(0xFFE57373), -90f, protSweep, false, style = Stroke(strokeWidth, cap = StrokeCap.Round))
                                drawArc(Color(0xFFFFD54F), -90f + protSweep, fatSweep, false, style = Stroke(strokeWidth, cap = StrokeCap.Round))
                                drawArc(Color(0xFF64B5F6), -90f + protSweep + fatSweep, carbSweep, false, style = Stroke(strokeWidth, cap = StrokeCap.Round))
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${summary.kcal.toInt()}", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("kcal", fontSize = 14.sp, color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Macros Row
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        MacroItem("Protein", summary.protein, Color(0xFFE57373)) {
                            if (summary.proteinBreakdown.isNotEmpty()) showPopupFor = "Protein" to summary.proteinBreakdown
                        }
                        MacroItem("Fat", summary.fat, Color(0xFFFFD54F)) {
                            if (summary.fatBreakdown.isNotEmpty()) showPopupFor = "Fat" to summary.fatBreakdown
                        }
                        MacroItem("Carbs", summary.carb, Color(0xFF64B5F6)) {
                            if (summary.carbBreakdown.isNotEmpty()) showPopupFor = "Carbohydrates" to summary.carbBreakdown
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                    HorizontalDivider(color = Color.DarkGray)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Micros Row 1
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        val waterLiters = summary.water / 1000.0
                        MicroItem("💧 Water", String.format(Locale.US, "%.1f L", waterLiters)) {
                            if (summary.waterBreakdown.isNotEmpty()) showPopupFor = "💧 Water" to summary.waterBreakdown
                        }
                        MicroItem("🌾 Fiber", "${summary.fiber.toInt()} g") {
                            if (summary.fiberBreakdown.isNotEmpty()) showPopupFor = "🌾 Fiber" to summary.fiberBreakdown
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Micros Row 2
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        MicroItem("🍬 Sugar", "${summary.sugar.toInt()} g") {
                            if (summary.sugarBreakdown.isNotEmpty()) showPopupFor = "🍬 Sugar" to summary.sugarBreakdown
                        }
                        MicroItem("🧂 Salt", String.format(Locale.US, "%.1f g", summary.salt)) {
                            if (summary.saltBreakdown.isNotEmpty()) showPopupFor = "🧂 Salt" to summary.saltBreakdown
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Micros Row 3: Vegetables & Fruits
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        MicroItem("🥦 Veg", "${summary.vegGrams.toInt()} g", modifier = Modifier.width(96.dp)) {
                            if (summary.vegBreakdown.isNotEmpty()) showPopupFor = "🥦 Vegetables" to summary.vegBreakdown
                        }
                        MicroItem("🍎 Fruit", "${summary.fruitGrams.toInt()} g", modifier = Modifier.width(96.dp)) {
                            if (summary.fruitBreakdown.isNotEmpty()) showPopupFor = "🍎 Fruits" to summary.fruitBreakdown
                        }
                        val totalVegFruit = (summary.vegGrams + summary.fruitGrams).toInt()
                        MicroItem("🥗 Total", "$totalVegFruit/400g", modifier = Modifier.width(108.dp)) {
                            if (summary.vegFruitBreakdown.isNotEmpty()) showPopupFor = "🥗 Vegetables & Fruits" to summary.vegFruitBreakdown
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MacroItem(name: String, amount: Double, color: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Text(name, color = color, fontWeight = FontWeight.Bold)
        Text("${amount.toInt()} g", color = Color.White)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MicroItem(icon: String, value: String, modifier: Modifier = Modifier.width(100.dp), onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(icon, fontSize = 15.sp, color = Color.White, maxLines = 1)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White, maxLines = 1)
        }
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
                Text("${String.format(Locale.US, "%.1f", item.amount)} $unitStr", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("${String.format(Locale.US, "%.1f", pct)}%", fontSize = 12.sp, color = Color.Gray)
            }
        }
        if (expanded && hasChildren) {
            item.components.forEach { child ->
                RecursiveBreakdownItem(child, title, grandTotal, depth + 1)
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Поделиться данными дня", fontWeight = FontWeight.Bold, fontSize = 20.sp)
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
                Text("Закрыть")
            }
        }
    )
}

