package com.example.kthealthsync.domain.util

import com.example.kthealthsync.Foodstuff
import com.example.kthealthsync.domain.model.FoodCategory
import com.example.kthealthsync.domain.util.NutrientParser.parseNutrient
import com.example.kthealthsync.domain.util.NutrientParser.parsePortionGrams

object HydrationCalculator {

    private val DIGIT_SPACE_DIGIT_REGEX = "(\\d)\\s+(\\d)".toRegex()
    private val SIMPLE_COUNT_REGEX = "^([\\d.]+)\\s*(?:ks|kus|piece|шт)?$".toRegex()
    private val PAREN_UNIT_REGEX = "\\(([\\d.]+)\\s*([a-zA-Z]+)\\)".toRegex()
    private val COUNT_X_REGEX = "([\\d.]+)\\s*x".toRegex()
    private val MULT_LIQUID_REGEX = "([\\d.]+)\\s*x\\s*([\\d.]+)?\\s*(ml|l|dl|cl|g|kg)\\b".toRegex(RegexOption.IGNORE_CASE)
    private val SIMPLE_LIQUID_REGEX = "([\\d.]+)\\s*(ml|l|dl|cl|g|kg)\\b".toRegex(RegexOption.IGNORE_CASE)
    private val MULT_DIGITS_REGEX = "([\\d.]+)\\s*x\\s*([\\d.]+)".toRegex()
    private val NUM_REGEX = "([\\d.]+)".toRegex()

