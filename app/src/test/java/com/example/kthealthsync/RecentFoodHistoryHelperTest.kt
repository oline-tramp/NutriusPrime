package com.example.kthealthsync

import com.example.kthealthsync.domain.draft.RecentFoodHistoryHelper
import org.junit.Assert.*
import org.junit.Test

class RecentFoodHistoryHelperTest {

    @Test
    fun buildMaskMatchesUserExampleForYesterdayAndFourDaysAgo() {
        // User example: "приём был вчера и 4 дня назад" -> 10010000000000
        val mask = RecentFoodHistoryHelper.buildMask(setOf(1, 4))
        assertEquals("10010000000000", mask)
        assertEquals(14, mask.length)
    }

    @Test
    fun buildMaskHandlesBoundaryDays() {
        val emptyMask = RecentFoodHistoryHelper.buildMask(emptySet())
        assertEquals("00000000000000", emptyMask)

        val day14Mask = RecentFoodHistoryHelper.buildMask(setOf(14))
        assertEquals("00000000000001", day14Mask)

        val allDaysMask = RecentFoodHistoryHelper.buildMask((1..14).toSet())
        assertEquals("11111111111111", allDaysMask)
    }

    @Test
    fun aggregateGroupsOccurrencesAndBuildsCorrectMask() {
        val borscht1 = Foodstuff(id = "b1", title = "Борщ домашний", unit = "350 g")
        val borscht4 = Foodstuff(id = "b1", title = "Борщ домашний", unit = "350 g")
        val salad2 = Foodstuff(id = "s1", title = "Салат Цезарь", unit = "200 g")

        val daysMap = mapOf(
            1 to listOf(borscht1),
            2 to listOf(salad2),
            4 to listOf(borscht4)
        )

        val items = RecentFoodHistoryHelper.aggregate(daysMap)
        assertEquals(2, items.size)

        val borschtItem = items.find { it.name == "Борщ домашний" }!!
        assertEquals("b1", borschtItem.id)
        assertEquals("10010000000000", borschtItem.mask)
        assertEquals(1, borschtItem.lastDaysAgo)
        assertEquals("350 g", borschtItem.portionText)

        val saladItem = items.find { it.name == "Салат Цезарь" }!!
        assertEquals("s1", saladItem.id)
        assertEquals("01000000000000", saladItem.mask)
        assertEquals(2, saladItem.lastDaysAgo)
    }

    @Test
    fun extractMealsNormalizesMealNamesAndGroupsFoods() {
        val lunchMeal = MealTime(
            id = "3",
            title = "Oběd",
            foodstuff = listOf(
                Foodstuff(id = "4582", title = "Борщ украинский", unit = "350 g"),
                Foodstuff(id = "1290", title = "Хлеб", unit = "50 g")
            )
        )
        val daysMealsMap = mapOf(1 to listOf(lunchMeal))
        val meals = RecentFoodHistoryHelper.extractMeals(daysMealsMap)

        assertEquals(1, meals.size)
        val meal = meals.single()
        assertEquals(1, meal.daysAgo)
        assertEquals("Обед", meal.mealName)
        assertEquals(2, meal.foods.size)
        assertEquals("Борщ украинский", meal.foods[0].name)
        assertEquals("Хлеб", meal.foods[1].name)
    }

    @Test
    fun formatForPromptIncludesBothMealsAndMaskedProducts() {
        val lunchMeal = MealTime(
            id = "3",
            title = "Обед",
            foodstuff = listOf(
                Foodstuff(id = "4582", title = "Борщ украинский", unit = "350 g"),
                Foodstuff(id = "1290", title = "Хлеб", unit = "50 g")
            )
        )
        val daysMealsMap = mapOf(1 to listOf(lunchMeal))
        val meals = RecentFoodHistoryHelper.extractMeals(daysMealsMap)

        val daysMap = mapOf(
            1 to listOf(Foodstuff(id = "4582", title = "Борщ украинский", unit = "350 g"))
        )
        val items = RecentFoodHistoryHelper.aggregate(daysMap)

        val promptText = RecentFoodHistoryHelper.formatForPrompt(items, meals)

        assertTrue(promptText.contains("Недавние приёмы пищи"))
        assertTrue(promptText.contains("Вчера (1 день назад):"))
        assertTrue(promptText.contains("- Обед: [id: \"4582\"] \"Борщ украинский\" 350 g, [id: \"1290\"] \"Хлеб\" 50 g"))
        assertTrue(promptText.contains("Все продукты за 14 дней"))
    }

    @Test
    fun secondMealsAreNotMergedWithFirstMeals() {
        assertEquals("Второй завтрак", RecentFoodHistoryHelper.normalizeMealName(MealTime(id = "2", title = "Второй завтрак")))
        assertEquals("Второй ужин", RecentFoodHistoryHelper.normalizeMealName(MealTime(id = "6", title = "Druhá večeře")))
        assertEquals("Обед", RecentFoodHistoryHelper.normalizeMealName(MealTime(id = "3", title = "Oběd")))
    }
}
