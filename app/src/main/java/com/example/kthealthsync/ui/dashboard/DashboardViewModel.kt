package com.example.kthealthsync.ui.dashboard

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kthealthsync.AddCustomFoodRequest
import com.example.kthealthsync.AddFoodRequest
import com.example.kthealthsync.DebugLogger
import com.example.kthealthsync.DiaryResponse
import com.example.kthealthsync.FoodFormResponse
import com.example.kthealthsync.Foodstuff
import com.example.kthealthsync.PreferencesManager
import com.example.kthealthsync.UnitOption
import com.example.kthealthsync.data.repository.KalorickeRepository
import com.example.kthealthsync.domain.model.DailySummary
import com.example.kthealthsync.domain.model.DiaryEditResult
import com.example.kthealthsync.domain.model.NutrientGoals
import com.example.kthealthsync.domain.util.HydrationCalculator
import com.example.kthealthsync.domain.util.NutrientParser
import com.example.kthealthsync.sync.DailySummaryRecords
import com.example.kthealthsync.sync.HealthConnectManager
import com.example.kthealthsync.ui.LatestRequest
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Success(
        val summary: DailySummary,
        val diaryResponse: DiaryResponse? = null
    ) : DashboardUiState
    data class Error(val message: String) : DashboardUiState
}

