package com.example.kthealthsync.ui.dashboard

import android.app.DatePickerDialog
import android.content.DialogInterface
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kthealthsync.AddFoodRequest
import com.example.kthealthsync.FoodFormUnitOption
import com.example.kthealthsync.Foodstuff
import com.example.kthealthsync.UnitOption
import com.example.kthealthsync.domain.model.DiaryEditResult
import com.example.kthealthsync.domain.model.LocalAppStrings
import com.example.kthealthsync.domain.model.MealPortion
import com.example.kthealthsync.domain.model.NutrientGoals
import com.example.kthealthsync.domain.model.PersonalMealForm
import com.example.kthealthsync.domain.util.MealTimeUtils
import com.example.kthealthsync.domain.util.NutrientParser
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round
import kotlinx.coroutines.launch

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

private val MULT_MATCH_REGEX = "^([\\d.]+)\\s*x\\s*([\\d.]+)?\\s*(.*)$".toRegex()
private val SIMPLE_MATCH_REGEX = "^([\\d.]+)\\s*([a-zA-Z]+)$".toRegex()

private fun parseInitialAmountAndUnit(unitStr: String?): Pair<String, String?> {
    if (unitStr.isNullOrBlank()) return "100" to null
    val normalized = unitStr.replace("\u00a0", " ").replace(",", ".").trim()

    // 1. Multiplier pattern: "150 x 1 g", "2 x kus (120 g)", "1 x 1.5 l"
    val multMatch = MULT_MATCH_REGEX.find(normalized)
    if (multMatch != null) {
        val count = multMatch.groupValues[1]
        val rest = multMatch.groupValues[3].trim().ifBlank { multMatch.groupValues[2].trim() }
        val countDouble = count.toDoubleOrNull()
        val countStr = if (countDouble != null && countDouble == countDouble.toInt().toDouble()) countDouble.toInt().toString() else count
        return countStr to rest
    }

    // 2. Simple pattern: "150 g", "250 ml", "0.5 kg"
    val simpleMatch = SIMPLE_MATCH_REGEX.find(normalized)
    if (simpleMatch != null) {
        val amount = simpleMatch.groupValues[1]
        val unit = simpleMatch.groupValues[2].lowercase()
        val amountDouble = amount.toDoubleOrNull()
        val amountStr = if (amountDouble != null && amountDouble == amountDouble.toInt().toDouble()) amountDouble.toInt().toString() else amount
        return amountStr to unit
    }

    // 3. Fallback: extract portion grams
    val g = NutrientParser.parsePortionGrams(unitStr)
    if (g > 0) {
        val gStr = if (g == g.toInt().toDouble()) g.toInt().toString() else String.format(Locale.US, "%.1f", g)
        return gStr to "g"
    }

    return "100" to null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditFoodDialog(
    food: Foodstuff,
    initialMealTimeId: String,
    currentDate: ZonedDateTime,
    viewModel: DashboardViewModel,
    isClone: Boolean = false,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
    onDelete: () -> Unit = {}
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val formatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy") }

    val timeOptions = remember(strings) { MealTimeUtils.getStandardMealOptions(strings) }

    val (parsedInitAmount, parsedInitUnit) = remember(food.unit) {
        parseInitialAmountAndUnit(food.unit)
    }

    var amountText by remember { mutableStateOf(parsedInitAmount) }
    var selectedUnit by remember { mutableStateOf<FoodFormUnitOption?>(null) }
    var unitOptions by remember { mutableStateOf<List<FoodFormUnitOption>>(emptyList()) }
    var selectedTimeGuid by remember { mutableStateOf(initialMealTimeId) }
    var targetDate by remember { mutableStateOf(currentDate.toLocalDate()) }

    var isLoadingOptions by remember { mutableStateOf(true) }
    var isSubmitting by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var needsOldEntryRemoval by remember { mutableStateOf(false) }

    if (needsOldEntryRemoval) {
        AlertDialog(
            onDismissRequest = { if (!isSubmitting) onDismiss() },
            title = { Text(strings.saveError) },
            text = { Text(strings.editCleanupError) },
            confirmButton = {
                TextButton(enabled = !isSubmitting, onClick = {
                    isSubmitting = true
                    viewModel.deleteEntries(setOf(food.id.orEmpty())) { success ->
                        isSubmitting = false
                        if (success) onSuccess()
                    }
                }) { Text(strings.retry) }
            },
            dismissButton = {
                TextButton(enabled = !isSubmitting, onClick = onDismiss) { Text(strings.close) }
            }
        )
        return
    }

    // Macro density base per 100g
    val portionGrams = remember(food.unit) {
        NutrientParser.parsePortionGrams(food.unit).coerceAtLeast(1.0)
    }
    var energyPer100 by remember {
        mutableDoubleStateOf(NutrientParser.parseNutrient(food.energy) / portionGrams * 100.0)
    }
    var proteinPer100 by remember {
        mutableDoubleStateOf(NutrientParser.parseNutrient(food.protein) / portionGrams * 100.0)
    }
    var carbsPer100 by remember {
        mutableDoubleStateOf(NutrientParser.parseNutrient(food.carbohydrate) / portionGrams * 100.0)
    }
    var fatPer100 by remember {
        mutableDoubleStateOf(NutrientParser.parseNutrient(food.fat) / portionGrams * 100.0)
    }
    var fiberPer100 by remember {
        mutableDoubleStateOf(NutrientParser.parseNutrient(food.fiber) / portionGrams * 100.0)
    }
    var sugarPer100 by remember {
        mutableDoubleStateOf(NutrientParser.parseNutrient(food.sugar) / portionGrams * 100.0)
    }
    var saltPer100 by remember {
        mutableDoubleStateOf(NutrientParser.parseNutrient(food.salt) / portionGrams * 100.0)
    }

    val isPersonalMealCandidate = food.isRecipe == true ||
            !food.components.isNullOrEmpty() ||
            !food.guidRecipe.isNullOrBlank() ||
            food.type == "meal" ||
            food.guidType == "meal"

    val rawCandidateGuid = food.guidRecipe?.takeIf { it.isNotBlank() }
        ?: (if (food.guidType != "meal") food.guidType else null)?.takeIf { it.isNotBlank() }
        ?: (if (food.guid != "meal") food.guid else null)?.takeIf { it.isNotBlank() }
        ?: ""

    val recipeGuid = rawCandidateGuid
    val productGuid = rawCandidateGuid

    var personalMealForm by remember { mutableStateOf<PersonalMealForm?>(null) }

    suspend fun loadStandardFoodForm(guid: String) {
        val res = viewModel.getFoodForm(guid)
        val opts = res.data?.unitOptions ?: emptyList()
        if (opts.isEmpty()) {
            throw IllegalStateException("Food form returned no unit options")
        }
        unitOptions = opts

        val matchedOpt = if (!parsedInitUnit.isNullOrBlank()) {
            opts.find { opt ->
                val optTitle = (opt.title ?: "").lowercase()
                optTitle.contains(parsedInitUnit.lowercase()) ||
                        parsedInitUnit.lowercase().contains(optTitle)
            }
        } else null

        selectedUnit = matchedOpt ?: opts.find { it.multiplier == 1.0 } ?: opts.first()

        if (!food.url.isNullOrBlank()) {
            val macros = viewModel.getFoodMacros(food.url, res.data?.url)
            if (macros.isNotEmpty()) {
                macros["energie"]?.let { energyPer100 = it }
                macros["bílkoviny"]?.let { proteinPer100 = it }
                macros["sacharidy"]?.let { carbsPer100 = it }
                macros["tuky"]?.let { fatPer100 = it }
                macros["vláknina"]?.let { fiberPer100 = it }
                macros["cukry"]?.let { sugarPer100 = it }
                macros["sůl"]?.let { saltPer100 = it }
            }
        }
    }

    LaunchedEffect(food) {
        val candidateGuid = rawCandidateGuid

        if (candidateGuid.isNotBlank() || isPersonalMealCandidate) {
            try {
                val isMeal = isPersonalMealCandidate || viewModel.isPersonalMeal(food)
                if (isMeal) {
                    try {
                        val mealGuid = food.guidRecipe?.takeIf { it.isNotBlank() }
                            ?: viewModel.getPersonalMealGuid(food)
                            ?: candidateGuid
                        val form = viewModel.getPersonalMealForm(mealGuid)
                        personalMealForm = form
                        unitOptions = form.units

                        val matchedOpt = if (!food.unit.isNullOrBlank()) {
                            val unitLower = food.unit.lowercase()
                            when {
                                unitLower.contains("%") -> form.units.find { it.multiplier == -1.0 }
                                unitLower.contains("porce") || unitLower.contains("porc") || unitLower.contains("portion") ->
                                    form.units.find { it.multiplier == -2.0 }
                                unitLower.contains("g") -> form.units.find { it.multiplier == 1.0 } ?: form.units.find { it.multiplier != null && it.multiplier > 0 }
                                else -> null
                            }
                        } else null

                        selectedUnit = matchedOpt ?: form.defaultUnit

                        if (amountText.isBlank() || amountText == "100") {
                            amountText = formatAmount(form.defaultAmount)
                        }
                    } catch (e: Exception) {
                        loadStandardFoodForm(candidateGuid)
                    }
                } else {
                    loadStandardFoodForm(candidateGuid)
                }
            } catch (e: Exception) {
                // If standard food form failed or had no units, attempt personal meal form:
                try {
                    val mealGuid = viewModel.getPersonalMealGuid(food) ?: candidateGuid
                    val form = viewModel.getPersonalMealForm(mealGuid)
                    personalMealForm = form
                    unitOptions = form.units
                    selectedUnit = form.defaultUnit
                    if (amountText.isBlank() || amountText == "100") {
                        amountText = formatAmount(form.defaultAmount)
                    }
                } catch (_: Exception) {
                    unitOptions = listOf(
                        FoodFormUnitOption("0000000000000001", "1 g", 1.0),
                        FoodFormUnitOption("100g", "100 g", 100.0)
                    )
                    selectedUnit = unitOptions.first()
                }
            } finally {
                isLoadingOptions = false
            }
        } else {
            isLoadingOptions = false
        }
    }

    val title = food.title ?: food.name ?: food.nazev ?: strings.editTitle

    // Calculate current preview nutrition based on user input
    val currentAmountVal = amountText.replace(",", ".").toDoubleOrNull() ?: 0.0
    val currentMultiplier = selectedUnit?.multiplier ?: 1.0
    val totalEffectiveGrams = currentAmountVal * currentMultiplier
    val scaleFactor = totalEffectiveGrams / 100.0

    val currentMealPortion = remember(personalMealForm, selectedUnit, currentAmountVal, targetDate, selectedTimeGuid) {
        if (personalMealForm != null && selectedUnit != null && currentAmountVal > 0) {
            runCatching {
                personalMealForm!!.portion(
                    selectedUnit!!.id ?: "",
                    currentAmountVal,
                    targetDate.format(formatter),
                    selectedTimeGuid
                )
            }.getOrNull()
        } else null
    }

    val previewKcal = when {
        currentMealPortion != null -> currentMealPortion.kcal.toInt()
        else -> (energyPer100 * scaleFactor).toInt()
    }
    val previewProt = when {
        currentMealPortion != null && currentMealPortion.protein > 0 -> currentMealPortion.protein
        currentMealPortion != null && NutrientParser.parseNutrient(food.energy) > 0 -> {
            val ratio = currentMealPortion.kcal / NutrientParser.parseNutrient(food.energy)
            NutrientParser.parseNutrient(food.protein) * ratio
        }
        currentMealPortion != null -> 0.0
        else -> (proteinPer100 * scaleFactor)
    }
    val previewCarbs = when {
        currentMealPortion != null && currentMealPortion.carbs > 0 -> currentMealPortion.carbs
        currentMealPortion != null && NutrientParser.parseNutrient(food.energy) > 0 -> {
            val ratio = currentMealPortion.kcal / NutrientParser.parseNutrient(food.energy)
            NutrientParser.parseNutrient(food.carbohydrate) * ratio
        }
        currentMealPortion != null -> 0.0
        else -> (carbsPer100 * scaleFactor)
    }
    val previewFat = when {
        currentMealPortion != null && currentMealPortion.fat > 0 -> currentMealPortion.fat
        currentMealPortion != null && NutrientParser.parseNutrient(food.energy) > 0 -> {
            val ratio = currentMealPortion.kcal / NutrientParser.parseNutrient(food.energy)
            NutrientParser.parseNutrient(food.fat) * ratio
        }
        currentMealPortion != null -> 0.0
        else -> (fatPer100 * scaleFactor)
    }
    val previewFiber = when {
        currentMealPortion != null && currentMealPortion.fiber > 0 -> currentMealPortion.fiber
        currentMealPortion != null && NutrientParser.parseNutrient(food.energy) > 0 -> {
            val ratio = currentMealPortion.kcal / NutrientParser.parseNutrient(food.energy)
            NutrientParser.parseNutrient(food.fiber) * ratio
        }
        currentMealPortion != null -> 0.0
        else -> (fiberPer100 * scaleFactor)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = if (isClone) strings.cloneTitle else strings.editTitle,
                    fontSize = 13.sp,
                    color = if (isClone) Color(0xFF81C784) else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            if (isLoadingOptions) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (errorMsg != null) {
                        Text(errorMsg!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }

                    // Amount and Unit Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Amount TextField
                        Column(modifier = Modifier.weight(0.45f)) {
                            Text(
                                text = strings.amountLabel,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                            )
                            OutlinedTextField(
                                value = amountText,
                                onValueChange = { amountText = it },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Unit Dropdown
                        var unitExpanded by remember { mutableStateOf(false) }
                        Column(modifier = Modifier.weight(0.55f)) {
                            Text(
                                text = strings.unitLabel,
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
                                    value = selectedUnit?.title ?: "1 g",
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
                                                if (personalMealForm != null) {
                                                    amountText = formatAmount(personalMealForm!!.suggestedAmount(opt))
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Meal Time Dropdown
                    var mealExpanded by remember { mutableStateOf(false) }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = strings.mealTimeLabel,
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

                    // Date Selection Chips
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "${strings.dateToday.substringBefore(" ")}: ${targetDate.format(formatter)}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                        )
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
                                dialog.setButton(DialogInterface.BUTTON_NEUTRAL, strings.dateToday) { _, _ ->
                                    targetDate = LocalDate.now()
                                }
                                dialog.show()
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.chooseAnotherDate, fontSize = 12.sp)
                        }
                    }

                    // Nutrition Preview Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF262626)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(strings.portionTotal.substringBefore("|").trim(), fontSize = 12.sp, color = Color.LightGray)
                                Text(
                                    text = "$previewKcal ${strings.kcalUnit}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFD54F)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${strings.proteinShort}: ${String.format(Locale.US, "%.1f", previewProt)} g", fontSize = 11.sp, color = Color(0xFFE57373))
                                Text("${strings.fatShort}: ${String.format(Locale.US, "%.1f", previewFat)} g", fontSize = 11.sp, color = Color(0xFFFFD54F))
                                Text("${strings.carbsShort}: ${String.format(Locale.US, "%.1f", previewCarbs)} g", fontSize = 11.sp, color = Color(0xFF64B5F6))
                                if (previewFiber > 0.1) {
                                    Text("${strings.fiberShort}: ${String.format(Locale.US, "%.1f", previewFiber)} g", fontSize = 11.sp, color = Color(0xFFAED581))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalAmount = amountText.replace(",", ".").toDoubleOrNull()
                    if (finalAmount == null || finalAmount <= 0.0) {
                        errorMsg = strings.invalidAmountError
                        return@Button
                    }
                    if (!isClone && food.id.isNullOrBlank()) {
                        errorMsg = strings.idNotFoundError
                        return@Button
                    }
                    if (personalMealForm == null && productGuid.isBlank()) {
                        errorMsg = strings.idNotFoundError
                        return@Button
                    }

                    isSubmitting = true
                    errorMsg = null

                    coroutineScope.launch {
                        if (personalMealForm != null && currentMealPortion != null) {
                            if (isClone) {
                                viewModel.addPersonalMeal(currentMealPortion.request) { success ->
                                    isSubmitting = false
                                    if (success) {
                                        onSuccess()
                                    } else {
                                        errorMsg = strings.cloneError
                                    }
                                }
                            } else {
                                viewModel.editPersonalMealEntry(
                                    oldEntryId = food.id ?: "",
                                    request = currentMealPortion.request
                                ) { result ->
                                    isSubmitting = false
                                    when (result) {
                                        DiaryEditResult.SUCCESS -> onSuccess()
                                        DiaryEditResult.ADD_FAILED -> errorMsg = strings.saveError
                                        DiaryEditResult.DELETE_FAILED -> needsOldEntryRemoval = true
                                    }
                                }
                            }
                            return@launch
                        }

                        val unitGuid = selectedUnit?.id ?: "0000000000000001"
                        val unitMult = selectedUnit?.multiplier ?: 1.0
                        val unitTitle = selectedUnit?.title ?: "1 g"
                        val formattedDate = targetDate.format(formatter)

                        val amountFormatted = if (finalAmount == finalAmount.toInt().toDouble()) {
                            finalAmount.toInt().toString()
                        } else {
                            String.format(Locale.US, "%.2f", finalAmount).trimEnd('0').trimEnd('.')
                        }

                        val request = AddFoodRequest(
                            guid = productGuid,
                            title = title,
                            url = food.url ?: "",
                            date = formattedDate,
                            multiplier = amountFormatted,
                            diaryTimeGuid = selectedTimeGuid,
                            unitGuid = unitGuid,
                            unitOptions = listOf(UnitOption(unitGuid, unitTitle, unitMult))
                        )

                        if (isClone) {
                            viewModel.addFoodEntry(request = request) { success ->
                                isSubmitting = false
                                if (success) {
                                    onSuccess()
                                } else {
                                    errorMsg = strings.cloneError
                                }
                            }
                        } else {
                            viewModel.editFoodEntry(
                                oldEntryId = food.id ?: "",
                                request = request
                            ) { result ->
                                isSubmitting = false
                                when (result) {
                                    DiaryEditResult.SUCCESS -> onSuccess()
                                    DiaryEditResult.ADD_FAILED -> errorMsg = strings.saveError
                                    DiaryEditResult.DELETE_FAILED -> needsOldEntryRemoval = true
                                }
                            }
                        }
                    }
                },
                enabled = !isLoadingOptions && !isSubmitting && amountText.isNotBlank() && (personalMealForm == null || currentMealPortion != null),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isClone) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                    contentColor = if (isClone) Color.White else Color.Black
                )
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = if (isClone) Color.White else Color.Black
                    )
                } else {
                    Text(if (isClone) strings.cloneAction else strings.save, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (isClone) {
                TextButton(onClick = onDismiss) {
                    Text(strings.cancel, color = Color.LightGray)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showDeleteConfirmation = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = strings.deleteAction,
                            tint = Color(0xFFEF5350),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(onClick = onDismiss) {
                        Text(strings.cancel, color = Color.LightGray)
                    }
                }
            }
        }
    )

    if (!isClone && showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(strings.deleteConfirmTitle) },
            text = {
                val displayName = food.title ?: food.name ?: food.nazev ?: strings.unnamedFood
                Text(String.format(strings.deleteSingleConfirmText, displayName))
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))
                ) {
                    Text(strings.deleteAction, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}
