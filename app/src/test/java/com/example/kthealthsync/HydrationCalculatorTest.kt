package com.example.kthealthsync

import com.example.kthealthsync.domain.util.HydrationCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class HydrationCalculatorTest {

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
        assertEquals(200.0, HydrationCalculator.parseLiquidVolumeMl(milk), 0.01)

        // Water with non-breaking space in 1 111 x 1 ml
        val waterThousands = Foodstuff(
            title = "voda čistá",
            name = null, nazev = null, energy = null, protein = null, carbohydrate = null,
            fat = null, sugar = null, salt = null, water = "1111.0", fiber = null,
            foodstuff = null, items = null, ingredients = null, components = null,
            guid = "w1", guidRecipe = null, isRecipe = false, unit = "1\u00a0111 x 1 ml"
        )
        assertEquals(1111.0, HydrationCalculator.parseLiquidVolumeMl(waterThousands), 0.01)

        // Tomato juice: 3 x 100 ml
        val juice = Foodstuff(
            title = "Džus 100% rajče Relax",
            name = null, nazev = null, energy = null, protein = null, carbohydrate = null,
            fat = null, sugar = null, salt = null, water = null, fiber = null,
            foodstuff = null, items = null, ingredients = null, components = null,
            guid = "j1", guidRecipe = null, isRecipe = false, unit = "3 x 100 ml"
        )
        assertEquals(300.0, HydrationCalculator.parseLiquidVolumeMl(juice), 0.01)

        // Non-alcoholic beer in single multiplier: 500 x 1 ml
        val birell = Foodstuff(
            title = "Pomelo & grep Birell",
            name = null, nazev = null, energy = null, protein = null, carbohydrate = null,
            fat = null, sugar = null, salt = null, water = null, fiber = null,
            foodstuff = null, items = null, ingredients = null, components = null,
            guid = "b1", guidRecipe = null, isRecipe = false, unit = "500 x 1 ml"
        )
        assertEquals(500.0, HydrationCalculator.parseLiquidVolumeMl(birell), 0.01)

        // Mineral water in liters: 1 x 1.5 l
        val magnesia = Foodstuff(
            title = "Neperlivá Magnesia",
            name = null, nazev = null, energy = null, protein = null, carbohydrate = null,
            fat = null, sugar = null, salt = null, water = null, fiber = null,
            foodstuff = null, items = null, ingredients = null, components = null,
            guid = "mag1", guidRecipe = null, isRecipe = false, unit = "1 x 1.5 l"
        )
        assertEquals(1500.0, HydrationCalculator.parseLiquidVolumeMl(magnesia), 0.01)

        // Juice in container: balení (1 000 ml)
        val pack = Foodstuff(
            title = "Pomerančový džus",
            name = null, nazev = null, energy = null, protein = null, carbohydrate = null,
            fat = null, sugar = null, salt = null, water = null, fiber = null,
            foodstuff = null, items = null, ingredients = null, components = null,
            guid = "p1", guidRecipe = null, isRecipe = false, unit = "balení (1\u00a0000 ml)"
        )
        assertEquals(1000.0, HydrationCalculator.parseLiquidVolumeMl(pack), 0.01)

        // Cooking oil: 10 x 1 ml should be excluded (0.0 ml fluid)
        val oil = Foodstuff(
            title = "Olivový olej extra panenský",
            name = null, nazev = null, energy = null, protein = null, carbohydrate = null,
            fat = null, sugar = null, salt = null, water = null, fiber = null,
            foodstuff = null, items = null, ingredients = null, components = null,
            guid = "o1", guidRecipe = null, isRecipe = false, unit = "10 x 1 ml"
        )
        assertEquals(0.0, HydrationCalculator.parseLiquidVolumeMl(oil), 0.01)

        // Solid food moisture: Goulash (178 x 1 g, water=21.68) should be excluded
        val gulas = Foodstuff(
            title = "Gulas 2.9",
            name = null, nazev = null, energy = null, protein = null, carbohydrate = null,
            fat = null, sugar = null, salt = null, water = "21.68", fiber = null,
            foodstuff = null, items = null, ingredients = null, components = null,
            guid = "g1", guidRecipe = null, isRecipe = false, unit = "178 x 1 g"
        )
        assertEquals(0.0, HydrationCalculator.parseLiquidVolumeMl(gulas), 0.01)

        // Drinkable fermented dairy: Kefir in grams (225 x 1 g)
        val kefir = Foodstuff(
            title = "Selský kefír z čerstvého mléka s živou kulturou 4% Milknatur",
            name = null, nazev = null, energy = null, protein = null, carbohydrate = null,
            fat = null, sugar = null, salt = null, water = null, fiber = null,
            foodstuff = null, items = null, ingredients = null, components = null,
            guid = "k1", guidRecipe = null, isRecipe = false, unit = "225 x 1 g"
        )
        assertEquals(225.0, HydrationCalculator.parseLiquidVolumeMl(kefir), 0.01)

        // Soup / broth: Borsch with recipe water (359.3 ml)
        val borsch = Foodstuff(
            title = "Borsch 25.8",
            name = null, nazev = null, energy = null, protein = null, carbohydrate = null,
            fat = null, sugar = null, salt = null, water = "359.32", fiber = null,
            foodstuff = null, items = null, ingredients = null, components = null,
            guid = "b1", guidRecipe = null, isRecipe = true, unit = "7,5%"
        )
        assertEquals(359.32, HydrationCalculator.parseLiquidVolumeMl(borsch), 0.01)

        // Sauce / condiment in ml: Sriracha (20 x 1 ml) should be excluded
        val sriracha = Foodstuff(
            title = "Sriracha hot chilli sauce Uni-Eagle",
            name = null, nazev = null, energy = null, protein = null, carbohydrate = null,
            fat = null, sugar = null, salt = null, water = null, fiber = null,
            foodstuff = null, items = null, ingredients = null, components = null,
            guid = "s1", guidRecipe = null, isRecipe = false, unit = "20 x 1 ml"
        )
        assertEquals(0.0, HydrationCalculator.parseLiquidVolumeMl(sriracha), 0.01)

        // Watermelon with KT water field (e.g. 500g has 457.5g water)
        val watermelonKt = Foodstuff(
            title = "Meloun vodní",
            name = null, nazev = null, energy = "155.0", protein = "3.0", carbohydrate = "37.5",
            fat = "0.75", sugar = "31.0", salt = "0.01", water = "457.5", fiber = "2.0",
            foodstuff = null, items = null, ingredients = null, components = null,
            guid = "w1", guidRecipe = null, isRecipe = false, unit = "500 x 1 g"
        )
        assertEquals(457.5, HydrationCalculator.parseLiquidVolumeMl(watermelonKt), 0.01)

        // Watermelon fallback in grams without water field (1000g * 0.915 = 915.0 ml)
        val watermelonFallbackG = Foodstuff(
            title = "Арбуз свежий",
            name = null, nazev = null, energy = null, protein = null, carbohydrate = null,
            fat = null, sugar = null, salt = null, water = null, fiber = null,
            foodstuff = null, items = null, ingredients = null, components = null,
            guid = "w2", guidRecipe = null, isRecipe = false, unit = "1000 x 1 g"
        )
        assertEquals(915.0, HydrationCalculator.parseLiquidVolumeMl(watermelonFallbackG), 0.01)

        // Watermelon fallback in kg without water field (1 x 1 kg -> 915.0 ml)
        val watermelonFallbackKg = Foodstuff(
            title = "Watermelon",
            name = null, nazev = null, energy = null, protein = null, carbohydrate = null,
            fat = null, sugar = null, salt = null, water = null, fiber = null,
            foodstuff = null, items = null, ingredients = null, components = null,
            guid = "w3", guidRecipe = null, isRecipe = false, unit = "1 x 1 kg"
        )
        assertEquals(915.0, HydrationCalculator.parseLiquidVolumeMl(watermelonFallbackKg), 0.01)

        // Watermelon syrup should be excluded
        val watermelonSyrup = Foodstuff(
            title = "Melounový sirup Monin",
            name = null, nazev = null, energy = null, protein = null, carbohydrate = null,
            fat = null, sugar = null, salt = null, water = null, fiber = null,
            foodstuff = null, items = null, ingredients = null, components = null,
            guid = "w4", guidRecipe = null, isRecipe = false, unit = "20 x 1 ml"
        )
        assertEquals(0.0, HydrationCalculator.parseLiquidVolumeMl(watermelonSyrup), 0.01)

        // Magnesia logged in grams (900 x 1 g, water = null) -> 900.0 ml
        val magnesiaGrams = Foodstuff(
            title = "Přírodní minerální voda Magnesia neperlivá",
            name = null, nazev = null, energy = "0", protein = "0", carbohydrate = "0",
            fat = "0", sugar = "0", salt = "0", water = null, fiber = "0",
            foodstuff = null, items = null, ingredients = null, components = null,
            guid = "mag2", guidRecipe = null, isRecipe = false, unit = "900 x 1 g"
        )
        assertEquals(900.0, HydrationCalculator.parseLiquidVolumeMl(magnesiaGrams), 0.01)

        // Magnesia simple grams (1500 g, water = "0") -> 1500.0 ml
        val magnesiaSimpleG = Foodstuff(
            title = "Magnesia Red",
            name = null, nazev = null, energy = null, protein = null, carbohydrate = null,
            fat = null, sugar = null, salt = null, water = "0", fiber = null,
            foodstuff = null, items = null, ingredients = null, components = null,
            guid = "mag3", guidRecipe = null, isRecipe = false, unit = "1500 g"
        )
        assertEquals(1500.0, HydrationCalculator.parseLiquidVolumeMl(magnesiaSimpleG), 0.01)

        // Mineral water container in grams: 1 x láhev (1500 g) -> 1500.0 ml
        val mattoniBottleG = Foodstuff(
            title = "Mattoni Grand perlivá",
            name = null, nazev = null, energy = null, protein = null, carbohydrate = null,
            fat = null, sugar = null, salt = null, water = null, fiber = null,
            foodstuff = null, items = null, ingredients = null, components = null,
            guid = "mat1", guidRecipe = null, isRecipe = false, unit = "1 x láhev (1500 g)"
        )
        assertEquals(1500.0, HydrationCalculator.parseLiquidVolumeMl(mattoniBottleG), 0.01)

        // Fresh cucumber (okurka salátová, 200 g) -> 200 * 0.952 = 190.4 ml
        val cucumberG = Foodstuff(
            title = "Okurka salátová",
            name = null, nazev = null, energy = null, protein = null, carbohydrate = null,
            fat = null, sugar = null, salt = null, water = null, fiber = null,
            foodstuff = null, items = null, ingredients = null, components = null,
            guid = "c1", guidRecipe = null, isRecipe = false, unit = "200 g"
        )
        assertEquals(190.4, HydrationCalculator.parseLiquidVolumeMl(cucumberG), 0.01)

        // Fresh cucumber Russian (Огурцы свежие, 150 x 1 g) -> 150 * 0.952 = 142.8 ml
        val cucumberRu = Foodstuff(
            title = "Огурцы свежие",
            name = null, nazev = null, energy = null, protein = null, carbohydrate = null,
            fat = null, sugar = null, salt = null, water = null, fiber = null,
            foodstuff = null, items = null, ingredients = null, components = null,
            guid = "c2", guidRecipe = null, isRecipe = false, unit = "150 x 1 g"
        )
        assertEquals(142.8, HydrationCalculator.parseLiquidVolumeMl(cucumberRu), 0.01)

        // Pickled cucumber (Kyselé okurky) -> excluded from raw hydration (0.0 ml)
        val pickledCucumber = Foodstuff(
            title = "Kyselé okurky Znojmia",
            name = null, nazev = null, energy = null, protein = null, carbohydrate = null,
            fat = null, sugar = null, salt = null, water = null, fiber = null,
            foodstuff = null, items = null, ingredients = null, components = null,
            guid = "c3", guidRecipe = null, isRecipe = false, unit = "100 g"
        )
        assertEquals(0.0, HydrationCalculator.parseLiquidVolumeMl(pickledCucumber), 0.01)

        // Meat & Steaks: hovězí zadní steak z válečku must NOT be parsed as "tea" beverage (0.0 ml)
        val hoveziSteak = Foodstuff(
            title = "hovězí zadní steak z válečku",
            name = null, nazev = null, energy = "110", protein = "22", carbohydrate = null,
            fat = "2.3", sugar = null, salt = "0.25", water = null, fiber = null,
            foodstuff = null, items = null, ingredients = null, components = null,
            guid = "hs1", guidRecipe = null, isRecipe = false, unit = "200 g"
        )
        assertEquals(0.0, HydrationCalculator.parseLiquidVolumeMl(hoveziSteak), 0.01)

        // Real tea beverage: must still be parsed correctly
        val blackTea = Foodstuff(
            title = "Černý čaj s citrónem",
            name = null, nazev = null, energy = "5", protein = "0", carbohydrate = "1",
            fat = "0", sugar = "1", salt = "0", water = null, fiber = "0",
            foodstuff = null, items = null, ingredients = null, components = null,
            guid = "tea1", guidRecipe = null, isRecipe = false, unit = "250 ml"
        )
        assertEquals(250.0, HydrationCalculator.parseLiquidVolumeMl(blackTea), 0.01)

        val greenTea = Foodstuff(
            title = "Green tea",
            name = null, nazev = null, energy = null, protein = null, carbohydrate = null,
            fat = null, sugar = null, salt = null, water = null, fiber = null,
            foodstuff = null, items = null, ingredients = null, components = null,
            guid = "tea2", guidRecipe = null, isRecipe = false, unit = "300 ml"
        )
        assertEquals(300.0, HydrationCalculator.parseLiquidVolumeMl(greenTea), 0.01)

        // Food with FoodCategory.BEVERAGE logged in grams
        val customDrink = Foodstuff(
            title = "Custom energy mix powder",
            unit = "250 g"
        )
        assertEquals(250.0, HydrationCalculator.parseLiquidVolumeMl(customDrink, com.example.kthealthsync.domain.model.FoodCategory.BEVERAGE), 0.01)
        assertEquals(0.0, HydrationCalculator.parseLiquidVolumeMl(customDrink, com.example.kthealthsync.domain.model.FoodCategory.OTHER), 0.01)
    }
}
