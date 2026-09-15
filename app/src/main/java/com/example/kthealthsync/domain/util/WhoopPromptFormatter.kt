package com.example.kthealthsync.domain.util

import com.example.kthealthsync.DiaryResponse
import com.example.kthealthsync.Foodstuff
import com.example.kthealthsync.domain.model.DailySummary
import com.example.kthealthsync.domain.util.NutrientParser.parseNutrient
import java.util.Locale

object WhoopPromptFormatter {

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
}
