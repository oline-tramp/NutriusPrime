package com.example.kthealthsync.sync

import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Volume
import com.example.kthealthsync.domain.model.DailySummary
import com.example.kthealthsync.domain.util.NutrientCalculator
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicLong

internal object DailySummaryRecords {
    private val lastVersion = AtomicLong()

    // Capture before fetching the diary, so an older response cannot replace a newer snapshot.
    fun nextVersion(): Long = lastVersion.updateAndGet { previous ->
        maxOf(System.currentTimeMillis(), previous + 1)
    }

    fun build(summary: DailySummary, date: ZonedDateTime, version: Long): List<Record> {
        val end = date.toLocalDate().atTime(23, 59).atZone(date.zone)
        val start = end.minusMinutes(1)
        val day = date.toLocalDate()
        fun metadata(kind: String) = Metadata(
            clientRecordId = "kt-daily-$day-$kind",
            clientRecordVersion = version,
            recordingMethod = Metadata.RECORDING_METHOD_MANUAL_ENTRY
        )

        // Write zero totals too: removing the last diary item must clear previously synced totals.
        return listOf(
            NutritionRecord(
                startTime = start.toInstant(),
                endTime = end.toInstant(),
                startZoneOffset = start.offset,
                endZoneOffset = end.offset,
                energy = Energy.kilocalories(summary.kcal),
                protein = Mass.grams(summary.protein),
                totalFat = Mass.grams(summary.fat),
                totalCarbohydrate = Mass.grams(summary.carb),
                sugar = Mass.grams(summary.sugar),
                sodium = Mass.grams(NutrientCalculator.saltToSodiumGrams(summary.salt)),
                dietaryFiber = Mass.grams(summary.fiber),
                name = "KT Auto Import",
                metadata = metadata("nutrition")
            ),
            HydrationRecord(
                startTime = start.toInstant(),
                endTime = end.toInstant(),
                startZoneOffset = start.offset,
                endZoneOffset = end.offset,
                volume = Volume.liters(summary.water / 1000.0),
                metadata = metadata("hydration")
            )
        )
    }
}
