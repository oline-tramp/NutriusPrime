package com.example.kthealthsync.ui.search.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kthealthsync.*
import com.example.kthealthsync.domain.model.NutrientGoals
import com.example.kthealthsync.domain.util.NutrientUtils
import com.example.kthealthsync.ui.search.SearchViewModel
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

private fun formatAmount(value: Double): String {
    if (abs(value - round(value)) < 1e-4) {
        return round(value).toInt().toString()
    }
    val rounded = round(value * 100.0) / 100.0
    if (abs(rounded - round(rounded)) < 1e-4) {
        return round(rounded).toInt().toString()
    }
    return String.format(Locale.US, "%.2f", rounded).trimEnd('0').trimEnd('.')
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodDialog(
    item: AutocompleteResponseItem,
    date: String,
    currentKcal: Double,
    currentProtein: Double,
    currentCarbs: Double,
    currentFat: Double,
    currentFiber: Double,
    currentSugar: Double,
    currentSalt: Double,
    nutrientGoals: NutrientGoals,
    viewModel: SearchViewModel,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    val timeOptions = remember {
        listOf(
            "1" to "Breakfast (Snídaně)",
            "2" to "Morning Snack (Dopolední svačina)",
            "3" to "Lunch (Oběd)",
            "4" to "Afternoon Snack (Odpolední svačina)",
            "5" to "Dinner (Večeře)",
            "6" to "Second Dinner (Druhá večeře)"
        )
    }

    var unitOptions by remember { mutableStateOf<List<FoodFormUnitOption>>(emptyList()) }
    var selectedUnit by remember { mutableStateOf<FoodFormUnitOption?>(null) }
    var amountText by remember { mutableStateOf("") }
    var actualDefaultBaseAmount by remember { mutableStateOf(1.0) }
    var selectedTimeGuid by remember(date) {
        mutableStateOf(NutrientUtils.getDefaultMealTimeGuid(date, timeOptions))
    }

    var isLoadingOptions by remember { mutableStateOf(true) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    var proteinPer100 by remember { mutableStateOf(0.0) }
    var carbsPer100 by remember { mutableStateOf(0.0) }
    var fatPer100 by remember { mutableStateOf(0.0) }
    var fiberPer100 by remember { mutableStateOf(0.0) }
    var sugarPer100 by remember { mutableStateOf(0.0) }
    var saltPer100 by remember { mutableStateOf(0.0) }
    var showImpactPreview by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val res = viewModel.getFoodForm(item.id!!)
            val opts = res.data?.unitOptions ?: emptyList()
            unitOptions = opts

            if (opts.isNotEmpty()) {
                val nonGramOpt = opts.firstOrNull { it.multiplier != 1.0 }
                actualDefaultBaseAmount = nonGramOpt?.multiplier ?: 1.0
                selectedUnit = opts.find { it.multiplier == 1.0 } ?: opts.first()
            }

            if (item.url != null) {
                val macros = viewModel.getFoodMacros(item.url, res.data?.url)
                proteinPer100 = macros["bílkoviny"] ?: 0.0
                carbsPer100 = macros["sacharidy"] ?: 0.0
                fatPer100 = macros["tuky"] ?: 0.0
                fiberPer100 = macros["vláknina"] ?: 0.0
                sugarPer100 = macros["cukry"] ?: 0.0
                saltPer100 = macros["sůl"] ?: 0.0
            }
        } catch (e: Exception) {
            errorMsg = "Failed to load form details"
        } finally {
            isLoadingOptions = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                item.title ?: "Add item",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            if (isLoadingOptions) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (errorMsg != null) {
                        Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Amount
                        Column(modifier = Modifier.weight(0.45f)) {
                            Text(
                                "Amount",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                            )
                            OutlinedTextField(
                                value = amountText,
                                onValueChange = { amountText = it },
                                placeholder = {
                                    val mult = selectedUnit?.multiplier ?: 1.0
                                    val pVal = if (mult == 1.0) actualDefaultBaseAmount else 1.0
                                    Text(formatAmount(pVal), maxLines = 1)
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Unit Dropdown
                        var unitExpanded by remember { mutableStateOf(false) }
                        Column(modifier = Modifier.weight(0.55f)) {
                            Text(
                                "Unit",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                            )
                            ExposedDropdownMenuBox(
                                expanded = unitExpanded,
                                onExpandedChange = { unitExpanded = !unitExpanded }
                            ) {
                                OutlinedTextField(
                                    readOnly = true,
                                    value = selectedUnit?.title ?: "",
                                    onValueChange = {},
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded) },
                                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                    singleLine = true,
                                    modifier = Modifier.menuAnchor().fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = unitExpanded,
                                    onDismissRequest = { unitExpanded = false }
                                ) {
                                    unitOptions.forEach { opt ->
                                        DropdownMenuItem(
                                            text = { Text(opt.title ?: "") },
                                            onClick = {
                                                selectedUnit = opt
                                                unitExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Meal Dropdown
                    var mealExpanded by remember { mutableStateOf(false) }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Meal Time",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                        )
                        ExposedDropdownMenuBox(
                            expanded = mealExpanded,
                            onExpandedChange = { mealExpanded = !mealExpanded }
                        ) {
                            OutlinedTextField(
                                readOnly = true,
                                value = timeOptions.find { it.first == selectedTimeGuid }?.second ?: "",
                                onValueChange = {},
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = mealExpanded) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                singleLine = true,
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = mealExpanded,
                                onDismissRequest = { mealExpanded = false }
                            ) {
                                timeOptions.forEach { (guid, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            selectedTimeGuid = guid
                                            mealExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    val itemKcal = item.value?.replace(Regex("[^0-9.]"), "")?.toDoubleOrNull() ?: 0.0
                    val mult = selectedUnit?.multiplier ?: 1.0
                    val pVal = if (mult == 1.0) actualDefaultBaseAmount else 1.0
                    val inputVal = amountText.replace(',', '.').toDoubleOrNull() ?: pVal

                    val factor = (inputVal * mult) / 100.0
                    val addedKcal = factor * itemKcal

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showImpactPreview = !showImpactPreview }
                            .padding(top = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Vliv na příjem",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Icon(
                            imageVector = if (showImpactPreview) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (showImpactPreview) "Skrýt" else "Zobrazit",
                            tint = Color.LightGray
                        )
                    }

                    if (showImpactPreview) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                CircularImpactIndicator(title = "Energie", current = currentKcal, added = addedKcal, max = nutrientGoals.kcal, unit = "kcal", color = Color(0xFFFFCA28))
                                CircularImpactIndicator(title = "Bílkoviny", current = currentProtein, added = proteinPer100 * factor, max = nutrientGoals.protein, unit = "g", color = Color(0xFFEF5350))
                                CircularImpactIndicator(title = "Sacharidy", current = currentCarbs, added = carbsPer100 * factor, max = nutrientGoals.carbs, unit = "g", color = Color(0xFFFFCA28))
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                CircularImpactIndicator(title = "Tuky", current = currentFat, added = fatPer100 * factor, max = nutrientGoals.fat, unit = "g", color = Color(0xFF66BB6A))
                                CircularImpactIndicator(title = "Vláknina", current = currentFiber, added = fiberPer100 * factor, max = nutrientGoals.fiber, unit = "g", color = Color(0xFFFFCA28))
                                CircularImpactIndicator(title = "Cukry", current = currentSugar, added = sugarPer100 * factor, max = nutrientGoals.sugar, unit = "g", color = Color(0xFF66BB6A))
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                CircularImpactIndicator(title = "Sůl", current = currentSalt, added = saltPer100 * factor, max = nutrientGoals.salt, unit = "g", color = Color(0xFFEF5350))
                                Spacer(modifier = Modifier.width(88.dp))
                                Spacer(modifier = Modifier.width(88.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    coroutineScope.launch {
                        isSubmitting = true
                        errorMsg = null
                        try {
                            val mult = selectedUnit?.multiplier ?: 1.0
                            val pVal = if (mult == 1.0) actualDefaultBaseAmount else 1.0
                            val inputVal = amountText.replace(',', '.').toDoubleOrNull() ?: pVal
                            val finalAmount = formatAmount(inputVal * mult)

                            val request = AddFoodRequest(
                                guid = item.id!!,
                                title = item.title ?: "",
                                url = item.url ?: "",
                                date = date,
                                multiplier = finalAmount,
                                diaryTimeGuid = selectedTimeGuid
                            )
                            val response = viewModel.addFood(request)
                            if (response.code == 0) {
                                onSuccess()
                            } else {
                                errorMsg = response.message ?: "Failed with code ${response.code}"
                            }
                        } catch (e: Exception) {
                            errorMsg = e.message ?: "Error saving food"
                        } finally {
                            isSubmitting = false
                        }
                    }
                },
                enabled = !isSubmitting && !isLoadingOptions
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Cancel")
            }
        }
    )
}
