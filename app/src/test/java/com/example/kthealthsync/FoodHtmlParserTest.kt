package com.example.kthealthsync

import com.example.kthealthsync.domain.model.FoodCategory
import com.example.kthealthsync.domain.util.FoodHtmlParser
import org.junit.Assert.assertEquals
import org.junit.Test

class FoodHtmlParserTest {

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

        val parsed = FoodHtmlParser.parseLdJsonMacros(sampleHtml)
        assertEquals(15.2, parsed["bílkoviny"] ?: 0.0, 0.01)
        assertEquals(40.0, parsed["sacharidy"] ?: 0.0, 0.01)
        assertEquals(8.5, parsed["tuky"] ?: 0.0, 0.01)
        assertEquals(5.0, parsed["vláknina"] ?: 0.0, 0.01)
        assertEquals(2.0, parsed["cukry"] ?: 0.0, 0.01)
        assertEquals(1.1, parsed["sůl"] ?: 0.0, 0.01)
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
        assertEquals(FoodCategory.VEGETABLE, FoodHtmlParser.parseCategoryFromHtml(vegHtml))

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
        assertEquals(FoodCategory.FRUIT, FoodHtmlParser.parseCategoryFromHtml(fruitHtml))

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
        assertEquals(FoodCategory.OTHER, FoodHtmlParser.parseCategoryFromHtml(potatoHtml))

        val breadHtml = """
            <a class="crumb-link" href="/pecivo">Pečivo</a>
        """.trimIndent()
        assertEquals(FoodCategory.OTHER, FoodHtmlParser.parseCategoryFromHtml(breadHtml))
    }

    @Test
    fun testParseCategoryFromHtml_SupplementsAndProtein() {
        val suppHtml = """
            <nav class="breadcrumb">
                <a class="crumb-link" href="/tabulka-potravin">Tabulka potravin</a>
                <a class="crumb-link" href="/doplnky-vyzivy">Doplňky výživy</a>
                <a class="crumb-link" href="/doplnky-vyzivy/vitaminy-mineraly-sirupy-kapky">Vitamíny a minerály</a>
            </nav>
        """.trimIndent()

        assertEquals(FoodCategory.SUPPLEMENT, FoodHtmlParser.parseCategoryFromHtml(suppHtml, "Magne B6 470mg, 5mg Sanofi"))
        assertEquals(FoodCategory.SUPPLEMENT, FoodHtmlParser.parseCategoryFromHtml(suppHtml, "OMEGA 3 rybí olej 1000 mg Nutricius"))
        assertEquals(FoodCategory.SUPPLEMENT, FoodHtmlParser.parseCategoryFromHtml(suppHtml, "Carnitine tabs 1000mg GymBeam"))
        assertEquals(FoodCategory.SUPPLEMENT, FoodHtmlParser.parseCategoryFromHtml(suppHtml, "Multivit 10 Dr.Max"))

        val fitnessHtml = """
            <nav class="breadcrumb">
                <a class="crumb-link" href="/tabulka-potravin">Tabulka potravin</a>
                <a class="crumb-link" href="/doplnky-vyzivy">Doplňky výživy</a>
                <a class="crumb-link" href="/doplnky-vyzivy/fitness-doplnky">Fitness doplňky</a>
            </nav>
        """.trimIndent()

        assertEquals(FoodCategory.OTHER, FoodHtmlParser.parseCategoryFromHtml(fitnessHtml, "IsoPrime CFM Isolate Vanilla Cream flavour Amix Nutrition"))
        assertEquals(FoodCategory.OTHER, FoodHtmlParser.parseCategoryFromHtml(fitnessHtml, "100% Whey Protein"))
    }

    @Test
    fun testParseCategoryFromUrl() {
        assertEquals(FoodCategory.VEGETABLE, FoodHtmlParser.parseCategoryFromUrl("zelenina/mrkev"))
        assertEquals(FoodCategory.OTHER, FoodHtmlParser.parseCategoryFromUrl("zelenina/brambory-a-topinambury"))
        assertEquals(FoodCategory.FRUIT, FoodHtmlParser.parseCategoryFromUrl("ovoce/jablko"))
        assertEquals(FoodCategory.BEVERAGE, FoodHtmlParser.parseCategoryFromUrl("napoje/voda-neperliva"))
        assertEquals(FoodCategory.BEVERAGE, FoodHtmlParser.parseCategoryFromUrl("napoje/caj-zeleny"))
        assertEquals(FoodCategory.SUPPLEMENT, FoodHtmlParser.parseCategoryFromUrl("doplnky-stravy/omega-3"))
        assertEquals(FoodCategory.OTHER, FoodHtmlParser.parseCategoryFromUrl("doplnky-stravy/whey-protein", "100% Whey Protein"))
        assertEquals(FoodCategory.OTHER, FoodHtmlParser.parseCategoryFromUrl("pecivo/chleb"))
        assertEquals(FoodCategory.OTHER, FoodHtmlParser.parseCategoryFromUrl("mlecne-vyrobky/jogurt"))
    }
}
