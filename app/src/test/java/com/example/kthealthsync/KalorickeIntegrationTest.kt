package com.example.kthealthsync

import com.example.kthealthsync.domain.model.FoodCategory
import com.example.kthealthsync.domain.util.NutrientUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Properties

class KalorickeIntegrationTest {
    
    private lateinit var cookie: String

    @Before
    fun setup() {
        val localPropsFile = File("../local.properties")
        if (!localPropsFile.exists()) {
            throw IllegalStateException("Missing local.properties file at ${localPropsFile.absolutePath}")
        }
        val properties = Properties()
        properties.load(localPropsFile.inputStream())
        
        cookie = properties.getProperty("KALORICKE_COOKIE") ?: ""
        if (cookie.isEmpty()) {
            throw IllegalStateException("KALORICKE_COOKIE is not defined in local.properties. Please add it!")
        }
    }

    @Test
    fun testBorshIntegration() = runBlocking {
        println("Using cookie starting with: ${cookie.take(20)}...")
        val api = RetrofitClient.instance
        
        // 1. Fetch meal list
        val mealListResp = try {
            api.getMealList(cookie)
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 302 || e.code() == 401) {
                org.junit.Assume.assumeTrue("Live session expired (HTTP ${e.code()}). Update KALORICKE_COOKIE in local.properties to run live integration test.", false)
                return@runBlocking
            }
            throw e
        }
        val mealMap = mutableMapOf<String, String>()
        mealListResp.data?.forEach { item ->
            val title = item.title
            val guid = item.guid
            if (title != null && guid != null) {
                mealMap[title] = guid
            }
        }
        println("Fetched ${mealMap.size} custom meals.")
        
        // 2. Fetch today's diary
        // The user specified to use today's date and find "Borsh 25.8"
        val diaryResp = api.getDiary("25.08.2026", cookie)
        val rawFoods = diaryResp.data?.times?.flatMap { it.foodstuff ?: emptyList() } ?: emptyList()
        println("Fetched ${rawFoods.size} foods from diary.")
        
        // 3. Find Borsh
        val borshRaw = rawFoods.find { it.title?.contains("Borsch", ignoreCase = true) == true || it.title?.contains("Borsh", ignoreCase = true) == true }
        assertNotNull("Could not find Borsh in the diary for 25.08.2026!", borshRaw)
        
        val name = borshRaw!!.title ?: borshRaw.name ?: borshRaw.nazev
        val borshGuid = borshRaw.guid ?: borshRaw.guidRecipe ?: mealMap[name]
        if (borshGuid == null) {
            println("Borsh found but no guid! Raw object: $borshRaw")
        }
        assertNotNull("Borsh must have a guid from mapping!", borshGuid)
        assertTrue("Borsh guid must be in the meal map!", mealMap.containsValue(borshGuid))
        
        println("Found Borsh with guid $borshGuid and energy ${borshRaw.energy}")
        
        // 4. Fetch recipe details
        val recipeData = api.getRecipeDetail(borshGuid!!, cookie).data
        assertNotNull("Failed to fetch recipe details for Borsh!", recipeData)
        
        val recipeEnergy = NutrientUtils.parseNutrient(recipeData!!.energy)
        val loggedEnergy = NutrientUtils.parseNutrient(borshRaw.energy)
        println("Recipe total energy: $recipeEnergy kcal. Logged energy: $loggedEnergy kcal")
        
        val scale = if (recipeEnergy > 0) loggedEnergy / recipeEnergy else 1.0
        
        // 5. Scale components
        val components = recipeData.content?.mapNotNull { item ->
            val compFood = item.foodstuff
            if (compFood != null) {
                compFood.copy(
                    protein = NutrientUtils.scaleNutrient(compFood.protein, scale),
                    fat = NutrientUtils.scaleNutrient(compFood.fat, scale),
                    carbohydrate = NutrientUtils.scaleNutrient(compFood.carbohydrate, scale),
                    sugar = NutrientUtils.scaleNutrient(compFood.sugar, scale),
                    salt = NutrientUtils.scaleNutrient(compFood.salt, scale),
                    fiber = NutrientUtils.scaleNutrient(compFood.fiber, scale),
                    water = NutrientUtils.scaleNutrient(compFood.water, scale),
                    energy = NutrientUtils.scaleNutrient(compFood.energy, scale)
                )
            } else null
        }
        
