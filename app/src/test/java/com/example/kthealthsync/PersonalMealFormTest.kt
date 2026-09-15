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
}
