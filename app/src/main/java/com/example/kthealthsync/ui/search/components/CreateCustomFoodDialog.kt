package com.example.kthealthsync.ui.search.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kthealthsync.AddCustomFoodRequest
import com.example.kthealthsync.domain.model.AddedFoodInfo
import com.example.kthealthsync.domain.model.LocalAppStrings
import com.example.kthealthsync.domain.util.MealTimeUtils
import com.example.kthealthsync.ui.search.SearchViewModel
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round
import kotlinx.coroutines.launch

private fun formatNumber(value: Double?): String? {
    if (value == null) return null
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
fun CreateCustomFoodDialog(
    initialTitle: String = "",
    date: String,
    initialMealTimeGuid: String? = null,
    viewModel: SearchViewModel,
    onDismiss: () -> Unit,
    onSuccess: (AddedFoodInfo) -> Unit
) {
    val strings = LocalAppStrings.current
    val coroutineScope = rememberCoroutineScope()
    val timeOptions = remember(strings) { MealTimeUtils.getStandardMealOptions(strings) }

    var title by remember { mutableStateOf(initialTitle) }
    var selectedTimeGuid by remember(date, initialMealTimeGuid) {
        mutableStateOf(initialMealTimeGuid ?: MealTimeUtils.getDefaultMealTimeGuid(date, timeOptions))
    }
    var energyText by remember { mutableStateOf("") }
    var proteinText by remember { mutableStateOf("") }
    var carbsText by remember { mutableStateOf("") }
    var fatText by remember { mutableStateOf("") }

    var fiberText by remember { mutableStateOf("") }
    var sugarText by remember { mutableStateOf("") }
    var satFatText by remember { mutableStateOf("") }
    var saltText by remember { mutableStateOf("") }

    var showMoreDetails by remember { mutableStateOf(false) }
    var mealExpanded by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    var aiPrompt by remember { mutableStateOf(initialTitle.ifBlank { "" }) }
    var isAiParsing by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf<String?>(null) }

    var isLinked by remember { mutableStateOf(false) }
    var activeEditingField by remember { mutableStateOf<String?>(null) }
    var baseEnergy by remember { mutableStateOf<Double?>(null) }
    var baseProtein by remember { mutableStateOf<Double?>(null) }
    var baseCarbs by remember { mutableStateOf<Double?>(null) }
    var baseFat by remember { mutableStateOf<Double?>(null) }
    var baseFiber by remember { mutableStateOf<Double?>(null) }
    var baseSugar by remember { mutableStateOf<Double?>(null) }
    var baseSatFat by remember { mutableStateOf<Double?>(null) }
    var baseSalt by remember { mutableStateOf<Double?>(null) }

    fun captureBaseline(field: String) {
        activeEditingField = field
        baseEnergy = energyText.replace(',', '.').trim().toDoubleOrNull()
        baseProtein = proteinText.replace(',', '.').trim().toDoubleOrNull()
        baseCarbs = carbsText.replace(',', '.').trim().toDoubleOrNull()
        baseFat = fatText.replace(',', '.').trim().toDoubleOrNull()
        baseFiber = fiberText.replace(',', '.').trim().toDoubleOrNull()
        baseSugar = sugarText.replace(',', '.').trim().toDoubleOrNull()
        baseSatFat = satFatText.replace(',', '.').trim().toDoubleOrNull()
        baseSalt = saltText.replace(',', '.').trim().toDoubleOrNull()
    }

    fun onMacroFieldChange(field: String, newText: String) {
        if (!isLinked) {
            when (field) {
                "energy" -> energyText = newText
                "protein" -> proteinText = newText
                "carbs" -> carbsText = newText
                "fat" -> fatText = newText
                "fiber" -> fiberText = newText
                "sugar" -> sugarText = newText
                "satFat" -> satFatText = newText
                "salt" -> saltText = newText
            }
            return
        }

        if (activeEditingField != field) {
            captureBaseline(field)
        }

        when (field) {
            "energy" -> energyText = newText
            "protein" -> proteinText = newText
            "carbs" -> carbsText = newText
            "fat" -> fatText = newText
            "fiber" -> fiberText = newText
            "sugar" -> sugarText = newText
            "satFat" -> satFatText = newText
            "salt" -> saltText = newText
        }

        val newNum = newText.replace(',', '.').trim().toDoubleOrNull()
        val baseNum = when (field) {
            "energy" -> baseEnergy
            "protein" -> baseProtein
            "carbs" -> baseCarbs
            "fat" -> baseFat
            "fiber" -> baseFiber
            "sugar" -> baseSugar
            "satFat" -> baseSatFat
            "salt" -> baseSalt
            else -> null
        }

        if (newNum != null && newNum >= 0.0 && baseNum != null && baseNum > 0.0) {
            val ratio = newNum / baseNum
            if (field != "energy") baseEnergy?.let { energyText = formatNumber(it * ratio) ?: "" }
            if (field != "protein") baseProtein?.let { proteinText = formatNumber(it * ratio) ?: "" }
            if (field != "carbs") baseCarbs?.let { carbsText = formatNumber(it * ratio) ?: "" }
            if (field != "fat") baseFat?.let { fatText = formatNumber(it * ratio) ?: "" }
            if (field != "fiber") baseFiber?.let { fiberText = formatNumber(it * ratio) ?: "" }
            if (field != "sugar") baseSugar?.let { sugarText = formatNumber(it * ratio) ?: "" }
            if (field != "satFat") baseSatFat?.let { satFatText = formatNumber(it * ratio) ?: "" }
            if (field != "salt") baseSalt?.let { saltText = formatNumber(it * ratio) ?: "" }
        }
    }

    val energyVal = energyText.replace(',', '.').trim().toDoubleOrNull()
    val proteinVal = proteinText.replace(',', '.').trim().toDoubleOrNull()
    val carbsVal = carbsText.replace(',', '.').trim().toDoubleOrNull()
    val fatVal = fatText.replace(',', '.').trim().toDoubleOrNull()

    val fiberVal = fiberText.replace(',', '.').trim().toDoubleOrNull()
    val sugarVal = sugarText.replace(',', '.').trim().toDoubleOrNull()
    val satFatVal = satFatText.replace(',', '.').trim().toDoubleOrNull()
    val saltVal = saltText.replace(',', '.').trim().toDoubleOrNull()

    val isFormValid = title.isNotBlank() &&
            energyVal != null && energyVal >= 0.0 &&
            proteinVal != null && proteinVal >= 0.0 &&
            carbsVal != null && carbsVal >= 0.0 &&
            fatVal != null && fatVal >= 0.0 &&
            (fiberText.isBlank() || (fiberVal != null && fiberVal >= 0.0)) &&
            (sugarText.isBlank() || (sugarVal != null && sugarVal >= 0.0)) &&
            (satFatText.isBlank() || (satFatVal != null && satFatVal >= 0.0)) &&
            (saltText.isBlank() || (saltVal != null && saltVal >= 0.0))

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AddCircleOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.createCustomFood, fontSize = 20.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // AI Prompt with Gemini Nano
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = aiPrompt,
                            onValueChange = {
                                aiPrompt = it
                                aiError = null
                            },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(strings.customFoodAiPromptLabel)
                                }
                            },
                            placeholder = { Text(strings.customFoodAiPromptHint, fontSize = 12.sp) },
                            trailingIcon = {
                                if (isAiParsing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    IconButton(
                                        onClick = {
                                            if (aiPrompt.isNotBlank()) {
                                                coroutineScope.launch {
                                                    isAiParsing = true
                                                    aiError = null
                                                    try {
                                                        val parsed = viewModel.parseCustomFoodWithNano(aiPrompt)
                                                        if (parsed != null) {
                                                            parsed.title?.let { if (it.isNotBlank()) title = it }
                                                            parsed.energy?.let { energyText = formatNumber(it) ?: "" }
                                                            parsed.protein?.let { proteinText = formatNumber(it) ?: "" }
                                                            parsed.carbs?.let { carbsText = formatNumber(it) ?: "" }
                                                            parsed.fat?.let { fatText = formatNumber(it) ?: "" }
                                                            if (parsed.fiber != null) {
                                                                fiberText = formatNumber(parsed.fiber) ?: ""
                                                                showMoreDetails = true
                                                            }
                                                            if (parsed.sugar != null) {
                                                                sugarText = formatNumber(parsed.sugar) ?: ""
                                                                showMoreDetails = true
                                                            }
                                                            if (parsed.satFat != null) {
                                                                satFatText = formatNumber(parsed.satFat) ?: ""
                                                                showMoreDetails = true
                                                            }
                                                            if (parsed.salt != null) {
                                                                saltText = formatNumber(parsed.salt) ?: ""
                                                                showMoreDetails = true
                                                            }
                                                            activeEditingField = null
                                                        } else {
                                                            aiError = strings.customFoodAiError
                                                        }
                                                    } catch (e: Exception) {
                                                        aiError = e.message ?: strings.customFoodAiError
                                                    } finally {
                                                        isAiParsing = false
                                                    }
                                                }
                                            }
                                        },
                                        enabled = aiPrompt.isNotBlank() && !isAiParsing
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = strings.customFoodAiFill,
                                            tint = if (aiPrompt.isNotBlank()) MaterialTheme.colorScheme.primary else Color.Gray
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3
                        )
                        if (aiError != null) {
                            Text(
                                aiError!!,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }

                // Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(strings.customFoodTitle) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Meal Time Dropdown
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        strings.mealTimeLabel,
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

                // Section header / note with proportional lock icon
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        strings.customFoodPortionNote,
                        fontSize = 13.sp,
                        color = Color.LightGray,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            isLinked = !isLinked
                            activeEditingField = null
                        }
                    ) {
                        Icon(
                            imageVector = if (isLinked) Icons.Default.Link else Icons.Default.LinkOff,
                            contentDescription = if (isLinked) strings.proportionalLinked else strings.proportionalUnlinked,
                            tint = if (isLinked) MaterialTheme.colorScheme.primary else Color(0xFFAAAAAA)
                        )
                    }
                }

                // Energy (kcal)
                OutlinedTextField(
                    value = energyText,
                    onValueChange = { onMacroFieldChange("energy", it) },
                    label = { Text("${strings.energy} (${strings.kcalUnit}) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 3 Macros in a Row: Protein, Carbs, Fat
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = proteinText,
                        onValueChange = { onMacroFieldChange("protein", it) },
                        label = { Text("${strings.proteinShort} (g) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = carbsText,
                        onValueChange = { onMacroFieldChange("carbs", it) },
                        label = { Text("${strings.carbsShort} (g) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = fatText,
                        onValueChange = { onMacroFieldChange("fat", it) },
                        label = { Text("${strings.fatShort} (g) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Expandable Details (Fiber, Sugar, Saturated Fat, Salt)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showMoreDetails = !showMoreDetails }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        strings.moreDetails,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = if (showMoreDetails) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                AnimatedVisibility(visible = showMoreDetails) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = fiberText,
                                onValueChange = { onMacroFieldChange("fiber", it) },
                                label = { Text("${strings.fiberShort} (g)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = sugarText,
                                onValueChange = { onMacroFieldChange("sugar", it) },
                                label = { Text("${strings.sugar} (g)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = satFatText,
                                onValueChange = { onMacroFieldChange("satFat", it) },
                                label = { Text("${strings.saturatedFatShort} (g)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = saltText,
                                onValueChange = { onMacroFieldChange("salt", it) },
                                label = { Text("${strings.salt} (g)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Error message
                errorMsg?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
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
                            val request = AddCustomFoodRequest(
                                guid = "0",
                                title = title.trim(),
                                diaryTimeGuid = selectedTimeGuid,
                                date = date,
                                energy = formatNumber(energyVal) ?: "0",
                                energyUnit = "kcal",
                                detailValues = true,
                                protein = formatNumber(proteinVal) ?: "0",
                                carbohydrate = formatNumber(carbsVal) ?: "0",
                                fat = formatNumber(fatVal) ?: "0",
                                fiber = formatNumber(fiberVal),
                                sugar = formatNumber(sugarVal),
                                saturatedFattyAcid = formatNumber(satFatVal),
                                salt = formatNumber(saltVal)
                            )
                            val response = viewModel.addCustomFood(request)
                            if (response.code == 0) {
                                val addedInfo = AddedFoodInfo(
                                    title = title.trim(),
                                    kcal = energyVal ?: 0.0,
                                    protein = proteinVal ?: 0.0,
                                    carbs = carbsVal ?: 0.0,
                                    fat = fatVal ?: 0.0,
                                    fiber = fiberVal ?: 0.0,
                                    sugar = sugarVal ?: 0.0,
                                    salt = saltVal ?: 0.0,
                                    unitStr = "1 porce",
                                    mealTimeId = selectedTimeGuid
                                )
                                onSuccess(addedInfo)
                            } else {
                                errorMsg = response.message ?: "Failed with code ${response.code}"
                            }
                        } catch (e: Exception) {
                            errorMsg = e.message ?: strings.saveError
                        } finally {
                            isSubmitting = false
                        }
                    }
                },
                enabled = !isSubmitting && isFormValid
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(strings.save)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text(strings.cancel)
            }
        }
    )
}
