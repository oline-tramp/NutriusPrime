package com.example.kthealthsync.domain.util

import com.example.kthealthsync.DiaryResponse
import com.example.kthealthsync.domain.model.DailySummary
import com.example.kthealthsync.domain.util.NutrientParser.parseNutrient
import java.util.Locale

object WhoopPromptFormatter {

    private val CZ_DATE_REGEX = "\\d{2}\\.\\d{2}\\.\\d{4}".toRegex()

    /**
     * Formats daily nutrition and lifestyle data into an unambiguous, tool-executable prompt
     * optimized for the Whoop AI Coach to log Whoop Journal behaviors based on the daily summary and food log.
     */
    fun formatWhoopBehaviorsPrompt(
        dateStr: String,
        summary: DailySummary,
        diaryResponse: DiaryResponse?
    ): String {
        val isoDate = try {
            if (dateStr.matches(CZ_DATE_REGEX)) {
                val parts = dateStr.split(".")
                "${parts[2]}-${parts[1]}-${parts[0]}"
            } else dateStr
        } catch (_: Exception) { dateStr }

        val mealTimes = diaryResponse?.data?.times ?: emptyList()

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
        sb.appendLine("1. RETRIEVE & STATE TRACKED BEHAVIORS FIRST: Before logging or updating any entries, retrieve my current Whoop Journal configuration/entry for $isoDate. In your initial response, explicitly list all behaviors that I actively track.")
        sb.appendLine("2. LOG ONLY MY ACTIVELY TRACKED BEHAVIORS: Cross-reference ONLY the behaviors I actively track against the daily summary and food log below:")
        sb.appendLine("   - For behaviors consumed or present in today's food log (e.g. food groups, nutrients, meal timing, caffeine, alcohol, hydration, supplements): Log them as YES with the exact value, count, dose, or timing.")
        sb.appendLine("   - For tracked behaviors NOT consumed or absent from today's food log: Log them as NO (did not consume / did not occur).")
        sb.appendLine("3. STRICTLY DO NOT ADD UNTRACKED BEHAVIORS: Under NO circumstances should you add, activate, or enable new behaviors to my Whoop Journal that I do not already track. If an item in the log is NOT currently tracked by me, IGNORE IT COMPLETELY.")
        sb.appendLine("4. NO CONFIRMATION NEEDED: DO NOT ask for confirmation. DO NOT ask \"Do you want me to log X?\". Proceed immediately with tool calls.")
        sb.appendLine()
        sb.appendLine("============================================================")
        sb.appendLine("1. NUTRITION & HYDRATION SUMMARY")
        sb.appendLine("============================================================")
        sb.appendLine("• Calories: ${summary.kcal.toInt()} kcal")
        sb.appendLine("• Protein: ${summary.protein.toInt()} g")
        sb.appendLine("• Carbohydrates: ${summary.carb.toInt()} g")
        sb.appendLine("• Fats: ${summary.fat.toInt()} g")
        sb.appendLine("• Fiber: ${summary.fiber.toInt()} g")
        sb.appendLine("• Added Sugar: ${summary.sugar.toInt()} g")
        if (summary.salt > 0) {
            sb.appendLine("• Salt: ${String.format(Locale.US, "%.1f", summary.salt)} g")
        }
        sb.appendLine("• Hydration: $glassesCount glasses (${String.format(Locale.US, "%.1f", waterLiters)} L / ${summary.water.toInt()} ml, 1 glass = 250 ml)")
        sb.appendLine("• Fruits and Vegetables: $vegFruitServings servings ($totalVegFruit g total — Vegetables: ${summary.vegGrams.toInt()} g, Fruits: ${summary.fruitGrams.toInt()} g; 1 serving = 80 g) -> Plenty: ${if (isPlentyVegFruit) "Yes" else "No"}")
        if (summary.supplementCount > 0) {
            sb.appendLine("• Supplements logged: ${summary.supplementCount} pcs")
        }
        if (summary.supplementBreakdown.isNotEmpty()) {
            sb.appendLine("• Supplements detail: ${summary.supplementBreakdown.joinToString { "${it.name} (${it.amount.toInt()} pcs)" }}")
        }
        sb.appendLine()
        sb.appendLine("============================================================")
        sb.appendLine("2. MEALS & FOOD LOG")
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
                val foods = mealTime.foodstuff?.filter { !it.title.isNullOrBlank() || !it.name.isNullOrBlank() || !it.nazev.isNullOrBlank() }
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
}
