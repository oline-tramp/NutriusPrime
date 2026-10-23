package com.example.kthealthsync

import com.example.kthealthsync.data.repository.KalorickeRepository
import com.example.kthealthsync.data.repository.RepositoryPreferences
import com.example.kthealthsync.domain.model.DiaryEditResult
import com.example.kthealthsync.domain.model.FoodCategory
import com.example.kthealthsync.domain.model.NutrientGoals
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import java.io.IOException
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resumeWithException

class KalorickeRepositoryTest {
    @Test fun searchPreservesCzechQueryAndAppliesSelectedLanguageToEveryPage() = runTest {
        val original = "session=secret; org.springframework.web.servlet.i18n.CookieLocaleResolver.LOCALE=ru"
        prefs.saveCookie(original)
        val repo = repository { method, args ->
            assertEquals("searchFood", method)
            assertEquals("okurka salatova", args[0])
            assertTrue((args[2] as String).contains("session=secret"))
            assertTrue((args[2] as String).contains("CookieLocaleResolver.LOCALE=cs"))
            assertFalse((args[2] as String).contains("LOCALE=ru"))
            assertEquals("cs", args[5])
            emptyList<AutocompleteResponseItem>()
        }
        for (page in 0..1) repo.searchFood("okurka salatova", emptyList(), page, com.example.kthealthsync.domain.model.AppLanguage.CS)
        assertEquals(original, prefs.cookieFlow.value)
    }
    private val prefs = MemoryPreferences()
    private val calls = mutableListOf<String>()
    private val request = AddFoodRequest("food", "Food", "food", "10.09.2026", "100", "3")

    private fun repository(handler: (String, Array<out Any?>) -> Any?): KalorickeRepository {
        val api = Proxy.newProxyInstance(KalorickeApiService::class.java.classLoader,
            arrayOf(KalorickeApiService::class.java)) { _, method, args ->
            calls += method.name
            try {
                handler(method.name, args ?: emptyArray())
            } catch (e: Exception) {
                // Match Retrofit's suspend failure instead of Java Proxy's checked-exception wrapper.
                @Suppress("UNCHECKED_CAST")
                val continuation = args!!.last() as Continuation<Any?>
                continuation.resumeWithException(e)
                COROUTINE_SUSPENDED
            }
        } as KalorickeApiService
        return KalorickeRepository(api, prefs)
    }

    @Test fun successfulInPlaceEditModifiesUnitWithoutAddOrDelete() = runTest {
        val repo = repository { method, args ->
            when (method) {
                "editFoodstuffUnit" -> {
                    val req = args[0] as EditDiaryFoodstuffUnitRequest
                    assertEquals("old-id", req.diaryGuid)
                    assertEquals(100.0, req.multiplier, 0.001)
                    assertEquals("0000000000000001", req.unitGuid)
                    DiaryActionResponse(0, null)
                }
                else -> error(method)
            }
        }
        assertEquals(DiaryEditResult.SUCCESS, repo.editFoodEntry("old-id", request))
        assertEquals(listOf("editFoodstuffUnit"), calls)
    }

    @Test fun inPlaceEditWithChangedMealAlsoMovesEntry() = runTest {
        val repo = repository { method, args ->
            when (method) {
                "editFoodstuffUnit" -> DiaryActionResponse(0, null)
                "moveFoodstuffDiary" -> {
                    assertEquals("old-id", args[0])
                    assertEquals("3", args[1]) // target meal from request
                    assertEquals("10.09.2026", args[2])
                    DiaryActionResponse(0, null)
                }
                else -> error(method)
            }
        }
        assertEquals(DiaryEditResult.SUCCESS, repo.editFoodEntry("old-id", request, currentMealTimeId = "1", currentDate = "10.09.2026"))
        assertEquals(listOf("editFoodstuffUnit", "moveFoodstuffDiary"), calls)
    }

