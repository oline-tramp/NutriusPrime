package com.example.kthealthsync

import com.example.kthealthsync.domain.model.DailySummary
import com.example.kthealthsync.domain.model.NutrientGoals
import com.example.kthealthsync.domain.util.DailyShareFormatter
import org.junit.Test

class WhoopPromptFormatterTest {

    @Test
    fun testFormatWhoopBehaviorsPrompt() {
        val summary = DailySummary(
            kcal = 2503.0,
            protein = 126.0,
            fat = 85.0,
            carb = 282.0,
            sugar = 112.0,
            salt = 2.6,
            water = 3500.0,
            fiber = 41.0,
            date = "08.09.2026",
            vegGrams = 75.0,
            fruitGrams = 396.0,
            supplementCount = 2
        )

        val breakfast = MealTime(
            id = "1",
            title = "Snídaně",
            name = "Snídaně",
            foodstuff = listOf(
                Foodstuff(
                    title = "Káva espresso",
                    name = null,
                    nazev = null,
                    energy = "5",
                    protein = "0.2",
                    carbohydrate = "0.5",
                    fat = "0.1",
                    sugar = "0",
                    salt = "0",
                    water = "50",
                    fiber = "0",
                    unit = "1 x 50 ml",
                    foodstuff = null,
                    items = null,
                    ingredients = null,
                    components = null,
                    guid = "f1",
                    guidRecipe = null,
                    isRecipe = false
                ),
                Foodstuff(
                    title = "Ovesné vločky",
                    name = null,
                    nazev = null,
                    energy = "200",
                    protein = "8",
                    carbohydrate = "35",
                    fat = "3",
                    sugar = "1",
                    salt = "0",
                    water = "0",
                    fiber = "5",
                    unit = "50 g",
                    foodstuff = null,
                    items = null,
                    ingredients = null,
                    components = null,
                    guid = "f2",
                    guidRecipe = null,
                    isRecipe = false
                )
            )
        )

        val lunch = MealTime(
            id = "3",
            title = "Oběd",
            name = "Oběd",
            foodstuff = listOf(
                Foodstuff(
                    title = "Fazole červené",
                    name = null,
                    nazev = null,
                    energy = "120",
                    protein = "9",
                    carbohydrate = "20",
                    fat = "0.5",
                    sugar = "1",
                    salt = "0.2",
                    water = "0",
                    fiber = "7",
                    unit = "75 x 1 g",
                    foodstuff = null,
                    items = null,
                    ingredients = null,
                    components = null,
                    guid = "f3",
                    guidRecipe = null,
                    isRecipe = false
                ),
                Foodstuff(
                    title = "Magne B6 470mg Sanofi",
                    name = null,
                    nazev = null,
                    energy = "0",
                    protein = "0",
                    carbohydrate = "0",
                    fat = "0",
                    sugar = "0",
                    salt = "0",
                    water = "0",
                    fiber = "0",
                    unit = "3 x 1 g",
                    foodstuff = null,
                    items = null,
                    ingredients = null,
                    components = null,
                    guid = "f4",
                    guidRecipe = null,
                    isRecipe = false
                )
            )
        )

        val diaryResponse = DiaryResponse(
            data = com.example.kthealthsync.DiaryData(
                times = listOf(breakfast, lunch)
            )
        )

        val prompt = DailyShareFormatter.formatDailyShareText(
            type = com.example.kthealthsync.domain.model.ShareFormatType.WHOOP_BEHAVIORS_LLM,
            dateStr = "08.09.2026",
            summary = summary,
            goals = NutrientGoals(2500.0, 130.0, 325.0, 75.0, 30.0, 40.0, 5.0, 3000.0, 400.0),
            diaryResponse = diaryResponse
        )

        println(prompt)

        // Verify date normalization
        org.junit.Assert.assertTrue(prompt.contains("Date: 2026-09-08"))
        // Verify directive
        org.junit.Assert.assertTrue(prompt.contains("RETRIEVE & STATE TRACKED BEHAVIORS FIRST"))
        org.junit.Assert.assertTrue(prompt.contains("LOG ONLY MY ACTIVELY TRACKED BEHAVIORS"))
        org.junit.Assert.assertTrue(prompt.contains("STRICTLY DO NOT ADD UNTRACKED BEHAVIORS"))
        org.junit.Assert.assertTrue(prompt.contains("DO NOT ask for confirmation"))
        org.junit.Assert.assertTrue(prompt.contains("NEGATIVE BEHAVIORS (marked NO)"))
        // Verify totals
        org.junit.Assert.assertTrue(prompt.contains("Calories: 2503 kcal"))
        org.junit.Assert.assertTrue(prompt.contains("Protein: 126 g"))
        org.junit.Assert.assertTrue(prompt.contains("Fats: 85 g"))
        org.junit.Assert.assertTrue(prompt.contains("Carbohydrates: 282 g"))
        org.junit.Assert.assertTrue(prompt.contains("Fiber: 41 g"))
        org.junit.Assert.assertTrue(prompt.contains("Added Sugar: 112 g"))
        org.junit.Assert.assertTrue(prompt.contains("Hydration: 14 glasses (3.5 L / 3500 ml"))
        org.junit.Assert.assertTrue(prompt.contains("Fruits and Vegetables: 6 servings (471 g total"))
        // Verify positive behaviors
        org.junit.Assert.assertTrue(prompt.contains("Breakfast: YES"))
        org.junit.Assert.assertTrue(prompt.contains("Caffeine: YES (1 serving(s)"))
        org.junit.Assert.assertTrue(prompt.contains("Beans & Legumes: YES (75 g"))
        org.junit.Assert.assertTrue(prompt.contains("Magnesium Supplement: YES"))
        // Verify negative behaviors
        org.junit.Assert.assertTrue(prompt.contains("Alcohol: NO (0 drinks consumed)"))
        org.junit.Assert.assertTrue(prompt.contains("Creatine: NO"))
        org.junit.Assert.assertTrue(prompt.contains("Omega-3 Supplement: NO"))
        org.junit.Assert.assertTrue(prompt.contains("Ashwagandha: NO"))
        org.junit.Assert.assertTrue(prompt.contains("Late Meal / Eating close to bedtime: NO"))
    }
}
