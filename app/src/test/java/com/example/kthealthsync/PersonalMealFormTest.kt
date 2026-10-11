package com.example.kthealthsync

import com.example.kthealthsync.domain.model.PersonalMealForm
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class PersonalMealFormTest {
    private fun form() = PersonalMealForm(JsonParser().parse("""
        {"guid":"meal","title":"Test meal","portionsMax":4,"selectedUnitGuid":"g","selectedUnitMultiplier":1000,
         "units":[{"id":"portion","title":"porce","multiplier":-2},{"id":"percent","title":"%","multiplier":-1},{"id":"g","title":"1 g","multiplier":1}],
         "foodstuff":[
          {"guid":"a","selected":true,"countOriginal":2,"count":2,"weight":200,"energy":2,"selectedUnitGuid":"100g","selectedUnitGuidOriginal":"100g","units":[{"id":"100g","multiplier":100}]},
          {"guid":"b","selected":true,"countOriginal":800,"count":800,"weight":800,"energy":1,"selectedUnitGuid":"g","units":[{"id":"g","multiplier":1}]},
          {"guid":"excluded","selected":false,"countOriginal":100,"count":100,"weight":100,"energy":10,"selectedUnitGuid":"g","units":[{"id":"g","multiplier":1}]}]}
    """).asJsonObject)

    @Test fun portionPercentAndGramsProduceEquivalentIngredientCounts() {
        for ((unit, amount) in listOf("portion" to 1.0, "percent" to 25.0, "g" to 250.0)) {
            val portion = form().portion(unit, amount, "16.09.2026", "5")
            assertEquals(300.0, portion.kcal, 0.001)
            assertEquals(250.0, portion.grams, 0.001)
            val request = portion.request
            assertEquals("16.09.2026", request.get("date").asString)
            assertEquals("5", request.get("diaryTimeGuid").asString)
            assertEquals(unit, request.get("selectedUnitGuid").asString)
            assertEquals(amount, request.get("selectedUnitMultiplier").asDouble, 0.001)
            val ingredients = request.getAsJsonArray("foodstuff")
            assertEquals(0.5, ingredients[0].asJsonObject.get("count").asDouble, 0.001)
            assertEquals(200.0, ingredients[1].asJsonObject.get("count").asDouble, 0.001)
            assertEquals(0.0, ingredients[2].asJsonObject.get("count").asDouble, 0.001)
        }
    }

    @Test fun changingUnitDoesNotScaleAnAlreadyScaledForm() {
        val form = form()
        form.portion("percent", 25.0, "16.09.2026", "3")
        assertEquals(1200.0, form.portion("percent", 100.0, "16.09.2026", "3").kcal, 0.001)
    }

    @Test fun invalidAmountsAndUnitsCannotBeSubmitted() {
        for (amount in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertTrue(runCatching { form().portion("g", amount, "16.09.2026", "3") }.isFailure)
        }
        assertTrue(runCatching { form().portion("unknown", 1.0, "16.09.2026", "3") }.isFailure)
    }

    @Test fun portionCalculatesMacrosFromIngredients() {
        val json = """
            {"guid":"meal","title":"Complex Dish","portionsMax":2,"selectedUnitGuid":"g","selectedUnitMultiplier":500,
             "units":[{"id":"portion","title":"porce","multiplier":-2},{"id":"percent","title":"%","multiplier":-1},{"id":"g","title":"1 g","multiplier":1}],
             "foodstuff":[
              {"guid":"c1","selected":true,"countOriginal":2,"count":2,"weight":200,"energy":1.5,"protein":0.2,"carbohydrate":0.3,"fat":0.05,"fiber":0.02,"selectedUnitGuid":"100g","units":[{"id":"100g","multiplier":100}]},
              {"guid":"c2","selected":true,"countOriginal":300,"count":300,"weight":300,"energy":1.0,"protein":0.1,"carbohydrate":0.05,"fat":0.02,"fiber":0.01,"selectedUnitGuid":"g","units":[{"id":"g","multiplier":1}]}
             ]}
        """.trimIndent()
        val form = PersonalMealForm(JsonParser().parse(json).asJsonObject)
        val halfPortion = form.portion("percent", 50.0, "11.10.2026", "3")
        // c1: count = 1.0 * 100 = 100 factorCount -> kcal: 150, prot: 20, carbs: 30, fat: 5, fiber: 2
        // c2: count = 150 * 1 = 150 factorCount -> kcal: 150, prot: 15, carbs: 7.5, fat: 3, fiber: 1.5
        // total: kcal: 300, prot: 35, carbs: 37.5, fat: 8, fiber: 3.5, grams: 250
        assertEquals(300.0, halfPortion.kcal, 0.001)
        assertEquals(250.0, halfPortion.grams, 0.001)
        assertEquals(35.0, halfPortion.protein, 0.001)
        assertEquals(37.5, halfPortion.carbs, 0.001)
        assertEquals(8.0, halfPortion.fat, 0.001)
        assertEquals(3.5, halfPortion.fiber, 0.001)
    }
}