    @Test fun rejectedInPlaceEditFallsBackToAddAndOldEntryDelete() = runTest {
        val repo = repository { method, args ->
            when (method) {
                "editFoodstuffUnit" -> DiaryActionResponse(1, "Unit edit not supported")
                "addFoodstuff" -> AddFoodResponse(0, null)
                "deleteFoodstuffDiary" -> {
                    assertEquals("old-id", args[0])
                    DiaryActionResponse(0, null)
                }
                else -> error(method)
            }
        }
        assertEquals(DiaryEditResult.SUCCESS, repo.editFoodEntry("old-id", request))
        assertEquals(listOf("editFoodstuffUnit", "addFoodstuff", "deleteFoodstuffDiary"), calls)
    }

    @Test fun rejectedOrMalformedAddNeverDeletesOriginal() = runTest {
        for (code in listOf(5, null)) {
            calls.clear()
            val repo = repository { method, _ ->
                when (method) {
                    "editFoodstuffUnit" -> DiaryActionResponse(1, "Error")
                    "addFoodstuff" -> AddFoodResponse(code, "Rejected")
                    else -> error(method)
                }
            }
            assertEquals(DiaryEditResult.ADD_FAILED, repo.editFoodEntry("old", request))
            assertEquals(listOf("editFoodstuffUnit", "addFoodstuff"), calls)
        }
    }

    @Test fun rejectedDeleteReportsPartialEditAndCleanupRetryDoesNotAddAgain() = runTest {
        var deleteCode = 9
        val repo = repository { method, _ ->
            when (method) {
                "editFoodstuffUnit" -> DiaryActionResponse(1, "Error")
                "addFoodstuff" -> AddFoodResponse(0, null)
                "deleteFoodstuffDiary" -> DiaryActionResponse(deleteCode, "Delete response")
                else -> error(method)
            }
        }
        assertEquals(DiaryEditResult.DELETE_FAILED, repo.editFoodEntry("old", request))
        deleteCode = 0
        assertTrue(repo.deleteDiaryEntries(setOf("old")).isSuccess)
        assertEquals(1, calls.count { it == "addFoodstuff" })
        assertEquals(2, calls.count { it == "deleteFoodstuffDiary" })
    }

    @Test fun disconnectedDeleteAlsoReportsPartialEdit() = runTest {
        val repo = repository { method, _ ->
            when (method) {
                "editFoodstuffUnit" -> DiaryActionResponse(1, "Error")
                "addFoodstuff" -> AddFoodResponse(0, null)
                else -> throw IOException("offline")
            }
        }
        assertEquals(DiaryEditResult.DELETE_FAILED, repo.editFoodEntry("old", request))
        assertEquals(1, calls.count { it == "addFoodstuff" })
    }

    @Test fun ambiguousAddFailureIsNeverRetriedAutomatically() = runTest {
        val failure = IOException("Server accepted the write, but response was lost")
        val repo = repository { _, _ -> throw failure }
        val result = runCatching { repo.addFoodstuff(request) }
        assertSame(failure, result.exceptionOrNull())
        assertEquals(listOf("addFoodstuff"), calls)
    }

    @Test fun rejectedAuthenticationCanRefreshSessionAndRetryAdd() = runTest {
        prefs.emailFlow.value = "test@example.test"
        prefs.passwordHashFlow.value = "test-hash"
        var adds = 0
        val repo = repository { method, args ->
            when (method) {
                "addFoodstuff" -> {
                    if (++adds == 1) throw HttpException(retrofit2.Response.error<Any>(401, "".toResponseBody()))
                    assertEquals("JSESSIONID=new", args[1])
                    AddFoodResponse(0, null)
                }
                "login" -> retrofit2.Response.success(LoginResponse(0, null),
                    Headers.headersOf("Set-Cookie", "JSESSIONID=new; Path=/"))
                else -> error(method)
            }
        }
        assertEquals(0, repo.addFoodstuff(request).code)
        assertEquals(listOf("addFoodstuff", "login", "addFoodstuff"), calls)
    }

