package com.example.kthealthsync

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
        val mealListResp = api.getMealList(cookie)
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
        
        val recipeEnergy = parseNutrient(recipeData!!.energy)
        val loggedEnergy = parseNutrient(borshRaw.energy)
        println("Recipe total energy: $recipeEnergy kcal. Logged energy: $loggedEnergy kcal")
        
        val scale = if (recipeEnergy > 0) loggedEnergy / recipeEnergy else 1.0
        
        // 5. Scale components
        val components = recipeData.content?.mapNotNull { item ->
            val compFood = item.foodstuff
            if (compFood != null) {
                compFood.copy(
                    protein = scaleNutrient(compFood.protein, scale),
                    fat = scaleNutrient(compFood.fat, scale),
                    carbohydrate = scaleNutrient(compFood.carbohydrate, scale),
                    sugar = scaleNutrient(compFood.sugar, scale),
                    salt = scaleNutrient(compFood.salt, scale),
                    fiber = scaleNutrient(compFood.fiber, scale),
                    water = scaleNutrient(compFood.water, scale),
                    energy = scaleNutrient(compFood.energy, scale)
                )
            } else null
        }
        
        val enrichedBorsh = borshRaw.copy(components = components, isRecipe = true)
        
        // 6. Test aggregation
        val allFoods = listOf(enrichedBorsh)
        val proteinBreakdown = aggregateMacro(allFoods, { parseNutrient(it.protein) }, withMeals = true)
        
        println("Protein Breakdown:")
        proteinBreakdown.forEach { breakdown ->
            println("- ${breakdown.name}: ${breakdown.amount}g")
            breakdown.components.forEach { comp ->
                println("  -- ${comp.name}: ${comp.amount}g")
            }
        }
        
        val totalBorshProteinFromComponents = proteinBreakdown.first().components.sumOf { it.amount }
        val borshLoggedProtein = parseNutrient(borshRaw.protein)
        
        println("Borsh logged protein: $borshLoggedProtein. Sum of components: $totalBorshProteinFromComponents")
        
        assertEquals(borshLoggedProtein, totalBorshProteinFromComponents, 1.0)
    }
}
