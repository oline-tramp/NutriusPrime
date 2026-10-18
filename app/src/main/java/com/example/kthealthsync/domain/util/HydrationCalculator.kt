package com.example.kthealthsync.domain.util

import com.example.kthealthsync.Foodstuff
import com.example.kthealthsync.domain.model.FoodCategory
import com.example.kthealthsync.domain.util.NutrientParser.parseNutrient
import com.example.kthealthsync.domain.util.NutrientParser.parsePortionGrams

object HydrationCalculator {

    private val DIGIT_SPACE_DIGIT_REGEX = "(\\d)\\s+(\\d)".toRegex()
    private val MULT_LIQUID_REGEX = "([\\d.]+)\\s*x\\s*([\\d.]+)?\\s*(ml|l|dl|cl|g|kg)\\b".toRegex(RegexOption.IGNORE_CASE)
    private val SIMPLE_LIQUID_REGEX = "([\\d.]+)\\s*(ml|l|dl|cl|g|kg)\\b".toRegex(RegexOption.IGNORE_CASE)
    private val PAREN_UNIT_REGEX = "\\(([\\d.]+)\\s*([a-zA-Z]+)\\)".toRegex()
    private val COUNT_X_REGEX = "([\\d.]+)\\s*x".toRegex()
    private val SIMPLE_COUNT_REGEX = "^([\\d.]+)\\s*(?:ks|kus|piece|шт)?$".toRegex()

    /**
     * Parses the liquid volume in milliliters (ml) for beverages and hydration.
     * Uses FoodCategory.BEVERAGE for high-precision drink detection,
     * food.water for soups/broths, and excludes non-beverage cooking oils/fats.
     */
    fun parseLiquidVolumeMl(food: Foodstuff, category: FoodCategory? = null): Double {
        val components = food.foodstuff ?: food.items ?: food.ingredients ?: food.components
        if (!components.isNullOrEmpty()) {
            val compVol = components.sumOf { parseLiquidVolumeMl(it, category) }
            if (compVol > 0) return compVol
        }
        val fname = food.title ?: food.name ?: food.nazev
        return parseLiquidVolumeMl(fname, food.unit, food.water, category)
    }

    fun parseLiquidVolumeMl(title: String?, unitStr: String?, waterStr: String?, category: FoodCategory? = null): Double {
        val titleLower = (title ?: "").lowercase()

        // Exclude cooking oils, butter, lard, sauces, dressings which may be measured in ml
        if (isCookingOilOrSauce(titleLower)) {
            return 0.0
        }

        // Soups and broths: water content represents fluid hydration
        if (isSoupOrBroth(titleLower)) {
            val waterVal = parseNutrient(waterStr)
            if (waterVal > 0) return waterVal
        }

        // Watermelon and melons (~91.5% water)
        if (isWatermelonOrMelon(titleLower)) {
            val waterVal = parseNutrient(waterStr)
            if (waterVal > 0) return waterVal
            val g = parsePortionGrams(unitStr)
            if (g > 0) return g * 0.915
        }

        // Cucumbers (~95.2% water)
        if (isCucumber(titleLower)) {
            val waterVal = parseNutrient(waterStr)
            if (waterVal > 0) return waterVal
            val g = parsePortionGrams(unitStr)
            if (g > 0) return g * 0.952
            val normalizedG = (unitStr ?: "").replace("\u00a0", " ").replace(",", ".").trim()
            val simpleCount = SIMPLE_COUNT_REGEX.find(normalizedG)?.groupValues?.get(1)?.toDoubleOrNull()
            if (simpleCount != null && simpleCount > 0) {
                return simpleCount * 250.0 * 0.952
            }
        }

        val isDrink = category == FoodCategory.BEVERAGE || isBeverageTitleFallback(titleLower)

        if (unitStr.isNullOrBlank()) {
            if (isDrink) {
                return parseNutrient(waterStr)
            }
            return 0.0
        }

        val normalized = unitStr.replace("\u00a0", " ").replace(",", ".").trim()
        val cleaned = normalized.replace(DIGIT_SPACE_DIGIT_REGEX, "$1$2")

        // 1. Multiplier with paren: '1 x balení (1 000 ml)' or '1 x láhev (1500 g)'
        val parenMatch = PAREN_UNIT_REGEX.find(cleaned)
        if (parenMatch != null) {
            val amt = parenMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            val unit = parenMatch.groupValues[2].lowercase()
            val count = COUNT_X_REGEX.find(cleaned)?.groupValues?.get(1)?.toDoubleOrNull() ?: 1.0
            val ml = convertToMl(count * amt, unit, isDrink)
            if (ml > 0) return ml
        }

        // 2. Liquid multiplier: '300 x 1 ml', '500 x 1 g', '1 x 1.5 l', etc.
        val multMatch = MULT_LIQUID_REGEX.find(cleaned)
        if (multMatch != null) {
            val count = multMatch.groupValues[1].toDoubleOrNull() ?: 1.0
            val amt = multMatch.groupValues[2].takeIf { it.isNotBlank() }?.toDoubleOrNull() ?: 1.0
            val unit = multMatch.groupValues[3].lowercase()
            val ml = convertToMl(count * amt, unit, isDrink)
            if (ml > 0) return ml
        }

        // 3. Simple liquid: '250 ml', '1.5 l', '1500 g'
        val simpleMatch = SIMPLE_LIQUID_REGEX.find(cleaned)
        if (simpleMatch != null) {
            val amt = simpleMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            val unit = simpleMatch.groupValues[2].lowercase()
            val ml = convertToMl(amt, unit, isDrink)
            if (ml > 0) return ml
        }

        return 0.0
    }

