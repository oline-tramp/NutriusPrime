package com.example.kthealthsync.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kthealthsync.DebugLogger
import com.example.kthealthsync.DiaryResponse
import com.example.kthealthsync.Foodstuff
import com.example.kthealthsync.PreferencesManager
import com.example.kthealthsync.data.repository.KalorickeRepository
import com.example.kthealthsync.domain.model.DailySummary
import com.example.kthealthsync.domain.model.NutrientGoals
import com.example.kthealthsync.domain.util.NutrientUtils
import com.example.kthealthsync.sync.HealthConnectManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Success(
        val summary: DailySummary,
        val diaryResponse: DiaryResponse? = null
    ) : DashboardUiState
    data class Error(val message: String) : DashboardUiState
}

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)
    private val repository = KalorickeRepository(prefs = prefs)
    private val healthConnectManager = HealthConnectManager(application)

    private val _selectedDate = MutableStateFlow(ZonedDateTime.now())
    val selectedDate: StateFlow<ZonedDateTime> = _selectedDate.asStateFlow()

    private val _withMeals = MutableStateFlow(false)
    val withMeals: StateFlow<Boolean> = _withMeals.asStateFlow()

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

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
        syncGoals()
    }

    private fun syncGoals() {
        viewModelScope.launch {
            try {
                val currentCookie = prefs.cookieFlow.first()
                if (currentCookie.isNotEmpty()) {
                    repository.fetchNutrientGoalsFromApi()
                }
            } catch (_: Exception) {}
        }
    }

    fun selectDate(date: ZonedDateTime) {
        val now = ZonedDateTime.now()
        if (!date.isAfter(now)) {
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
        loadDailyData()
    }

    fun refresh() {
        viewModelScope.launch {
            repository.fetchNutrientGoalsFromApi(force = true)
        }
        loadDailyData()
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
        val currentState = _uiState.value
        val isDifferentDay = !targetDate.isNullOrBlank() &&
                targetDate != _selectedDate.value.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

        if (currentState is DashboardUiState.Success && currentState.diaryResponse?.data?.times != null) {
            val allTimes = currentState.diaryResponse.data.times
            val movedFoods = allTimes.flatMap { it.foodstuff ?: emptyList() }.filter { targetIds.contains(it.id) }

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
        }

        viewModelScope.launch {
            val result = repository.moveDiaryEntries(targetIds, targetTimeId, targetDate)
            loadDailyData(silent = true)
            onComplete?.invoke(result.isSuccess)
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
            dKcal += NutrientUtils.parseNutrient(f.energy)
            dProt += NutrientUtils.parseNutrient(f.protein)
            dFat += NutrientUtils.parseNutrient(f.fat)
            dCarb += NutrientUtils.parseNutrient(f.carbohydrate)
            dSugar += NutrientUtils.parseNutrient(f.sugar)
            dSalt += NutrientUtils.parseNutrient(f.salt)
            dWater += NutrientUtils.parseLiquidVolumeMl(f)
            dFiber += NutrientUtils.parseNutrient(f.fiber)
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

    private fun loadDailyData(silent: Boolean = false) {
        viewModelScope.launch {
            val currentCookie = prefs.cookieFlow.stateIn(this).value
            if (currentCookie.isEmpty()) {
                _uiState.value = DashboardUiState.Error("Please log in in Settings first.")
                return@launch
            }

            // Silently check and update goals in background if TTL expired
            launch {
                try {
                    repository.fetchNutrientGoalsFromApi()
                } catch (_: Exception) {}
            }

            if (!silent) {
                _uiState.value = DashboardUiState.Loading
            }
            val date = _selectedDate.value
            val dateStr = date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

            try {
                DebugLogger.addActionLog("Fetching data from Kalorické Tabulky for $dateStr...")
                val (summary, diaryResponse) = repository.getEnrichedDailySummary(dateStr, _withMeals.value)
                _uiState.value = DashboardUiState.Success(summary, diaryResponse)

                // Trigger auto-sync to Health Connect if past day
                val now = ZonedDateTime.now()
                val isToday = date.toLocalDate() == now.toLocalDate()
                if (!isToday && (summary.kcal > 0 || summary.protein > 0 || summary.water > 0)) {
                    if (healthConnectManager.isAvailable() && healthConnectManager.hasAllPermissions()) {
                        healthConnectManager.syncDailySummary(summary, date)
                    } else {
                        DebugLogger.addActionLog("Health Connect permissions missing or SDK unavailable for $dateStr auto-sync")
                    }
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Unknown error"
                if (errorMsg.contains("302") || errorMsg.contains("401")) {
                    prefs.saveCookie("")
                    _uiState.value = DashboardUiState.Error("Session expired. Please log in again in Settings.")
                } else {
                    _uiState.value = DashboardUiState.Error("Error: $errorMsg")
                }
                DebugLogger.addActionLog("Error loading $dateStr: $errorMsg")
            }
        }
    }
}
