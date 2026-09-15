package com.example.kthealthsync.domain.util

import com.example.kthealthsync.Foodstuff
import com.example.kthealthsync.domain.model.FoodBreakdown
import com.example.kthealthsync.domain.util.NutrientParser.parseNutrient
import java.util.Locale

object NutrientCalculator {

    /**
     * Scales nutrient string by multiplier factor and formats to 1 decimal place.
     */
    fun scaleNutrient(value: String?, scale: Double): String? {
        if (value == null) return null
        val parsed = parseNutrient(value)
        return String.format(Locale.US, "%.1f", parsed * scale)
    }

    /**
     * Converts table salt (NaCl) mass in grams to pure Sodium (Na) mass in grams.
     * Molar mass ratio: Na (22.99) / NaCl (58.44) ≈ 0.39339.
     */
    fun saltToSodiumGrams(saltGrams: Double): Double {
        return saltGrams * 0.39339
    }

    /**
     * Aggregates and merges macro/micronutrients from a list of foodstuffs,
     * supporting recursive recipes.
     */
    fun aggregateMacro(
        foodstuffs: List<Foodstuff>,
        extractor: (Foodstuff) -> Double,
        withMeals: Boolean
    ): List<FoodBreakdown> {
        val result = mutableListOf<FoodBreakdown>()
        for (f in foodstuffs) {
            val fname = f.title ?: f.name ?: f.nazev ?: "Unknown"
            val value = extractor(f)
            val children = f.foodstuff ?: f.items ?: f.ingredients ?: f.components
            val isRecipe = f.isRecipe == true || !children.isNullOrEmpty()

            if (isRecipe && withMeals) {
                val childBreakdowns = children?.let { aggregateMacro(it, extractor, false) } ?: emptyList()
                if (value > 0 || childBreakdowns.isNotEmpty()) {
                    result.add(FoodBreakdown(fname, value, childBreakdowns, originalFood = f))
                }
            } else if (isRecipe && !withMeals) {
                if (!children.isNullOrEmpty()) {
                    result.addAll(aggregateMacro(children, extractor, false))
                } else {
                    if (value > 0) result.add(FoodBreakdown(fname, value, originalFood = f))
                }
            } else {
                if (value > 0) result.add(FoodBreakdown(fname, value, originalFood = f))
            }
        }
        return mergeBreakdowns(result)
    }

    /**
     * Groups breakdowns by item name and sums amounts, preserving and merging subcomponents.
     */
    fun mergeBreakdowns(breakdowns: List<FoodBreakdown>): List<FoodBreakdown> {
        return breakdowns.groupBy { it.name }.map { (name, items) ->
            val totalAmount = items.sumOf { it.amount }.takeIf { it > 0 } ?: items.firstOrNull()?.amount ?: 0.0
            val allComponents = items.flatMap { it.components }
            val mergedComponents = if (allComponents.isNotEmpty()) mergeBreakdowns(allComponents) else emptyList()
            val origFood = items.mapNotNull { it.originalFood }.firstOrNull()
            FoodBreakdown(name, totalAmount, mergedComponents, origFood)
        }.sortedByDescending { it.amount }
    }
}
