package com.example.kthealthsync.domain.util

import com.example.kthealthsync.Foodstuff
import com.example.kthealthsync.domain.model.FoodCategory
import com.example.kthealthsync.domain.util.NutrientParser.parseNutrient

object HydrationCalculator {

    private val DIGIT_SPACE_DIGIT_REGEX = "(\\d)\\s+(\\d)".toRegex()
    private val MULT_LIQUID_REGEX = "([\\d.]+)\\s*x\\s*([\\d.]+)?\\s*(ml|l|dl|cl|g|kg)\\b".toRegex(RegexOption.IGNORE_CASE)
    private val SIMPLE_LIQUID_REGEX = "([\\d.]+)\\s*(ml|l|dl|cl|g|kg)\\b".toRegex(RegexOption.IGNORE_CASE)
    private val PAREN_UNIT_REGEX = "\\(([\\d.]+)\\s*([a-zA-Z]+)\\)".toRegex()
    private val COUNT_X_REGEX = "([\\d.]+)\\s*x".toRegex()

    /**
     * Parses the liquid volume in milliliters (ml) for fluid hydration.
     * Strictly relies on FoodCategory.BEVERAGE (or composite recipe water for soups/broths).
     * Zero hardcoded name dictionaries.
     */
    fun parseLiquidVolumeMl(food: Foodstuff, category: FoodCategory? = null): Double {
        val components = food.foodstuff ?: food.items ?: food.ingredients ?: food.components
        if (!components.isNullOrEmpty()) {
            val compVol = components.sumOf { parseLiquidVolumeMl(it, category) }
            if (compVol > 0) return compVol
        }

        // Soups and broths with recipe water content
        if (food.isRecipe == true) {
            val waterVal = parseNutrient(food.water)
            if (waterVal > 0) return waterVal
        }

        val fname = food.title ?: food.name ?: food.nazev ?: ""
        val resolvedCategory = category ?: FoodHtmlParser.parseCategoryFromUrl(food.url, fname)
        return parseLiquidVolumeMl(fname, food.unit, food.water, resolvedCategory)
    }

    fun parseLiquidVolumeMl(title: String?, unitStr: String?, waterStr: String?, category: FoodCategory? = null): Double {
        // Strictly only beverages count towards fluid hydration
        if (category != FoodCategory.BEVERAGE) {
            return 0.0
        }

        if (unitStr.isNullOrBlank()) {
            return parseNutrient(waterStr)
        }

        val normalized = unitStr.replace("\u00a0", " ").replace(",", ".").trim()
        val cleaned = normalized.replace(DIGIT_SPACE_DIGIT_REGEX, "$1$2")

        // 1. Multiplier with paren: '1 x balení (1 000 ml)' or '1 x láhev (1500 g)'
        val parenMatch = PAREN_UNIT_REGEX.find(cleaned)
        if (parenMatch != null) {
            val amt = parenMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            val unit = parenMatch.groupValues[2].lowercase()
            val count = COUNT_X_REGEX.find(cleaned)?.groupValues?.get(1)?.toDoubleOrNull() ?: 1.0
            val ml = convertToMl(count * amt, unit)
            if (ml > 0) return ml
        }

        // 2. Multiplier: '300 x 1 ml', '500 x 1 g', '1 x 1.5 l', etc.
        val multMatch = MULT_LIQUID_REGEX.find(cleaned)
        if (multMatch != null) {
            val count = multMatch.groupValues[1].toDoubleOrNull() ?: 1.0
            val amt = multMatch.groupValues[2].takeIf { it.isNotBlank() }?.toDoubleOrNull() ?: 1.0
            val unit = multMatch.groupValues[3].lowercase()
            val ml = convertToMl(count * amt, unit)
            if (ml > 0) return ml
        }

        // 3. Simple liquid: '250 ml', '1.5 l', '1500 g'
        val simpleMatch = SIMPLE_LIQUID_REGEX.find(cleaned)
        if (simpleMatch != null) {
            val amt = simpleMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            val unit = simpleMatch.groupValues[2].lowercase()
            val ml = convertToMl(amt, unit)
            if (ml > 0) return ml
        }

        return parseNutrient(waterStr)
    }

    private fun convertToMl(amount: Double, unit: String): Double {
        return when (unit) {
            "ml" -> amount
            "l" -> amount * 1000.0
            "dl" -> amount * 100.0
            "cl" -> amount * 10.0
            "g" -> amount
            "kg" -> amount * 1000.0
            else -> 0.0
        }
    }
}
