package com.example.kthealthsync.data.repository

import com.example.kthealthsync.*
import com.example.kthealthsync.domain.model.DailySummary
import com.example.kthealthsync.domain.model.FoodCategory
import com.example.kthealthsync.domain.util.NutrientUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class KalorickeRepository(
    private val api: KalorickeApiService = RetrofitClient.instance,
    private val prefs: PreferencesManager
) {
    // Thread-safe caches
    private val recipeDetailsCache = ConcurrentHashMap<String, RecipeData>()
    private val macroCache = ConcurrentHashMap<String, Map<String, Double>>()
    private val categoryMemoryCache = ConcurrentHashMap<String, FoodCategory>()
    
    @Volatile
    private var cachedMealMap: Map<String, String> = emptyMap()
    @Volatile
    private var lastMealListFetchTime: Long = 0
    @Volatile
    private var lastGoalsFetchTime: Long = 0

    suspend fun getCookie(): String = prefs.cookieFlow.first()

    suspend fun login(email: String, md5Password: String): retrofit2.Response<LoginResponse> {
        return api.login(LoginRequest(email, md5Password))
    }

    suspend fun getDiaryWithAutoLogin(date: String): DiaryResponse {
        var cookie = getCookie()
        if (cookie.isBlank()) throw IllegalStateException("No authentication cookie available. Please log in.")

        try {
            return api.getDiary(date, cookie)
        } catch (e: Exception) {
            val code = (e as? retrofit2.HttpException)?.code()
            val errorMsg = e.message ?: ""
            val isAuthError = code == 302 || code == 401 || errorMsg.contains("302") || errorMsg.contains("401")

            if (isAuthError) {
                val email = prefs.emailFlow.first()
                val passHash = prefs.passwordHashFlow.first()
                DebugLogger.addActionLog("Session expired (302/401). Saved creds: email=${email.isNotEmpty()}, hash=${passHash.isNotEmpty()}")

                if (email.isNotEmpty() && passHash.isNotEmpty()) {
                    DebugLogger.addActionLog("Attempting auto-login...")
                    try {
                        val loginResp = api.login(LoginRequest(email, passHash))
                        if (loginResp.isSuccessful && loginResp.body()?.code == 0) {
                            val jsessionCookie = loginResp.headers().values("Set-Cookie").firstOrNull { it.contains("JSESSIONID") }
                            if (jsessionCookie != null) {
                                cookie = jsessionCookie.substringBefore(";")
                                prefs.saveCookie(cookie)
                                DebugLogger.addActionLog("Auto-login successful, retrying fetch...")
                                return api.getDiary(date, cookie)
                            }
                        } else {
                            DebugLogger.addActionLog("Auto-login failed. Server returned code ${loginResp.code()}")
                        }
                    } catch (loginEx: Exception) {
                        DebugLogger.addActionLog("Auto-login request failed: ${loginEx.message}")
                    }
                } else {
                    DebugLogger.addActionLog("Cannot auto-login: No credentials saved.")
                }
            } else {
                DebugLogger.addActionLog("Fetch error (not 302): $errorMsg")
            }
            throw e
        }
    }

    suspend fun getMealMap(cookie: String): Map<String, String> {
        val now = System.currentTimeMillis()
        if (now - lastMealListFetchTime > 60_000 || cachedMealMap.isEmpty()) {
            try {
                val resp = api.getMealList(cookie)
                val map = mutableMapOf<String, String>()
                resp.data?.forEach { item ->
                    if (item.title != null && item.guid != null) {
                        map[item.title] = item.guid
                    }
                }
                cachedMealMap = map
                lastMealListFetchTime = now
            } catch (e: Exception) {
                DebugLogger.addActionLog("Failed to fetch meal list: ${e.message}")
            }
        }
        return cachedMealMap
    }

    suspend fun getRecipeDetail(guid: String, cookie: String): RecipeData? {
        recipeDetailsCache[guid]?.let { return it }
        return try {
            val resp = api.getRecipeDetail(guid, cookie)
            resp.data?.also { recipeDetailsCache[guid] = it }
        } catch (e: Exception) {
            DebugLogger.addActionLog("Failed to fetch recipe $guid: ${e.message}")
            null
        }
    }

    suspend fun getEnrichedDailySummary(dateStr: String, withMeals: Boolean): Pair<DailySummary, DiaryResponse> {
        val diaryResponse = getDiaryWithAutoLogin(dateStr)
        val cookie = getCookie()
        val mealMap = getMealMap(cookie)
        val rawFoods = diaryResponse.data?.times?.flatMap { it.foodstuff ?: emptyList() } ?: emptyList()

        val allFoodstuffs = rawFoods.map { f ->
            val name = f.title ?: f.name ?: f.nazev
            val guid = f.guid ?: f.guidRecipe ?: mealMap[name]
            if (guid != null && (mealMap.containsValue(guid) || mealMap.containsKey(name))) {
                val recipeData = getRecipeDetail(guid, cookie)
                if (recipeData != null) {
                    val recipeEnergy = NutrientUtils.parseNutrient(recipeData.energy)
                    val loggedEnergy = NutrientUtils.parseNutrient(f.energy)
                    val scale = if (recipeEnergy > 0) loggedEnergy / recipeEnergy else 1.0

                    val components = recipeData.content?.mapNotNull { item ->
                        item.foodstuff?.let { compFood ->
                            val origUnit = item.unit ?: compFood.unit
                            val origGrams = NutrientUtils.parsePortionGrams(origUnit)
                            val scaledUnit = if (origGrams > 0) "${String.format(Locale.US, "%.1f", origGrams * scale)} g" else origUnit
                            compFood.copy(
                                unit = scaledUnit,
                                protein = NutrientUtils.scaleNutrient(compFood.protein, scale),
                                fat = NutrientUtils.scaleNutrient(compFood.fat, scale),
                                carbohydrate = NutrientUtils.scaleNutrient(compFood.carbohydrate, scale),
                                sugar = NutrientUtils.scaleNutrient(compFood.sugar, scale),
                                salt = NutrientUtils.scaleNutrient(compFood.salt, scale),
                                fiber = NutrientUtils.scaleNutrient(compFood.fiber, scale),
                                water = NutrientUtils.scaleNutrient(compFood.water, scale),
                                energy = NutrientUtils.scaleNutrient(compFood.energy, scale)
                            )
                        }
                    }
                    f.copy(components = components, isRecipe = true)
                } else f
            } else f
        }

        // Preload categories for all individual foods and recipe ingredients in parallel
        val leafFoods = mutableListOf<Foodstuff>()
        for (f in allFoodstuffs) {
            val children = f.foodstuff ?: f.items ?: f.ingredients ?: f.components
            if (!children.isNullOrEmpty()) {
                leafFoods.addAll(children)
            } else {
                leafFoods.add(f)
            }
        }

        coroutineScope {
            leafFoods.distinctBy { it.guid ?: it.title }.map { food ->
                async { getFoodCategory(food) }
            }.awaitAll()
        }

        var kcal = 0.0; var prot = 0.0; var fat = 0.0; var carb = 0.0
        var sugar = 0.0; var salt = 0.0; var water = 0.0; var fiber = 0.0

        for (f in allFoodstuffs) {
            kcal += NutrientUtils.parseNutrient(f.energy)
            prot += NutrientUtils.parseNutrient(f.protein)
            fat += NutrientUtils.parseNutrient(f.fat)
            carb += NutrientUtils.parseNutrient(f.carbohydrate)
            sugar += NutrientUtils.parseNutrient(f.sugar)
            salt += NutrientUtils.parseNutrient(f.salt)
            water += NutrientUtils.parseLiquidVolumeMl(f)
            fiber += NutrientUtils.parseNutrient(f.fiber)
        }

        val vegBreakdown = NutrientUtils.aggregateMacro(allFoodstuffs, { f ->
            if (getFoodCategorySync(f) == FoodCategory.VEGETABLE) NutrientUtils.parsePortionGrams(f.unit) else 0.0
        }, withMeals)

        val fruitBreakdown = NutrientUtils.aggregateMacro(allFoodstuffs, { f ->
            if (getFoodCategorySync(f) == FoodCategory.FRUIT) NutrientUtils.parsePortionGrams(f.unit) else 0.0
        }, withMeals)

        val vegFruitBreakdown = NutrientUtils.aggregateMacro(allFoodstuffs, { f ->
            val cat = getFoodCategorySync(f)
            if (cat == FoodCategory.VEGETABLE || cat == FoodCategory.FRUIT) NutrientUtils.parsePortionGrams(f.unit) else 0.0
        }, withMeals)

        val vegGrams = vegBreakdown.sumOf { it.amount }
        val fruitGrams = fruitBreakdown.sumOf { it.amount }

        val summary = DailySummary(
            kcal = kcal,
            protein = prot,
            fat = fat,
            carb = carb,
            sugar = sugar,
            salt = salt,
            water = water,
            fiber = fiber,
            date = dateStr,
            kcalBreakdown = NutrientUtils.aggregateMacro(allFoodstuffs, { NutrientUtils.parseNutrient(it.energy) }, withMeals),
            proteinBreakdown = NutrientUtils.aggregateMacro(allFoodstuffs, { NutrientUtils.parseNutrient(it.protein) }, withMeals),
            fatBreakdown = NutrientUtils.aggregateMacro(allFoodstuffs, { NutrientUtils.parseNutrient(it.fat) }, withMeals),
            carbBreakdown = NutrientUtils.aggregateMacro(allFoodstuffs, { NutrientUtils.parseNutrient(it.carbohydrate) }, withMeals),
            sugarBreakdown = NutrientUtils.aggregateMacro(allFoodstuffs, { NutrientUtils.parseNutrient(it.sugar) }, withMeals),
            saltBreakdown = NutrientUtils.aggregateMacro(allFoodstuffs, { NutrientUtils.parseNutrient(it.salt) }, withMeals),
            waterBreakdown = NutrientUtils.aggregateMacro(allFoodstuffs, { NutrientUtils.parseLiquidVolumeMl(it) }, withMeals),
            fiberBreakdown = NutrientUtils.aggregateMacro(allFoodstuffs, { NutrientUtils.parseNutrient(it.fiber) }, withMeals),
            vegGrams = vegGrams,
            fruitGrams = fruitGrams,
            vegBreakdown = vegBreakdown,
            fruitBreakdown = fruitBreakdown,
            vegFruitBreakdown = vegFruitBreakdown
        )

        return summary to diaryResponse
    }

    fun getFoodCategorySync(food: Foodstuff): FoodCategory {
        val key = food.guid ?: food.title ?: return FoodCategory.OTHER
        return categoryMemoryCache[key] ?: FoodCategory.OTHER
    }

    suspend fun getFoodCategory(food: Foodstuff): FoodCategory {
        val key = food.guid ?: food.title ?: return FoodCategory.OTHER
        categoryMemoryCache[key]?.let { return it }

        val persisted = prefs.getFoodCategory(key)
        if (persisted != null) {
            val cat = try { FoodCategory.valueOf(persisted) } catch (_: Exception) { FoodCategory.OTHER }
            categoryMemoryCache[key] = cat
            return cat
        }

        val cat = fetchFoodCategoryFromWeb(food)
        categoryMemoryCache[key] = cat
        prefs.saveFoodCategory(key, cat.name)
        return cat
    }

    private suspend fun fetchFoodCategoryFromWeb(food: Foodstuff): FoodCategory {
        val cookie = getCookie()
        val guid = food.guid
        var itemUrl: String? = null

        if (guid != null) {
            try {
                val formResp = api.getFoodForm(guid, cookie)
                itemUrl = formResp.data?.url
            } catch (e: Exception) {
                DebugLogger.addActionLog("Failed to get food form for category $guid: ${e.message}")
            }
        }

        if (itemUrl.isNullOrBlank()) {
            val title = food.title ?: food.name ?: food.nazev
            if (!title.isNullOrBlank()) {
                try {
                    val searchItems = api.searchFood(query = title, page = 0, cookie = cookie)
                    itemUrl = searchItems.firstOrNull()?.url
                } catch (e: Exception) {
                    DebugLogger.addActionLog("Failed search food for category $title: ${e.message}")
                }
            }
        }

        if (itemUrl.isNullOrBlank()) return FoodCategory.OTHER

        return try {
            val fullUrl = if (itemUrl.contains("/")) itemUrl else "potraviny/$itemUrl"
            val htmlUrl = "https://www.kaloricketabulky.cz/$fullUrl"
            val htmlResp = api.getFoodHtml(htmlUrl, cookie).string()
            NutrientUtils.parseCategoryFromHtml(htmlResp)
        } catch (e: Exception) {
            DebugLogger.addActionLog("Failed to fetch food category HTML for $itemUrl: ${e.message}")
            FoodCategory.OTHER
        }
    }

    suspend fun getFrequentFoodstuff(page: Int = 0, limit: Int = 10): List<AutocompleteResponseItem> {
        val cookie = getCookie()
        return if (cookie.isNotEmpty()) {
            try {
                api.getFrequentFoodstuff(page = page, limit = limit, cookie = cookie).filter { it.id != null }
            } catch (e: Exception) {
                DebugLogger.addActionLog("Get frequent foodstuff page $page failed: ${e.message}")
                emptyList()
            }
        } else emptyList()
    }

    suspend fun getFavoriteFoodstuff(page: Int = 0, limit: Int = 10): List<AutocompleteResponseItem> {
        val cookie = getCookie()
        return if (cookie.isNotEmpty()) {
            try {
                api.getFavoriteFoodstuff(page = page, limit = limit, cookie = cookie).filter { it.id != null }
            } catch (e: Exception) {
                DebugLogger.addActionLog("Get favorite foodstuff page $page failed: ${e.message}")
                emptyList()
            }
        } else emptyList()
    }

    suspend fun getMyMeals(page: Int = 0, limit: Int = 50): List<AutocompleteResponseItem> {
        val cookie = getCookie()
        return if (cookie.isNotEmpty()) {
            try {
                api.getMyMeals(page = page, limit = limit, cookie = cookie).filter { it.id != null }
            } catch (e: Exception) {
                DebugLogger.addActionLog("Get my meals page $page failed: ${e.message}")
                emptyList()
            }
        } else emptyList()
    }

    suspend fun searchFood(
        query: String,
        customMeals: List<AutocompleteResponseItem>,
        page: Int = 0
    ): List<AutocompleteResponseItem> {
        val cookie = getCookie()
        if (cookie.isEmpty()) return emptyList()

        val serverResults = try {
            api.searchFood(query = query, page = page, cookie = cookie).filter { it.id != null }
        } catch (e: Exception) {
            DebugLogger.addActionLog("Search food page $page failed: ${e.message}")
            emptyList()
        }

        val localMatches = if (page == 0) {
            customMeals.filter {
                it.title?.contains(query, ignoreCase = true) == true
            }
        } else emptyList()

        return (localMatches + serverResults).distinctBy { it.id }
    }

    suspend fun getFoodForm(guid: String): FoodFormResponse {
        val cookie = getCookie()
        return api.getFoodForm(guid, cookie)
    }

    suspend fun getFoodMacrosFromWeb(itemUrl: String, formUrl: String?): Map<String, Double> {
        macroCache[itemUrl]?.let { return it }

        val cookie = getCookie()
        return try {
            val fullUrl = formUrl ?: if (itemUrl.contains("/")) itemUrl else "potraviny/$itemUrl"
            val htmlUrl = "https://www.kaloricketabulky.cz/$fullUrl"
            val htmlResp = api.getFoodHtml(htmlUrl, cookie).string()
            val parsed = NutrientUtils.parseLdJsonMacros(htmlResp)
            if (parsed.isNotEmpty()) {
                macroCache[itemUrl] = parsed
            }
            parsed
        } catch (e: Exception) {
            DebugLogger.addActionLog("Failed to fetch food macros HTML: ${e.message}")
            emptyMap()
        }
    }

    suspend fun addFoodstuff(request: AddFoodRequest): AddFoodResponse {
        val cookie = getCookie()
        return api.addFoodstuff(request, cookie)
    }

    /**
     * Fetches official user daily goals (DDD / Target energy and macros)
     * directly from Kalorické Tabulky user profile settings API.
     * Uses a 10-minute cache to prevent redundant requests unless force is true.
     */
    suspend fun fetchNutrientGoalsFromApi(force: Boolean = false): com.example.kthealthsync.domain.model.NutrientGoals? {
        val now = System.currentTimeMillis()
        if (!force && now - lastGoalsFetchTime < 10 * 60 * 1000L) {
            return prefs.nutrientGoalsFlow.first()
        }

        val cookie = getCookie()
        if (cookie.isBlank()) return null
        return try {
            val resp = api.getUserProfileForm(cookie)
            val data = resp.data ?: return null

            lastGoalsFetchTime = now

            val currentGoals = prefs.nutrientGoalsFlow.first()

            val kcal = data.ownTargetEnergy
                ?: NutrientUtils.parseNutrient(data.computedTargetEnergy).takeIf { it > 0 }
                ?: currentGoals.kcal

            var protein = currentGoals.protein
            var carbs = currentGoals.carbs
            var fat = currentGoals.fat
            var fiber = currentGoals.fiber
            var sugar = currentGoals.sugar
            var salt = currentGoals.salt

            data.ownNutrients?.forEach { item ->
                val num = item.value ?: NutrientUtils.parseNutrient(item.computedValue)
                if (num > 0) {
                    when (item.code?.lowercase()) {
                        "protein" -> protein = num
                        "carbohydrate" -> carbs = num
                        "fat" -> fat = num
                        "fiber" -> fiber = num
                        "sugar" -> sugar = num
                        "salt" -> salt = num
                    }
                }
            }

            val goals = com.example.kthealthsync.domain.model.NutrientGoals(
                kcal = kcal,
                protein = protein,
                carbs = carbs,
                fat = fat,
                fiber = fiber,
                sugar = sugar,
                salt = salt,
                vegFruit = currentGoals.vegFruit,
                water = data.drinkRegime ?: currentGoals.water
            )
            prefs.saveNutrientGoals(goals)
            DebugLogger.addActionLog("Fetched target goals from Kalorické Tabulky API: ${goals.kcal.toInt()} kcal, P:${goals.protein.toInt()}g, C:${goals.carbs.toInt()}g, F:${goals.fat.toInt()}g")
            goals
        } catch (e: Exception) {
            DebugLogger.addActionLog("Failed to fetch goals from API: ${e.message}")
            null
        }
    }
}
