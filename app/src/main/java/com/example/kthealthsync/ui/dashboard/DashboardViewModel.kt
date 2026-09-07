package com.example.kthealthsync.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kthealthsync.DebugLogger
import com.example.kthealthsync.DiaryResponse
import com.example.kthealthsync.PreferencesManager
import com.example.kthealthsync.data.repository.KalorickeRepository
import com.example.kthealthsync.domain.model.DailySummary
import com.example.kthealthsync.domain.model.NutrientGoals
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

    private fun loadDailyData() {
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

            _uiState.value = DashboardUiState.Loading
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