    @Test fun cancellationIsPropagatedWithoutRetryOrEmptyResults() = runTest {
        val cancelled = CancellationException("new query")
        val repo = repository { _, _ -> throw cancelled }
        val result = runCatching { repo.searchFood("apple", emptyList()) }
        assertSame(cancelled, result.exceptionOrNull())
        assertEquals(listOf("searchFood"), calls)
    }

    @Test fun searchOutageIsAnErrorRatherThanEndOfResults() = runTest {
        val failure = IOException("offline")
        val repo = repository { _, _ -> throw failure }
        val result = runCatching { repo.searchFood("apple", emptyList(), page = 1) }
        assertSame(failure, result.exceptionOrNull())
        assertEquals(3, calls.size)
    }

    @Test fun failedCategoryLookupDoesNotPoisonPersistentCache() = runTest {
        var offline = true
        val repo = repository { method, _ ->
            if (offline) throw IOException("offline")
            when (method) {
                "getFoodForm" -> FoodFormResponse(0, null,
                    FoodFormData("food", "Carrot", "potraviny/carrot", null, null, null))
                "getFoodHtml" -> "<a class=\"crumb-link\" href=\"/zelenina\">Zelenina</a>".toResponseBody()
                else -> error(method)
            }
        }
        val food = Foodstuff(guid = "food", title = "Carrot")
        assertEquals(FoodCategory.OTHER, repo.getFoodCategory(food))
        assertTrue(prefs.categories.isEmpty())
        offline = false
        assertEquals(FoodCategory.VEGETABLE, repo.getFoodCategory(food))
        assertEquals("VEGETABLE", prefs.categories["food"])
    }

    @Test fun foodUrlAndFoodFormImmediatelyTagCategories() = runTest {
        val repo = repository { method, _ ->
            when (method) {
                "getFoodForm" -> FoodFormResponse(0, null,
                    FoodFormData("g1", "Jablko Golden", "ovoce/jablko-golden", null, null, null))
                else -> error(method)
            }
        }
        // 1. Food with explicit URL resolves immediately without calling web
        val drink = Foodstuff(guid = "d1", title = "Mineral water", url = "napoje/mineralka")
        assertEquals(FoodCategory.BEVERAGE, repo.getFoodCategory(drink))
        assertEquals("BEVERAGE", prefs.categories["d1"])

        // 2. Loading a food form tags both guid and title into cache and preferences
        repo.getFoodForm("g1")
        assertEquals("FRUIT", prefs.categories["g1"])
        assertEquals("FRUIT", prefs.categories["Jablko Golden"])
    }

    @Test fun nanoClassifiesFoodsWhenKtLacksBreadcrumbCategory() = runTest {
        val mockNano = object : com.example.kthealthsync.data.nano.NanoDraftClient() {
            override suspend fun classifyFood(name: String): FoodCategory? {
                return if (name == "Перец красный сладкий") FoodCategory.VEGETABLE else null
            }
        }
        val api = Proxy.newProxyInstance(KalorickeApiService::class.java.classLoader,
            arrayOf(KalorickeApiService::class.java)) { _, method, _ ->
            error("Unexpected api call: $method")
        } as KalorickeApiService

        val repo = KalorickeRepository(api, prefs, mockNano)
        val food = Foodstuff(title = "Перец красный сладкий")
        assertEquals(FoodCategory.VEGETABLE, repo.getFoodCategory(food))
        assertEquals("VEGETABLE", prefs.categories["Перец красный сладкий"])
    }

    @Test fun missingDiaryPayloadCannotBeSyncedAsZeroTotals() = runTest {
        val repo = repository { method, _ ->
            when (method) {
                "getDiary" -> DiaryResponse(null)
                "getMealList" -> MealListResponse(emptyList())
                else -> error(method)
            }
        }
        assertTrue(runCatching { repo.getEnrichedDailySummary("10.09.2026", false) }.isFailure)
    }