    /**
     * Parses the liquid volume in milliliters (ml) for beverages and hydration.
     * Accurately extracts fluid amounts from unit strings like "200 x 1 ml", "3 x 100 ml",
     * "1 111 x 1 ml", "1 x 1.5 l", "balení (1 000 ml)", etc.
     * Excludes cooking oils and solid food moisture to conform to clinical Fluid Intake (Hydration) standards.
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

        // Exclude cooking oils, butter, lard, sauces, dressings which may be measured in ml but are not beverages
        if (titleLower.contains("olej") || titleLower.contains(" oil") ||
            titleLower.contains("sádlo") || titleLower.contains("sadlo") ||
            titleLower.contains("máslo") || titleLower.contains("maslo") ||
            titleLower.contains("butter") || titleLower.contains("lard") ||
            titleLower.contains("omáčk") || titleLower.contains("omack") ||
            titleLower.contains("sauce") || titleLower.contains("dressing") ||
            titleLower.contains("kečup") || titleLower.contains("ketchup") ||
            titleLower.contains("ocet") || titleLower.contains("vinegar") ||
            titleLower.contains("sirup") || titleLower.contains("syrup")) {
            return 0.0
        }

        // Soups and broths: water content represents fluid hydration
        if (isSoupOrBroth(titleLower)) {
            val waterVal = parseNutrient(waterStr)
            if (waterVal > 0) return waterVal
        }

        // Watermelon and melons: water content represents fluid hydration (~91.5% water)
        if (isWatermelonOrMelon(titleLower)) {
            val waterVal = parseNutrient(waterStr)
            if (waterVal > 0) return waterVal

            // Fallback if waterStr was null/not populated: 1g watermelon ≈ 0.915 ml water
            val g = parsePortionGrams(unitStr)
            if (g > 0) return g * 0.915
        }

        // Cucumbers: water content represents fluid hydration (~95.2% water)
        if (isCucumber(titleLower)) {
            val waterVal = parseNutrient(waterStr)
            if (waterVal > 0) return waterVal

            // Fallback if waterStr was null/not populated: 1g cucumber ≈ 0.952 ml water
            val g = parsePortionGrams(unitStr)
            if (g > 0) return g * 0.952

            val normalizedG = (unitStr ?: "").replace("\u00a0", " ").replace(",", ".").trim()
            val simpleCount = SIMPLE_COUNT_REGEX.find(normalizedG)?.groupValues?.get(1)?.toDoubleOrNull()
            if (simpleCount != null && simpleCount > 0) {
                return simpleCount * 250.0 * 0.952
            }
        }

        // Drinkable fermented dairy (e.g. Kefir) logged in grams: 1g ≈ 1ml
        if (isDrinkableFermentedDairy(titleLower)) {
            val ml = parsePortionGrams(unitStr)
            if (ml > 0) return ml
        }

        val isPureWater = isPureWaterOrMineral(titleLower)
        val isDrink = category == FoodCategory.BEVERAGE || isPureWater || isBeverageOrDrink(titleLower)

        if (unitStr.isNullOrBlank()) {
            if (isPureWater) {
                return parseNutrient(waterStr)
            }
            return 0.0
        }

        val normalized = unitStr.replace("\u00a0", " ").replace(",", ".").trim()
        val cleaned = normalized.replace(DIGIT_SPACE_DIGIT_REGEX, "$1$2")

        // 1. Container pattern: "balení (1000 ml)", "sklenice (250 ml)", "2 x balení (500 ml)", "1 x láhev (1500 g)"
        val parenMatch = PAREN_UNIT_REGEX.find(cleaned)
        if (parenMatch != null) {
            val count = COUNT_X_REGEX.find(cleaned)?.groupValues?.get(1)?.toDoubleOrNull() ?: 1.0
            val amount = parenMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            val unit = parenMatch.groupValues[2].lowercase()
            val ml = convertToMl(count * amount, unit, isDrink)
            if (ml > 0) return ml
        }

        // 2. Multiplier pattern: "200 x 1 ml", "3 x 100 ml", "1 x 0.5 l", "1 x 2 dl", "1 x 25 cl", "900 x 1 g"
        val multMatch = MULT_LIQUID_REGEX.find(cleaned)
        if (multMatch != null) {
            val count = multMatch.groupValues[1].toDoubleOrNull() ?: 1.0
            val amount = multMatch.groupValues[2].takeIf { it.isNotBlank() }?.toDoubleOrNull() ?: 1.0
            val unit = multMatch.groupValues[3].lowercase()
            val ml = convertToMl(count * amount, unit, isDrink)
            if (ml > 0) return ml
        }

        // 3. Simple pattern: "500 ml", "0.5 l", "2 dl", "25 cl", "900 g"
        val simpleMatch = SIMPLE_LIQUID_REGEX.find(cleaned)
        if (simpleMatch != null) {
            val amount = simpleMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            val unit = simpleMatch.groupValues[2].lowercase()
            val ml = convertToMl(amount, unit, isDrink)
            if (ml > 0) return ml
        }

        // 4. Fallback for pure water / mineral water logged in grams or pure number
        if (isPureWater) {
            val waterVal = parseNutrient(waterStr)
            if (waterVal > 0) return waterVal

            // Extract numeric digits from unitStr (e.g. "900", "1500", "900 x 1")
            val multG = MULT_DIGITS_REGEX.find(cleaned)
            if (multG != null) {
                val count = multG.groupValues[1].toDoubleOrNull() ?: 1.0
                val amount = multG.groupValues[2].toDoubleOrNull() ?: 1.0
                val g = count * amount
                if (g > 0) return g
            }
            val match = NUM_REGEX.find(cleaned)
            if (match != null) {
                val amount = match.groupValues[1].toDoubleOrNull() ?: 0.0
                if (amount > 0) return amount
            }
        }

        return 0.0
    }

    private fun isPureWaterOrMineral(titleLower: String): Boolean {
        return titleLower.contains("voda") || titleLower.contains("water") ||
                titleLower.contains("magnesia") || titleLower.contains("mattoni") ||
                titleLower.contains("aquila") || titleLower.contains("rajec") ||
                titleLower.contains("korunní") || titleLower.contains("korunni") ||
                titleLower.contains("poděbrad") || titleLower.contains("podebrad") ||
                titleLower.contains("vincentka") || titleLower.contains("bílinsk") ||
                titleLower.contains("bilinsk") || titleLower.contains("san pellegrino") ||
                titleLower.contains("perrier") || titleLower.contains("evian") ||
                titleLower.contains("bonaqua") || titleLower.contains("gemerka") ||
                titleLower.contains("kláštorn") || titleLower.contains("klastorn") ||
                titleLower.contains("fatra") || titleLower.contains("budiš") ||
                titleLower.contains("budis") || titleLower.contains("mitick") ||
                titleLower.contains("baldovsk") || titleLower.contains("šaratic") ||
                titleLower.contains("saratic") || titleLower.contains("minerál") ||
                titleLower.contains("mineral") || titleLower.contains("минерал")
    }

    private val DRINK_WORD_REGEX = Regex(
        """(?:\b|^|[\s,\-_()/'"”’])(?:tea|teas|iced\s*tea|čaj\w*|caj\w*|чай\w*|káva\w*|kava\w*|coffee|кофе\w*|espresso|cappuccino|latte|latté|džus\w*|dzus\w*|juice\w*|сок[а-я]*|mošt\w*|limonád\w*|limonad\w*|lemonade\w*|лимонад\w*|nápoj\w*|napoj\w*|напиток\w*|drink\w*|cola|coca-cola|pepsi|sprite|fanta|kombucha|birell|smoothie|kompot\w*|компот\w*|odvar\w*|infusion)(?:\b|$|[\s,\-_()/'"”’])""",
        RegexOption.IGNORE_CASE
    )

    private fun isSolidFood(titleLower: String): Boolean {
        return titleLower.contains("steak") || titleLower.contains("řízek") ||
                titleLower.contains("rizek") || titleLower.contains("kotlet") ||
                titleLower.contains("guláš") || titleLower.contains("gulas") ||
                titleLower.contains("hověz") || titleLower.contains("hovez") ||
                titleLower.contains("vepřov") || titleLower.contains("veprov") ||
                titleLower.contains("kuřec") || titleLower.contains("kurec") ||
                titleLower.contains("krůt") || titleLower.contains("krut") ||
                titleLower.contains("šunk") || titleLower.contains("sunk") ||
                titleLower.contains("klobás") || titleLower.contains("klobas") ||
                titleLower.contains("párek") || titleLower.contains("parek") ||
                titleLower.contains("salám") || titleLower.contains("salam") ||
                titleLower.contains("filet") || titleLower.contains("file") ||
                titleLower.contains("losos") || titleLower.contains("tuňák") ||
                titleLower.contains("tunak") || titleLower.contains("maso") ||
                titleLower.contains("meat") || titleLower.contains("стейк") ||
                titleLower.contains("котлет") || titleLower.contains("фарш") ||
                titleLower.contains("мясо") || titleLower.contains("говядин") ||
                titleLower.contains("свинин") || titleLower.contains("куриц") ||
                titleLower.contains("курин") || titleLower.contains("индейк") ||
                titleLower.contains("рыб") || titleLower.contains("колбас") ||
                titleLower.contains("сосиск") || titleLower.contains("ветчин")
    }

    private fun isBeverageOrDrink(titleLower: String): Boolean {
        if (isSolidFood(titleLower)) return false
        return DRINK_WORD_REGEX.containsMatchIn(titleLower)
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

    private fun isDrinkableFermentedDairy(titleLower: String): Boolean {
        return titleLower.contains("kefír") || titleLower.contains("kefir") ||
                titleLower.contains("podmáslí") || titleLower.contains("podmasli") ||
                titleLower.contains("acidofilní") || titleLower.contains("acidofilni") ||
                titleLower.contains("syrovátka") || titleLower.contains("syrovatka") ||
                titleLower.contains("jogurtový nápoj") || titleLower.contains("jogurtovy napoj")
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