private val COUNT_PORTION_REGEX = "([\\d.]+)\\s*(?:x\\s*)?(?:porce|porc|portion)".toRegex(RegexOption.IGNORE_CASE)
private val PCT_REGEX = "([\\d.]+)\\s*%".toRegex()

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)
    private val repository = KalorickeRepository(prefs = prefs)
    private val healthConnectManager = HealthConnectManager(application)
    private val dailyRequest = LatestRequest()

    private val _selectedDate = MutableStateFlow(ZonedDateTime.now())
    val selectedDate: StateFlow<ZonedDateTime> = _selectedDate.asStateFlow()

    private val _withMeals = MutableStateFlow(false)
    val withMeals: StateFlow<Boolean> = _withMeals.asStateFlow()

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val nutrientGoals: StateFlow<NutrientGoals> = prefs.nutrientGoalsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = NutrientGoals()
    )

    val cookie: StateFlow<String> = prefs.cookieFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    init {
        loadDailyData()
        viewModelScope.launch {
            com.example.kthealthsync.data.repository.DiaryUpdateEvents.events.collect {
                refresh(silent = true)
            }
        }
    }

    private fun syncGoals() {
        viewModelScope.launch {
            try {
                val currentCookie = prefs.cookieFlow.first()
                if (currentCookie.isNotEmpty()) {
                    repository.fetchNutrientGoalsFromApi()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun selectDate(date: ZonedDateTime) {
        val today = java.time.LocalDate.now(date.zone)
        if (!date.toLocalDate().isAfter(today)) {
            _selectedDate.value = date
            loadDailyData()
        }
    }

    fun nextDay() {
        val next = _selectedDate.value.plusDays(1)
        selectDate(next)
    }

    fun prevDay() {
        val prev = _selectedDate.value.minusDays(1)
        selectDate(prev)
    }

    fun toggleWithMeals(withMeals: Boolean) {
        _withMeals.value = withMeals
        loadDailyData(silent = true)
    }

    fun refresh(silent: Boolean = false) {
        _isRefreshing.value = true
        viewModelScope.launch {
            repository.fetchNutrientGoalsFromApi(force = true)
        }
        loadDailyData(silent = silent)
    }

    fun addEntryOptimistic(
        title: String,
        kcal: Double,
        protein: Double,
        carbs: Double,
        fat: Double,
        fiber: Double,
        sugar: Double,
        salt: Double,
        unitStr: String,
        mealTimeId: String?
    ) {
        val currentState = _uiState.value
        if (currentState is DashboardUiState.Success && currentState.diaryResponse?.data?.times != null) {
            val targetMealId = mealTimeId ?: "1"
            val tempId = "temp_${System.currentTimeMillis()}"
            val newFood = Foodstuff(
                id = tempId,
                title = title,
                name = title,
                energy = String.format(Locale.US, "%.1f", kcal),
                protein = String.format(Locale.US, "%.1f", protein),
                carbohydrate = String.format(Locale.US, "%.1f", carbs),
                fat = String.format(Locale.US, "%.1f", fat),
                fiber = String.format(Locale.US, "%.1f", fiber),
                sugar = String.format(Locale.US, "%.1f", sugar),
                salt = String.format(Locale.US, "%.1f", salt),
                unit = unitStr
            )

            val updatedTimes = currentState.diaryResponse.data.times.map { mealTime ->
                if (mealTime.id == targetMealId) {
                    val existing = mealTime.foodstuff ?: emptyList()
                    mealTime.copy(foodstuff = existing + newFood)
                } else {
                    mealTime
                }
            }

            val updatedDiary = currentState.diaryResponse.copy(
                data = currentState.diaryResponse.data.copy(times = updatedTimes)
            )

            val currentSummary = currentState.summary
            val updatedSummary = currentSummary.copy(
                kcal = currentSummary.kcal + kcal,
                protein = currentSummary.protein + protein,
                carb = currentSummary.carb + carbs,
                fat = currentSummary.fat + fat,
                fiber = currentSummary.fiber + fiber,
                sugar = currentSummary.sugar + sugar,
                salt = currentSummary.salt + salt
            )

            _uiState.value = DashboardUiState.Success(updatedSummary, updatedDiary)
            updateNotificationIfToday(updatedSummary)
            Log.d("NutriusAdd", "Optimistic add applied for $title ($kcal kcal to meal $targetMealId)")
            DebugLogger.addActionLog("Optimistic add: $title ($kcal kcal to meal $targetMealId)")
        }
    }

    fun deleteEntries(ids: Collection<String>, onComplete: ((Boolean) -> Unit)? = null) {
        val targetIds = ids.toSet()
        // Optimistic UI update: instantly update local numbers and food list without full page reload
        val currentState = _uiState.value
        if (currentState is DashboardUiState.Success && currentState.diaryResponse?.data?.times != null) {
            val allTimes = currentState.diaryResponse.data.times
            val removedFoods = allTimes.flatMap { it.foodstuff ?: emptyList() }.filter { targetIds.contains(it.id) }
            val updatedTimes = allTimes.map { mealTime ->
                val remaining = mealTime.foodstuff?.filterNot { targetIds.contains(it.id) }
                mealTime.copy(foodstuff = remaining)
            }
            val updatedDiary = currentState.diaryResponse.copy(
                data = currentState.diaryResponse.data.copy(times = updatedTimes)
            )
            val updatedSummary = recalculateOptimisticSummary(currentState.summary, removedFoods)
            _uiState.value = DashboardUiState.Success(updatedSummary, updatedDiary)
            updateNotificationIfToday(updatedSummary)
        }

        viewModelScope.launch {
            val result = repository.deleteDiaryEntries(targetIds)
            loadDailyData(silent = true)
            onComplete?.invoke(result.isSuccess)
        }
    }

    fun moveEntries(
        ids: Collection<String>,
        targetTimeId: String,
        targetDate: String? = null,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val targetIds = ids.toSet()
        Log.d("NutriusDrag", "moveEntries called: targetIds=$targetIds, targetTimeId=$targetTimeId, targetDate=$targetDate")
        DebugLogger.addActionLog("moveEntries: $targetIds to meal $targetTimeId, date=$targetDate")

        val currentState = _uiState.value
        val isDifferentDay = !targetDate.isNullOrBlank() &&
                targetDate != _selectedDate.value.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

        if (currentState is DashboardUiState.Success && currentState.diaryResponse?.data?.times != null) {
            val allTimes = currentState.diaryResponse.data.times
            val movedFoods = allTimes.flatMap { it.foodstuff ?: emptyList() }.filter { targetIds.contains(it.id) }
            Log.d("NutriusDrag", "Found ${movedFoods.size} moved foods for optimistic update")

            val updatedTimes = if (isDifferentDay) {
                allTimes.map { mealTime ->
                    val remaining = mealTime.foodstuff?.filterNot { targetIds.contains(it.id) }
                    mealTime.copy(foodstuff = remaining)
                }
            } else {
                allTimes.map { mealTime ->
                    val remaining = mealTime.foodstuff?.filterNot { targetIds.contains(it.id) } ?: emptyList()
                    if (mealTime.id == targetTimeId) {
                        mealTime.copy(foodstuff = remaining + movedFoods)
                    } else {
                        mealTime.copy(foodstuff = remaining)
                    }
                }
            }

            val updatedDiary = currentState.diaryResponse.copy(
                data = currentState.diaryResponse.data.copy(times = updatedTimes)
            )
            val updatedSummary = if (isDifferentDay) {
                recalculateOptimisticSummary(currentState.summary, movedFoods)
            } else {
                currentState.summary
            }
            _uiState.value = DashboardUiState.Success(updatedSummary, updatedDiary)
            updateNotificationIfToday(updatedSummary)
            Log.d("NutriusDrag", "Optimistic UI state applied successfully")
        } else {
            Log.w("NutriusDrag", "Cannot apply optimistic update: currentState is not Success or times is null")
        }

        viewModelScope.launch {
            try {
                val result = repository.moveDiaryEntries(targetIds, targetTimeId, targetDate)
                Log.d("NutriusDrag", "repository.moveDiaryEntries result: isSuccess=${result.isSuccess}")
                loadDailyData(silent = true)
                onComplete?.invoke(result.isSuccess)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("NutriusDrag", "repository.moveDiaryEntries exception: ${e.message}", e)
                DebugLogger.addActionLog("moveDiaryEntries error: ${e.message}")
                onComplete?.invoke(false)
            }
        }
    }

    suspend fun getFoodForm(guid: String): FoodFormResponse {
        return repository.getFoodForm(guid)
    }

    suspend fun getFoodMacros(itemUrl: String, formUrl: String?): Map<String, Double> {
        return repository.getFoodMacrosFromWeb(itemUrl, formUrl)
    }

    fun addFoodEntry(
        request: AddFoodRequest,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        viewModelScope.launch {
            try {
                val addResp = repository.addFoodstuff(request)
                if (addResp.code == 0 || addResp.code == null) {
                    loadDailyData(silent = true)
                    onComplete?.invoke(true)
                } else {
                    DebugLogger.addActionLog("addFoodEntry failed to add: ${addResp.message}")
                    onComplete?.invoke(false)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("NutriusPrime", "addFoodEntry error: ${e.message}", e)
                DebugLogger.addActionLog("addFoodEntry error: ${e.message}")
                onComplete?.invoke(false)
            }
        }
    }

    fun applyOptimisticFoodEdit(
        oldEntryId: String,
        targetTimeId: String,
        targetDateStr: String?,
        updatedFood: Foodstuff
    ) {
        val currentState = _uiState.value
        if (currentState is DashboardUiState.Success && currentState.diaryResponse?.data?.times != null) {
            val allTimes = currentState.diaryResponse.data.times
            val oldFood = allTimes.flatMap { it.foodstuff ?: emptyList() }.firstOrNull { it.id == oldEntryId }
            if (oldFood == null) return

            val isDifferentDay = !targetDateStr.isNullOrBlank() &&
                    targetDateStr != _selectedDate.value.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

            val updatedTimes = if (isDifferentDay) {
                allTimes.map { mealTime ->
                    val remaining = mealTime.foodstuff?.filterNot { it.id == oldEntryId }
                    mealTime.copy(foodstuff = remaining)
                }
            } else {
                allTimes.map { mealTime ->
                    val hasOld = mealTime.foodstuff?.any { it.id == oldEntryId } == true
                    if (mealTime.id == targetTimeId) {
                        if (hasOld) {
                            val newFoods = mealTime.foodstuff?.map {
                                if (it.id == oldEntryId) updatedFood else it
                            }
                            mealTime.copy(foodstuff = newFoods)
                        } else {
                            val remaining = mealTime.foodstuff?.filterNot { it.id == oldEntryId } ?: emptyList()
                            mealTime.copy(foodstuff = remaining + updatedFood)
                        }
                    } else {
                        val remaining = mealTime.foodstuff?.filterNot { it.id == oldEntryId }
                        mealTime.copy(foodstuff = remaining)
                    }
                }
            }

            val diffSummary = if (isDifferentDay) {
                recalculateOptimisticSummary(currentState.summary, listOf(oldFood))
            } else {
                val oldKcal = NutrientParser.parseNutrient(oldFood.energy)
                val newKcal = NutrientParser.parseNutrient(updatedFood.energy)
                val oldProt = NutrientParser.parseNutrient(oldFood.protein)
                val newProt = NutrientParser.parseNutrient(updatedFood.protein)
                val oldFat = NutrientParser.parseNutrient(oldFood.fat)
                val newFat = NutrientParser.parseNutrient(updatedFood.fat)
                val oldCarb = NutrientParser.parseNutrient(oldFood.carbohydrate)
                val newCarb = NutrientParser.parseNutrient(updatedFood.carbohydrate)
                val oldSugar = NutrientParser.parseNutrient(oldFood.sugar)
                val newSugar = NutrientParser.parseNutrient(updatedFood.sugar)
                val oldSalt = NutrientParser.parseNutrient(oldFood.salt)
                val newSalt = NutrientParser.parseNutrient(updatedFood.salt)
                val oldFiber = NutrientParser.parseNutrient(oldFood.fiber)
                val newFiber = NutrientParser.parseNutrient(updatedFood.fiber)
                val oldWater = HydrationCalculator.parseLiquidVolumeMl(oldFood, repository.getFoodCategorySync(oldFood))
                val newWater = HydrationCalculator.parseLiquidVolumeMl(updatedFood, repository.getFoodCategorySync(updatedFood))

                currentState.summary.copy(
                    kcal = (currentState.summary.kcal - oldKcal + newKcal).coerceAtLeast(0.0),
                    protein = (currentState.summary.protein - oldProt + newProt).coerceAtLeast(0.0),
                    fat = (currentState.summary.fat - oldFat + newFat).coerceAtLeast(0.0),
                    carb = (currentState.summary.carb - oldCarb + newCarb).coerceAtLeast(0.0),
                    sugar = (currentState.summary.sugar - oldSugar + newSugar).coerceAtLeast(0.0),
                    salt = (currentState.summary.salt - oldSalt + newSalt).coerceAtLeast(0.0),
                    fiber = (currentState.summary.fiber - oldFiber + newFiber).coerceAtLeast(0.0),
                    water = (currentState.summary.water - oldWater + newWater).coerceAtLeast(0.0)
                )
            }

            val updatedDiary = currentState.diaryResponse.copy(
                data = currentState.diaryResponse.data.copy(times = updatedTimes)
            )
            _uiState.value = DashboardUiState.Success(diffSummary, updatedDiary)
            updateNotificationIfToday(diffSummary)
        }
    }

    fun editFoodEntry(
        oldEntryId: String,
        request: AddFoodRequest,
        currentMealTimeId: String? = null,
        currentDate: String? = null,
        updatedFood: Foodstuff? = null,
        onComplete: (DiaryEditResult) -> Unit
    ) {
        if (updatedFood != null) {
            applyOptimisticFoodEdit(
                oldEntryId = oldEntryId,
                targetTimeId = request.diaryTimeGuid,
                targetDateStr = request.date,
                updatedFood = updatedFood
            )
        }
        viewModelScope.launch {
            val result = try {
                repository.editFoodEntry(oldEntryId, request, currentMealTimeId, currentDate)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DebugLogger.addActionLog("editFoodEntry error: ${e.message}")
                DiaryEditResult.ADD_FAILED
            }
            // Refresh after partial success too, to show the actual server state.
            loadDailyData(silent = true)
            onComplete(result)
        }
    }

    suspend fun getPersonalMealForm(guid: String): com.example.kthealthsync.domain.model.PersonalMealForm =
        repository.getPersonalMealForm(guid)

    fun addPersonalMeal(
        request: com.google.gson.JsonObject,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        viewModelScope.launch {
            try {
                val resp = repository.addPersonalMeal(request)
                if (resp.code == 0 || resp.code == null) {
                    loadDailyData(silent = true)
                    onComplete?.invoke(true)
                } else {
                    DebugLogger.addActionLog("addPersonalMeal failed: ${resp.message}")
                    onComplete?.invoke(false)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                DebugLogger.addActionLog("addPersonalMeal error: ${e.message}")
                onComplete?.invoke(false)
            }
        }
    }

    fun editPersonalMealEntry(
        oldEntryId: String,
        request: com.google.gson.JsonObject,
        targetTimeId: String? = null,
        targetDateStr: String? = null,
        updatedFood: Foodstuff? = null,
        onComplete: (DiaryEditResult) -> Unit
    ) {
        if (updatedFood != null && targetTimeId != null) {
            applyOptimisticFoodEdit(
                oldEntryId = oldEntryId,
                targetTimeId = targetTimeId,
                targetDateStr = targetDateStr,
                updatedFood = updatedFood
            )
        }
        viewModelScope.launch {
            val result = try {
                repository.editPersonalMealEntry(oldEntryId, request)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DebugLogger.addActionLog("editPersonalMealEntry error: ${e.message}")
                DiaryEditResult.ADD_FAILED
            }
            loadDailyData(silent = true)
            onComplete(result)
        }
    }

    suspend fun isPersonalMeal(food: Foodstuff): Boolean {
        if (food.isRecipe == true || !food.components.isNullOrEmpty() || !food.guidRecipe.isNullOrBlank() ||
            food.type == "meal" || food.guidType == "meal") return true
        val cookie = repository.getCookie()
        if (cookie.isEmpty()) return false
        val mealMap = repository.getMealMap(cookie)
        val name = food.title ?: food.name ?: food.nazev ?: ""
        val guid = food.guid ?: food.guidRecipe ?: ""
        return mealMap.containsKey(name) || (guid.isNotBlank() && mealMap.containsValue(guid))
    }

    suspend fun getPersonalMealGuid(food: Foodstuff): String? {
        val candidate = food.guidRecipe?.takeIf { it.isNotBlank() }
            ?: (if (food.guidType != "meal") food.guidType else null)?.takeIf { it.isNotBlank() }
            ?: (if (food.guid != "meal") food.guid else null)?.takeIf { it.isNotBlank() }
        val cookie = repository.getCookie()
        if (cookie.isEmpty()) return candidate
        val mealMap = repository.getMealMap(cookie)
        val name = food.title ?: food.name ?: food.nazev ?: ""
        return if (candidate != null && mealMap.containsValue(candidate)) candidate
            else mealMap[name] ?: candidate
    }

    fun isCustomFood(food: Foodstuff): Boolean {
        if (food.isRecipe == true || !food.components.isNullOrEmpty() || !food.guidRecipe.isNullOrBlank() ||
            food.type == "meal" || food.guidType == "meal") return false
        val guid = food.guidRecipe?.takeIf { it.isNotBlank() }
            ?: (if (food.guidType != "meal") food.guidType else null)?.takeIf { it.isNotBlank() }
            ?: (if (food.guid != "meal") food.guid else null)?.takeIf { it.isNotBlank() }
        return guid == null || guid == "0" || (guid.isBlank() && food.url.isNullOrBlank())
    }

    fun editCustomFoodEntry(
        oldEntryId: String,
        request: AddCustomFoodRequest,
        updatedFood: Foodstuff? = null,
        onResult: (DiaryEditResult) -> Unit
    ) {
        if (updatedFood != null) {
            applyOptimisticFoodEdit(
                oldEntryId = oldEntryId,
                targetTimeId = request.diaryTimeGuid,
                targetDateStr = request.date,
                updatedFood = updatedFood
            )
        }
        viewModelScope.launch {
            val result = repository.editCustomFoodEntry(oldEntryId, request)
            loadDailyData(silent = true)
            onResult(result)
        }
    }

    fun addCustomFood(request: AddCustomFoodRequest, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val resp = repository.addCustomFood(request)
            val success = resp.code == 0
            loadDailyData(silent = true)
            onResult(success)
        }
    }

    fun cloneEntries(
        foods: List<Foodstuff>,
        targetTimeId: String,
        targetDate: String? = null,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val dateToUse = targetDate ?: _selectedDate.value.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

        // Optimistic UI update for snappy feedback
        val currentState = _uiState.value
        val isSameDay = targetDate.isNullOrBlank() ||
                targetDate == _selectedDate.value.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

        if (isSameDay && currentState is DashboardUiState.Success && currentState.diaryResponse?.data?.times != null) {
            val allTimes = currentState.diaryResponse.data.times
            val clonedFoods = foods.map { food ->
                food.copy(id = "temp_clone_${System.currentTimeMillis()}_${food.id ?: food.guid ?: ""}")
            }
            val updatedTimes = allTimes.map { mealTime ->
                val currentList = mealTime.foodstuff ?: emptyList()
                if (mealTime.id == targetTimeId) {
                    mealTime.copy(foodstuff = currentList + clonedFoods)
                } else {
                    mealTime
                }
            }
            val updatedDiary = currentState.diaryResponse.copy(
                data = currentState.diaryResponse.data.copy(times = updatedTimes)
            )
            _uiState.value = DashboardUiState.Success(currentState.summary, updatedDiary)
            updateNotificationIfToday(currentState.summary)
        }

        viewModelScope.launch {
            try {
                var allSuccess = true
                for (food in foods) {
                    if (isPersonalMeal(food)) {
                        val mealGuid = getPersonalMealGuid(food)
                        if (!mealGuid.isNullOrBlank()) {
                            try {
                                val form = getPersonalMealForm(mealGuid)
                                val matchedOpt = if (!food.unit.isNullOrBlank()) {
                                    val unitLower = food.unit.lowercase()
                                    when {
                                        unitLower.contains("%") -> form.units.find { it.multiplier == -1.0 }
                                        unitLower.contains("porce") || unitLower.contains("porc") || unitLower.contains("portion") ->
                                            form.units.find { it.multiplier == -2.0 }
                                        unitLower.contains("g") -> form.units.find { it.multiplier == 1.0 }
                                            ?: form.units.find { it.multiplier != null && it.multiplier > 0 }
                                        else -> null
                                    }
                                } else null

                                val selectedUnit = matchedOpt ?: form.defaultUnit
                                val portionGrams = NutrientParser.parsePortionGrams(food.unit)
                                val amount = when {
                                    selectedUnit.multiplier == 1.0 && portionGrams > 0 -> portionGrams
                                    selectedUnit.multiplier == -2.0 -> {
                                        COUNT_PORTION_REGEX.find(food.unit ?: "")?.groupValues?.get(1)?.toDoubleOrNull() ?: 1.0
                                    }
                                    selectedUnit.multiplier == -1.0 -> {
                                        PCT_REGEX.find(food.unit ?: "")?.groupValues?.get(1)?.toDoubleOrNull() ?: 100.0
                                    }
                                    portionGrams > 0 && selectedUnit.multiplier != null && selectedUnit.multiplier > 0 ->
                                        portionGrams / selectedUnit.multiplier
                                    else -> form.defaultAmount
                                }

                                val portion = form.portion(
                                    selectedUnit.id ?: "",
                                    amount,
                                    dateToUse,
                                    targetTimeId
                                )
                                val resp = repository.addPersonalMeal(portion.request)
                                if (resp.code != 0 && resp.code != null) {
                                    allSuccess = false
                                }
                                continue
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                Log.w("NutriusPrime", "Failed to clone as personal meal, trying standard food: ${e.message}")
                            }
                        }
                    } else if (isCustomFood(food)) {
                        val request = AddCustomFoodRequest(
                            guid = "0",
                            title = food.title ?: food.name ?: food.nazev ?: "",
                            diaryTimeGuid = targetTimeId,
                            date = dateToUse,
                            energy = food.energy ?: "0",
                            energyUnit = "kcal",
                            detailValues = true,
                            protein = food.protein ?: "0",
                            carbohydrate = food.carbohydrate ?: "0",
                            fat = food.fat ?: "0",
                            fiber = food.fiber,
                            sugar = food.sugar,
                            salt = food.salt,
                            water = food.water
                        )
                        val resp = repository.addCustomFood(request)
                        if (resp.code != 0 && resp.code != null) {
                            allSuccess = false
                        }
                        continue
                    }

                    val guid = (if (food.guidType != "meal") food.guidType else null)?.takeIf { it.isNotBlank() }
                        ?: (if (food.guid != "meal") food.guid else null)?.takeIf { it.isNotBlank() }
                        ?: food.guidRecipe?.takeIf { it.isNotBlank() }
                    if (guid.isNullOrBlank()) {
                        allSuccess = false
                        continue
                    }

                    val portion = NutrientParser.parsePortionGrams(food.unit).coerceAtLeast(1.0)
                    val multStr = if (portion == portion.toInt().toDouble()) portion.toInt().toString() else String.format(Locale.US, "%.1f", portion)

                    val request = AddFoodRequest(
                        guid = guid,
                        title = food.title ?: food.name ?: food.nazev ?: "",
                        url = food.url ?: "",
                        date = dateToUse,
                        multiplier = multStr,
                        diaryTimeGuid = targetTimeId,
                        unitGuid = "0000000000000001",
                        unitOptions = listOf(UnitOption("0000000000000001", "1 g", 1.0))
                    )
                    val resp = repository.addFoodstuff(request)
                    if (resp.code != 0 && resp.code != null) {
                        allSuccess = false
                    }
                }
                loadDailyData(silent = true)
                onComplete?.invoke(allSuccess)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("NutriusPrime", "cloneEntries error: ${e.message}", e)
                DebugLogger.addActionLog("cloneEntries error: ${e.message}")
                loadDailyData(silent = true)
                onComplete?.invoke(false)
            }
        }
    }

    private fun recalculateOptimisticSummary(
        currentSummary: DailySummary,
        removedFoods: List<Foodstuff>
    ): DailySummary {
        var dKcal = 0.0
        var dProt = 0.0
        var dFat = 0.0
        var dCarb = 0.0
        var dSugar = 0.0
        var dSalt = 0.0
        var dWater = 0.0
        var dFiber = 0.0

        for (f in removedFoods) {
            dKcal += NutrientParser.parseNutrient(f.energy)
            dProt += NutrientParser.parseNutrient(f.protein)
            dFat += NutrientParser.parseNutrient(f.fat)
            dCarb += NutrientParser.parseNutrient(f.carbohydrate)
            dSugar += NutrientParser.parseNutrient(f.sugar)
            dSalt += NutrientParser.parseNutrient(f.salt)
            dWater += HydrationCalculator.parseLiquidVolumeMl(f, repository.getFoodCategorySync(f))
            dFiber += NutrientParser.parseNutrient(f.fiber)
        }

        return currentSummary.copy(
            kcal = (currentSummary.kcal - dKcal).coerceAtLeast(0.0),
            protein = (currentSummary.protein - dProt).coerceAtLeast(0.0),
            fat = (currentSummary.fat - dFat).coerceAtLeast(0.0),
            carb = (currentSummary.carb - dCarb).coerceAtLeast(0.0),
            sugar = (currentSummary.sugar - dSugar).coerceAtLeast(0.0),
            salt = (currentSummary.salt - dSalt).coerceAtLeast(0.0),
            water = (currentSummary.water - dWater).coerceAtLeast(0.0),
            fiber = (currentSummary.fiber - dFiber).coerceAtLeast(0.0)
        )
    }

    private fun updateNotificationIfToday(summary: DailySummary) {
        val isToday = _selectedDate.value.toLocalDate() == ZonedDateTime.now().toLocalDate()
        if (isToday) {
            viewModelScope.launch {
                try {
                    val notifEnabled = prefs.dailyNotificationEnabledFlow.first()
                    if (notifEnabled) {
                        val goals = prefs.nutrientGoalsFlow.first()
                        val lang = prefs.resolvedLanguageFlow.first()
                        com.example.kthealthsync.domain.util.DailyNotificationHelper.updateNotification(
                            getApplication(),
                            summary,
                            goals,
                            lang
                        )
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("NutriusNotif", "Failed to update notification: ${e.message}")
                }
            }
        }
    }

    private fun loadDailyData(silent: Boolean = false) {
        syncGoals()
        val date = _selectedDate.value
        val withMeals = _withMeals.value
        val dateStr = date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        val version = DailySummaryRecords.nextVersion()
        if (!silent) _uiState.value = DashboardUiState.Loading
        dailyRequest.launch(
            scope = viewModelScope,
            load = {
                check(prefs.cookieFlow.first().isNotEmpty()) { "Please log in in Settings first." }
                DebugLogger.addActionLog("Fetching data from Kalorické Tabulky for $dateStr...")
                repository.getEnrichedDailySummary(dateStr, withMeals)
            },
            onSuccess = { (summary, diaryResponse) ->
                _uiState.value = DashboardUiState.Success(summary, diaryResponse)
                updateNotificationIfToday(summary)
                if (date.toLocalDate().isBefore(ZonedDateTime.now().toLocalDate())) {
                    try {
                        healthConnectManager.syncDailySummary(summary, date, version)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // A sync failure must not discard a successfully loaded diary.
                        DebugLogger.addActionLog("Health Connect sync failed for $dateStr: ${e.message}")
                    }
                }
            },
            onError = { e ->
                val code = (e as? retrofit2.HttpException)?.code()
                val message = if (code == 302 || code == 401) {
                    "Session expired. Please log in again in Settings."
                } else "Error: ${e.message ?: "Unknown error"}"
                if (!silent || _uiState.value !is DashboardUiState.Success) {
                    _uiState.value = DashboardUiState.Error(message)
                }
                DebugLogger.addActionLog("Error loading $dateStr: ${e.message}")
            },
            onFinally = {
                _isRefreshing.value = false
            }
        )
    }
}
