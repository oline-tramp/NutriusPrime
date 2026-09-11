package com.example.kthealthsync.sync

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.NutritionRecord
import com.example.kthealthsync.DebugLogger
import com.example.kthealthsync.domain.model.DailySummary
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
    suspend fun syncDailySummary(
        summary: DailySummary,
        date: ZonedDateTime,
        version: Long = DailySummaryRecords.nextVersion()
    ): Boolean {
        val client = getClient()
        if (client == null) {
            DebugLogger.addActionLog("[HealthConnect] SDK unavailable on this device.")
            return false
        }

        if (!hasAllPermissions()) {
            DebugLogger.addActionLog("[HealthConnect] Missing required permissions for sync.")
            return false
        }

        val records = DailySummaryRecords.build(summary, date, version)
        client.insertRecords(records)
        DebugLogger.addActionLog("[HealthConnect] Synced daily records for ${summary.date}")
        return true
    }
}
