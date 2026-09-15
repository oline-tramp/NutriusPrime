package com.example.kthealthsync

import com.example.kthealthsync.domain.util.MealTimeUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class MealTimeUtilsTest {

    @Test
    fun testGetDefaultMealTimeGuid_PastDate() {
        val options = listOf(
            "1" to "Breakfast",
            "2" to "Morning Snack",
            "3" to "Lunch",
            "4" to "Afternoon Snack",
            "5" to "Dinner",
            "6" to "Second Dinner"
        )
        val today = java.time.LocalDate.of(2026, 9, 7)
        // Yesterday (past date) should return the last meal slot ("6")
        val result = MealTimeUtils.getDefaultMealTimeGuid(
            dateStr = "06.09.2026",
            options = options,
            now = java.time.LocalTime.of(8, 30),
            today = today
        )
        org.junit.Assert.assertEquals("6", result)
    }

    @Test
    fun testGetDefaultMealTimeGuid_TodayTimeSlots() {
        val options = listOf(
            "1" to "Breakfast",
            "2" to "Morning Snack",
            "3" to "Lunch",
            "4" to "Afternoon Snack",
            "5" to "Dinner",
            "6" to "Second Dinner"
        )
        val today = java.time.LocalDate.of(2026, 9, 7)

        // 08:30 -> Breakfast ("1")
        org.junit.Assert.assertEquals("1", MealTimeUtils.getDefaultMealTimeGuid("07.09.2026", options, java.time.LocalTime.of(8, 30), today))
        // 11:15 -> Morning Snack ("2")
        org.junit.Assert.assertEquals("2", MealTimeUtils.getDefaultMealTimeGuid("07.09.2026", options, java.time.LocalTime.of(11, 15), today))
        // 13:45 -> Lunch ("3")
        org.junit.Assert.assertEquals("3", MealTimeUtils.getDefaultMealTimeGuid("07.09.2026", options, java.time.LocalTime.of(13, 45), today))
        // 16:30 -> Afternoon Snack ("4")
        org.junit.Assert.assertEquals("4", MealTimeUtils.getDefaultMealTimeGuid("07.09.2026", options, java.time.LocalTime.of(16, 30), today))
        // 19:20 -> Dinner ("5")
        org.junit.Assert.assertEquals("5", MealTimeUtils.getDefaultMealTimeGuid("07.09.2026", options, java.time.LocalTime.of(19, 20), today))
        // 22:10 -> Second Dinner ("6")
        org.junit.Assert.assertEquals("6", MealTimeUtils.getDefaultMealTimeGuid("07.09.2026", options, java.time.LocalTime.of(22, 10), today))
    }
}
