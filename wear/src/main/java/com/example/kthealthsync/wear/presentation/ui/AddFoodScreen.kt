package com.example.kthealthsync.wear.presentation.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.example.kthealthsync.wear.data.WatchDataManager
import com.example.kthealthsync.wear.data.WatchMealTimeUtils
import com.example.kthealthsync.wear.data.WatchNutrientParser
import com.example.kthealthsync.wear.data.WearAddFoodRequest
import com.example.kthealthsync.wear.data.WearFoodItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal fun calculateFineStep(currentGrams: Int): Int {
    return when {
        currentGrams < 10 -> 1
        currentGrams < 50 -> 5
        else -> 10
    }
}

internal fun getGramPresets(selected: Int): List<Int> {
    val set = sortedSetOf<Int>()
    // Below 10 by 1 down to 1
    for (i in 1..10) set.add(i)
    // 10 to 50 by 10
    for (i in 10..50 step 10) set.add(i)
    // 50 to 500 by 50
    for (i in 50..500 step 50) set.add(i)
    // 500 to 2000 by 100
    for (i in 500..2000 step 100) set.add(i)
    // Offsets around selected value
    if (selected in 1..2000) set.add(selected)
    for (k in 1..3) {
        if (selected - k * 50 in 1..2000) set.add(selected - k * 50)
        if (selected + k * 50 in 1..2000) set.add(selected + k * 50)
    }
    return set.toList()
}

