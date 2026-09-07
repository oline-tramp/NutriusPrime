package com.example.kthealthsync

import com.example.kthealthsync.domain.model.DailySummary
import com.example.kthealthsync.domain.model.NutrientGoals
import com.example.kthealthsync.domain.util.NutrientUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NutrientUtilsTest {

    @Test
    fun testParseNutrient() {
        assertEquals(12.5, NutrientUtils.parseNutrient("12,5 g"), 0.001)
        assertEquals(350.0, NutrientUtils.parseNutrient("350 kcal"), 0.001)
        assertEquals(0.0, NutrientUtils.parseNutrient(null), 0.001)
        assertEquals(0.0, NutrientUtils.parseNutrient(""), 0.001)
        assertEquals(0.0, NutrientUtils.parseNutrient("abc"), 0.001)
        assertEquals(1234.56, NutrientUtils.parseNutrient("1234.56"), 0.001)
    }

    @Test
    fun testScaleNutrient() {
        assertEquals("50.0", NutrientUtils.scaleNutrient("100.0", 0.5))
        assertEquals("33.3", NutrientUtils.scaleNutrient("100.0", 0.3333))
        assertNull(NutrientUtils.scaleNutrient(null, 1.5))
    }

    @Test
    fun testSaltToSodiumConversion() {
        // Table salt 5g -> ~1.967g sodium
        val salt = 5.0
        val sodium = NutrientUtils.saltToSodiumGrams(salt)
        assertEquals(1.967, sodium, 0.01)
    }

    @Test
    fun testAggregateMacro() {
        val food1 = Foodstuff(
            title = "Oatmeal",
            name = null,
            nazev = null,
            energy = "150",
            protein = "5",
            carbohydrate = "27",
            fat = "3",
            sugar = "1",
            salt = "0.01",
            water = "10",
            fiber = "4",
            foodstuff = null,
            items = null,
            ingredients = null,
            components = null,
            guid = "1",
            guidRecipe = null,
            isRecipe = false
        )

        val food2 = Foodstuff(
            title = "Banana",
            name = null,
            nazev = null,
            energy = "100",
            protein = "1",
            carbohydrate = "23",
            fat = "0.3",
            sugar = "12",
            salt = "0.0",
            water = "75",
            fiber = "2.6",
            foodstuff = null,
            items = null,
            ingredients = null,
            components = null,
            guid = "2",
            guidRecipe = null,
            isRecipe = false
        )

        val breakdowns = NutrientUtils.aggregateMacro(
            listOf(food1, food2),
            { NutrientUtils.parseNutrient(it.energy) },
            withMeals = false
        )

        assertEquals(2, breakdowns.size)
        assertEquals("Oatmeal", breakdowns[0].name)
        assertEquals(150.0, breakdowns[0].amount, 0.001)
        assertEquals("Banana", breakdowns[1].name)
        assertEquals(100.0, breakdowns[1].amount, 0.001)
    }

    @Test
    fun testParseLdJsonMacros() {
        val sampleHtml = """
            <!DOCTYPE html>
            <html>
            <head>
            <script type="application/ld+json">
            {
                "@context": "https://schema.org",
                "@type": "Product",
                "name": "Test Food",
                "keywords": [
                    "Bílkoviny: 15,2 g",
                    "Sacharidy: 40 g",
                    "Tuky: 8.5 g",
                    "Vláknina: 5 g",
                    "Cukry: 2 g",
                    "Sůl: 1.1 g"
                ]
            }
            </script>
            </head>
            <body></body>
            </html>
        """.trimIndent()

        val parsed = NutrientUtils.parseLdJsonMacros(sampleHtml)
        assertEquals(15.2, parsed["bílkoviny"] ?: 0.0, 0.01)
        assertEquals(40.0, parsed["sacharidy"] ?: 0.0, 0.01)
        assertEquals(8.5, parsed["tuky"] ?: 0.0, 0.01)
        assertEquals(5.0, parsed["vláknina"] ?: 0.0, 0.01)
        assertEquals(2.0, parsed["cukry"] ?: 0.0, 0.01)
        assertEquals(1.1, parsed["sůl"] ?: 0.0, 0.01)
    }

    @Test
    fun testParseLiquidVolumeMl() {
        // Milk in ml multiplier
        val milk = Foodstuff(
            title = "Long life milk semi skimmed 1,5% fat lactose free Miil",
            name = null, nazev = null, energy = null, protein = null, carbohydrate = null,
            fat = null, sugar = null, salt = null, water = null, fiber = null,
            foodstuff = null, items = null, ingredients = null, components = null,
            guid = "m1", guidRecipe = null, isRecipe = false, unit = "200 x 1 ml"
        )
        assertEquals(200.0, NutrientUtils.parseLiquidVolumeMl(milk), 0.01)

        // Water with non-breaking space in 1 111 x 1 ml
        val waterThousands = Foodstuff(
            title = "voda čistá",
            name = null, nazev = null, energy = null, protein = null, carbohydrate = null,
            fat = null, sugar = null, salt = null, water = "1111.0", fiber = null,
            foodstuff = null, items = null, ingredients = null, components = null,
            guid = "w1", guidRecipe = null, isRecipe = false, unit = "1\u00a0111 x 1 ml"
        )
        assertEquals(1111.0, NutrientUtils.parseLiquidVolumeMl(waterThousands), 0.01)

        // Tomato juice: 3 x 100 ml
        val juice = Foodstuff(
            title = "Džus 100% rajče Relax",
            name = null, nazev = null, energy = null, protein = null, carbohydrate = null,
            fat = null, sugar = null, salt = null, water = null, fiber = null,
            foodstuff = null, items = null, ingredients = null, components = null,
            guid = "j1", guidRecipe = null, isRecipe = false, unit = "3 x 100 ml"
        )
        assertEquals(300.0, NutrientUtils.parseLiquidVolumeMl(juice), 0.01)

        // Non-alcoholic beer in single multiplier: 500 x 1 ml
        val birell = Foodstuff(
            title = "Pomelo & grep Birell",
            name = null, nazev = null, energy = null, protein = null, carbohydrate = null,
            fat = null, sugar = null, salt = null, water = null, fiber = null,
            foodstuff = null, items = null, ingredients = null, components = null,
            guid = "b1", guidRecipe = null, isRecipe = false, unit = "500 x 1 ml"
        )
        assertEquals(500.0, NutrientUtils.parseLiquidVolumeMl(birell), 0.01)

        // Mineral water in liters: 1 x 1.5 l
        val magnesia = Foodstuff(
            title = "Neperlivá Magnesia",
            name = null, nazev = null, energy = null, protein = null, carbohydrate = null,
            fat = null, sugar = null, salt = null, water = null, fiber = null,
            foodstuff = null, items = null, ingredients = null, components = null,
            guid = "mag1", guidRecipe = null, isRecipe = false, unit = "1 x 1.5 l"
        )
        assertEquals(1500.0, NutrientUtils.parseLiquidVolumeMl(magnesia), 0.01)

        // Juice in container: balení (1 000 ml)
        val pack = Foodstuff(
            title = "Pomerančový džus",
            name = null, nazev = null, energy = null, protein = null, carbohydrate = null,
            fat = null, sugar = null, salt = null, water = null, fiber = null,
            foodstuff = null, items = null, ingredients = null, components = null,
            guid = "p1", guidRecipe = null, isRecipe = false, unit = "balení (1\u00a0000 ml)"
        )
        assertEquals(1000.0, NutrientUtils.parseLiquidVolumeMl(pack), 0.01)

        // Cooking oil: 10 x 1 ml should be excluded (0.0 ml fluid)
        val oil = Foodstuff(
            title = "Olivový olej extra panenský",
            name = null, nazev = null, energy = null, protein = null, carbohydrate = null,
            fat = null, sugar = null, salt = null, water = null, fiber = null,
            foodstuff = null, items = null, ingredients = null, components = null,
            guid = "o1", guidRecipe = null, isRecipe = false, unit = "10 x 1 ml"
        )
        assertEquals(0.0, NutrientUtils.parseLiquidVolumeMl(oil), 0.01)

        // Solid food moisture: Goulash (178 x 1 g, water=21.68) should be excluded
        val gulas = Foodstuff(
            title = "Gulas 2.9",
            name = null, nazev = null, energy = null, protein = null, carbohydrate = null,
            fat = null, sugar = null, salt = null, water = "21.68", fiber = null,
            foodstuff = null, items = null, ingredients = null, components = null,
            guid = "g1", guidRecipe = null, isRecipe = false, unit = "178 x 1 g"
        )
        assertEquals(0.0, NutrientUtils.parseLiquidVolumeMl(gulas), 0.01)

        // Drinkable fermented dairy: Kefir in grams (225 x 1 g)
        val kefir = Foodstuff(
            title = "Selský kefír z čerstvého mléka s živou kulturou 4% Milknatur",
            name = null, nazev = null, energy = null, protein = null, carbohydrate = null,
            fat = null, sugar = null, salt = null, water = null, fiber = null,
            foodstuff = null, items = null, ingredients = null, components = null,
            guid = "k1", guidRecipe = null, isRecipe = false, unit = "225 x 1 g"
        )
        assertEquals(225.0, NutrientUtils.parseLiquidVolumeMl(kefir), 0.01)

        // Soup / broth: Borsch with recipe water (359.3 ml)
        val borsch = Foodstuff(
            title = "Borsch 25.8",
            name = null, nazev = null, energy = null, protein = null, carbohydrate = null,
            fat = null, sugar = null, salt = null, water = "359.32", fiber = null,
            foodstuff = null, items = null, ingredients = null, components = null,
            guid = "b1", guidRecipe = null, isRecipe = true, unit = "7,5%"
        )
        assertEquals(359.32, NutrientUtils.parseLiquidVolumeMl(borsch), 0.01)

        // Sauce / condiment in ml: Sriracha (20 x 1 ml) should be excluded
        val sriracha = Foodstuff(
            title = "Sriracha hot chilli sauce Uni-Eagle",
            name = null, nazev = null, energy = null, protein = null, carbohydrate = null,
            fat = null, sugar = null, salt = null, water = null, fiber = null,
            foodstuff = null, items = null, ingredients = null, components = null,
            guid = "s1", guidRecipe = null, isRecipe = false, unit = "20 x 1 ml"
        )
        assertEquals(0.0, NutrientUtils.parseLiquidVolumeMl(sriracha), 0.01)

        // Watermelon with KT water field (e.g. 500g has 457.5g water)
        val watermelonKt = Foodstuff(
            title = "Meloun vodní",
            name = null, nazev = null, energy = "155.0", protein = "3.0", carbohydrate = "37.5",
            fat = "0.75", sugar = "31.0", salt = "0.01", water = "457.5", fiber = "2.0",
            foodstuff = null, items = null, ingredients = null, components = null,
            guid = "w1", guidRecipe = null, isRecipe = false, unit = "500 x 1 g"
        )
        assertEquals(457.5, NutrientUtils.parseLiquidVolumeMl(watermelonKt), 0.01)

        // Watermelon fallback in grams without water field (1000g * 0.915 = 915.0 ml)
        val watermelonFallbackG = Foodstuff(
            title = "Арбуз свежий",
            name = null, nazev = null, energy = null, protein = null, carbohydrate = null,
            fat = null, sugar = null, salt = null, water = null, fiber = null,
            foodstuff = null, items = null, ingredients = null, components = null,
            guid = "w2", guidRecipe = null, isRecipe = false, unit = "1000 x 1 g"
        )
        assertEquals(915.0, NutrientUtils.parseLiquidVolumeMl(watermelonFallbackG), 0.01)

        // Watermelon fallback in kg without water field (1 x 1 kg -> 915.0 ml)
        val watermelonFallbackKg = Foodstuff(
            title = "Watermelon",
            name = null, nazev = null, energy = null, protein = null, carbohydrate = null,
            fat = null, sugar = null, salt = null, water = null, fiber = null,
            foodstuff = null, items = null, ingredients = null, components = null,
            guid = "w3", guidRecipe = null, isRecipe = false, unit = "1 x 1 kg"
        )
        assertEquals(915.0, NutrientUtils.parseLiquidVolumeMl(watermelonFallbackKg), 0.01)

        // Watermelon syrup should be excluded
        val watermelonSyrup = Foodstuff(
            title = "Melounový sirup Monin",
            name = null, nazev = null, energy = null, protein = null, carbohydrate = null,
            fat = null, sugar = null, salt = null, water = null, fiber = null,
            foodstuff = null, items = null, ingredients = null, components = null,
            guid = "w4", guidRecipe = null, isRecipe = false, unit = "20 x 1 ml"
        )
        assertEquals(0.0, NutrientUtils.parseLiquidVolumeMl(watermelonSyrup), 0.01)
    }

    @Test
    fun testParsePortionGrams() {
        assertEquals(150.0, NutrientUtils.parsePortionGrams("150 x 1 g"), 0.001)
        assertEquals(200.0, NutrientUtils.parsePortionGrams("2 x 100 g"), 0.001)
        assertEquals(120.0, NutrientUtils.parsePortionGrams("1 x kus (120 g)"), 0.001)
        assertEquals(260.0, NutrientUtils.parsePortionGrams("2 x kus (130 g)"), 0.001)
        assertEquals(125.0, NutrientUtils.parsePortionGrams("0.5 x porce (250 g)"), 0.001)
        assertEquals(500.0, NutrientUtils.parsePortionGrams("1 x balení (500 g)"), 0.001)
        assertEquals(300.0, NutrientUtils.parsePortionGrams("0.3 x 1 kg"), 0.001)
        assertEquals(150.0, NutrientUtils.parsePortionGrams("150 g"), 0.001)
        assertEquals(250.0, NutrientUtils.parsePortionGrams("0.25 kg"), 0.001)
        assertEquals(250.0, NutrientUtils.parsePortionGrams("250 ml"), 0.001)
        assertEquals(200.0, NutrientUtils.parsePortionGrams("1 x 200 ml"), 0.001)
        assertEquals(0.0, NutrientUtils.parsePortionGrams(null), 0.001)
        assertEquals(0.0, NutrientUtils.parsePortionGrams(""), 0.001)
    }

    @Test
    fun testParseCategoryFromHtml() {
        val vegHtml = """
            <div layout="row" property="itemListElement" typeof="ListItem">
                <a property="item" typeof="WebPage" class="crumb-link" href="/zelenina" reload>
                    <span property="name">Zelenina</span>
                </a>
            </div>
            <div layout="row" property="itemListElement" typeof="ListItem">
                <a property="item" typeof="WebPage" class="crumb-link" href="/zelenina/zelenina-cerstva" reload>
                    <span property="name">Zelenina čerstvá</span>
                </a>
            </div>
        """.trimIndent()
        assertEquals(com.example.kthealthsync.domain.model.FoodCategory.VEGETABLE, NutrientUtils.parseCategoryFromHtml(vegHtml))

        val fruitHtml = """
            <div layout="row" property="itemListElement" typeof="ListItem">
                <a property="item" typeof="WebPage" class="crumb-link" href="/ovoce" reload>
                    <span property="name">Ovoce</span>
                </a>
            </div>
            <div layout="row" property="itemListElement" typeof="ListItem">
                <a property="item" typeof="WebPage" class="crumb-link" href="/ovoce/ovoce-cerstve" reload>
                    <span property="name">Ovoce čerstvé</span>
                </a>
            </div>
        """.trimIndent()
        assertEquals(com.example.kthealthsync.domain.model.FoodCategory.FRUIT, NutrientUtils.parseCategoryFromHtml(fruitHtml))

        val potatoHtml = """
            <div layout="row" property="itemListElement" typeof="ListItem">
                <a property="item" typeof="WebPage" class="crumb-link" href="/zelenina" reload>
                    <span property="name">Zelenina</span>
                </a>
            </div>
            <div layout="row" property="itemListElement" typeof="ListItem">
                <a property="item" typeof="WebPage" class="crumb-link" href="/zelenina/brambory-a-topinambury" reload>
                    <span property="name">Brambory a topinambury</span>
                </a>
            </div>
        """.trimIndent()
        assertEquals(com.example.kthealthsync.domain.model.FoodCategory.OTHER, NutrientUtils.parseCategoryFromHtml(potatoHtml))

        val breadHtml = """
            <a class="crumb-link" href="/pecivo">Pečivo</a>
        """.trimIndent()
        assertEquals(com.example.kthealthsync.domain.model.FoodCategory.OTHER, NutrientUtils.parseCategoryFromHtml(breadHtml))
    }

    @Test
    fun testFormatDailyContextForLlm() {
        val summary = DailySummary(
            kcal = 1850.0,
            protein = 95.0,
            fat = 52.0,
            carb = 210.0,
            sugar = 45.0,
            salt = 3.8,
            water = 1800.0,
            fiber = 22.0,
            date = "07.09.2026",
            vegGrams = 180.0,
            fruitGrams = 100.0
        )
        val goals = NutrientGoals(
            kcal = 2300.0,
            protein = 130.0,
            carbs = 285.0,
            fat = 70.0,
            fiber = 30.0,
            sugar = 70.0,
            salt = 5.0,
            vegFruit = 400.0,
            water = 2500.0
        )
        val food1 = Foodstuff(
            title = "Овсянка",
            name = null,
            nazev = null,
            energy = "320",
            protein = "12",
            carbohydrate = "54",
            fat = "6",
            sugar = "2",
            salt = "0.01",
            water = "50",
            fiber = "5",
            foodstuff = null,
            items = null,
            ingredients = null,
            components = null,
            guid = "1",
            guidRecipe = null,
            isRecipe = false,
            unit = "250 g"
        )
        val diary = DiaryResponse(
            data = DiaryData(
                times = listOf(
                    MealTime(title = "Завтрак", foodstuff = listOf(food1))
                )
            )
        )

        val result = NutrientUtils.formatDailyContextForLlm("07.09.2026", summary, goals, diary)
        org.junit.Assert.assertTrue(result.contains("07.09.2026"))
        org.junit.Assert.assertTrue(result.contains("Калории: 1850.0 / 2300.0 ккал"))
        org.junit.Assert.assertTrue(result.contains("Белки: 95.0 / 130.0 г"))
        org.junit.Assert.assertTrue(result.contains("Овощи и фрукты (норма ВОЗ): 280 / 400 г"))
        org.junit.Assert.assertTrue(result.contains("[Завтрак]"))
        org.junit.Assert.assertTrue(result.contains("Овсянка (250 g)"))
        org.junit.Assert.assertTrue(result.contains("Ты персональный нутрициолог"))
    }

    @Test
    fun testFormatDailyShareText_DailySummary() {
        val summary = DailySummary(
            kcal = 1850.0,
            protein = 95.0,
            fat = 55.0,
            carb = 210.0,
            sugar = 45.0,
            salt = 3.2,
            water = 1900.0,
            fiber = 22.0,
            date = "07.09.2026",
            vegGrams = 180.0,
            fruitGrams = 100.0
        )
        val goals = NutrientGoals(
            kcal = 2300.0, protein = 130.0, carbs = 285.0, fat = 70.0,
            fiber = 30.0, sugar = 70.0, salt = 5.0, vegFruit = 400.0, water = 2500.0
        )
        val diary = DiaryResponse(
            data = DiaryData(
                times = listOf(
                    MealTime(
                        title = "Обед",
                        foodstuff = listOf(
                            Foodstuff(
                                title = "Куриная грудка гриль",
                                name = null, nazev = null, energy = "200", protein = "35",
                                carbohydrate = "0", fat = "3", sugar = "0", salt = "0.5",
                                water = "60", fiber = "0", foodstuff = null, items = null,
                                ingredients = null, components = null, guid = "c1",
                                guidRecipe = null, isRecipe = false, unit = "150 g"
                            )
                        )
                    )
                )
            )
        )

        val cleanSummary = NutrientUtils.formatDailyShareText(
            com.example.kthealthsync.domain.model.ShareFormatType.DAILY_SUMMARY,
            "07.09.2026",
            summary,
            goals,
            diary
        )

        org.junit.Assert.assertTrue(cleanSummary.contains("📊 ДНЕВНОЙ ОТЧЁТ ПИТАНИЯ"))
        org.junit.Assert.assertTrue(cleanSummary.contains("📅 ДАТА: 07.09.2026"))
        org.junit.Assert.assertTrue(cleanSummary.contains("Калории: 1850.0 / 2300.0 ккал"))
        org.junit.Assert.assertTrue(cleanSummary.contains("[Обед]"))
        org.junit.Assert.assertTrue(cleanSummary.contains("Куриная грудка гриль"))
        // DAILY_SUMMARY must NOT contain LLM prompts
        org.junit.Assert.assertFalse(cleanSummary.contains("Ты персональный нутрициолог"))
        org.junit.Assert.assertFalse(cleanSummary.contains("Задача:"))
    }

    @Test
    fun testFormatDailyShareText_FoodListOnly() {
        val summary = DailySummary(
            kcal = 1850.0, protein = 95.0, fat = 55.0, carb = 210.0,
            sugar = 45.0, salt = 3.2, water = 1900.0, fiber = 22.0,
            date = "07.09.2026", vegGrams = 180.0, fruitGrams = 100.0
        )
        val goals = NutrientGoals()
        val diary = DiaryResponse(
            data = DiaryData(
                times = listOf(
                    MealTime(
                        title = "Перекус",
                        foodstuff = listOf(
                            Foodstuff(
                                title = "Яблоко Gala",
                                name = null, nazev = null, energy = "70", protein = "0.5",
                                carbohydrate = "18", fat = "0.2", sugar = "14", salt = "0.0",
                                water = "100", fiber = "3", foodstuff = null, items = null,
                                ingredients = null, components = null, guid = "a1",
                                guidRecipe = null, isRecipe = false, unit = "130 g"
                            )
                        )
                    )
                )
            )
        )

        val foodList = NutrientUtils.formatDailyShareText(
            com.example.kthealthsync.domain.model.ShareFormatType.FOOD_LIST_ONLY,
            "07.09.2026",
            summary,
            goals,
            diary
        )

        org.junit.Assert.assertTrue(foodList.contains("🍽 ДНЕВНИК ПИТАНИЯ"))
        org.junit.Assert.assertTrue(foodList.contains("📅 ДАТА: 07.09.2026"))
        org.junit.Assert.assertTrue(foodList.contains("[Перекус]"))
        org.junit.Assert.assertTrue(foodList.contains("Яблоко Gala (130 g)"))
        org.junit.Assert.assertFalse(foodList.contains("ИТОГИ ДНЯ"))
    }

    @Test
    fun testFormatDailyShareText_JsonExport() {
        val summary = DailySummary(
            kcal = 1850.0, protein = 95.0, fat = 55.0, carb = 210.0,
            sugar = 45.0, salt = 3.2, water = 1900.0, fiber = 22.0,
            date = "07.09.2026", vegGrams = 180.0, fruitGrams = 100.0
        )
        val goals = NutrientGoals(kcal = 2000.0)

        val json = NutrientUtils.formatDailyShareText(
            com.example.kthealthsync.domain.model.ShareFormatType.JSON_EXPORT,
            "07.09.2026",
            summary,
            goals,
            null
        )

        org.junit.Assert.assertTrue(json.startsWith("{"))
        org.junit.Assert.assertTrue(json.endsWith("}"))
        org.junit.Assert.assertTrue(json.contains("\"date\": \"07.09.2026\""))
        org.junit.Assert.assertTrue(json.contains("\"totals\": {"))
        org.junit.Assert.assertTrue(json.contains("\"goals\": {"))
    }

    @Test
    fun testGetDefaultMealTimeGuid_PastDate() {
        val options = listOf(
            "1" to "Breakfast",
            "2" to "Morning Snack",
            "3" to "Lunch",
            "4" to "Afternoon Snack",
            "5" to "Dinner",
            "6" to "Second Dinner"
        )
        val today = java.time.LocalDate.of(2026, 9, 7)
        // Yesterday (past date) should return the last meal slot ("6")
        val result = NutrientUtils.getDefaultMealTimeGuid(
            dateStr = "06.09.2026",
            options = options,
            now = java.time.LocalTime.of(8, 30),
            today = today
        )
        org.junit.Assert.assertEquals("6", result)
    }

    @Test
    fun testGetDefaultMealTimeGuid_TodayTimeSlots() {
        val options = listOf(
            "1" to "Breakfast",
            "2" to "Morning Snack",
            "3" to "Lunch",
            "4" to "Afternoon Snack",
            "5" to "Dinner",
            "6" to "Second Dinner"
        )
        val today = java.time.LocalDate.of(2026, 9, 7)

        // 08:30 -> Breakfast ("1")
        org.junit.Assert.assertEquals("1", NutrientUtils.getDefaultMealTimeGuid("07.09.2026", options, java.time.LocalTime.of(8, 30), today))
        // 11:15 -> Morning Snack ("2")
        org.junit.Assert.assertEquals("2", NutrientUtils.getDefaultMealTimeGuid("07.09.2026", options, java.time.LocalTime.of(11, 15), today))
        // 13:45 -> Lunch ("3")
        org.junit.Assert.assertEquals("3", NutrientUtils.getDefaultMealTimeGuid("07.09.2026", options, java.time.LocalTime.of(13, 45), today))
        // 16:30 -> Afternoon Snack ("4")
        org.junit.Assert.assertEquals("4", NutrientUtils.getDefaultMealTimeGuid("07.09.2026", options, java.time.LocalTime.of(16, 30), today))
        // 19:20 -> Dinner ("5")
        org.junit.Assert.assertEquals("5", NutrientUtils.getDefaultMealTimeGuid("07.09.2026", options, java.time.LocalTime.of(19, 20), today))
        // 22:10 -> Second Dinner ("6")
        org.junit.Assert.assertEquals("6", NutrientUtils.getDefaultMealTimeGuid("07.09.2026", options, java.time.LocalTime.of(22, 10), today))
    }
}


