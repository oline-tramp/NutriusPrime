package com.example.kthealthsync.domain.model

import com.example.kthealthsync.FoodFormUnitOption
import com.google.gson.JsonNull
import com.google.gson.JsonObject

/** Preserves the server's ingredient/unit fields when preparing the meal-specific request. */
class PersonalMealForm(private val data: JsonObject) {
    val units: List<FoodFormUnitOption> = data.getAsJsonArray("units").map {
        val unit = it.asJsonObject
        FoodFormUnitOption(unit.get("id").asString, unit.get("title").asString, unit.get("multiplier").asDouble)
    }.also { require(it.isNotEmpty()) { "Meal has no available units" } }
    val defaultUnit = units.firstOrNull { it.id == data.get("selectedUnitGuid")?.takeUnless { it.isJsonNull }?.asString }
        ?: units.first()
    val defaultAmount = data.number("selectedUnitMultiplier")?.takeIf { it > 0 && it.isFinite() }
        ?: suggestedAmount(defaultUnit)

    fun suggestedAmount(unit: FoodFormUnitOption) = when (unit.multiplier) {
        -2.0 -> 1.0
        -1.0 -> 100.0
        else -> 100.0 / requireNotNull(unit.multiplier).also { require(it > 0) }
    }

    fun portion(unitId: String, amount: Double, date: String, timeGuid: String): MealPortion {
        require(amount.isFinite() && amount > 0) { "Enter a positive amount" }
        val unit = requireNotNull(units.find { it.id == unitId }) { "Select a meal unit" }
        val ingredients = data.getAsJsonArray("foodstuff").map { it.asJsonObject }
        val weight = ingredients.filter { it.get("selected")?.asBoolean == true }.sumOf { it.number("weight") ?: 0.0 }
        val factor = when (unit.multiplier) {
            -1.0 -> amount / 100.0
            -2.0 -> amount / requireNotNull(data.number("portionsMax")).also { require(it > 0) { "Meal has no portion count" } }
            else -> {
                require(weight > 0) { "Meal has no weight" }
                amount * requireNotNull(unit.multiplier).also { require(it > 0) } / weight
            }
        }
        require(factor.isFinite()) { "Invalid meal amount" }
        val request = data.deepCopy()
        request.addProperty("date", date)
        request.addProperty("diaryTimeGuid", timeGuid)
        request.addProperty("selectedUnitGuid", unitId)
        request.addProperty("selectedUnitMultiplier", amount)
        request.addProperty("dontSendUnit", false)
        request.addProperty("timeUser", false)
        request.add("time", JsonNull.INSTANCE)
        if (requireNotNull(unit.multiplier) > 0) request.addProperty("count", amount * unit.multiplier)
        else request.add("count", JsonNull.INSTANCE)
        var kcal = 0.0
        request.getAsJsonArray("foodstuff").forEach {
            val ingredient = it.asJsonObject
            val selected = ingredient.get("selected")?.asBoolean == true
            val originalCount = ingredient.number("countOriginal") ?: ingredient.number("count") ?: 0.0
            val count = if (selected) originalCount * factor else 0.0
            ingredient.addProperty("count", count)
            ingredient.get("selectedUnitGuidOriginal")?.takeUnless { it.isJsonNull }?.let { original ->
                ingredient.add("selectedUnitGuid", original.deepCopy())
            }
            val selectedId = ingredient.get("selectedUnitGuid").asString
            val ingredientUnit = ingredient.getAsJsonArray("units").firstOrNull { u -> u.asJsonObject.get("id").asString == selectedId }
            requireNotNull(ingredientUnit) { "Ingredient has no matching unit" }
            val multiplier = ingredientUnit.asJsonObject.number("multiplier") ?: 1.0
            kcal += (ingredient.number("energy") ?: 0.0) * count * multiplier
        }
        return MealPortion(request, kcal, weight * factor)
    }
}

data class MealPortion(val request: JsonObject, val kcal: Double, val grams: Double)

private fun JsonObject.number(key: String): Double? = get(key)?.takeUnless { it.isJsonNull }?.asDouble
