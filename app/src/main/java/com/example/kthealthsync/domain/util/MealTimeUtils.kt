package com.example.kthealthsync.domain.util

import com.example.kthealthsync.MealTime
import com.example.kthealthsync.domain.model.AppStrings
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object MealTimeUtils {

    /**
     * Determines default meal time slot:
     * - For past dates: automatically selects the last meal of the day.
     * - For today: selects the current meal slot based on the time of day:
     *     < 10:00 -> Breakfast (1)
     *     10:00 - 12:00 -> Morning Snack (2)
     *     12:00 - 15:00 -> Lunch (3)
     *     15:00 - 18:00 -> Afternoon Snack (4)
     *     18:00 - 21:00 -> Dinner (5)
     *     >= 21:00 -> Second Dinner (6)
     */
    fun getDefaultMealTimeGuid(
        dateStr: String,
        options: List<Pair<String, String>>,
        now: LocalTime = LocalTime.now(),
        today: LocalDate = LocalDate.now()
    ): String {
        if (options.isEmpty()) return "1"

        val isToday = try {
            val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
            LocalDate.parse(dateStr, formatter) == today
        } catch (_: Exception) {
            true
        }

        if (!isToday) {
            return options.last().first
        }

        val hour = now.hour
        val targetIndex = when {
            hour < 10 -> 0
            hour < 12 -> 1
            hour < 15 -> 2
            hour < 18 -> 3
            hour < 21 -> 4
            else -> 5
        }.coerceIn(0, options.lastIndex)

        return options[targetIndex].first
    }

    fun getStandardMealOptions(strings: AppStrings): List<Pair<String, String>> {
        return listOf(
            "1" to strings.breakfast,
            "2" to strings.morningSnack,
            "3" to strings.lunch,
            "4" to strings.afternoonSnack,
            "5" to strings.dinner,
            "6" to strings.secondDinner
        )
    }

    val STANDARD_MEAL_OPTIONS = listOf(
        "1" to "Завтрак",
        "2" to "Второй завтрак",
        "3" to "Обед",
        "4" to "Полдник",
        "5" to "Ужин",
        "6" to "Второй ужин"
    )

    fun getMealTimeDisplayName(mealTime: MealTime, strings: AppStrings? = null): String {
        val title = mealTime.title?.trim()
        if (!title.isNullOrEmpty()) return title
        val name = mealTime.name?.trim()
        if (!name.isNullOrEmpty()) return name

        return when (mealTime.id) {
            "1" -> strings?.breakfast ?: "Завтрак"
            "2" -> strings?.morningSnack ?: "Второй завтрак"
            "3" -> strings?.lunch ?: "Обед"
            "4" -> strings?.afternoonSnack ?: "Полдник"
            "5" -> strings?.dinner ?: "Ужин"
            "6" -> strings?.secondDinner ?: "Второй ужин"
            else -> strings?.meal ?: "Приём пищи"
        }
    }
}
