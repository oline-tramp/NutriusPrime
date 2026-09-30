package com.example.kthealthsync.wear.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class MealSlot(
    val id: String,
    val name: String
)

object WatchMealTimeUtils {
    val MEAL_SLOTS = listOf(
        MealSlot("1", "Завтрак"),
        MealSlot("2", "2-й завтрак"),
        MealSlot("3", "Обед"),
        MealSlot("4", "Полдник"),
        MealSlot("5", "Ужин"),
        MealSlot("6", "2-й ужин")
    )

    fun getCurrentMealSlot(): MealSlot {
        val hour = LocalTime.now().hour
        val targetId = when {
            hour < 10 -> "1"
            hour < 12 -> "2"
            hour < 15 -> "3"
            hour < 18 -> "4"
            hour < 21 -> "5"
            else -> "6"
        }
        return MEAL_SLOTS.firstOrNull { it.id == targetId } ?: MEAL_SLOTS[0]
    }

    fun getTodayDateString(): String {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
    }
}