@Composable
private fun RepeatingButton(
    icon: ImageVector,
    contentDescription: String,
    onSingleClick: () -> Unit,
    onRepeatStep: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (isPressed) Color(0xFF3C3C46) else Color(0xFF24242A))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        var isHeld = false
                        val repeatJob = coroutineScope.launch {
                            delay(400) // Initial hold threshold
                            isHeld = true
                            while (isActive) {
                                onRepeatStep()
                                delay(1000) // 50 every 1 second while held
                            }
                        }
                        val released = tryAwaitRelease()
                        repeatJob.cancel()
                        isPressed = false
                        if (released && !isHeld) {
                            onSingleClick()
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun AddFoodScreen(
    food: WearFoodItem,
    dataManager: WatchDataManager,
    onSuccess: () -> Unit
) {
    val listState = rememberScalingLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    val parsedDefault = remember(food.unit) {
        val p = WatchNutrientParser.parsePortionGrams(food.unit).roundToInt()
        if (p in 1..2000) p else null
    }

    var grams by remember(food.id) { mutableIntStateOf(parsedDefault ?: 100) }
    var userHasModifiedGrams by remember { mutableStateOf(false) }
    var stepMode by remember { mutableIntStateOf(10) }
    var isDrumFocused by remember { mutableStateOf(false) }
    var accumulatedRotaryForGrams by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            isDrumFocused = false
        }
    }

    LaunchedEffect(food.id) {
        val apiGrams = dataManager.getFoodDefaultGrams(food.id)
        if (apiGrams != null && apiGrams in 1..2000 && !userHasModifiedGrams) {
            grams = apiGrams
        }
    }

    val unitLabel = remember(food.unit) {
        if (food.unit?.contains(Regex("ml|мл| l| л", RegexOption.IGNORE_CASE)) == true) "мл" else "г"
    }

    var selectedSlotIndex by remember {
        val initialSlot = WatchMealTimeUtils.getCurrentMealSlot()
        val index = WatchMealTimeUtils.MEAL_SLOTS.indexOfFirst { it.id == initialSlot.id }.coerceAtLeast(0)
        mutableIntStateOf(index)
    }

    var isSubmitting by remember { mutableStateOf(false) }
    var submitResult by remember { mutableStateOf<Pair<Boolean, String?>?>(null) }

    val baseKcalPer100g = remember(food.energy) {
        food.energy?.replace(Regex("[^0-9.]"), "")?.toDoubleOrNull()
    }

    LaunchedEffect(Unit) {
        delay(150)
        try {
            focusRequester.requestFocus()
        } catch (e: Exception) {
            Log.w("AddFoodScreen", "Initial focus failed: ${e.message}")
        }
    }

    LaunchedEffect(isDrumFocused) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        if (submitResult != null) {
            val (success, message) = submitResult!!
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = if (success) Icons.Default.Check else Icons.Default.Remove,
                        contentDescription = "Status",
                        tint = if (success) MaterialTheme.colors.primary else Color.Red,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = message ?: if (success) "Добавлено!" else "Ошибка",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester)
                    .focusable()
                    .onRotaryScrollEvent { event ->
                        Log.d("AddFoodScreen", "Rotary event: delta=${event.verticalScrollPixels}, drumFocused=$isDrumFocused")
                        if (isDrumFocused) {
                            userHasModifiedGrams = true
                            accumulatedRotaryForGrams += event.verticalScrollPixels
                            val threshold = 18f
                            while (accumulatedRotaryForGrams >= threshold) {
                                grams = (grams + stepMode).coerceAtMost(2000)
                                accumulatedRotaryForGrams -= threshold
                            }
                            while (accumulatedRotaryForGrams <= -threshold) {
                                grams = (grams - stepMode).coerceAtLeast(1)
                                accumulatedRotaryForGrams += threshold
                            }
                            true
                        } else {
                            scope.launch {
                                listState.scrollBy(event.verticalScrollPixels)
                            }
                            true
                        }
                    }
            ) {
                ScalingLazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                // Title
                item {
                    Text(
                        text = food.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
                    )
                }

                // Dynamic Calorie info
                item {
                    if (baseKcalPer100g != null) {
                        val computed = (baseKcalPer100g * (grams / 100.0)).roundToInt()
                        Text(
                            text = "≈ $computed ккал",
                            fontSize = 12.sp,
                            color = MaterialTheme.colors.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else if (!food.energy.isNullOrBlank()) {
                        Text(
                            text = "${food.energy}",
                            fontSize = 11.sp,
                            color = Color.LightGray
                        )
                    }
                }

                // Step Mode Selector (±1 / ±10)
                item {
                    Row(
                        modifier = Modifier.padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(1, 10).forEach { s ->
                            val isSelected = stepMode == s
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) MaterialTheme.colors.primary else Color(0xFF26262B))
                                    .clickable {
                                        stepMode = s
                                        isDrumFocused = true
                                    }
                                    .padding(horizontal = 10.dp, vertical = 3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "±$s $unitLabel",
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.Black else Color.White
                                )
                            }
                        }
                    }
                }

                // Fine-tuning Drum Wheel & Stepper (+ / -)
                item {
                    val step = stepMode
                    val previousGrams = (grams - step).coerceAtLeast(1)
                    val nextGrams = (grams + step).coerceAtMost(2000)
                    var accumulatedDrag by remember { mutableFloatStateOf(0f) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RepeatingButton(
                            icon = Icons.Default.Remove,
                            contentDescription = "-$step",
                            onSingleClick = {
                                userHasModifiedGrams = true
                                isDrumFocused = true
                                grams = (grams - stepMode).coerceAtLeast(1)
                            },
                            onRepeatStep = {
                                userHasModifiedGrams = true
                                val prev = if (grams % 50 == 0) grams - 50 else (grams / 50) * 50
                                grams = prev.coerceAtLeast(1)
                            }
                        )

                        // Alarm-clock style vertical drum picker
                        Column(
                            modifier = Modifier
                                .width(96.dp)
                                .padding(horizontal = 4.dp)
                                .pointerInput(grams, stepMode) {
                                    detectVerticalDragGestures(
                                        onDragStart = { accumulatedDrag = 0f },
                                        onDragEnd = { accumulatedDrag = 0f },
                                        onDragCancel = { accumulatedDrag = 0f },
                                        onVerticalDrag = { change, dragAmount ->
                                            userHasModifiedGrams = true
                                            isDrumFocused = true
                                            accumulatedDrag += dragAmount
                                            val thresholdPx = 16f
                                            while (accumulatedDrag <= -thresholdPx) {
                                                grams = (grams + stepMode).coerceAtMost(2000)
                                                accumulatedDrag += thresholdPx
                                            }
                                            while (accumulatedDrag >= thresholdPx) {
                                                grams = (grams - stepMode).coerceAtLeast(1)
                                                accumulatedDrag -= thresholdPx
                                            }
                                            change.consume()
                                        }
                                    )
                                },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (previousGrams != grams) "$previousGrams" else "",
                                fontSize = 11.sp,
                                color = Color(0x66FFFFFF),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable {
                                    userHasModifiedGrams = true
                                    isDrumFocused = true
                                    grams = previousGrams
                                }
                            )

                            Box(
                                modifier = Modifier
                                    .padding(vertical = 1.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isDrumFocused) Color(0x3364FFDA) else Color(0x1A64FFDA))
                                    .then(
                                        if (isDrumFocused) Modifier.border(1.5.dp, MaterialTheme.colors.primary, RoundedCornerShape(8.dp))
                                        else Modifier
                                    )
                                    .clickable {
                                        isDrumFocused = !isDrumFocused
                                    }
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$grams $unitLabel",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colors.primary,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Text(
                                text = if (nextGrams != grams) "$nextGrams" else "",
                                fontSize = 11.sp,
                                color = Color(0x66FFFFFF),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable {
                                    userHasModifiedGrams = true
                                    isDrumFocused = true
                                    grams = nextGrams
                                }
                            )
                        }

                        RepeatingButton(
                            icon = Icons.Default.Add,
                            contentDescription = "+$step",
                            onSingleClick = {
                                userHasModifiedGrams = true
                                isDrumFocused = true
                                grams = (grams + stepMode).coerceAtMost(2000)
                            },
                            onRepeatStep = {
                                userHasModifiedGrams = true
                                val next = if (grams % 50 == 0) grams + 50 else ((grams / 50) + 1) * 50
                                grams = next.coerceAtMost(2000)
                            }
                        )
                    }
                }

                // Horizontal scrollable quick preset chips
                item {
                    val presetList = remember(grams) { getGramPresets(grams) }
                    val rowState = rememberLazyListState()

                    LaunchedEffect(grams) {
                        val index = presetList.indexOf(grams)
                        if (index >= 0) {
                            rowState.animateScrollToItem((index - 1).coerceAtLeast(0))
                        }
                    }

                    LazyRow(
                        state = rowState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        items(presetList) { preset ->
                            CompactChip(
                                onClick = {
                                    userHasModifiedGrams = true
                                    grams = preset
                                },
                                label = {
                                    Text(
                                        text = "$preset",
                                        fontSize = 11.sp,
                                        fontWeight = if (grams == preset) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = ChipDefaults.chipColors(
                                    backgroundColor = if (grams == preset) MaterialTheme.colors.primary else Color(0xFF26262B),
                                    contentColor = if (grams == preset) Color.Black else Color.White
                                )
                            )
                        }
                    }
                }

                // Meal slot selector (Tap to cycle)
                item {
                    val slots = WatchMealTimeUtils.MEAL_SLOTS
                    val currentSlot = slots[selectedSlotIndex]
                    Chip(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 2.dp),
                        onClick = {
                            selectedSlotIndex = (selectedSlotIndex + 1) % slots.size
                        },
                        label = {
                            Text(
                                text = "Приём: ${currentSlot.name}",
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        },
                        secondaryLabel = {
                            Text(
                                text = "Нажмите для смены",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        },
                        colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF1E1E22))
                    )
                }

                // Confirm / Add Button
                item {
                    val currentSlot = WatchMealTimeUtils.MEAL_SLOTS[selectedSlotIndex]
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        onClick = {
                            if (isSubmitting) return@Button
                            isSubmitting = true
                            scope.launch {
                                val req = WearAddFoodRequest(
                                    guid = food.id,
                                    title = food.title,
                                    multiplier = grams.toString(),
                                    diaryTimeGuid = currentSlot.id,
                                    date = WatchMealTimeUtils.getTodayDateString(),
                                    unitGuid = "0000000000000001",
                                    url = food.url.orEmpty()
                                )
                                val res = dataManager.addFood(req)
                                isSubmitting = false
                                submitResult = Pair(res.success, res.message)
                                if (res.success) {
                                    delay(1400)
                                    onSuccess()
                                } else {
                                    delay(2000)
                                    submitResult = null
                                }
                            }
                        },
                        enabled = !isSubmitting,
                        colors = ButtonDefaults.primaryButtonColors()
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                indicatorColor = Color.Black
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Check, contentDescription = "Add")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Добавить",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
}
