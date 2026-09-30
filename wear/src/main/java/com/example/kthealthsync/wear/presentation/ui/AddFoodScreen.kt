package com.example.kthealthsync.wear.presentation.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.example.kthealthsync.wear.data.WatchDataManager
import com.example.kthealthsync.wear.data.WatchMealTimeUtils
import com.example.kthealthsync.wear.data.WearAddFoodRequest
import com.example.kthealthsync.wear.data.WearFoodItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun AddFoodScreen(
    food: WearFoodItem,
    dataManager: WatchDataManager,
    onSuccess: () -> Unit
) {
    val listState = rememberScalingLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    var grams by remember { mutableIntStateOf(100) }
    var selectedSlotIndex by remember {
        val initialSlot = WatchMealTimeUtils.getCurrentMealSlot()
        val index = WatchMealTimeUtils.MEAL_SLOTS.indexOfFirst { it.id == initialSlot.id }.coerceAtLeast(0)
        mutableIntStateOf(index)
    }

    var isSubmitting by remember { mutableStateOf(false) }
    var submitResult by remember { mutableStateOf<Pair<Boolean, String?>?>(null) }

    // Parse base energy number for scaled calculation
    val baseKcalPer100g = remember(food.energy) {
        food.energy?.replace(Regex("[^0-9.]"), "")?.toDoubleOrNull()
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
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
            ScalingLazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .onRotaryScrollEvent { event ->
                        val delta = event.verticalScrollPixels
                        if (delta > 0) {
                            grams = (grams + 10).coerceAtMost(2000)
                        } else if (delta < 0) {
                            grams = (grams - 10).coerceAtLeast(10)
                        }
                        true
                    }
                    .focusRequester(focusRequester)
                    .focusable(),
                state = listState,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title
                item {
                    Text(
                        text = food.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                // Dynamic Calorie info
                item {
                    if (baseKcalPer100g != null) {
                        val computed = (baseKcalPer100g * (grams / 100.0)).roundToInt()
                        Text(
                            text = "≈ $computed ккал",
                            fontSize = 13.sp,
                            color = MaterialTheme.colors.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else if (!food.energy.isNullOrBlank()) {
                        Text(
                            text = "${food.energy}",
                            fontSize = 12.sp,
                            color = Color.LightGray
                        )
                    }
                }

                // Weight display & Stepper (+ / -)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompactButton(
                            onClick = { grams = (grams - 10).coerceAtLeast(10) },
                            colors = ButtonDefaults.secondaryButtonColors()
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "-10g")
                        }

                        Text(
                            text = "$grams г",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        CompactButton(
                            onClick = { grams = (grams + 10).coerceAtMost(2000) },
                            colors = ButtonDefaults.secondaryButtonColors()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "+10g")
                        }
                    }
                }

                // Quick Gram chips
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf(50, 100, 150, 200).forEach { presetGrams ->
                            CompactChip(
                                onClick = { grams = presetGrams },
                                label = { Text("$presetGrams", fontSize = 11.sp) },
                                colors = ChipDefaults.chipColors(
                                    backgroundColor = if (grams == presetGrams) MaterialTheme.colors.primary else Color(0xFF26262B),
                                    contentColor = if (grams == presetGrams) Color.Black else Color.White
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
                            .padding(horizontal = 16.dp, vertical = 4.dp),
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
                            .padding(horizontal = 16.dp, vertical = 6.dp),
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
