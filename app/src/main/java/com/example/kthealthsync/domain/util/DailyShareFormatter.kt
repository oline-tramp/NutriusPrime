package com.example.kthealthsync.domain.util

import com.example.kthealthsync.DiaryResponse
import com.example.kthealthsync.domain.model.DailySummary
import com.example.kthealthsync.domain.model.NutrientGoals
import com.example.kthealthsync.domain.model.ShareFormatType
import com.example.kthealthsync.domain.util.HydrationCalculator.parseLiquidVolumeMl
import com.example.kthealthsync.domain.util.NutrientParser.parseNutrient
import com.example.kthealthsync.domain.util.WhoopPromptFormatter.formatWhoopBehaviorsPrompt
import com.google.gson.GsonBuilder
import java.util.Locale

object DailyShareFormatter {

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
}