    @Test fun failedMoveIsNotReportedAsSuccess() = runTest {
        val repo = repository { _, _ -> DiaryActionResponse(7, "Rejected") }
        assertTrue(repo.moveDiaryEntries(setOf("old"), "3").isFailure)
    }

    @Test fun missingOldIdCannotAddAnUnremovableReplacement() = runTest {
        val repo = repository { _, _ -> error("Should not access API") }
        assertTrue(runCatching { repo.editFoodEntry("", request) }.isFailure)
        assertTrue(calls.isEmpty())
    }

    @Test fun myMealsUsesPersonalListAndStopsAtReportedTotal() = runTest {
        val pages = mutableListOf<Int>()
        val repo = repository { method, args ->
            check(method == "getMealList") { "Must not crawl the public autocomplete catalog" }
            val page = args[3] as Int
            assertEquals(50, args[4])
            pages += page
            MealListResponse((page * 50 until minOf((page + 1) * 50, 80)).map {
                MealListItem("$it", "Meal $it", "1 329")
            }, count = 80)
        }
        val meals = repo.getAllMyMeals()
        assertEquals((0 until 80).map { "$it" }, meals.map { it.id })
        assertEquals(listOf(0, 1), pages)
        assertTrue(meals.all { it.clazz == "meal" })
        assertTrue("Total recipe energy must not be used as kcal per 100g", meals.all { it.value == null })
    }

    @Test fun personalMealsWithoutTotalContinuePastShortPages() = runTest {
        val pages = mutableListOf<Int>()
        val repo = repository { method, args ->
            check(method == "getMealList")
            val page = args[3] as Int
            pages += page
            MealListResponse((page * 30 until minOf((page + 1) * 30, 125)).map {
                MealListItem("$it", "Meal $it", null)
            })
        }
        assertEquals(125, repo.getAllMyMeals().size)
        assertEquals(listOf(0, 1, 2, 3, 4, 5), pages)
    }

    @Test fun repeatedMealPageFailsInsteadOfLoopingOrReturningPartialCache() = runTest {
        val repo = repository { _, _ -> MealListResponse(listOf(MealListItem("one", "Meal", null)), 2) }
        assertTrue(runCatching { repo.getAllMyMeals() }.exceptionOrNull() is IllegalStateException)
        assertEquals(2, calls.size)
    }

    @Test fun missingOrTruncatedMealPayloadFailsInsteadOfClearingCache() = runTest {
        for (response in listOf(MealListResponse(null), MealListResponse(emptyList(), 80))) {
            val repo = repository { _, _ -> response }
            assertTrue(runCatching { repo.getAllMyMeals() }.isFailure)
        }
    }

    @Test fun laterMealPageFailureDoesNotReturnPartialList() = runTest {
        val repo = repository { _, args ->
            if (args[3] == 0) MealListResponse(listOf(MealListItem("one", "Meal", null)), 2)
            else throw IOException("offline")
        }
        assertTrue(runCatching { repo.getAllMyMeals() }.exceptionOrNull() is IOException)
    }

    @Test fun personalMealAdditionUsesItsOwnApiAndNeverRetriesAmbiguousWrites() = runTest {
        val body = com.google.gson.JsonObject().apply { addProperty("guid", "meal") }
        val failure = IOException("Response lost after write")
        val repo = repository { method, args ->
            assertEquals("addPersonalMeal", method)
            assertSame(body, args[0])
            throw failure
        }
        assertSame(failure, runCatching { repo.addPersonalMeal(body) }.exceptionOrNull())
        assertEquals(listOf("addPersonalMeal"), calls)
    }

