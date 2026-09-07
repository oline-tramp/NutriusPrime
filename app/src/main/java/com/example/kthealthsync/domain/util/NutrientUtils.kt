package com.example.kthealthsync.domain.util

import com.example.kthealthsync.DiaryResponse
import com.example.kthealthsync.Foodstuff
import com.example.kthealthsync.MealTime
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
        val firstNum = "^([\\d.]+)".toRegex().find(normalized)
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

        if (unitStr.isNullOrBlank()) {
            if (isPureWaterOrMineral(titleLower)) {
                return parseNutrient(waterStr)
            }
            return 0.0
        }

        val normalized = unitStr.replace("\u00a0", " ").replace(",", ".").trim()
        val cleaned = normalized.replace("(\\d)\\s+(\\d)".toRegex(), "$1$2")

        // 1. Multiplier pattern: "200 x 1 ml", "3 x 100 ml", "1 x 0.5 l", "1 x 2 dl", "1 x 25 cl"
        val multRegex = "([\\d.]+)\\s*x\\s*([\\d.]+)?\\s*([a-zA-Z]+)".toRegex()
        val multMatch = multRegex.find(cleaned)
        if (multMatch != null) {
            val count = multMatch.groupValues[1].toDoubleOrNull() ?: 1.0
            val amount = multMatch.groupValues[2].takeIf { it.isNotBlank() }?.toDoubleOrNull() ?: 1.0
            val unit = multMatch.groupValues[3].lowercase()
            val ml = convertToMl(count * amount, unit)
            if (ml > 0) return ml
        }

        // 2. Container pattern: "balení (1000 ml)", "sklenice (250 ml)", "2 x balení (500 ml)"
        val parenRegex = "\\(([\\d.]+)\\s*([a-zA-Z]+)\\)".toRegex()
        val parenMatch = parenRegex.find(cleaned)
        if (parenMatch != null) {
            val countRegex = "([\\d.]+)\\s*x".toRegex()
            val count = countRegex.find(cleaned)?.groupValues?.get(1)?.toDoubleOrNull() ?: 1.0
            val amount = parenMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            val unit = parenMatch.groupValues[2].lowercase()
            val ml = convertToMl(count * amount, unit)
            if (ml > 0) return ml
        }

        // 3. Simple pattern: "500 ml", "0.5 l", "2 dl", "25 cl"
        val simpleRegex = "([\\d.]+)\\s*([a-zA-Z]+)".toRegex()
        val simpleMatch = simpleRegex.find(cleaned)
        if (simpleMatch != null) {
            val amount = simpleMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            val unit = simpleMatch.groupValues[2].lowercase()
            val ml = convertToMl(amount, unit)
            if (ml > 0) return ml
        }

        // 4. Fallback for pure water logged in grams
        if (isPureWaterOrMineral(titleLower)) {
            val waterVal = parseNutrient(waterStr)
            if (waterVal > 0) return waterVal
        }

        return 0.0
    }

    private fun isPureWaterOrMineral(titleLower: String): Boolean {
        return titleLower.contains("voda") || titleLower.contains("water") ||
                titleLower.contains("magnesia") || titleLower.contains("aquila") ||
                titleLower.contains("rajec") || titleLower.contains("minerálk") ||
                titleLower.contains("mineralk")
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

    private fun convertToMl(amount: Double, unit: String): Double {
        return when (unit) {
            "ml" -> amount
            "l" -> amount * 1000.0
            "dl" -> amount * 100.0
            "cl" -> amount * 10.0
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
                    result.add(FoodBreakdown(fname, value, childBreakdowns))
                }
            } else if (isRecipe && !withMeals) {
                if (!children.isNullOrEmpty()) {
                    result.addAll(aggregateMacro(children, extractor, false))
                } else {
                    if (value > 0) result.add(FoodBreakdown(fname, value))
                }
            } else {
                if (value > 0) result.add(FoodBreakdown(fname, value))
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
            FoodBreakdown(name, totalAmount, mergedComponents)
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
    fun parseCategoryFromHtml(html: String): FoodCategory {
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
                return FoodCategory.SUPPLEMENT
            }
        }
        return FoodCategory.OTHER
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

    val STANDARD_MEAL_OPTIONS = listOf(
        "1" to "Завтрак (Snídaně)",
        "2" to "Второй завтрак (Dopolední svačina)",
        "3" to "Обед (Oběd)",
        "4" to "Полдник (Odpolední svačina)",
        "5" to "Ужин (Večeře)",
        "6" to "Второй ужин (Druhá večeře)"
    )

    fun getMealTimeDisplayName(mealTime: MealTime): String {
        return when (mealTime.id) {
            "1" -> "Завтрак"
            "2" -> "Второй завтрак"
            "3" -> "Обед"
            "4" -> "Полдник"
            "5" -> "Ужин"
            "6" -> "Второй ужин"
            else -> mealTime.title ?: mealTime.name ?: "Приём пищи"
        }
    }
}
