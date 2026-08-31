package com.example.kthealthsync

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Volume
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
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

        try {
            DebugLogger.addActionLog("[SyncWorker] Background sync started.")
            val hcClient = HealthConnectClient.getOrCreate(context)
            val permissions = setOf(
                HealthPermission.getWritePermission(NutritionRecord::class),
                HealthPermission.getWritePermission(HydrationRecord::class)
            )
            val granted = hcClient.permissionController.getGrantedPermissions()
            if (!granted.containsAll(permissions)) {
                DebugLogger.addActionLog("[SyncWorker] Missing Health Connect permissions. Aborting.")
                return Result.failure()
            }

            val yesterday = ZonedDateTime.now().minusDays(1)
            val dateStr = yesterday.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
            
            val response = RetrofitClient.instance.getDiaryWithAutoLogin(dateStr, prefs)
            var kcal = 0.0; var prot = 0.0; var fat = 0.0; var carb = 0.0
            var sugar = 0.0; var salt = 0.0; var water = 0.0; var fiber = 0.0

            response.data?.times?.forEach { meal ->
                meal.foodstuff?.forEach { f ->
                    kcal += f.energy?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
                    prot += f.protein?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
                    fat += f.fat?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
                    carb += f.carbohydrate?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
                    sugar += f.sugar?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
                    salt += f.salt?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
                    water += f.water?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
                    fiber += f.fiber?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
                }
            }

            if (kcal == 0.0 && prot == 0.0) {
                DebugLogger.addActionLog("[SyncWorker] No data found for ${dateStr}. Success.")
                return Result.success()
            }

            val endTime = yesterday.withHour(23).withMinute(59).withSecond(0).toInstant()
            val startTime = endTime.minusSeconds(60)
            val zoneOffset = ZoneId.systemDefault().rules.getOffset(endTime)

            val records = mutableListOf<androidx.health.connect.client.records.Record>()

            records.add(NutritionRecord(
                startTime = startTime, endTime = endTime,
                startZoneOffset = zoneOffset, endZoneOffset = zoneOffset,
                energy = Energy.kilocalories(kcal),
                protein = Mass.grams(prot),
                totalFat = Mass.grams(fat),
                totalCarbohydrate = Mass.grams(carb),
                sugar = Mass.grams(sugar),
                sodium = Mass.grams(salt), // salt -> sodium (approx)
                dietaryFiber = Mass.grams(fiber),
                name = "KT Auto Import"
            ))

            // Add water if any (convert liters/ml if needed, assume KT sends liters, wait, KT usually sends liters for water or ml?
            // Actually KT usually returns Liters for water. So if water=1.5, it's 1.5L.
            if (water > 0.0) {
                records.add(HydrationRecord(
                    startTime = startTime, endTime = endTime,
                    startZoneOffset = zoneOffset, endZoneOffset = zoneOffset,
                    volume = Volume.liters(water / 1000.0)
                ))
            }

            hcClient.insertRecords(records)
            DebugLogger.addActionLog("[SyncWorker] Sync complete! Uploaded ${kcal.toInt()} kcal.")
            return Result.success()
        } catch (e: Exception) {
            DebugLogger.addActionLog("[SyncWorker] Error during sync: ${e.message}")
            e.printStackTrace()
            return Result.retry()
        }
    }
}
