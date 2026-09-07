package com.example.kthealthsync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.kthealthsync.data.repository.KalorickeRepository
import com.example.kthealthsync.sync.HealthConnectManager
import kotlinx.coroutines.flow.first
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class SyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = PreferencesManager(context)
        val cookie = prefs.cookieFlow.first()
        if (cookie.isEmpty()) {
            DebugLogger.addActionLog("[SyncWorker] No cookie found. Aborting.")
            return Result.failure()
        }

        val hcManager = HealthConnectManager(context)
        if (!hcManager.isAvailable()) {
            DebugLogger.addActionLog("[SyncWorker] Health Connect is not available on this device. Aborting.")
            return Result.failure()
        }

        if (!hcManager.hasAllPermissions()) {
            DebugLogger.addActionLog("[SyncWorker] Missing Health Connect permissions. Aborting.")
            return Result.failure()
        }

        return try {
            DebugLogger.addActionLog("[SyncWorker] Background sync started.")
            val repository = KalorickeRepository(prefs = prefs)

            val yesterday = ZonedDateTime.now().minusDays(1)
            val dateStr = yesterday.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

            val (summary, _) = repository.getEnrichedDailySummary(dateStr, withMeals = false)

            val hasData = summary.kcal > 0 || summary.protein > 0 || summary.water > 0
            if (!hasData) {
                DebugLogger.addActionLog("[SyncWorker] No data found for $dateStr. Success.")
                return Result.success()
            }

            val success = hcManager.syncDailySummary(summary, yesterday)
            if (success) {
                DebugLogger.addActionLog("[SyncWorker] Sync complete! Uploaded ${summary.kcal.toInt()} kcal.")
                Result.success()
            } else {
                DebugLogger.addActionLog("[SyncWorker] Health Connect sync returned false.")
                Result.retry()
            }
        } catch (e: Exception) {
            DebugLogger.addActionLog("[SyncWorker] Error during sync: ${e.message}")
            e.printStackTrace()
            Result.retry()
        }
    }
}
