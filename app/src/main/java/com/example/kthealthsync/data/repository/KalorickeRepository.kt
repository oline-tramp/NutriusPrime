package com.example.kthealthsync.data.repository

import com.example.kthealthsync.*
import com.example.kthealthsync.domain.model.DailySummary
import com.example.kthealthsync.domain.model.DiaryEditResult
import com.example.kthealthsync.domain.model.FoodCategory
import com.example.kthealthsync.domain.util.FoodHtmlParser
import com.example.kthealthsync.domain.util.HydrationCalculator
import com.example.kthealthsync.domain.util.NutrientCalculator
import com.example.kthealthsync.domain.util.NutrientParser
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

class KalorickeRepository(
    private val api: KalorickeApiService = RetrofitClient.instance,
    private val prefs: RepositoryPreferences
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
            if (e is CancellationException) throw e
            val code = (e as? retrofit2.HttpException)?.code()
            val errorMsg = e.message ?: ""
            val isAuthError = code == 302 || code == 401

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
                        if (loginEx is CancellationException) throw loginEx
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
                if (e is CancellationException) throw e
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
            if (e is CancellationException) throw e
            DebugLogger.addActionLog("Failed to fetch recipe $guid: ${e.message}")
            null
        }
    }

    suspend fun getEnrichedDailySummary(dateStr: String, withMeals: Boolean): Pair<DailySummary, DiaryResponse> {
        val diaryResponse = getDiaryWithAutoLogin(dateStr)
        val cookie = getCookie()
        val mealMap = getMealMap(cookie)
        val times = checkNotNull(diaryResponse.data?.times) { "Diary response is missing meal data" }
        val rawFoods = times.flatMap { it.foodstuff ?: emptyList() }

        val allFoodstuffs = rawFoods.map { f ->
            val name = f.title ?: f.name ?: f.nazev
            val guid = f.guid ?: f.guidRecipe ?: mealMap[name]
            if (guid != null && (mealMap.containsValue(guid) || mealMap.containsKey(name))) {
                val recipeData = getRecipeDetail(guid, cookie)
                if (recipeData != null) {
                    val recipeEnergy = NutrientParser.parseNutrient(recipeData.energy)
                    val loggedEnergy = NutrientParser.parseNutrient(f.energy)
                    val scale = if (recipeEnergy > 0) loggedEnergy / recipeEnergy else 1.0

                    val components = recipeData.content?.mapNotNull { item ->
                        item.foodstuff?.let { compFood ->
                            val origUnit = item.unit ?: compFood.unit
                            val origGrams = NutrientParser.parsePortionGrams(origUnit)
                            val scaledUnit = if (origGrams > 0) "${String.format(Locale.US, "%.1f", origGrams * scale)} g" else origUnit
                            compFood.copy(
                                unit = scaledUnit,
                                protein = NutrientCalculator.scaleNutrient(compFood.protein, scale),
                                fat = NutrientCalculator.scaleNutrient(compFood.fat, scale),
                                carbohydrate = NutrientCalculator.scaleNutrient(compFood.carbohydrate, scale),
                                sugar = NutrientCalculator.scaleNutrient(compFood.sugar, scale),
                                salt = NutrientCalculator.scaleNutrient(compFood.salt, scale),
                                fiber = NutrientCalculator.scaleNutrient(compFood.fiber, scale),
                                water = NutrientCalculator.scaleNutrient(compFood.water, scale),
                                energy = NutrientCalculator.scaleNutrient(compFood.energy, scale)
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
            leafFoods.distinctBy { it.guid ?: it.title ?: it.name ?: it.nazev }.map { food ->
                async { getFoodCategory(food) }
            }.awaitAll()
        }

        var kcal = 0.0; var prot = 0.0; var fat = 0.0; var carb = 0.0
        var sugar = 0.0; var salt = 0.0; var water = 0.0; var fiber = 0.0

        for (f in allFoodstuffs) {
            kcal += NutrientParser.parseNutrient(f.energy)
            prot += NutrientParser.parseNutrient(f.protein)
            fat += NutrientParser.parseNutrient(f.fat)
            carb += NutrientParser.parseNutrient(f.carbohydrate)
            sugar += NutrientParser.parseNutrient(f.sugar)
            salt += NutrientParser.parseNutrient(f.salt)
            water += HydrationCalculator.parseLiquidVolumeMl(f)
            fiber += NutrientParser.parseNutrient(f.fiber)
        }

        val vegBreakdown = NutrientCalculator.aggregateMacro(allFoodstuffs, { f ->
            if (getFoodCategorySync(f) == FoodCategory.VEGETABLE) NutrientParser.parsePortionGrams(f.unit) else 0.0
        }, withMeals)

        val fruitBreakdown = NutrientCalculator.aggregateMacro(allFoodstuffs, { f ->
            if (getFoodCategorySync(f) == FoodCategory.FRUIT) NutrientParser.parsePortionGrams(f.unit) else 0.0
        }, withMeals)

        val vegFruitBreakdown = NutrientCalculator.aggregateMacro(allFoodstuffs, { f ->
            val cat = getFoodCategorySync(f)
            if (cat == FoodCategory.VEGETABLE || cat == FoodCategory.FRUIT) NutrientParser.parsePortionGrams(f.unit) else 0.0
        }, withMeals)

        val supplementBreakdown = NutrientCalculator.aggregateMacro(allFoodstuffs, { f ->
            if (getFoodCategorySync(f) == FoodCategory.SUPPLEMENT) NutrientParser.parseSupplementCount(f.unit) else 0.0
        }, withMeals)

        val vegGrams = vegBreakdown.sumOf { it.amount }
        val fruitGrams = fruitBreakdown.sumOf { it.amount }
        val supplementCount = supplementBreakdown.sumOf { it.amount }.toInt()

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
            kcalBreakdown = NutrientCalculator.aggregateMacro(allFoodstuffs, { NutrientParser.parseNutrient(it.energy) }, withMeals),
            proteinBreakdown = NutrientCalculator.aggregateMacro(allFoodstuffs, { NutrientParser.parseNutrient(it.protein) }, withMeals),
            fatBreakdown = NutrientCalculator.aggregateMacro(allFoodstuffs, { NutrientParser.parseNutrient(it.fat) }, withMeals),
            carbBreakdown = NutrientCalculator.aggregateMacro(allFoodstuffs, { NutrientParser.parseNutrient(it.carbohydrate) }, withMeals),
            sugarBreakdown = NutrientCalculator.aggregateMacro(allFoodstuffs, { NutrientParser.parseNutrient(it.sugar) }, withMeals),
            saltBreakdown = NutrientCalculator.aggregateMacro(allFoodstuffs, { NutrientParser.parseNutrient(it.salt) }, withMeals),
            waterBreakdown = NutrientCalculator.aggregateMacro(allFoodstuffs, { HydrationCalculator.parseLiquidVolumeMl(it) }, withMeals),
            fiberBreakdown = NutrientCalculator.aggregateMacro(allFoodstuffs, { NutrientParser.parseNutrient(it.fiber) }, withMeals),
            vegGrams = vegGrams,
            fruitGrams = fruitGrams,
            vegBreakdown = vegBreakdown,
            fruitBreakdown = fruitBreakdown,
            vegFruitBreakdown = vegFruitBreakdown,
            supplementCount = supplementCount,
            supplementBreakdown = supplementBreakdown
        )

        return summary to diaryResponse
    }

    fun getFoodCategorySync(food: Foodstuff): FoodCategory {
        val key = food.guid ?: food.title ?: food.name ?: food.nazev ?: return FoodCategory.OTHER
        categoryMemoryCache[key]?.let { return it }
        val persisted = prefs.getFoodCategory(key)
        if (persisted != null) {
            val cat = try { FoodCategory.valueOf(persisted) } catch (_: Exception) { FoodCategory.OTHER }
            categoryMemoryCache[key] = cat
            return cat
        }
        return FoodCategory.OTHER
    }

    suspend fun getFoodCategory(food: Foodstuff): FoodCategory {
        val key = food.guid ?: food.title ?: food.name ?: food.nazev ?: return FoodCategory.OTHER
        categoryMemoryCache[key]?.let { return it }

        val persisted = prefs.getFoodCategory(key)
        if (persisted != null) {
            val cat = try { FoodCategory.valueOf(persisted) } catch (_: Exception) { FoodCategory.OTHER }
            categoryMemoryCache[key] = cat
            return cat
        }

        val cat = fetchFoodCategoryFromWeb(food) ?: return FoodCategory.OTHER
        categoryMemoryCache[key] = cat
        prefs.saveFoodCategory(key, cat.name)
        return cat
    }

    private suspend fun fetchFoodCategoryFromWeb(food: Foodstuff): FoodCategory? {
        val cookie = getCookie()
        val guid = food.guid
        var itemUrl: String? = null
        var lookupFailed = false

        if (guid != null) {
            try {
                val formResp = api.getFoodForm(guid, cookie)
                itemUrl = formResp.data?.url
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                lookupFailed = true
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
                    if (e is CancellationException) throw e
                    lookupFailed = true
                    DebugLogger.addActionLog("Failed search food for category $title: ${e.message}")
                }
            }
        }

        if (itemUrl.isNullOrBlank()) return if (lookupFailed) null else FoodCategory.OTHER

        return try {
            val fullUrl = if (itemUrl.contains("/")) itemUrl else "potraviny/$itemUrl"
            val htmlUrl = "https://www.kaloricketabulky.cz/$fullUrl"
            val htmlResp = api.getFoodHtml(htmlUrl, cookie).string()
            FoodHtmlParser.parseCategoryFromHtml(htmlResp, food.title ?: food.name ?: food.nazev)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            DebugLogger.addActionLog("Failed to fetch food category HTML for $itemUrl: ${e.message}")
            null
        }
    }

    suspend fun getFrequentFoodstuff(page: Int = 0, limit: Int = 10): List<AutocompleteResponseItem> {
        val cookie = getCookie()
        if (cookie.isEmpty()) return emptyList()
        return try {
            executeWithRetry(operationName = "getFrequentFoodstuff") { cookieParam ->
                api.getFrequentFoodstuff(page = page, limit = limit, cookie = cookieParam).filter { it.id != null }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            DebugLogger.addActionLog("Get frequent foodstuff page $page failed: ${e.message}")
            throw e
        }
    }

    suspend fun getFavoriteFoodstuff(page: Int = 0, limit: Int = 10): List<AutocompleteResponseItem> {
        val cookie = getCookie()
        if (cookie.isEmpty()) return emptyList()
        return try {
            executeWithRetry(operationName = "getFavoriteFoodstuff") { cookieParam ->
                api.getFavoriteFoodstuff(page = page, limit = limit, cookie = cookieParam).filter { it.id != null }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            DebugLogger.addActionLog("Get favorite foodstuff page $page failed: ${e.message}")
            throw e
        }
    }

    suspend fun getAllMyMeals(): List<AutocompleteResponseItem> {
        if (getCookie().isBlank()) return emptyList()
        val meals = linkedMapOf<String, AutocompleteResponseItem>()
        var page = 0
        while (true) {
            val response = executeWithRetry(operationName = "getAllMyMeals page $page") { cookie ->
                api.getMealList(cookie = cookie, page = page, limit = 50)
            }
            val items = checkNotNull(response.data) { "Personal meals response has no data" }
            if (items.isEmpty()) {
                check(response.count == null || meals.size >= response.count) {
                    "Personal meals list ended before all meals were received"
                }
                return meals.values.toList()
            }
            val previousSize = meals.size
            items.forEach { item ->
                val id = checkNotNull(item.guid?.takeIf { it.isNotBlank() }) { "Personal meal has no ID" }
                meals[id] = AutocompleteResponseItem(
                    id = id, title = item.title, url = null, unit = "g", isLiquid = false,
                    // The list's energy is for the entire recipe, not per 100 g.
                    value = null, favorite = true, hasImage = false, clazz = "meal"
                )
            }
            check(meals.size > previousSize) { "Personal meals API repeated a page" }
            if (response.count != null && meals.size >= response.count) return meals.values.toList()
            page++
        }
    }

    suspend fun getPersonalMealForm(guid: String): com.example.kthealthsync.domain.model.PersonalMealForm {
        val response = executeWithRetry(operationName = "getPersonalMealForm") { cookie ->
            api.getPersonalMealForm(guid, cookie)
        }
        check(response.code == 0) { response.message ?: "Failed to load meal form" }
        return com.example.kthealthsync.domain.model.PersonalMealForm(checkNotNull(response.data) { "Meal form has no data" })
    }

    suspend fun addPersonalMeal(request: com.google.gson.JsonObject): AddFoodResponse =
        executeWithRetry(operationName = "addPersonalMeal", retryTransientErrors = false) { cookie ->
            api.addPersonalMeal(request, cookie)
        }

    suspend fun searchFood(
        query: String,
        customMeals: List<AutocompleteResponseItem>,
        page: Int = 0,
        language: com.example.kthealthsync.domain.model.AppLanguage? = null
    ): List<AutocompleteResponseItem> {
        val cookie = getCookie()
        if (cookie.isEmpty()) return emptyList()

        val serverResults = try {
            executeWithRetry(operationName = "searchFood") { cookieParam ->
                api.searchFood(
                    query = query, page = page,
                    cookie = com.example.kthealthsync.domain.model.SearchLocale.cookie(cookieParam, language),
                    language = language?.takeUnless { it == com.example.kthealthsync.domain.model.AppLanguage.AUTO }?.code
                ).filter { it.id != null }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            DebugLogger.addActionLog("Search food page $page failed: ${e.message}")
            throw e
        }

        val localMatches = if (page == 0) {
            customMeals.filter {
                it.title?.contains(query, ignoreCase = true) == true
            }
        } else emptyList()

        return (localMatches + serverResults).distinctBy { it.id }
    }

    suspend fun getFoodForm(guid: String): FoodFormResponse {
        return executeWithRetry(operationName = "getFoodForm") { cookieParam ->
            api.getFoodForm(guid, cookieParam)
        }
    }

    suspend fun getFoodMacrosFromWeb(itemUrl: String, formUrl: String?): Map<String, Double> {
        macroCache[itemUrl]?.let { return it }

        return try {
            val fullUrl = formUrl ?: if (itemUrl.contains("/")) itemUrl else "potraviny/$itemUrl"
            val htmlUrl = "https://www.kaloricketabulky.cz/$fullUrl"
            val htmlResp = executeWithRetry(operationName = "getFoodMacrosFromWeb") { cookieParam ->
                api.getFoodHtml(htmlUrl, cookieParam).string()
            }
            val parsed = FoodHtmlParser.parseLdJsonMacros(htmlResp)
            if (parsed.isNotEmpty()) {
                macroCache[itemUrl] = parsed
            }
            parsed
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            DebugLogger.addActionLog("Failed to fetch food macros HTML: ${e.message}")
            emptyMap()
        }
    }

    suspend fun addFoodstuff(request: AddFoodRequest): AddFoodResponse {
        return executeWithRetry(operationName = "addFoodstuff", retryTransientErrors = false) { cookieParam ->
            api.addFoodstuff(request, cookieParam)
        }
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
            val resp = executeWithRetry(operationName = "fetchNutrientGoalsFromApi") { cookieParam ->
                api.getUserProfileForm(cookieParam)
            }
            val data = resp.data ?: return null

            lastGoalsFetchTime = now

            val currentGoals = prefs.nutrientGoalsFlow.first()

            val kcal = data.ownTargetEnergy
                ?: NutrientParser.parseNutrient(data.computedTargetEnergy).takeIf { it > 0 }
                ?: currentGoals.kcal

            var protein = currentGoals.protein
            var carbs = currentGoals.carbs
            var fat = currentGoals.fat
            var fiber = currentGoals.fiber
            var sugar = currentGoals.sugar
            var salt = currentGoals.salt

            data.ownNutrients?.forEach { item ->
                val num = item.value ?: NutrientParser.parseNutrient(item.computedValue)
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
                water = when {
                    data.drinkRegime != null && data.drinkRegime > 0 -> {
                        if (data.drinkRegime < 50.0) data.drinkRegime * 1000.0 else data.drinkRegime
                    }
                    else -> currentGoals.water
                }
            )
            prefs.saveNutrientGoals(goals)
            DebugLogger.addActionLog("Fetched target goals from Kalorické Tabulky API: ${goals.kcal.toInt()} kcal, P:${goals.protein.toInt()}g, C:${goals.carbs.toInt()}g, F:${goals.fat.toInt()}g")
            goals
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            DebugLogger.addActionLog("Failed to fetch goals from API: ${e.message}")
            null
        }
    }

    suspend fun editFoodEntry(oldEntryId: String, request: AddFoodRequest): DiaryEditResult {
        require(oldEntryId.isNotBlank()) { "Missing diary entry ID" }
        val response = addFoodstuff(request)
        if (response.code != 0) return DiaryEditResult.ADD_FAILED
        return if (deleteDiaryEntries(setOf(oldEntryId)).isSuccess) {
            DiaryEditResult.SUCCESS
        } else {
            DiaryEditResult.DELETE_FAILED
        }
    }

    suspend fun deleteDiaryEntries(ids: Collection<String>): Result<Unit> {
        val validIds = ids.filter { it.isNotBlank() }
        if (validIds.isEmpty()) return Result.success(Unit)

        return try {
            val joined = validIds.joinToString(",")
            val resp = executeWithRetry(operationName = "deleteFoodstuffDiary") { cookieParam ->
                api.deleteFoodstuffDiary(joined, cookieParam)
            }
            check(resp.code == 0) { resp.message ?: "Delete rejected (code=${resp.code})" }
            DebugLogger.addActionLog("Deleted ${validIds.size} diary entries: $joined (code=${resp.code}, msg=${resp.message})")
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            DebugLogger.addActionLog("Failed to delete diary entries: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun moveDiaryEntries(
        ids: Collection<String>,
        targetTimeId: String,
        targetDate: String? = null
    ): Result<Unit> {
        val validIds = ids.filter { it.isNotBlank() }
        if (validIds.isEmpty()) return Result.success(Unit)

        val dateParam = targetDate?.takeIf { it.isNotBlank() } ?: "0"

        return try {
            for (id in validIds) {
                val resp = executeWithRetry(operationName = "moveFoodstuffDiary") { cookieParam ->
                    api.moveFoodstuffDiary(id, targetTimeId, dateParam, cookieParam)
                }
                check(resp.code == 0) { resp.message ?: "Move rejected (code=${resp.code})" }
                DebugLogger.addActionLog("Moved diary entry $id to time=$targetTimeId, date=$dateParam (code=${resp.code})")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            DebugLogger.addActionLog("Failed to move diary entries: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Executes a network operation against Kalorické Tabulky with automatic retry
     * and auto-relogin on session expiration (HTTP 302 / 401).
     *
     * Kalorické Tabulky redirects to login page (HTTP 302) when a session expires
     * or when concurrent diary modifications cause temporary redirects.
     */
    suspend fun <T> executeWithRetry(
        maxRetries: Int = 3,
        operationName: String = "operation",
        retryTransientErrors: Boolean = true,
        block: suspend (cookie: String) -> T
    ): T {
        var lastException: Exception? = null
        var currentCookie = getCookie()

        for (attempt in 1..maxRetries) {
            try {
                return block(currentCookie)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                lastException = e
                val code = (e as? retrofit2.HttpException)?.code()
                val errorMsg = e.message ?: ""
                val isAuthError = code == 302 || code == 401

                DebugLogger.addActionLog("[$operationName] Attempt $attempt/$maxRetries failed (code=$code): $errorMsg")

                if (isAuthError) {
                    val email = prefs.emailFlow.first()
                    val passHash = prefs.passwordHashFlow.first()
                    if (email.isNotEmpty() && passHash.isNotEmpty()) {
                        DebugLogger.addActionLog("[$operationName] 302/401 received. Attempting auto-login...")
                        try {
                            val loginResp = api.login(LoginRequest(email, passHash))
                            if (loginResp.isSuccessful && loginResp.body()?.code == 0) {
                                val jsessionCookie = loginResp.headers().values("Set-Cookie").firstOrNull { it.contains("JSESSIONID") }
                                if (jsessionCookie != null) {
                                    val newCookie = jsessionCookie.substringBefore(";")
                                    prefs.saveCookie(newCookie)
                                    currentCookie = newCookie
                                    DebugLogger.addActionLog("[$operationName] Auto-login successful, retrying immediately...")
                                    continue
                                }
                            } else {
                                DebugLogger.addActionLog("[$operationName] Auto-login failed: HTTP ${loginResp.code()}")
                            }
                        } catch (loginEx: Exception) {
                            if (loginEx is CancellationException) throw loginEx
                            DebugLogger.addActionLog("[$operationName] Auto-login request error: ${loginEx.message}")
                        }
                    } else {
                        DebugLogger.addActionLog("[$operationName] 302/401 received but no credentials saved for auto-login.")
                    }
                }

                if (!retryTransientErrors) throw e

                if (attempt < maxRetries) {
                    val delayMs = attempt * 350L
                    delay(delayMs)
                }
            }
        }
        throw lastException ?: IllegalStateException("Operation $operationName failed after $maxRetries attempts")
    }
}
