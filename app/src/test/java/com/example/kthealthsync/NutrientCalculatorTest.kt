package com.example.kthealthsync

import com.example.kthealthsync.domain.util.NutrientCalculator
import com.example.kthealthsync.domain.util.NutrientParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NutrientCalculatorTest {

    @Test
    fun testScaleNutrient() {
        assertEquals("50.0", NutrientCalculator.scaleNutrient("100.0", 0.5))
        assertEquals("33.3", NutrientCalculator.scaleNutrient("100.0", 0.3333))
        assertNull(NutrientCalculator.scaleNutrient(null, 1.5))
    }

    @Test
    fun testSaltToSodiumConversion() {
        // Table salt 5g -> ~1.967g sodium
        val salt = 5.0
        val sodium = NutrientCalculator.saltToSodiumGrams(salt)
        assertEquals(1.967, sodium, 0.01)
    }

    @Test
    fun testAggregateMacro() {
        val food1 = Foodstuff(
            title = "Oatmeal",
            name = null,
            nazev = null,
            energy = "150",
            protein = "5",
            carbohydrate = "27",
            fat = "3",
            sugar = "1",
            salt = "0.01",
            water = "10",
            fiber = "4",
            foodstuff = null,
            items = null,
            ingredients = null,
            components = null,
            guid = "1",
            guidRecipe = null,
            isRecipe = false
        )

        val food2 = Foodstuff(
            title = "Banana",
            name = null,
            nazev = null,
            energy = "100",
            protein = "1",
            carbohydrate = "23",
            fat = "0.3",
            sugar = "12",
            salt = "0.0",
            water = "75",
            fiber = "2.6",
            foodstuff = null,
            items = null,
            ingredients = null,
            components = null,
            guid = "2",
            guidRecipe = null,
            isRecipe = false
        )

        val breakdowns = NutrientCalculator.aggregateMacro(
            listOf(food1, food2),
            { NutrientParser.parseNutrient(it.energy) },
            withMeals = false
        )

        assertEquals(2, breakdowns.size)
        assertEquals("Oatmeal", breakdowns[0].name)
        assertEquals(150.0, breakdowns[0].amount, 0.001)
        assertEquals("Banana", breakdowns[1].name)
        assertEquals(100.0, breakdowns[1].amount, 0.001)
    }

    @Test
    fun testAggregateMacroWithMealsForRecipeWithoutDirectNutrient() {
        val carrot = Foodstuff(
            title = "Carrot",
            name = null,
            nazev = null,
            unit = "30 g",
            isRecipe = false
        )
        val potato = Foodstuff(
            title = "Potato",
            name = null,
            nazev = null,
            unit = "20 g",
            isRecipe = false
        )
        val salad = Foodstuff(
            title = "Olivier Salad",
            name = null,
            nazev = null,
            unit = "200 g",
            isRecipe = true,
            components = listOf(carrot, potato)
        )

        // Extractor mimics veg extractor: 0 for the recipe itself, but grams for carrot & potato
        val vegExtractor: (Foodstuff) -> Double = { f ->
            when (f.title) {
                "Carrot" -> 30.0
                "Potato" -> 20.0
                else -> 0.0
            }
        }

        val breakdownsWithMeals = NutrientCalculator.aggregateMacro(
            listOf(salad),
            vegExtractor,
            withMeals = true
        )

        assertEquals(1, breakdownsWithMeals.size)
        val recipeBreakdown = breakdownsWithMeals[0]
        assertEquals("Olivier Salad", recipeBreakdown.name)
        assertEquals(50.0, recipeBreakdown.amount, 0.001)
        assertEquals(2, recipeBreakdown.components.size)
        assertEquals("Carrot", recipeBreakdown.components[0].name)
        assertEquals(30.0, recipeBreakdown.components[0].amount, 0.001)
        assertEquals("Potato", recipeBreakdown.components[1].name)
        assertEquals(20.0, recipeBreakdown.components[1].amount, 0.001)

        val breakdownsWithoutMeals = NutrientCalculator.aggregateMacro(
            listOf(salad),
            vegExtractor,
            withMeals = false
        )

        assertEquals(2, breakdownsWithoutMeals.size)
        assertEquals("Carrot", breakdownsWithoutMeals[0].name)
        assertEquals(30.0, breakdownsWithoutMeals[0].amount, 0.001)
        assertEquals("Potato", breakdownsWithoutMeals[1].name)
        assertEquals(20.0, breakdownsWithoutMeals[1].amount, 0.001)
    }
}
