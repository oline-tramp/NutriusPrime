package com.example.kthealthsync

import com.example.kthealthsync.domain.model.DailySummary
import com.example.kthealthsync.domain.model.NutrientGoals
import com.example.kthealthsync.domain.util.DailyShareFormatter
import org.junit.Test

class DailyShareFormatterTest {

    @Test
    fun testFormatDailyContextForLlm() {
        val summary = DailySummary(
            kcal = 1850.0,
            protein = 95.0,
            fat = 52.0,
            carb = 210.0,
            sugar = 45.0,
            salt = 3.8,
            water = 1800.0,
            fiber = 22.0,
            date = "07.09.2026",
            vegGrams = 180.0,
            fruitGrams = 100.0
        )
        val goals = NutrientGoals(
            kcal = 2300.0,
            protein = 130.0,
            carbs = 285.0,
            fat = 70.0,
            fiber = 30.0,
            sugar = 70.0,
            salt = 5.0,
            vegFruit = 400.0,
            water = 2500.0
        )
        val food1 = Foodstuff(
            title = "Овсянка",
            name = null,
            nazev = null,
            energy = "320",
            protein = "12",
            carbohydrate = "54",
            fat = "6",
            sugar = "2",
            salt = "0.01",
            water = "50",
            fiber = "5",
            foodstuff = null,
            items = null,
            ingredients = null,
            components = null,
            guid = "1",
            guidRecipe = null,
            isRecipe = false,
            unit = "250 g"
        )
        val diary = DiaryResponse(
            data = DiaryData(
                times = listOf(
                    MealTime(title = "Завтрак", foodstuff = listOf(food1))
                )
            )
        )

        val result = DailyShareFormatter.formatDailyContextForLlm("07.09.2026", summary, goals, diary)
        org.junit.Assert.assertTrue(result.contains("07.09.2026"))
        org.junit.Assert.assertTrue(result.contains("Калории: 1850.0 / 2300.0 ккал"))
        org.junit.Assert.assertTrue(result.contains("Белки: 95.0 / 130.0 г"))
        org.junit.Assert.assertTrue(result.contains("Овощи и фрукты (норма ВОЗ): 280 / 400 г"))
        org.junit.Assert.assertTrue(result.contains("[Завтрак]"))
        org.junit.Assert.assertTrue(result.contains("Овсянка (250 g)"))
        org.junit.Assert.assertTrue(result.contains("Ты персональный нутрициолог"))
    }

    @Test
    fun testFormatDailyShareText_DailySummary() {
        val summary = DailySummary(
            kcal = 1850.0,
            protein = 95.0,
            fat = 55.0,
            carb = 210.0,
            sugar = 45.0,
            salt = 3.2,
            water = 1900.0,
            fiber = 22.0,
            date = "07.09.2026",
            vegGrams = 180.0,
            fruitGrams = 100.0
        )
        val goals = NutrientGoals(
            kcal = 2300.0, protein = 130.0, carbs = 285.0, fat = 70.0,
            fiber = 30.0, sugar = 70.0, salt = 5.0, vegFruit = 400.0, water = 2500.0
        )
        val diary = DiaryResponse(
            data = DiaryData(
                times = listOf(
                    MealTime(
                        title = "Обед",
                        foodstuff = listOf(
                            Foodstuff(
                                title = "Куриная грудка гриль",
                                name = null, nazev = null, energy = "200", protein = "35",
                                carbohydrate = "0", fat = "3", sugar = "0", salt = "0.5",
                                water = "60", fiber = "0", foodstuff = null, items = null,
                                ingredients = null, components = null, guid = "c1",
                                guidRecipe = null, isRecipe = false, unit = "150 g"
                            )
                        )
                    )
                )
            )
        )

        val cleanSummary = DailyShareFormatter.formatDailyShareText(
            com.example.kthealthsync.domain.model.ShareFormatType.DAILY_SUMMARY,
            "07.09.2026",
            summary,
            goals,
            diary
        )

        org.junit.Assert.assertTrue(cleanSummary.contains("📊 ДНЕВНОЙ ОТЧЁТ ПИТАНИЯ"))
        org.junit.Assert.assertTrue(cleanSummary.contains("📅 ДАТА: 07.09.2026"))
        org.junit.Assert.assertTrue(cleanSummary.contains("Калории: 1850.0 / 2300.0 ккал"))
        org.junit.Assert.assertTrue(cleanSummary.contains("[Обед]"))
        org.junit.Assert.assertTrue(cleanSummary.contains("Куриная грудка гриль"))
        // DAILY_SUMMARY must NOT contain LLM prompts
        org.junit.Assert.assertFalse(cleanSummary.contains("Ты персональный нутрициолог"))
        org.junit.Assert.assertFalse(cleanSummary.contains("Задача:"))
    }

    @Test
    fun testFormatDailyShareText_FoodListOnly() {
        val summary = DailySummary(
            kcal = 1850.0, protein = 95.0, fat = 55.0, carb = 210.0,
            sugar = 45.0, salt = 3.2, water = 1900.0, fiber = 22.0,
            date = "07.09.2026", vegGrams = 180.0, fruitGrams = 100.0
        )
        val goals = NutrientGoals()
        val diary = DiaryResponse(
            data = DiaryData(
                times = listOf(
                    MealTime(
                        title = "Перекус",
                        foodstuff = listOf(
                            Foodstuff(
                                title = "Яблоко Gala",
                                name = null, nazev = null, energy = "70", protein = "0.5",
                                carbohydrate = "18", fat = "0.2", sugar = "14", salt = "0.0",
                                water = "100", fiber = "3", foodstuff = null, items = null,
                                ingredients = null, components = null, guid = "a1",
                                guidRecipe = null, isRecipe = false, unit = "130 g"
                            )
                        )
                    )
                )
            )
        )

        val foodList = DailyShareFormatter.formatDailyShareText(
            com.example.kthealthsync.domain.model.ShareFormatType.FOOD_LIST_ONLY,
            "07.09.2026",
            summary,
            goals,
            diary
        )

        org.junit.Assert.assertTrue(foodList.contains("🍽 ДНЕВНИК ПИТАНИЯ"))
        org.junit.Assert.assertTrue(foodList.contains("📅 ДАТА: 07.09.2026"))
        org.junit.Assert.assertTrue(foodList.contains("[Перекус]"))
        org.junit.Assert.assertTrue(foodList.contains("Яблоко Gala (130 g)"))
        org.junit.Assert.assertFalse(foodList.contains("ИТОГИ ДНЯ"))
    }

    @Test
    fun testFormatDailyShareText_JsonExport() {
        val summary = DailySummary(
            kcal = 1850.0, protein = 95.0, fat = 55.0, carb = 210.0,
            sugar = 45.0, salt = 3.2, water = 1900.0, fiber = 22.0,
            date = "07.09.2026", vegGrams = 180.0, fruitGrams = 100.0
        )
        val goals = NutrientGoals(kcal = 2000.0)

        val json = DailyShareFormatter.formatDailyShareText(
            com.example.kthealthsync.domain.model.ShareFormatType.JSON_EXPORT,
            "07.09.2026",
            summary,
            goals,
            null
        )

        org.junit.Assert.assertTrue(json.startsWith("{"))
        org.junit.Assert.assertTrue(json.endsWith("}"))
        org.junit.Assert.assertTrue(json.contains("\"date\": \"07.09.2026\""))
        org.junit.Assert.assertTrue(json.contains("\"totals\": {"))
        org.junit.Assert.assertTrue(json.contains("\"goals\": {"))
    }
}
