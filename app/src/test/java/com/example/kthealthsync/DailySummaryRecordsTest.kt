package com.example.kthealthsync

import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.NutritionRecord
import com.example.kthealthsync.domain.model.DailySummary
import com.example.kthealthsync.sync.DailySummaryRecords
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime
import java.time.ZoneOffset

class DailySummaryRecordsTest {
    private val day = ZonedDateTime.parse("2026-09-10T12:34:56.123+02:00[Europe/Prague]")
    private val summary = DailySummary(2000.0, 100.0, 60.0, 250.0, 30.0, 5.0, 1500.0, 25.0, "10.09.2026")

    @Test fun repeatedDayKeepsIdentityAndUpdatedTotalsGetNewerVersion() {
        val initial = DailySummaryRecords.build(summary, day, 10)
        val updated = DailySummaryRecords.build(summary.copy(kcal = 1800.0), day.plusHours(3), 11)
        assertEquals(initial.map { it.metadata.clientRecordId }, updated.map { it.metadata.clientRecordId })
        assertEquals(2, initial.map { it.metadata.clientRecordId }.toSet().size)
        assertTrue(initial.all { !it.metadata.clientRecordId.isNullOrBlank() })
        assertTrue(updated.all { it.metadata.clientRecordVersion > initial.first().metadata.clientRecordVersion })
        assertEquals(1800.0, (updated[0] as NutritionRecord).energy!!.inKilocalories, 0.001)
        assertNotEquals(initial[0].metadata.clientRecordId,
            DailySummaryRecords.build(summary, day.plusDays(1), 12)[0].metadata.clientRecordId)
    }

    @Test fun deletingAllFoodReplacesBothPreviouslyNonzeroRecords() {
        val empty = summary.copy(kcal = 0.0, protein = 0.0, fat = 0.0, carb = 0.0,
            sugar = 0.0, salt = 0.0, water = 0.0, fiber = 0.0)
        val before = DailySummaryRecords.build(summary, day, 1)
        val after = DailySummaryRecords.build(empty, day, 2)
        assertEquals(before.map { it.metadata.clientRecordId }, after.map { it.metadata.clientRecordId })
        assertEquals(0.0, (after[0] as NutritionRecord).energy!!.inKilocalories, 0.0)
        assertEquals(0.0, (after[1] as HydrationRecord).volume.inLiters, 0.0)
    }

    @Test fun recordsPreserveConversionsAndUseTheDateZoneAtTheRecordTime() {
        val dstDay = ZonedDateTime.parse("2026-03-29T00:00:00+01:00[Europe/Prague]")
        val records = DailySummaryRecords.build(summary, dstDay, 1)
        val nutrition = records[0] as NutritionRecord
        assertEquals(5.0 * 0.39339, nutrition.sodium!!.inGrams, 0.00001)
        assertEquals(1.5, (records[1] as HydrationRecord).volume.inLiters, 0.0)
        assertEquals(ZoneOffset.ofHours(2), nutrition.startZoneOffset)
        assertEquals(60, java.time.Duration.between(nutrition.startTime, nutrition.endTime).seconds.toInt())
        assertEquals(0, nutrition.endTime.nano)
    }

    @Test fun requestVersionsIncreaseEvenWithinOneMillisecond() {
        val versions = List(100) { DailySummaryRecords.nextVersion() }
        assertTrue(versions.zipWithNext().all { (a, b) -> b > a })
    }
}
