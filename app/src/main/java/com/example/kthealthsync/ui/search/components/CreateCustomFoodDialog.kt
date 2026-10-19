package com.example.kthealthsync.ui.search.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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

                // Section header / note
                Text(
                    strings.customFoodPortionNote,
                    fontSize = 13.sp,
                    color = Color.LightGray,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // Energy (kcal)
                OutlinedTextField(
                    value = energyText,
                    onValueChange = { energyText = it },
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
                        onValueChange = { proteinText = it },
                        label = { Text("${strings.proteinShort} (g) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = carbsText,
                        onValueChange = { carbsText = it },
                        label = { Text("${strings.carbsShort} (g) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = fatText,
                        onValueChange = { fatText = it },
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
                                onValueChange = { fiberText = it },
                                label = { Text("${strings.fiberShort} (g)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = sugarText,
                                onValueChange = { sugarText = it },
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
                                onValueChange = { satFatText = it },
                                label = { Text("${strings.saturatedFatShort} (g)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = saltText,
                                onValueChange = { saltText = it },
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