        val enrichedBorsh = borshRaw.copy(components = components, isRecipe = true)
        
        // 6. Test aggregation
        val allFoods = listOf(enrichedBorsh)
        val proteinBreakdown = NutrientUtils.aggregateMacro(allFoods, { NutrientUtils.parseNutrient(it.protein) }, withMeals = true)
        
        println("Protein Breakdown:")
        proteinBreakdown.forEach { breakdown ->
            println("- ${breakdown.name}: ${breakdown.amount}g")
            breakdown.components.forEach { comp ->
                println("  -- ${comp.name}: ${comp.amount}g")
            }
        }
        
        val totalBorshProteinFromComponents = proteinBreakdown.first().components.sumOf { it.amount }
        val borshLoggedProtein = NutrientUtils.parseNutrient(borshRaw.protein)
        
        println("Borsh logged protein: $borshLoggedProtein. Sum of components: $totalBorshProteinFromComponents")
        
        assertEquals(borshLoggedProtein, totalBorshProteinFromComponents, 1.0)
    }

    @Test
    fun testYesterdaySupplements() = runBlocking {
        println("Using cookie starting with: ${cookie.take(20)}...")
        val api = RetrofitClient.instance
        
        try {
            api.getDiary("07.09.2026", cookie)
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 302 || e.code() == 401) {
                org.junit.Assume.assumeTrue("Live session expired (HTTP ${e.code()}). Update KALORICKE_COOKIE in local.properties to run live integration test.", false)
                return@runBlocking
            }
        }
        
        listOf("01.09.2026", "02.09.2026", "03.09.2026", "04.09.2026", "05.09.2026", "06.09.2026", "07.09.2026", "08.09.2026").forEach { testDate ->
            val dResp = api.getDiary(testDate, cookie)
            val allF = dResp.data?.times?.flatMap { it.foodstuff ?: emptyList() } ?: emptyList()
            val citrulin = allF.filter { (it.title ?: "").contains("citrul", ignoreCase = true) || (it.title ?: "").contains("malat", ignoreCase = true) }
            if (citrulin.isNotEmpty()) {
                println(">>> Found on $testDate: ${citrulin.map { "${it.title} (unit=${it.unit})" }}")
            }
        }

        try {
            val freq = api.getFrequentFoodstuff(0, 50, cookie)
            val matchingFreq = freq.filter { (it.title ?: "").contains("citrul", ignoreCase = true) || (it.title ?: "").contains("malat", ignoreCase = true) }
            println(">>> Matching frequent items: ${matchingFreq.map { "${it.title} (url=${it.url})" }}")
        } catch (e: Exception) {
            println("getFrequentFoodstuff error: ${e.message}")
        }
        
        try {
            val html = api.getFoodHtml("https://www.kaloricketabulky.cz/potraviny/citrulline-malate", cookie).string()
            val crumbRegex = "class=\"crumb-link\"\\s+href=\"(/[^\"]+)\"".toRegex()
            val allHrefs = crumbRegex.findAll(html).map { it.groupValues[1].lowercase() }.toList()
            println(">>> citrulline-malate crumb-links: $allHrefs")
        } catch (e: Exception) {
            println("citrulline-malate error: ${e.message}")
        }
        
        val diaryResp = api.getDiary("07.09.2026", cookie)
        val times = diaryResp.data?.times ?: emptyList()
        println("Times count: ${times.size}")
        times.forEach { meal ->
            println("=== Meal: ${meal.title ?: meal.name} (id=${meal.id}) ===")
            meal.foodstuff?.forEach { f ->
                println("Food: title='${f.title ?: f.name}' unit='${f.unit}' energy='${f.energy}' guid='${f.guid}' id='${f.id}'")
                
                // Fetch food form to get URL
                var itemUrl: String? = null
                if (f.guid != null) {
                    try {
                        val form = api.getFoodForm(f.guid!!, cookie)
                        itemUrl = form.data?.url
                        println("  -> from getFoodForm: url=$itemUrl")
                    } catch (e: Exception) {
                        println("  -> getFoodForm error: ${e.message}")
                    }
                }
                if (itemUrl.isNullOrBlank()) {
                    val title = f.title ?: f.name ?: f.nazev
                    if (!title.isNullOrBlank()) {
                        try {
                            val search = api.searchFood(title, 0, cookie)
                            itemUrl = search.firstOrNull()?.url
                            println("  -> from searchFood: url=$itemUrl")
                        } catch (e: Exception) {
                            println("  -> searchFood error: ${e.message}")
                        }
                    }
                }
                
                if (!itemUrl.isNullOrBlank()) {
                    val fullUrl = if (itemUrl.contains("/")) itemUrl else "potraviny/$itemUrl"
                    val htmlUrl = "https://www.kaloricketabulky.cz/$fullUrl"
                    try {
                        val html = api.getFoodHtml(htmlUrl, cookie).string()
                        val category = NutrientUtils.parseCategoryFromHtml(html, f.title ?: f.name ?: f.nazev)
                        val count = if (category == FoodCategory.SUPPLEMENT) NutrientUtils.parseSupplementCount(f.unit) else 0.0
                        println("  -> category=$category, count=$count")
                    } catch (e: Exception) {
                        println("  -> getFoodHtml error: ${e.message}")
                    }
                }
            }
        }
    }

    @Test
    fun testBarcodeSearch() {
        runBlocking {
        val api = RetrofitClient.instance
        val testBarcodes = listOf(
            "8000500179864", // Nutella
            "5449000000996"  // Coca-Cola
        )
        val client = okhttp3.OkHttpClient()
        for (barcode in testBarcodes) {
            try {
                val results = api.searchFood(barcode, 0, cookie)
                println("KT Barcode $barcode search result count: ${results.size}")
                results.forEach { item ->
                    println("  -> KT found: id=${item.id}, title=${item.title}, unit=${item.unit}")
                }
                if (results.isEmpty()) {
                    // Try Open Food Facts
                    val req = okhttp3.Request.Builder()
                        .url("https://world.openfoodfacts.org/api/v0/product/$barcode.json")
                        .header("User-Agent", "NutriusPrime - Android")
                        .build()
                    val resp = client.newCall(req).execute()
                    val body = resp.body?.string() ?: ""
                    println("  -> OFF HTTP ${resp.code}, contains 'product_name': ${body.contains("product_name")}")
                    if (body.contains("\"product_name\"")) {
                        val nameRegex = Regex(""""product_name(?:_[a-z]+)?":\s*"([^"]+)"""")
                        val match = nameRegex.find(body)
                        val name = match?.groupValues?.get(1)
                        println("  -> OFF product_name: $name")
                        if (!name.isNullOrBlank()) {
                            val ktResults = api.searchFood(name, 0, cookie)
                            println("  -> Searching KT by OFF name '$name': count=${ktResults.size}")
                            ktResults.take(3).forEach {
                                println("     -> KT match: ${it.title} (${it.id})")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                println("Barcode $barcode search error: ${e.message}")
            }
        }
    }
}

    @Test
    fun testFetchUserProfileForm() {
        runBlocking {
            val api = RetrofitClient.instance
            try {
                val resp = api.getUserProfileForm(cookie)
                println("UserProfileForm code=${resp.code}, msg=${resp.message}")
                val data = resp.data
                println("ownTargetEnergy=${data?.ownTargetEnergy}, computed=${data?.computedTargetEnergy}, drinkRegime=${data?.drinkRegime}")
                println("ownNutrients:")
                data?.ownNutrients?.forEach {
                    println("  -> code=${it.code}, title=${it.title}, value=${it.value}, computed=${it.computedValue}")
                }
            } catch (e: Exception) {
                println("UserProfileForm error: ${e.message}")
            }
        }
    }
}
