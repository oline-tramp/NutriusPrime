package com.example.kthealthsync.sync

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Volume
import com.example.kthealthsync.DebugLogger
import com.example.kthealthsync.domain.model.DailySummary
import com.example.kthealthsync.domain.util.NutrientUtils
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class HealthConnectManager(private val context: Context) {

    val permissions = setOf(
        HealthPermission.getWritePermission(NutritionRecord::class),
        HealthPermission.getReadPermission(NutritionRecord::class),
        HealthPermission.getWritePermission(HydrationRecord::class),
        HealthPermission.getReadPermission(HydrationRecord::class)
    )

    fun isAvailable(): Boolean {
        val status = HealthConnectClient.getSdkStatus(context)
        return status == HealthConnectClient.SDK_AVAILABLE
    }

    fun getClient(): HealthConnectClient? {
        return if (isAvailable()) HealthConnectClient.getOrCreate(context) else null
    }

    suspend fun hasAllPermissions(): Boolean {
        val client = getClient() ?: return false
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(permissions)
    }

    /**
     * Inserts DailySummary into Health Connect as NutritionRecord and HydrationRecord.
     * Correctly calculates pure sodium from salt (NaCl * 0.3933).
     */
    suspend fun syncDailySummary(summary: DailySummary, date: ZonedDateTime): Boolean {
        val client = getClient()
        if (client == null) {
            DebugLogger.addActionLog("[HealthConnect] SDK unavailable on this device.")
            return false
        }

        if (!hasAllPermissions()) {
            DebugLogger.addActionLog("[HealthConnect] Missing required permissions for sync.")
            return false
        }

        val endTime = date.withHour(23).withMinute(59).withSecond(0).toInstant()
        val startTime = endTime.minusSeconds(60)
        val zoneOffset = ZoneId.systemDefault().rules.getOffset(startTime)

        val records = mutableListOf<Record>()

        val hasNutrition = summary.kcal > 0 || summary.protein > 0 || summary.carb > 0 || summary.fat > 0
        if (hasNutrition) {
            // Correct conversion: table salt -> sodium
            val sodiumGrams = NutrientUtils.saltToSodiumGrams(summary.salt)

            records.add(
                NutritionRecord(
                    startTime = startTime,
                    endTime = endTime,
                    startZoneOffset = zoneOffset,
                    endZoneOffset = zoneOffset,
                    energy = Energy.kilocalories(summary.kcal),
                    protein = Mass.grams(summary.protein),
                    totalFat = Mass.grams(summary.fat),
                    totalCarbohydrate = Mass.grams(summary.carb),
                    sugar = Mass.grams(summary.sugar),
                    sodium = Mass.grams(sodiumGrams),
                    dietaryFiber = Mass.grams(summary.fiber),
                    name = "KT Auto Import"
                )
            )
        }

        // Water sync: water in summary is in milliliters (or grams)
        if (summary.water > 0.0) {
            records.add(
                HydrationRecord(
                    startTime = startTime,
                    endTime = endTime,
                    startZoneOffset = zoneOffset,
                    endZoneOffset = zoneOffset,
                    volume = Volume.liters(summary.water / 1000.0)
                )
            )
        }

        if (records.isNotEmpty()) {
            client.insertRecords(records)
            DebugLogger.addActionLog("[HealthConnect] Successfully inserted ${records.size} record(s) for ${summary.date}")
            return true
        } else {
            DebugLogger.addActionLog("[HealthConnect] No records to insert (all values zero).")
            return true
        }
    }
}
