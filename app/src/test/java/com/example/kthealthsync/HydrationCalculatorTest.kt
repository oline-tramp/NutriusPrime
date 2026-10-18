package com.example.kthealthsync

import com.example.kthealthsync.domain.model.FoodCategory
import com.example.kthealthsync.domain.util.HydrationCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class HydrationCalculatorTest {

    @Test
    fun testParseLiquidVolumeMl() {
        // Milk in ml multiplier with KT URL
        val milk = Foodstuff(
            title = "Long life milk semi skimmed 1,5% fat lactose free Miil",
            url = "nealkoholicke-napoje/mleko-miil",
            unit = "200 x 1 ml"
        )
        assertEquals(200.0, HydrationCalculator.parseLiquidVolumeMl(milk), 0.01)

        // Water with non-breaking space in 1 111 x 1 ml
        val waterThousands = Foodstuff(
            title = "voda čistá",
            url = "nealkoholicke-napoje/voda-cista",
            unit = "1\u00a0111 x 1 ml"
        )
        assertEquals(1111.0, HydrationCalculator.parseLiquidVolumeMl(waterThousands), 0.01)

        // Tomato juice: 3 x 100 ml
        val juice = Foodstuff(
            title = "Džus 100% rajče Relax",
            url = "nealkoholicke-napoje/dzus-rajce",
            unit = "3 x 100 ml"
        )
        assertEquals(300.0, HydrationCalculator.parseLiquidVolumeMl(juice), 0.01)

        // Non-alcoholic beer in single multiplier: 500 x 1 ml
        val birell = Foodstuff(
            title = "Pomelo & grep Birell",
            url = "nealkoholicke-napoje/pomelo-grep-birell",
            unit = "500 x 1 ml"
        )
        assertEquals(500.0, HydrationCalculator.parseLiquidVolumeMl(birell), 0.01)

        // Mineral water in liters: 1 x 1.5 l
        val magnesia = Foodstuff(
            title = "Neperlivá Magnesia",
            url = "nealkoholicke-napoje/magnesia",
            unit = "1 x 1.5 l"
        )
        assertEquals(1500.0, HydrationCalculator.parseLiquidVolumeMl(magnesia), 0.01)

        // Juice in container: balení (1 000 ml)
        val pack = Foodstuff(
            title = "Pomerančový džus",
            url = "nealkoholicke-napoje/pomerancovy-dzus",
            unit = "balení (1\u00a0000 ml)"
        )
        assertEquals(1000.0, HydrationCalculator.parseLiquidVolumeMl(pack), 0.01)

        // Cooking oil: 10 x 1 ml should be excluded (0.0 ml fluid)
        val oil = Foodstuff(
            title = "Olivový olej extra panenský",
            url = "potraviny/olej-olivovy",
            unit = "10 x 1 ml"
        )
        assertEquals(0.0, HydrationCalculator.parseLiquidVolumeMl(oil), 0.01)

        // Solid food moisture: Goulash (178 x 1 g, water=21.68) should be excluded
        val gulas = Foodstuff(
            title = "Gulas 2.9",
            url = "jidla-a-prilohy/hovezi-gulas",
            water = "21.68",
            unit = "178 x 1 g"
        )
        assertEquals(0.0, HydrationCalculator.parseLiquidVolumeMl(gulas), 0.01)

        // Drinkable dairy: Kefir in grams (225 x 1 g) classified as BEVERAGE
        val kefir = Foodstuff(
            title = "Selský kefír z čerstvého mléka s živou kulturou 4% Milknatur",
            url = "nealkoholicke-napoje/selsky-kefir",
            unit = "225 x 1 g"
        )
        assertEquals(225.0, HydrationCalculator.parseLiquidVolumeMl(kefir), 0.01)

        // Soup / broth: Borsch with recipe water (359.3 ml)
        val borsch = Foodstuff(
            title = "Borsch 25.8",
            isRecipe = true,
            water = "359.32",
            unit = "7,5%"
        )
        assertEquals(359.32, HydrationCalculator.parseLiquidVolumeMl(borsch), 0.01)

        // Sauce / condiment in ml: Sriracha (20 x 1 ml) should be excluded
        val sriracha = Foodstuff(
            title = "Sriracha hot chilli sauce Uni-Eagle",
            url = "jidla-a-prilohy/sriracha",
            unit = "20 x 1 ml"
        )
        assertEquals(0.0, HydrationCalculator.parseLiquidVolumeMl(sriracha), 0.01)

        // Watermelon without BEVERAGE category returns 0.0 (counted as fruit quota, not fluid)
        val watermelonKt = Foodstuff(
            title = "Meloun vodní",
            url = "ovoce/meloun-vodni",
            unit = "500 x 1 g"
        )
        assertEquals(0.0, HydrationCalculator.parseLiquidVolumeMl(watermelonKt), 0.01)

        val watermelonFallbackG = Foodstuff(
            title = "Арбуз свежий",
            url = "ovoce/arbuz",
            unit = "1000 x 1 g"
        )
        assertEquals(0.0, HydrationCalculator.parseLiquidVolumeMl(watermelonFallbackG), 0.01)

        val watermelonFallbackKg = Foodstuff(
            title = "Watermelon",
            url = "ovoce/watermelon",
            unit = "1 x 1 kg"
        )
        assertEquals(0.0, HydrationCalculator.parseLiquidVolumeMl(watermelonFallbackKg), 0.01)

        // Watermelon syrup should be excluded
        val watermelonSyrup = Foodstuff(
            title = "Melounový sirup Monin",
            url = "cukrovinky/sirup",
            unit = "20 x 1 ml"
        )
        assertEquals(0.0, HydrationCalculator.parseLiquidVolumeMl(watermelonSyrup), 0.01)

        // Magnesia logged in grams (900 x 1 g, water = null) -> 900.0 ml
        val magnesiaGrams = Foodstuff(
            title = "Přírodní minerální voda Magnesia neperlivá",
            url = "nealkoholicke-napoje/magnesia",
            unit = "900 x 1 g"
        )
        assertEquals(900.0, HydrationCalculator.parseLiquidVolumeMl(magnesiaGrams), 0.01)

        // Magnesia simple grams (1500 g, water = "0") -> 1500.0 ml
        val magnesiaSimpleG = Foodstuff(
            title = "Magnesia Red",
            url = "nealkoholicke-napoje/magnesia-red",
            water = "0",
            unit = "1500 g"
        )
        assertEquals(1500.0, HydrationCalculator.parseLiquidVolumeMl(magnesiaSimpleG), 0.01)

        // Mineral water container in grams: 1 x láhev (1500 g) -> 1500.0 ml
        val mattoniBottleG = Foodstuff(
            title = "Mattoni Grand perlivá",
            url = "nealkoholicke-napoje/mattoni-grand",
            unit = "1 x láhev (1500 g)"
        )
        assertEquals(1500.0, HydrationCalculator.parseLiquidVolumeMl(mattoniBottleG), 0.01)

        // Fresh cucumber (okurka salátová, 200 g) -> 0.0 (counted as vegetable quota)
        val cucumberG = Foodstuff(
            title = "Okurka salátová",
            url = "zelenina/okurka-salatova",
            unit = "200 g"
        )
        assertEquals(0.0, HydrationCalculator.parseLiquidVolumeMl(cucumberG), 0.01)

        // Fresh cucumber Russian (Огурцы свежие, 150 x 1 g) -> 0.0
        val cucumberRu = Foodstuff(
            title = "Огурцы свежие",
            url = "zelenina/ogurcy",
            unit = "150 x 1 g"
        )
        assertEquals(0.0, HydrationCalculator.parseLiquidVolumeMl(cucumberRu), 0.01)

        // Pickled cucumber (Kyselé okurky) -> excluded from raw hydration (0.0 ml)
        val pickledCucumber = Foodstuff(
            title = "Kyselé okurky Znojmia",
            url = "zelenina/kysele-okurky",
            unit = "100 g"
        )
        assertEquals(0.0, HydrationCalculator.parseLiquidVolumeMl(pickledCucumber), 0.01)

        // Meat & Steaks: hovězí zadní steak z válečku must NOT be parsed as beverage (0.0 ml)
        val hoveziSteak = Foodstuff(
            title = "hovězí zadní steak z válečku",
            url = "maso/hovezi-steak",
            unit = "200 g"
        )
        assertEquals(0.0, HydrationCalculator.parseLiquidVolumeMl(hoveziSteak), 0.01)

        // Real tea beverage: with FoodCategory.BEVERAGE
        val blackTea = Foodstuff(
            title = "Černý čaj s citrónem",
            url = "nealkoholicke-napoje/cerny-caj",
            unit = "250 ml"
        )
        assertEquals(250.0, HydrationCalculator.parseLiquidVolumeMl(blackTea), 0.01)

        val greenTea = Foodstuff(
            title = "Green tea",
            url = "nealkoholicke-napoje/zeleny-caj",
            unit = "300 ml"
        )
        assertEquals(300.0, HydrationCalculator.parseLiquidVolumeMl(greenTea), 0.01)

        // Direct explicit category passing
        val customDrink = Foodstuff(
            title = "Custom energy mix powder",
            unit = "250 g"
        )
        assertEquals(250.0, HydrationCalculator.parseLiquidVolumeMl(customDrink, FoodCategory.BEVERAGE), 0.01)
        assertEquals(0.0, HydrationCalculator.parseLiquidVolumeMl(customDrink, FoodCategory.OTHER), 0.01)
    }
}