    private fun isCookingOilOrSauce(titleLower: String): Boolean {
        return titleLower.contains("olej") || titleLower.contains(" oil") ||
                titleLower.contains("sádlo") || titleLower.contains("sadlo") ||
                titleLower.contains("máslo") || titleLower.contains("maslo") ||
                titleLower.contains("butter") || titleLower.contains("lard") ||
                titleLower.contains("omáčk") || titleLower.contains("omack") ||
                titleLower.contains("sauce") || titleLower.contains("dressing") ||
                titleLower.contains("kečup") || titleLower.contains("ketchup") ||
                titleLower.contains("ocet") || titleLower.contains("vinegar") ||
                titleLower.contains("sirup") || titleLower.contains("syrup")
    }

    private fun isSoupOrBroth(titleLower: String): Boolean {
        return titleLower.contains("polévk") || titleLower.contains("polevk") ||
                titleLower.contains("vývar") || titleLower.contains("vyvar") ||
                titleLower.contains("soup") || titleLower.contains("broth") ||
                titleLower.contains("boršč") || titleLower.contains("borsch") ||
                titleLower.contains("ramen") || titleLower.contains("pho") ||
                titleLower.contains("kulajda") || titleLower.contains("česnečk") ||
                titleLower.contains("soljanka")
    }

    private fun isWatermelonOrMelon(titleLower: String): Boolean {
        if (titleLower.contains("sirup") || titleLower.contains("syrup") ||
            titleLower.contains("žvýkačk") || titleLower.contains("zvykack") ||
            titleLower.contains("bonbon") || titleLower.contains("aroma") ||
            titleLower.contains("likér") || titleLower.contains("liker")) {
            return false
        }
        return titleLower.contains("meloun") ||
                titleLower.contains("watermelon") ||
                titleLower.contains("арбуз") ||
                titleLower.contains("дыня") ||
                titleLower.contains("cantaloupe")
    }

    private fun isCucumber(titleLower: String): Boolean {
        if (titleLower.contains("kysel") || titleLower.contains("nakládan") ||
            titleLower.contains("nakladan") || titleLower.contains("steril") ||
            titleLower.contains("pickl") || titleLower.contains("солен") ||
            titleLower.contains("маринован") || titleLower.contains("квашен") ||
            titleLower.contains("znojmo") || titleLower.contains("relish")) {
            return false
        }
        return titleLower.contains("okurk") ||
                titleLower.contains("cucumber") ||
                titleLower.contains("огурец") ||
                titleLower.contains("огурц") ||
                titleLower.contains("gurke")
    }

    private val TEA_WORD_REGEX = "\\b(?:tea|teas)\\b".toRegex(RegexOption.IGNORE_CASE)

    /** Fallback when category is not provided (e.g. standalone tests or raw input). */
    private fun isBeverageTitleFallback(titleLower: String): Boolean {
        return titleLower.contains("voda") || titleLower.contains("water") ||
                titleLower.contains("minerál") || titleLower.contains("mineral") ||
                titleLower.contains("čaj") || titleLower.contains("чай") || TEA_WORD_REGEX.containsMatchIn(titleLower) ||
                titleLower.contains("káva") || titleLower.contains("coffee") || titleLower.contains("кофе") ||
                titleLower.contains("džus") || titleLower.contains("juice") || titleLower.contains("сок") ||
                titleLower.contains("kefír") || titleLower.contains("kefir") ||
                titleLower.contains("birell") || titleLower.contains("cola") ||
                titleLower.contains("magnesia") || titleLower.contains("mattoni")
    }

    private fun convertToMl(amount: Double, unit: String, isDrink: Boolean = false): Double {
        return when (unit) {
            "ml" -> amount
            "l" -> amount * 1000.0
            "dl" -> amount * 100.0
            "cl" -> amount * 10.0
            "g" -> if (isDrink) amount else 0.0
            "kg" -> if (isDrink) amount * 1000.0 else 0.0
            else -> 0.0
        }
    }
}
