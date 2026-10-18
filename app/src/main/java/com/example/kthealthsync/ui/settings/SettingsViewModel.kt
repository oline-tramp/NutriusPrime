package com.example.kthealthsync.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.kthealthsync.DebugLogger
import com.example.kthealthsync.PreferencesManager
import com.example.kthealthsync.SyncWorker
import com.example.kthealthsync.domain.model.NutrientGoals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)
    private val repository = com.example.kthealthsync.data.repository.KalorickeRepository(prefs = prefs)
    private val workManager = WorkManager.getInstance(application)

    val cookie: StateFlow<String> = prefs.cookieFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    val syncHour: StateFlow<Int> = prefs.syncHourFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 6
    )

    val syncMinute: StateFlow<Int> = prefs.syncMinuteFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val autoSyncEnabled: StateFlow<Boolean> = prefs.autoSyncEnabledFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val dailyNotificationEnabled: StateFlow<Boolean> = prefs.dailyNotificationEnabledFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    fun toggleDailyNotification(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setDailyNotificationEnabled(enabled)
            if (!enabled) {
                com.example.kthealthsync.domain.util.DailyNotificationHelper.cancelNotification(getApplication())
            }
        }
    }

    val nutrientGoals: StateFlow<NutrientGoals> = prefs.nutrientGoalsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = NutrientGoals()
    )

    val appLanguage: StateFlow<com.example.kthealthsync.domain.model.AppLanguage> = prefs.appLanguageFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = com.example.kthealthsync.domain.model.AppLanguage.AUTO
    )

    fun updateAppLanguage(language: com.example.kthealthsync.domain.model.AppLanguage) {
        viewModelScope.launch {
            prefs.saveAppLanguage(language)
        }
    }

    fun toggleAutoSync(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setAutoSync(enabled)
            val h = syncHour.value
            val m = syncMinute.value
            scheduleWorker(enabled, h, m)
        }
    }

    fun updateSyncTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            prefs.saveSyncTime(hour, minute)
            scheduleWorker(autoSyncEnabled.value, hour, minute)
        }
    }

    private val _isSyncingGoals = MutableStateFlow(false)
    val isSyncingGoals: StateFlow<Boolean> = _isSyncingGoals.asStateFlow()

    fun updateNutrientGoals(goals: NutrientGoals) {
        viewModelScope.launch {
            prefs.saveNutrientGoals(goals)
            DebugLogger.addActionLog("Nutrient goals updated: ${goals.kcal.toInt()} kcal, ${goals.protein.toInt()}g protein")
        }
    }

    fun syncGoalsFromApi(onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _isSyncingGoals.value = true
            try {
                val goals = repository.fetchNutrientGoalsFromApi()
                onComplete(goals != null)
            } finally {
                _isSyncingGoals.value = false
            }
        }
    }

    private fun scheduleWorker(enabled: Boolean, hour: Int, minute: Int) {
        if (!enabled) {
            workManager.cancelUniqueWork("KTSyncWorker")
            DebugLogger.addActionLog("Auto-sync disabled via Settings")
            return
        }

        DebugLogger.addActionLog("Auto-sync scheduled for $hour:$minute")
        val now = ZonedDateTime.now()
        var targetTime = now.with(LocalTime.of(hour, minute, 0, 0))
        if (!now.isBefore(targetTime)) targetTime = targetTime.plusDays(1)
        val initialDelay = Duration.between(now, targetTime).toMillis()

        val syncWork = PeriodicWorkRequestBuilder<SyncWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork("KTSyncWorker", ExistingPeriodicWorkPolicy.UPDATE, syncWork)
    }
}
