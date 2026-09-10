package com.example.kthealthsync.domain.util

import com.example.kthealthsync.DiaryResponse
import com.example.kthealthsync.Foodstuff
import com.example.kthealthsync.MealTime
import com.example.kthealthsync.domain.model.AppLanguage
import com.example.kthealthsync.domain.model.AppStrings
import com.example.kthealthsync.domain.model.DailySummary
import com.example.kthealthsync.domain.model.FoodBreakdown
import com.example.kthealthsync.domain.model.FoodCategory
import com.example.kthealthsync.domain.model.NutrientGoals
import com.example.kthealthsync.domain.model.ShareFormatType
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object NutrientUtils {

    /**
     * Extracts numeric value from strings like "12,5 g", "350 kcal", etc.
     */
    fun parseNutrient(value: String?): Double {
        if (value.isNullOrBlank()) return 0.0
        val cleaned = value.replace("[^0-9,.-]".toRegex(), "").replace(",", ".")
        return cleaned.toDoubleOrNull() ?: 0.0
    }

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
     * Parses the count/dose of supplements (tablets, capsules, portions, etc.).
     */
    fun parseSupplementCount(unitStr: String?): Double {
        if (unitStr.isNullOrBlank()) return 1.0
        val normalized = unitStr.replace("\u00a0", " ").replace(",", ".").trim()
        val multMatch = "([\\d.]+)\\s*x".toRegex().find(normalized)
        if (multMatch != null) {
            val mult = multMatch.groupValues[1].toDoubleOrNull() ?: 1.0
            return if (mult > 0) mult else 1.0
        }
        val firstNum = "([\\d.]+)".toRegex().find(normalized)
        if (firstNum != null) {
            val num = firstNum.groupValues[1].toDoubleOrNull() ?: 1.0
            return if (num > 0) num else 1.0
        }
        return 1.0
    }

    /**
     * Parses the liquid volume in milliliters (ml) for beverages and hydration.
     * Accurately extracts fluid amounts from unit strings like "200 x 1 ml", "3 x 100 ml",
     * "1 111 x 1 ml", "1 x 1.5 l", "balení (1 000 ml)", etc.
     * Excludes cooking oils and solid food moisture to conform to clinical Fluid Intake (Hydration) standards.
     */
    fun parseLiquidVolumeMl(food: Foodstuff): Double {
        val components = food.foodstuff ?: food.items ?: food.ingredients ?: food.components
        if (!components.isNullOrEmpty()) {
            val compVol = components.sumOf { parseLiquidVolumeMl(it) }
            if (compVol > 0) return compVol
        }
        val fname = food.title ?: food.name ?: food.nazev
        return parseLiquidVolumeMl(fname, food.unit, food.water)
    }

    fun parseLiquidVolumeMl(title: String?, unitStr: String?, waterStr: String?): Double {
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
            val normalizedG = (unitStr ?: "").replace("\u00a0", " ").replace(",", ".").trim()
            val multG = "([\\d.]+)\\s*x\\s*([\\d.]+)?\\s*g".toRegex().find(normalizedG)
            if (multG != null) {
                val count = multG.groupValues[1].toDoubleOrNull() ?: 1.0
                val amount = multG.groupValues[2].takeIf { it.isNotBlank() }?.toDoubleOrNull() ?: 1.0
                val g = count * amount
                if (g > 0) return g * 0.915
            }
            val simpleG = "([\\d.]+)\\s*g".toRegex().find(normalizedG)
            if (simpleG != null) {
                val amount = simpleG.groupValues[1].toDoubleOrNull() ?: 0.0
                if (amount > 0) return amount * 0.915
            }
            val multKg = "([\\d.]+)\\s*x\\s*([\\d.]+)?\\s*kg".toRegex().find(normalizedG)
            if (multKg != null) {
                val count = multKg.groupValues[1].toDoubleOrNull() ?: 1.0
                val amount = multKg.groupValues[2].takeIf { it.isNotBlank() }?.toDoubleOrNull() ?: 1.0
                val g = count * amount * 1000.0
                if (g > 0) return g * 0.915
            }
            val simpleKg = "([\\d.]+)\\s*kg".toRegex().find(normalizedG)
            if (simpleKg != null) {
                val amount = simpleKg.groupValues[1].toDoubleOrNull() ?: 0.0
                if (amount > 0) return amount * 1000.0 * 0.915
            }
            val parenG = "\\(([\\d.]+)\\s*g\\)".toRegex().find(normalizedG)
            if (parenG != null) {
                val count = "([\\d.]+)\\s*x".toRegex().find(normalizedG)?.groupValues?.get(1)?.toDoubleOrNull() ?: 1.0
                val amount = parenG.groupValues[1].toDoubleOrNull() ?: 0.0
                val g = count * amount
                if (g > 0) return g * 0.915
            }
        }

        // Cucumbers: water content represents fluid hydration (~95.2% water)
        if (isCucumber(titleLower)) {
            val waterVal = parseNutrient(waterStr)
            if (waterVal > 0) return waterVal

            // Fallback if waterStr was null/not populated: 1g cucumber ≈ 0.952 ml water
            val normalizedG = (unitStr ?: "").replace("\u00a0", " ").replace(",", ".").trim()
            val multG = "([\\d.]+)\\s*x\\s*([\\d.]+)?\\s*g".toRegex().find(normalizedG)
            if (multG != null) {
                val count = multG.groupValues[1].toDoubleOrNull() ?: 1.0
                val amount = multG.groupValues[2].takeIf { it.isNotBlank() }?.toDoubleOrNull() ?: 1.0
                val g = count * amount
                if (g > 0) return g * 0.952
            }
            val simpleG = "([\\d.]+)\\s*g".toRegex().find(normalizedG)
            if (simpleG != null) {
                val amount = simpleG.groupValues[1].toDoubleOrNull() ?: 0.0
                if (amount > 0) return amount * 0.952
            }
            val multKg = "([\\d.]+)\\s*x\\s*([\\d.]+)?\\s*kg".toRegex().find(normalizedG)
            if (multKg != null) {
                val count = multKg.groupValues[1].toDoubleOrNull() ?: 1.0
                val amount = multKg.groupValues[2].takeIf { it.isNotBlank() }?.toDoubleOrNull() ?: 1.0
                val g = count * amount * 1000.0
                if (g > 0) return g * 0.952
            }
            val simpleKg = "([\\d.]+)\\s*kg".toRegex().find(normalizedG)
            if (simpleKg != null) {
                val amount = simpleKg.groupValues[1].toDoubleOrNull() ?: 0.0
                if (amount > 0) return amount * 1000.0 * 0.952
            }
            val parenG = "\\(([\\d.]+)\\s*g\\)".toRegex().find(normalizedG)
            if (parenG != null) {
                val count = "([\\d.]+)\\s*x".toRegex().find(normalizedG)?.groupValues?.get(1)?.toDoubleOrNull() ?: 1.0
                val amount = parenG.groupValues[1].toDoubleOrNull() ?: 0.0
                val g = count * amount
                if (g > 0) return g * 0.952
            }
            val simpleCountRegex = "^([\\d.]+)\\s*(?:ks|kus|piece|шт)?$".toRegex()
            val simpleCount = simpleCountRegex.find(normalizedG)?.groupValues?.get(1)?.toDoubleOrNull()
            if (simpleCount != null && simpleCount > 0) {
                return simpleCount * 250.0 * 0.952
            }
        }

        // Drinkable fermented dairy (e.g. Kefir) logged in grams: 1g ≈ 1ml
        if (isDrinkableFermentedDairy(titleLower)) {
            val normalizedG = (unitStr ?: "").replace("\u00a0", " ").replace(",", ".").trim()
            val multG = "([\\d.]+)\\s*x\\s*([\\d.]+)?\\s*g".toRegex().find(normalizedG)
            if (multG != null) {
                val count = multG.groupValues[1].toDoubleOrNull() ?: 1.0
                val amount = multG.groupValues[2].takeIf { it.isNotBlank() }?.toDoubleOrNull() ?: 1.0
                val ml = count * amount
                if (ml > 0) return ml
            }
            val simpleG = "([\\d.]+)\\s*g".toRegex().find(normalizedG)
            if (simpleG != null) {
                val amount = simpleG.groupValues[1].toDoubleOrNull() ?: 0.0
                if (amount > 0) return amount
            }
        }

        val isPureWater = isPureWaterOrMineral(titleLower)
        val isDrink = isPureWater || isBeverageOrDrink(titleLower)

        if (unitStr.isNullOrBlank()) {
            if (isPureWater) {
                return parseNutrient(waterStr)
            }
            return 0.0
        }

        val normalized = unitStr.replace("\u00a0", " ").replace(",", ".").trim()
        val cleaned = normalized.replace("(\\d)\\s+(\\d)".toRegex(), "$1$2")

        // 1. Container pattern: "balení (1000 ml)", "sklenice (250 ml)", "2 x balení (500 ml)", "1 x láhev (1500 g)"
        val parenRegex = "\\(([\\d.]+)\\s*([a-zA-Z]+)\\)".toRegex()
        val parenMatch = parenRegex.find(cleaned)
        if (parenMatch != null) {
            val countRegex = "([\\d.]+)\\s*x".toRegex()
            val count = countRegex.find(cleaned)?.groupValues?.get(1)?.toDoubleOrNull() ?: 1.0
            val amount = parenMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            val unit = parenMatch.groupValues[2].lowercase()
            val ml = convertToMl(count * amount, unit, isDrink)
            if (ml > 0) return ml
        }

        // 2. Multiplier pattern: "200 x 1 ml", "3 x 100 ml", "1 x 0.5 l", "1 x 2 dl", "1 x 25 cl", "900 x 1 g"
        val multRegex = "([\\d.]+)\\s*x\\s*([\\d.]+)?\\s*(ml|l|dl|cl|g|kg)\\b".toRegex(RegexOption.IGNORE_CASE)
        val multMatch = multRegex.find(cleaned)
        if (multMatch != null) {
            val count = multMatch.groupValues[1].toDoubleOrNull() ?: 1.0
            val amount = multMatch.groupValues[2].takeIf { it.isNotBlank() }?.toDoubleOrNull() ?: 1.0
            val unit = multMatch.groupValues[3].lowercase()
            val ml = convertToMl(count * amount, unit, isDrink)
            if (ml > 0) return ml
        }

        // 3. Simple pattern: "500 ml", "0.5 l", "2 dl", "25 cl", "900 g"
        val simpleRegex = "([\\d.]+)\\s*(ml|l|dl|cl|g|kg)\\b".toRegex(RegexOption.IGNORE_CASE)
        val simpleMatch = simpleRegex.find(cleaned)
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
            val multG = "([\\d.]+)\\s*x\\s*([\\d.]+)".toRegex().find(cleaned)
            if (multG != null) {
                val count = multG.groupValues[1].toDoubleOrNull() ?: 1.0
                val amount = multG.groupValues[2].toDoubleOrNull() ?: 1.0
                val g = count * amount
                if (g > 0) return g
            }
            val numRegex = "([\\d.]+)".toRegex()
            val match = numRegex.find(cleaned)
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

    private fun isBeverageOrDrink(titleLower: String): Boolean {
        return titleLower.contains("čaj") || titleLower.contains("caj") ||
                titleLower.contains("tea") || titleLower.contains("чай") ||
                titleLower.contains("káva") || titleLower.contains("kava") ||
                titleLower.contains("coffee") || titleLower.contains("кофе") ||
                titleLower.contains("espresso") || titleLower.contains("cappuccino") ||
                titleLower.contains("džus") || titleLower.contains("dzus") ||
                titleLower.contains("juice") || titleLower.contains("сок") ||
                titleLower.contains("mošt") || titleLower.contains("most") ||
                titleLower.contains("limonád") || titleLower.contains("limonad") ||
                titleLower.contains("lemonade") || titleLower.contains("лимонад") ||
                titleLower.contains("sirupový nápoj") || titleLower.contains("nápoj") ||
                titleLower.contains("napoj") || titleLower.contains("drink") ||
                titleLower.contains("cola") || titleLower.contains("pepsi") ||
                titleLower.contains("sprite") || titleLower.contains("fanta") ||
                titleLower.contains("kombucha") || titleLower.contains("birell") ||
                titleLower.contains("smoothie") || titleLower.contains("kompot") ||
                titleLower.contains("odvar") || titleLower.contains("infusion")
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

    /**
     * Safely extracts nutritional keywords from HTML containing <script type="application/ld+json">.
     * Uses Gson for pure Java portability (doesn't depend on Android stub classes in JVM tests).
     */
    fun parseLdJsonMacros(html: String): Map<String, Double> {
        val startTag = "<script type=\"application/ld+json\">"
        val endTag = "</script>"
        val startIndex = html.indexOf(startTag)
        if (startIndex == -1) return emptyMap()

        val jsonStart = startIndex + startTag.length
        val endIndex = html.indexOf(endTag, jsonStart)
        if (endIndex == -1) return emptyMap()

        val jsonStr = html.substring(jsonStart, endIndex).trim()
        val result = mutableMapOf<String, Double>()
        try {
            val jsonObj = com.google.gson.Gson().fromJson(jsonStr, com.google.gson.JsonObject::class.java)
            if (jsonObj != null && jsonObj.has("keywords") && jsonObj.get("keywords").isJsonArray) {
                val keywordsArr = jsonObj.getAsJsonArray("keywords")
                for (i in 0 until keywordsArr.size()) {
                    val kw = keywordsArr.get(i).asString
                    val parts = kw.split(":").map { s -> s.trim() }
                    if (parts.size >= 2) {
                        val key = parts[0].lowercase()
                        val rawVal = parts[1].replace(",", ".").replace("g", "").replace("mg", "").trim()
                        val num = rawVal.toDoubleOrNull() ?: 0.0
                        result[key] = num
                    }
                }
            }
        } catch (_: Exception) {
            // Return whatever was successfully parsed
        }
        return result
    }

    /**
     * Parses the portion mass in grams from Kalorické Tabulky unit strings.
     * Supports patterns like:
     * - "150 x 1 g", "2 x 100 g", "0.5 x 1 kg"
     * - "1 x kus (120 g)", "2 x porce (200 g)", "1 x balení (0.5 kg)"
     * - "150 g", "0.25 kg", "200 ml" (1 ml ≈ 1 g for purees/smoothies)
     */
    fun parsePortionGrams(unitStr: String?): Double {
        if (unitStr.isNullOrBlank()) return 0.0
        val normalized = unitStr.replace("\u00a0", " ").replace(",", ".").trim()
        val cleaned = normalized.replace("(\\d)\\s+(\\d)".toRegex(), "$1$2")

        // 1. Multiplier with paren: '2 x kus (150 g)' or '1 x balení (0.5 kg)'
        val multParen = "([\\d.]+)\\s*x[^(]*\\(([\\d.]+)\\s*(g|kg|ml|l)\\)".toRegex(RegexOption.IGNORE_CASE).find(cleaned)
        if (multParen != null) {
            val count = multParen.groupValues[1].toDoubleOrNull() ?: 1.0
            val amt = multParen.groupValues[2].toDoubleOrNull() ?: 0.0
            val unit = multParen.groupValues[3].lowercase()
            val scale = if (unit == "kg" || unit == "l") 1000.0 else 1.0
            return count * amt * scale
        }

        // 2. Paren only: 'kus (150 g)' or 'balení (0.5 kg)'
        val paren = "\\(([\\d.]+)\\s*(g|kg|ml|l)\\)".toRegex(RegexOption.IGNORE_CASE).find(cleaned)
        if (paren != null) {
            val amt = paren.groupValues[1].toDoubleOrNull() ?: 0.0
            val unit = paren.groupValues[2].lowercase()
            val scale = if (unit == "kg" || unit == "l") 1000.0 else 1.0
            return amt * scale
        }

        // 3. Multiplier: '150 x 1 g' or '2 x 100 g' or '0.5 x 1 kg'
        val mult = "([\\d.]+)\\s*x\\s*([\\d.]+)?\\s*(g|kg|ml|l)\\b".toRegex(RegexOption.IGNORE_CASE).find(cleaned)
        if (mult != null) {
            val count = mult.groupValues[1].toDoubleOrNull() ?: 1.0
            val amt = mult.groupValues[2].takeIf { it.isNotBlank() }?.toDoubleOrNull() ?: 1.0
            val unit = mult.groupValues[3].lowercase()
            val scale = if (unit == "kg" || unit == "l") 1000.0 else 1.0
            return count * amt * scale
        }

        // 4. Simple: '150 g' or '0.5 kg' or '200 ml'
        val simple = "([\\d.]+)\\s*(g|kg|ml|l)\\b".toRegex(RegexOption.IGNORE_CASE).find(cleaned)
        if (simple != null) {
            val amt = simple.groupValues[1].toDoubleOrNull() ?: 0.0
            val unit = simple.groupValues[2].lowercase()
            val scale = if (unit == "kg" || unit == "l") 1000.0 else 1.0
            return amt * scale
        }

        return 0.0
    }

    /**
     * Extracts food category from official Kalorické Tabulky breadcrumb links.
     * Breadcrumbs follow patterns like:
     * - href="/zelenina" or href="/zelenina/zelenina-cerstva"
     * - href="/ovoce" or href="/ovoce/ovoce-cerstve"
     *
     * Starchy tubers like potatoes (/zelenina/brambory-a-topinambury or /jidla-a-prilohy/prilohy-brambory*)
     * are excluded from the WHO 400g/day fruit & vegetable recommendation.
     */
    fun parseCategoryFromHtml(html: String, foodTitle: String? = null): FoodCategory {
        val crumbRegex = "class=\"crumb-link\"\\s+href=\"(/[^\"]+)\"".toRegex()
        val allHrefs = crumbRegex.findAll(html).map { it.groupValues[1].lowercase() }.toList()

        // If any crumb marks it as potatoes/topinambur, exclude from 400g quota (starchy tubers per WHO)
        if (allHrefs.any { it.contains("brambor") || it.contains("topinambur") }) {
            return FoodCategory.OTHER
        }

        for (href in allHrefs) {
            if (href.startsWith("/zelenina")) {
                return FoodCategory.VEGETABLE
            }
            if (href.startsWith("/ovoce")) {
                return FoodCategory.FRUIT
            }
            if (href.startsWith("/doplnky-vyzivy")) {
                val titleLower = (foodTitle ?: "").lowercase()
                if (isProteinPowderOrBar(titleLower)) {
                    return FoodCategory.OTHER
                }
                return FoodCategory.SUPPLEMENT
            }
        }
        return FoodCategory.OTHER
    }

    fun isProteinPowderOrBar(titleLower: String): Boolean {
        if (titleLower.contains("protein") ||
            titleLower.contains("isolate") ||
            titleLower.contains("izolát") ||
            titleLower.contains("izolat") ||
            titleLower.contains("whey") ||
            titleLower.contains("casein") ||
            titleLower.contains("kasein") ||
            titleLower.contains("gainer") ||
            titleLower.contains("syrovátkový") ||
            titleLower.contains("syrovatkovy")
        ) {
            return true
        }
        return false
    }

    /**
     * Formats the full daily diary context (totals, goals, remaining balance, and all meals/foods)
     * into a clean, structured prompt ready to be sent to an LLM (ChatGPT, Claude, Gemini, etc.).
     */
    fun formatDailyContextForLlm(
        dateStr: String,
        summary: DailySummary,
        goals: NutrientGoals,
        diaryResponse: DiaryResponse?
    ): String = formatDailyShareText(ShareFormatType.HOW_TO_HIT_GOALS_LLM, dateStr, summary, goals, diaryResponse)

    /**
     * Unified daily share text formatter supporting multiple scenarios:
     * - HOW_TO_HIT_GOALS_LLM: LLM prompt for hitting remaining daily goals
     * - DAILY_SUMMARY: Clean report for coaches, notes, or archiving
     * - DIET_ANALYSIS_LLM: LLM prompt for evaluating overall diet quality & habits
     * - FOOD_LIST_ONLY: Compact diary of eaten foods
     * - JSON_EXPORT: Structured JSON data
     */
    fun formatDailyShareText(
        type: ShareFormatType,
        dateStr: String,
        summary: DailySummary,
        goals: NutrientGoals,
        diaryResponse: DiaryResponse?
    ): String {
        return when (type) {
            ShareFormatType.WHOOP_BEHAVIORS_LLM -> formatWhoopBehaviorsPrompt(dateStr, summary, diaryResponse)

            ShareFormatType.HOW_TO_HIT_GOALS_LLM -> formatPrompt(
                prefix = "Ты персональный нутрициолог. Проанализируй мой рацион за день и подскажи, что мне лучше съесть, чтобы добрать оставшиеся нормы нутриентов.",
                dateStr = dateStr,
                summary = summary,
                goals = goals,
                diaryResponse = diaryResponse,
                postfix = """
                    ---
                    Вопрос к тебе: Исходя из уже съеденного за день и оставшихся целевых норм:
                    1. Что мне лучше доесть сегодня (на ужин или перекус), чтобы сбалансировать КБЖУ, клетчатку, воду и овощи?
                    2. Предложи конкретные варианты продуктов или простых блюд с примерными граммовками.
                    3. Есть ли перекосы в рационе за сегодня, на которые стоит обратить внимание?
                """.trimIndent()
            )

            ShareFormatType.DAILY_SUMMARY -> formatPrompt(
                prefix = "📊 ДНЕВНОЙ ОТЧЁТ ПИТАНИЯ",
                dateStr = dateStr,
                summary = summary,
                goals = goals,
                diaryResponse = diaryResponse,
                postfix = null
            )

            ShareFormatType.DIET_ANALYSIS_LLM -> formatPrompt(
                prefix = "Ты эксперт по доказательной нутрициологии и здоровому питанию. Проанализируй качество и баланс моего рациона за день.",
                dateStr = dateStr,
                summary = summary,
                goals = goals,
                diaryResponse = diaryResponse,
                postfix = """
                    ---
                    Вопросы для анализа:
                    1. Насколько рацион сбалансирован по макро- и микронутриентам (клетчатка, сахар, соль, овощи)?
                    2. Оцени качество источников белка, полезных жиров и сложных углеводов.
                    3. Достаточно ли разнообразия в растительной пище и соблюдена ли норма ВОЗ по овощам/фруктам (400 г)?
                    4. Дай 2-3 конкретных совета по улучшению рациона и пищевых привычек.
                """.trimIndent()
            )

            ShareFormatType.FOOD_LIST_ONLY -> formatFoodListOnly(dateStr, diaryResponse)

            ShareFormatType.JSON_EXPORT -> formatJsonExport(dateStr, summary, goals, diaryResponse)
        }
    }

    private fun formatPrompt(
        prefix: String,
        dateStr: String,
        summary: DailySummary,
        goals: NutrientGoals,
        diaryResponse: DiaryResponse?,
        postfix: String?
    ): String {
        val sb = StringBuilder()
        sb.appendLine(prefix)
        sb.appendLine()
        sb.appendLine("📅 ДАТА: $dateStr")
        sb.appendLine()
        sb.appendLine("📊 ИТОГИ ДНЯ (СЪЕДЕНО / ЦЕЛЕВАЯ НОРМА):")

        fun formatNutrient(name: String, eaten: Double, target: Double, unit: String, isUpperLimit: Boolean = false): String {
            if (target <= 0) {
                return "• $name: ${String.format(Locale.US, "%.1f", eaten)} $unit (норма не установлена)"
            }
            val diff = target - eaten
            val diffStr = if (diff > 0.05) {
                if (isUpperLimit) "остаток до лимита: ${String.format(Locale.US, "%.1f", diff)} $unit"
                else "не хватает: ${String.format(Locale.US, "%.1f", diff)} $unit"
            } else if (diff < -0.05) {
                if (isUpperLimit) "превышение лимита на: ${String.format(Locale.US, "%.1f", -diff)} $unit"
                else "цель достигнута (+${String.format(Locale.US, "%.1f", -diff)} $unit)"
            } else "норма выполнена"
            return "• $name: ${String.format(Locale.US, "%.1f", eaten)} / ${String.format(Locale.US, "%.1f", target)} $unit ($diffStr)"
        }

        sb.appendLine(formatNutrient("Калории", summary.kcal, goals.kcal, "ккал"))
        sb.appendLine(formatNutrient("Белки", summary.protein, goals.protein, "г"))
        sb.appendLine(formatNutrient("Жиры", summary.fat, goals.fat, "г"))
        sb.appendLine(formatNutrient("Углеводы", summary.carb, goals.carbs, "г"))
        sb.appendLine(formatNutrient("Клетчатка", summary.fiber, goals.fiber, "г"))
        sb.appendLine(formatNutrient("Сахар", summary.sugar, goals.sugar, "г", isUpperLimit = true))
        sb.appendLine(formatNutrient("Соль", summary.salt, goals.salt, "г", isUpperLimit = true))
        if (goals.water > 0) {
            sb.appendLine(formatNutrient("Вода / напитки", summary.water, goals.water, "мл"))
        } else {
            sb.appendLine("• Вода / напитки: ${String.format(Locale.US, "%.1f", summary.water)} мл (норма не установлена)")
        }

        val totalVegFruit = summary.vegGrams + summary.fruitGrams
        val vegFruitTarget = if (goals.vegFruit > 0) goals.vegFruit else 400.0
        val vegFruitDiff = vegFruitTarget - totalVegFruit
        val vegFruitDiffStr = if (vegFruitDiff > 0.5) "не хватает: ${vegFruitDiff.toInt()} г" else "норма выполнена"
        sb.appendLine("• Овощи и фрукты (норма ВОЗ): ${totalVegFruit.toInt()} / ${vegFruitTarget.toInt()} г (овощи: ${summary.vegGrams.toInt()} г, фрукты: ${summary.fruitGrams.toInt()} г, $vegFruitDiffStr)")
        if (summary.supplementCount > 0) {
            sb.appendLine("• 💊 Суплементы / витамины: ${summary.supplementCount} шт.")
        }

        sb.appendLine()
        sb.appendLine("🍽 СЪЕДЕННЫЕ ПРИЁМЫ ПИЩИ:")

        val mealNames = listOf(
            "Завтрак / Snídaně",
            "Утренний перекус / Dopolední svačina",
            "Обед / Oběd",
            "Дневной перекус / Odpolední svačina",
            "Ужин / Večeře",
            "Поздний ужин / Druhá večeře"
        )

        val times = diaryResponse?.data?.times
        var hasAnyFood = false
        if (!times.isNullOrEmpty()) {
            times.forEachIndexed { index, mealTime ->
                val foods = mealTime.foodstuff?.filter { !it.title.isNullOrBlank() || !it.name.isNullOrBlank() || !it.nazev.isNullOrBlank() }
                if (!foods.isNullOrEmpty()) {
                    hasAnyFood = true
                    val mealTitle = mealTime.title?.takeIf { it.isNotBlank() }
                        ?: mealTime.name?.takeIf { it.isNotBlank() }
                        ?: mealNames.getOrElse(index) { "Приём пищи ${index + 1}" }
                    sb.appendLine()
                    sb.appendLine("[$mealTitle]")
                    for (f in foods) {
                        val fName = f.title ?: f.name ?: f.nazev ?: "Продукт"
                        val fUnit = f.unit?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
                        val fKcal = parseNutrient(f.energy).toInt()
                        val fP = parseNutrient(f.protein)
                        val fF = parseNutrient(f.fat)
                        val fC = parseNutrient(f.carbohydrate)
                        val fWater = parseLiquidVolumeMl(f).toInt()

                        val details = mutableListOf<String>()
                        if (fKcal > 0) details.add("$fKcal ккал")
                        if (fP > 0) details.add("Б: ${String.format(Locale.US, "%.1f", fP)}г")
                        if (fF > 0) details.add("Ж: ${String.format(Locale.US, "%.1f", fF)}г")
                        if (fC > 0) details.add("У: ${String.format(Locale.US, "%.1f", fC)}г")
                        if (fWater > 0) details.add("Вода: ${fWater}мл")

                        val detailsStr = if (details.isNotEmpty()) " — ${details.joinToString(", ")}" else ""
                        sb.appendLine("- $fName$fUnit$detailsStr")
                    }
                }
            }
        }
        if (!hasAnyFood) {
            sb.appendLine("За этот день записей о еде пока нет.")
        }

        if (!postfix.isNullOrBlank()) {
            sb.appendLine()
            sb.appendLine(postfix)
        }

        return sb.toString().trim()
    }

    private fun formatFoodListOnly(dateStr: String, diaryResponse: DiaryResponse?): String {
        val sb = StringBuilder()
        sb.appendLine("🍽 ДНЕВНИК ПИТАНИЯ")
        sb.appendLine("📅 ДАТА: $dateStr")

        val mealNames = listOf(
            "Завтрак / Snídaně",
            "Утренний перекус / Dopolední svačina",
            "Обед / Oběd",
            "Дневной перекус / Odpolední svačina",
            "Ужин / Večeře",
            "Поздний ужин / Druhá večeře"
        )

        val times = diaryResponse?.data?.times
        var hasAnyFood = false
        if (!times.isNullOrEmpty()) {
            times.forEachIndexed { index, mealTime ->
                val foods = mealTime.foodstuff?.filter { !it.title.isNullOrBlank() || !it.name.isNullOrBlank() || !it.nazev.isNullOrBlank() }
                if (!foods.isNullOrEmpty()) {
                    hasAnyFood = true
                    val mealTitle = mealTime.title?.takeIf { it.isNotBlank() }
                        ?: mealTime.name?.takeIf { it.isNotBlank() }
                        ?: mealNames.getOrElse(index) { "Приём пищи ${index + 1}" }
                    sb.appendLine()
                    sb.appendLine("[$mealTitle]")
                    for (f in foods) {
                        val fName = f.title ?: f.name ?: f.nazev ?: "Продукт"
                        val fUnit = f.unit?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
                        val fKcal = parseNutrient(f.energy).toInt()
                        val kcalStr = if (fKcal > 0) " — $fKcal ккал" else ""
                        sb.appendLine("• $fName$fUnit$kcalStr")
                    }
                }
            }
        }
        if (!hasAnyFood) {
            sb.appendLine()
            sb.appendLine("За этот день записей о еде пока нет.")
        }
        return sb.toString().trim()
    }

    private fun formatJsonExport(
        dateStr: String,
        summary: DailySummary,
        goals: NutrientGoals,
        diaryResponse: DiaryResponse?
    ): String {
        val mealNames = listOf(
            "Breakfast",
            "Morning Snack",
            "Lunch",
            "Afternoon Snack",
            "Dinner",
            "Second Dinner"
        )

        val mealsList = mutableListOf<Map<String, Any?>>()
        diaryResponse?.data?.times?.forEachIndexed { index, mealTime ->
            val foods = mealTime.foodstuff?.filter { !it.title.isNullOrBlank() || !it.name.isNullOrBlank() }
            if (!foods.isNullOrEmpty()) {
                val mealTitle = mealTime.title?.takeIf { it.isNotBlank() }
                    ?: mealTime.name?.takeIf { it.isNotBlank() }
                    ?: mealNames.getOrElse(index) { "Meal ${index + 1}" }
                val foodItems = foods.map { f ->
                    linkedMapOf(
                        "name" to (f.title ?: f.name ?: f.nazev ?: "Food"),
                        "unit" to f.unit,
                        "kcal" to parseNutrient(f.energy),
                        "protein_g" to parseNutrient(f.protein),
                        "fat_g" to parseNutrient(f.fat),
                        "carb_g" to parseNutrient(f.carbohydrate),
                        "fiber_g" to parseNutrient(f.fiber),
                        "sugar_g" to parseNutrient(f.sugar),
                        "salt_g" to parseNutrient(f.salt),
                        "water_ml" to parseLiquidVolumeMl(f)
                    )
                }
                mealsList.add(
                    linkedMapOf(
                        "meal" to mealTitle,
                        "items" to foodItems
                    )
                )
            }
        }

        val jsonMap = linkedMapOf(
            "date" to dateStr,
            "totals" to linkedMapOf(
                "kcal" to summary.kcal,
                "protein_g" to summary.protein,
                "fat_g" to summary.fat,
                "carb_g" to summary.carb,
                "fiber_g" to summary.fiber,
                "sugar_g" to summary.sugar,
                "salt_g" to summary.salt,
                "water_ml" to summary.water,
                "vegetables_g" to summary.vegGrams,
                "fruits_g" to summary.fruitGrams,
                "supplements_count" to summary.supplementCount
            ),
            "goals" to linkedMapOf(
                "kcal" to goals.kcal,
                "protein_g" to goals.protein,
                "fat_g" to goals.fat,
                "carb_g" to goals.carbs,
                "fiber_g" to goals.fiber,
                "sugar_g" to goals.sugar,
                "salt_g" to goals.salt,
                "water_ml" to goals.water,
                "vegetables_and_fruits_g" to goals.vegFruit
            ),
            "meals" to mealsList
        )

        return GsonBuilder().setPrettyPrinting().create().toJson(jsonMap)
    }

    /**
     * Formats daily nutrition and lifestyle data into an unambiguous, tool-executable prompt
     * optimized for the Whoop AI Coach to log Whoop Journal behaviors.
     */
    fun formatWhoopBehaviorsPrompt(
        dateStr: String,
        summary: DailySummary,
        diaryResponse: DiaryResponse?
    ): String {
        val isoDate = try {
            if (dateStr.matches("\\d{2}\\.\\d{2}\\.\\d{4}".toRegex())) {
                val parts = dateStr.split(".")
                "${parts[2]}-${parts[1]}-${parts[0]}"
            } else dateStr
        } catch (_: Exception) { dateStr }

        val mealTimes = diaryResponse?.data?.times ?: emptyList()
        val allFoods = mealTimes.flatMap { it.foodstuff ?: emptyList() }.filter {
            !it.title.isNullOrBlank() || !it.name.isNullOrBlank() || !it.nazev.isNullOrBlank()
        }

        fun extractWeightGrams(f: Foodstuff): Double {
            val unitStr = (f.unit ?: "").replace("\u00a0", " ").replace(",", ".").trim()
            val multMatch = "([\\d.]+)\\s*x\\s*([\\d.]+)?\\s*g\\b".toRegex(RegexOption.IGNORE_CASE).find(unitStr)
            if (multMatch != null) {
                val c = multMatch.groupValues[1].toDoubleOrNull() ?: 1.0
                val a = multMatch.groupValues[2].takeIf { it.isNotBlank() }?.toDoubleOrNull() ?: 1.0
                return c * a
            }
            val simpleMatch = "([\\d.]+)\\s*g\\b".toRegex(RegexOption.IGNORE_CASE).find(unitStr)
            if (simpleMatch != null) {
                return simpleMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            }
            val parenMatch = "\\(([\\d.]+)\\s*g\\)".toRegex(RegexOption.IGNORE_CASE).find(unitStr)
            if (parenMatch != null) {
                val c = "([\\d.]+)\\s*x".toRegex().find(unitStr)?.groupValues?.get(1)?.toDoubleOrNull() ?: 1.0
                return c * (parenMatch.groupValues[1].toDoubleOrNull() ?: 0.0)
            }
            return 0.0
        }

        // 1. Breakfast
        val breakfastMeal = mealTimes.firstOrNull { meal ->
            val t = (meal.title ?: meal.name ?: "").lowercase()
            t.contains("snídaně") || t.contains("snidane") || t.contains("breakfast") || t.contains("завтрак")
        } ?: mealTimes.firstOrNull()
        val breakfastFoods = breakfastMeal?.foodstuff?.filter { !it.title.isNullOrBlank() || !it.name.isNullOrBlank() } ?: emptyList()
        val hadBreakfast = breakfastFoods.isNotEmpty()
        val breakfastDesc = if (hadBreakfast) {
            val items = breakfastFoods.take(3).joinToString(", ") { it.title ?: it.name ?: "" }
            "YES (Morning first meal: $items)"
        } else {
            "NO (Skipped breakfast)"
        }

        // 2. Late meal / eating close to bedtime
        val nonEmptyMeals = mealTimes.filter { it.foodstuff?.isNotEmpty() == true }
        val lastMeal = nonEmptyMeals.lastOrNull()
        val lastMealTitle = (lastMeal?.title ?: lastMeal?.name ?: "").lowercase()
        val isLateMeal = lastMealTitle.contains("druhá večeře") ||
                lastMealTitle.contains("druha vecere") ||
                lastMealTitle.contains("поздний") ||
                lastMealTitle.contains("второй ужин") ||
                lastMealTitle.contains("late")
        val lateMealDesc = if (isLateMeal) {
            val items = lastMeal?.foodstuff?.take(3)?.joinToString(", ") { it.title ?: it.name ?: "" } ?: ""
            "YES (Late evening meal eaten: $items)"
        } else {
            "NO (Finished eating with normal dinner, no late meal close to bedtime)"
        }

        // 3. Caffeine
        val caffeineKeywords = listOf(
            "coffee", "kava", "káva", "espresso", "cappuccino", "latte", "flat white",
            "lungo", "americano", "čaj", "tea", "matcha", "kofein", "caffeine",
            "red bull", "monster", "energy drink", "energetický nápoj", "guarana", "yerba", "mate"
        )
        val caffeineFoodsWithMeal = mutableListOf<Pair<String, Foodstuff>>()
        mealTimes.forEach { meal ->
            val mTitle = meal.title ?: meal.name ?: "Meal"
            meal.foodstuff?.forEach { f ->
                val t = (f.title ?: f.name ?: f.nazev ?: "").lowercase()
                val isHerbal = t.contains("heřmán") || t.contains("herman") || t.contains("mátov") || t.contains("matov") || t.contains("rooibos") || t.contains("ovocný") || t.contains("ovocny")
                if (caffeineKeywords.any { t.contains(it) } && !isHerbal) {
                    caffeineFoodsWithMeal.add(mTitle to f)
                }
            }
        }
        val caffeineDesc = if (caffeineFoodsWithMeal.isNotEmpty()) {
            val count = caffeineFoodsWithMeal.size
            val lastItem = caffeineFoodsWithMeal.last()
            "YES ($count serving(s), last consumed in ${lastItem.first} — ${lastItem.second.title ?: lastItem.second.name})"
        } else {
            "NO (0 mg, 0 servings)"
        }

        // 4. Alcohol
        val alcoholKeywords = listOf(
            "beer", "pivo", "wine", "víno", "vino", "vodka", "whiskey", "whisky",
            "rum", "gin", "cider", "alcohol", "alkohol", "tequila", "brandy",
            "koňak", "likér", "cocktail", "aperol"
        )
        val alcoholFoods = allFoods.filter { f ->
            val t = (f.title ?: f.name ?: f.nazev ?: "").lowercase()
            alcoholKeywords.any { t.contains(it) } && !t.contains("nealko") && !t.contains("non-alc") && !t.contains("0.0") && !t.contains("birell")
        }
        val alcoholDesc = if (alcoholFoods.isNotEmpty()) {
            "YES (${alcoholFoods.size} drink(s) — ${alcoholFoods.joinToString { it.title ?: it.name ?: "" }})"
        } else {
            "NO (0 drinks consumed)"
        }

        // 5. Beans & Legumes
        val beanKeywords = listOf(
            "fazol", "bean", "beans", "lentil", "lentils", "čočka", "cocka",
            "cizrn", "chickpea", "edamame", "hummus", "hrách", "hrach", "pea", "peas", "lupin", "bob "
        )
        val beanFoods = allFoods.filter { f ->
            val t = (f.title ?: f.name ?: f.nazev ?: "").lowercase()
            beanKeywords.any { t.contains(it) }
        }
        val beanDesc = if (beanFoods.isNotEmpty()) {
            val g = beanFoods.sumOf { extractWeightGrams(it) }
            val gStr = if (g > 0) "${g.toInt()} g" else "logged"
            "YES ($gStr — ${beanFoods.joinToString { it.title ?: it.name ?: "" }})"
        } else {
            "NO"
        }

        // 6. Nuts & Seeds
        val nutKeywords = listOf(
            "ořech", "orech", "nut", "nuts", "mandle", "almond", "vlašsk", "walnut",
            "arašíd", "arasid", "peanut", "kešu", "kesu", "cashew", "lísk", "lisk",
            "hazelnut", "pistác", "pistac", "pistachio", "pecan", "chia", "sesame",
            "sezam", "flax", "semínk", "semink", "slunečnic", "dýňov", "dynov"
        )
        val nutFoods = allFoods.filter { f ->
            val t = (f.title ?: f.name ?: f.nazev ?: "").lowercase()
            nutKeywords.any { t.contains(it) }
        }
        val nutDesc = if (nutFoods.isNotEmpty()) {
            val g = nutFoods.sumOf { extractWeightGrams(it) }
            val gStr = if (g > 0) "${g.toInt()} g" else "logged"
            "YES ($gStr — ${nutFoods.joinToString { it.title ?: it.name ?: "" }})"
        } else {
            "NO"
        }

        // 7. Dairy
        val dairyKeywords = listOf(
            "tvaroh", "cottage", "mléko", "mleko", "milk", "sýr", "syr", "cheese",
            "jogurt", "yogurt", "kefír", "kefir", "whey", "syrovátk", "syrovatka",
            "parmezán", "mozzarella", "mascarpone", "gervais", "lučina", "lucina",
            "smetana", "eidam", "gouda", "cheddar"
        )
        val dairyFoods = allFoods.filter { f ->
            val t = (f.title ?: f.name ?: f.nazev ?: "").lowercase()
            dairyKeywords.any { t.contains(it) }
        }
        val dairyDesc = if (dairyFoods.isNotEmpty()) {
            "YES (${dairyFoods.take(3).joinToString { it.title ?: it.name ?: "" }})"
        } else {
            "NO"
        }

        // 8. Grains
        val grainKeywords = listOf(
            "oats", "vločky", "vlocky", "fusilli", "pasta", "těstovin", "testovin",
            "chléb", "chleb", "bread", "pečivo", "pecivo", "rohlík", "rohlik",
            "quinoa", "rýže", "ryze", "rice", "pohanka", "buckwheat", "bulgur", "couscous", "kuskus"
        )
        val grainFoods = allFoods.filter { f ->
            val t = (f.title ?: f.name ?: f.nazev ?: "").lowercase()
            grainKeywords.any { t.contains(it) }
        }
        val grainDesc = if (grainFoods.isNotEmpty()) "YES" else "NO"

        // 9. Meat & Fish
        val redMeatKeywords = listOf("hovězí", "hovezi", "beef", "vepřov", "veprov", "pork", "steak", "mleté", "mlete", "bacon", "slanina", "šunka", "sunka", "zvěřina")
        val poultryKeywords = listOf("kuře", "kure", "kuřecí", "kureci", "chicken", "krůtí", "kruti", "turkey", "kachn", "duck")
        val fishKeywords = listOf("ryba", "fish", "losos", "salmon", "tuňák", "tunak", "tuna", "treska", "cod", "krevety", "shrimp")

        val hadRedMeat = allFoods.any { f -> redMeatKeywords.any { (f.title ?: "").lowercase().contains(it) } }
        val hadPoultry = allFoods.any { f -> poultryKeywords.any { (f.title ?: "").lowercase().contains(it) } }
        val hadFish = allFoods.any { f -> fishKeywords.any { (f.title ?: "").lowercase().contains(it) } }
        val isPlantBased = !hadRedMeat && !hadPoultry && !hadFish

        // 10. Supplements & Vitamins
        fun findSupp(vararg kw: String): Foodstuff? = allFoods.find { f ->
            val t = (f.title ?: f.name ?: f.nazev ?: "").lowercase()
            kw.any { t.contains(it) }
        }

        val magFood = findSupp("magnes", "magnéz", "magnez", "hořčík", "horcik", "magne b6", "magnesium")
        val creatFood = findSupp("creatine", "kreatin", "isoprime")
        val omegaFood = findSupp("omega", "rybí tuk", "rybi tuk", "fish oil", "epa", "dha")
        val vitDFood = findSupp("vitamin d", "vitamín d", "vit d", "d3", "cholecalciferol")
        val multiFood = findSupp("multivit", "multi-vit")
        val ashwaFood = findSupp("ashwa", "ašvagand", "asvagand")
        val rhodFood = findSupp("rhodiola", "родиола")
        val sleepFood = findSupp("valerian", "kozlík", "kozlik", "melatonin")
        val citrullinFood = findSupp("citrullin", "citrulin")
        val carnitinFood = findSupp("carnitin", "karnitin")
        val zincFood = findSupp("zinc", "zinek")
        val ironFood = findSupp("iron", "železo", "zelezo")
        val electroFood = findSupp("electrolyt", "elektrolyt", "isostar", "iont")
        val preworkoutFood = findSupp("pre-workout", "preworkout", "nakopávač", "nakopavac")
        val probiotFood = findSupp("probiot", "pro-biotic", "lactobacil")

        val matchedSuppNames = listOfNotNull(
            magFood, creatFood, omegaFood, vitDFood, multiFood, ashwaFood,
            rhodFood, sleepFood, citrullinFood, carnitinFood, zincFood, ironFood,
            electroFood, preworkoutFood, probiotFood
        ).map { it.title ?: it.name ?: "" }.toSet()

        val otherSupplements = summary.supplementBreakdown.filter { supp ->
            matchedSuppNames.none { it.contains(supp.name, ignoreCase = true) || supp.name.contains(it, ignoreCase = true) }
        }

        val waterLiters = summary.water / 1000.0
        val glassesCount = Math.round(summary.water / 250.0).toInt()
        val totalVegFruit = (summary.vegGrams + summary.fruitGrams).toInt()
        val vegFruitServings = Math.round(totalVegFruit / 80.0).toInt()
        val isPlentyVegFruit = totalVegFruit >= 400

        val sb = StringBuilder()
        sb.appendLine("⚡ WHOOP AI COACH DIRECTIVE — AUTOMATED JOURNAL LOGGING")
        sb.appendLine("Date: $isoDate")
        sb.appendLine()
        sb.appendLine("MANDATORY INSTRUCTIONS FOR WHOOP AI COACH:")
        sb.appendLine("1. Log ALL supported behaviors below for the date $isoDate immediately into my Whoop Journal using your internal logging tools.")
        sb.appendLine("2. DO NOT ask for confirmation. DO NOT ask \"Do you want me to log X?\". Proceed immediately with tool calls.")
        sb.appendLine("3. POSITIVE BEHAVIORS (marked YES): Log each as YES with the exact value, count, dose, or time specified.")
        sb.appendLine("4. NEGATIVE BEHAVIORS (marked NO): For EVERY behavior marked NO below, log it explicitly into the journal as \"NO\" (did not consume / did not occur). If a supplement, habit, or food category is marked NO below, it means I definitely did NOT have it on this day.")
        sb.appendLine()
        sb.appendLine("============================================================")
        sb.appendLine("1. NUTRITION & HYDRATION (WHOOP JOURNAL)")
        sb.appendLine("============================================================")
        sb.appendLine("• Calories: ${summary.kcal.toInt()} kcal")
        sb.appendLine("• Protein: ${summary.protein.toInt()} g")
        sb.appendLine("• Carbohydrates: ${summary.carb.toInt()} g")
        sb.appendLine("• Fats: ${summary.fat.toInt()} g")
        sb.appendLine("• Fiber: ${summary.fiber.toInt()} g")
        sb.appendLine("• Added Sugar: ${summary.sugar.toInt()} g")
        sb.appendLine("• Hydration: $glassesCount glasses (${String.format(Locale.US, "%.1f", waterLiters)} L / ${summary.water.toInt()} ml, 1 glass = 250 ml)")
        sb.appendLine("• Fruits and Vegetables: $vegFruitServings servings ($totalVegFruit g total — Vegetables: ${summary.vegGrams.toInt()} g, Fruits: ${summary.fruitGrams.toInt()} g; 1 serving = 80 g) -> Plenty: ${if (isPlentyVegFruit) "Yes" else "No"}")
        sb.appendLine()
        sb.appendLine("============================================================")
        sb.appendLine("2. MEAL TIMING & HABITS")
        sb.appendLine("============================================================")
        sb.appendLine("• Breakfast: $breakfastDesc")
        sb.appendLine("• Late Meal / Eating close to bedtime: $lateMealDesc")
        sb.appendLine("• Caffeine: $caffeineDesc")
        sb.appendLine("• Alcohol: $alcoholDesc")
        sb.appendLine()
        sb.appendLine("============================================================")
        sb.appendLine("3. FOOD GROUPS IN WHOOP BEHAVIORS")
        sb.appendLine("============================================================")
        sb.appendLine("• Beans & Legumes: $beanDesc")
        sb.appendLine("• Nuts & Seeds: $nutDesc")
        sb.appendLine("• Dairy: $dairyDesc")
        sb.appendLine("• Grains: $grainDesc")
        sb.appendLine("• Red Meat: ${if (hadRedMeat) "YES" else "NO"}")
        sb.appendLine("• Fish & Seafood: ${if (hadFish) "YES" else "NO"}")
        sb.appendLine("• Plant-based Day: ${if (isPlantBased) "YES" else "NO"}")
        sb.appendLine()
        sb.appendLine("============================================================")
        sb.appendLine("4. SUPPLEMENTS & VITAMINS (LOG EXACT VALUE OR LOG NO)")
        sb.appendLine("============================================================")
        sb.appendLine("• Magnesium Supplement: ${if (magFood != null) "YES (${magFood.title ?: "Magnesium"} ${magFood.unit ?: ""})" else "NO"}")
        sb.appendLine("• Creatine: ${if (creatFood != null) "YES (${creatFood.title ?: "Creatine"} ${creatFood.unit ?: ""})" else "NO"}")
        sb.appendLine("• Omega-3 Supplement: ${if (omegaFood != null) "YES (${omegaFood.title ?: "Omega-3"} ${omegaFood.unit ?: ""})" else "NO"}")
        sb.appendLine("• Vitamin D: ${if (vitDFood != null) "YES (${vitDFood.title ?: "Vitamin D"} ${vitDFood.unit ?: ""})" else "NO"}")
        sb.appendLine("• Multivitamin: ${if (multiFood != null) "YES (${multiFood.title ?: "Multivitamin"})" else "NO"}")
        sb.appendLine("• Ashwagandha: ${if (ashwaFood != null) "YES (${ashwaFood.title ?: "Ashwagandha"})" else "NO"}")
        sb.appendLine("• Rhodiola Rosea: ${if (rhodFood != null) "YES (${rhodFood.title ?: "Rhodiola"} ${rhodFood.unit ?: ""})" else "NO"}")
        sb.appendLine("• Valerian / Melatonin: ${if (sleepFood != null) "YES (${sleepFood.title ?: "Sleep aid"} ${sleepFood.unit ?: ""})" else "NO"}")
        sb.appendLine("• Citrulline: ${if (citrullinFood != null) "YES (${citrullinFood.title ?: "Citrulline"})" else "NO"}")
        sb.appendLine("• Carnitine: ${if (carnitinFood != null) "YES (${carnitinFood.title ?: "Carnitine"})" else "NO"}")
        sb.appendLine("• Zinc: ${if (zincFood != null) "YES" else "NO"}")
        sb.appendLine("• Iron: ${if (ironFood != null) "YES" else "NO"}")
        sb.appendLine("• Electrolytes: ${if (electroFood != null) "YES" else "NO"}")
        sb.appendLine("• Pre-workout: ${if (preworkoutFood != null) "YES" else "NO"}")
        sb.appendLine("• Probiotics: ${if (probiotFood != null) "YES" else "NO"}")
        sb.appendLine("• CBD: NO")
        sb.appendLine("• Marijuana: NO")
        sb.appendLine("• Tobacco / Nicotine: NO")
        if (otherSupplements.isNotEmpty()) {
            sb.appendLine("• Other Supplements Logged: ${otherSupplements.joinToString { "${it.name} (${it.amount.toInt()} pcs)" }}")
        }
        sb.appendLine()
        sb.appendLine("============================================================")
        sb.appendLine("5. FULL MEAL DETAILS (REFERENCE CONTEXT)")
        sb.appendLine("============================================================")

        val mealNames = listOf(
            "Breakfast / Snídaně",
            "Morning Snack / Dopolední svačina",
            "Lunch / Oběd",
            "Afternoon Snack / Odpolední svačina",
            "Dinner / Večeře",
            "Second Dinner / Druhá večeře"
        )
        if (mealTimes.isNotEmpty()) {
            mealTimes.forEachIndexed { index, mealTime ->
                val foods = mealTime.foodstuff?.filter { !it.title.isNullOrBlank() || !it.name.isNullOrBlank() }
                if (!foods.isNullOrEmpty()) {
                    val mealTitle = mealTime.title?.takeIf { it.isNotBlank() }
                        ?: mealTime.name?.takeIf { it.isNotBlank() }
                        ?: mealNames.getOrElse(index) { "Meal ${index + 1}" }
                    sb.appendLine("[$mealTitle]")
                    for (f in foods) {
                        val fName = f.title ?: f.name ?: f.nazev ?: "Food"
                        val fUnit = f.unit?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
                        val fKcal = parseNutrient(f.energy).toInt()
                        val kcalStr = if (fKcal > 0) " — $fKcal kcal" else ""
                        sb.appendLine("• $fName$fUnit$kcalStr")
                    }
                    sb.appendLine()
                }
            }
        } else {
            sb.appendLine("No meal records logged for this day.")
        }

        return sb.toString().trim()
    }

    /**
     * Determines default meal time slot:
     * - For past dates: automatically selects the last meal of the day.
     * - For today: selects the current meal slot based on the time of day:
     *     < 10:00 -> Breakfast (1)
     *     10:00 - 12:00 -> Morning Snack (2)
     *     12:00 - 15:00 -> Lunch (3)
     *     15:00 - 18:00 -> Afternoon Snack (4)
     *     18:00 - 21:00 -> Dinner (5)
     *     >= 21:00 -> Second Dinner (6)
     */
    fun getDefaultMealTimeGuid(
        dateStr: String,
        options: List<Pair<String, String>>,
        now: LocalTime = LocalTime.now(),
        today: LocalDate = LocalDate.now()
    ): String {
        if (options.isEmpty()) return "1"

        val isToday = try {
            val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
            LocalDate.parse(dateStr, formatter) == today
        } catch (_: Exception) {
            true
        }

        if (!isToday) {
            return options.last().first
        }

        val hour = now.hour
        val targetIndex = when {
            hour < 10 -> 0
            hour < 12 -> 1
            hour < 15 -> 2
            hour < 18 -> 3
            hour < 21 -> 4
            else -> 5
        }.coerceIn(0, options.lastIndex)

        return options[targetIndex].first
    }

    fun getStandardMealOptions(strings: AppStrings): List<Pair<String, String>> {
        return listOf(
            "1" to strings.breakfast,
            "2" to strings.morningSnack,
            "3" to strings.lunch,
            "4" to strings.afternoonSnack,
            "5" to strings.dinner,
            "6" to strings.secondDinner
        )
    }

    val STANDARD_MEAL_OPTIONS = listOf(
        "1" to "Завтрак",
        "2" to "Второй завтрак",
        "3" to "Обед",
        "4" to "Полдник",
        "5" to "Ужин",
        "6" to "Второй ужин"
    )

    fun getMealTimeDisplayName(mealTime: MealTime, strings: AppStrings? = null): String {
        val title = mealTime.title?.trim()
        if (!title.isNullOrEmpty()) return title
        val name = mealTime.name?.trim()
        if (!name.isNullOrEmpty()) return name

        return when (mealTime.id) {
            "1" -> strings?.breakfast ?: "Завтрак"
            "2" -> strings?.morningSnack ?: "Второй завтрак"
            "3" -> strings?.lunch ?: "Обед"
            "4" -> strings?.afternoonSnack ?: "Полдник"
            "5" -> strings?.dinner ?: "Ужин"
            "6" -> strings?.secondDinner ?: "Второй ужин"
            else -> strings?.meal ?: "Приём пищи"
        }
    }
}