    @Test fun addCustomFoodDelegatesToApiAndReturnsResponse() = runTest {
        val customRequest = AddCustomFoodRequest(
            guid = "0",
            title = "Test Food",
            diaryTimeGuid = "2",
            date = "19.10.2026",
            energy = "250",
            protein = "20",
            carbohydrate = "30",
            fat = "5"
        )
        val expectedResp = AddFoodResponse(0, "Potravina byla úspěšně zapsána")
        val repo = repository { method, args ->
            assertEquals("addCustomFood", method)
            assertSame(customRequest, args[0])
            assertTrue((args[1] as String).contains("JSESSIONID=test"))
            expectedResp
        }
        val result = repo.addCustomFood(customRequest)
        assertEquals(expectedResp, result)
        assertEquals(listOf("addCustomFood"), calls)
    }

    @Test fun editCustomFoodEntryAddsReplacementThenDeletesOldEntry() = runTest {
        val customRequest = AddCustomFoodRequest(
            guid = "0",
            title = "Test Food",
            diaryTimeGuid = "2",
            date = "19.10.2026",
            energy = "250",
            protein = "20",
            carbohydrate = "30",
            fat = "5"
        )
        val repo = repository { method, args ->
            when (method) {
                "addCustomFood" -> AddFoodResponse(0, null)
                "deleteFoodstuffDiary" -> {
                    assertEquals("custom-old-id", args[0])
                    DiaryActionResponse(0, null)
                }
                else -> error(method)
            }
        }
        assertEquals(DiaryEditResult.SUCCESS, repo.editCustomFoodEntry("custom-old-id", customRequest))
        assertEquals(listOf("addCustomFood", "deleteFoodstuffDiary"), calls)
    }

    @Test fun editCustomFoodEntryFailedAddNeverDeletesOldEntry() = runTest {
        val customRequest = AddCustomFoodRequest(
            guid = "0",
            title = "Test Food",
            diaryTimeGuid = "2",
            date = "19.10.2026",
            energy = "250",
            protein = "20",
            carbohydrate = "30",
            fat = "5"
        )
        val repo = repository { method, _ ->
            when (method) {
                "addCustomFood" -> AddFoodResponse(5, "Error")
                else -> error(method)
            }
        }
        assertEquals(DiaryEditResult.ADD_FAILED, repo.editCustomFoodEntry("custom-old-id", customRequest))
        assertEquals(listOf("addCustomFood"), calls)
    }

    @Test fun editCustomFoodEntryFailedDeleteReportsDeleteFailed() = runTest {
        val customRequest = AddCustomFoodRequest(
            guid = "0",
            title = "Test Food",
            diaryTimeGuid = "2",
            date = "19.10.2026",
            energy = "250",
            protein = "20",
            carbohydrate = "30",
            fat = "5"
        )
        val repo = repository { method, _ ->
            when (method) {
                "addCustomFood" -> AddFoodResponse(0, null)
                "deleteFoodstuffDiary" -> DiaryActionResponse(9, "Failed to delete")
                else -> error(method)
            }
        }
        assertEquals(DiaryEditResult.DELETE_FAILED, repo.editCustomFoodEntry("custom-old-id", customRequest))
        assertEquals(listOf("addCustomFood", "deleteFoodstuffDiary"), calls)
    }

    private class MemoryPreferences : RepositoryPreferences {
        override val cookieFlow = MutableStateFlow("JSESSIONID=test")
        override val emailFlow = MutableStateFlow("")
        override val passwordHashFlow = MutableStateFlow("")
        override val nutrientGoalsFlow = MutableStateFlow(NutrientGoals())
        val categories = mutableMapOf<String, String>()
        override suspend fun saveCookie(cookie: String) { cookieFlow.value = cookie }
        override suspend fun saveNutrientGoals(goals: NutrientGoals) { nutrientGoalsFlow.value = goals }
        override fun getFoodCategory(guidOrUrl: String) = categories[guidOrUrl]
        override fun saveFoodCategory(guidOrUrl: String, categoryName: String) {
            categories[guidOrUrl] = categoryName
        }
    }
}
