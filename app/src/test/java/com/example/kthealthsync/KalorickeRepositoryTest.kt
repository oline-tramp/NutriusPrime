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

    @Test fun successfulEditAddsReplacementThenDeletesOldEntry() = runTest {
        val repo = repository { method, args ->
            when (method) {
                "addFoodstuff" -> AddFoodResponse(0, null)
                "deleteFoodstuffDiary" -> {
                    assertEquals("old-id", args[0])
                    DiaryActionResponse(0, null)
                }
                else -> error(method)
            }
        }
        assertEquals(DiaryEditResult.SUCCESS, repo.editFoodEntry("old-id", request))
        assertEquals(listOf("addFoodstuff", "deleteFoodstuffDiary"), calls)
    }

    @Test fun rejectedOrMalformedAddNeverDeletesOriginal() = runTest {
        for (code in listOf(5, null)) {
            calls.clear()
            val repo = repository { _, _ -> AddFoodResponse(code, "Rejected") }
            assertEquals(DiaryEditResult.ADD_FAILED, repo.editFoodEntry("old", request))
            assertEquals(listOf("addFoodstuff"), calls)
        }
    }

    @Test fun rejectedDeleteReportsPartialEditAndCleanupRetryDoesNotAddAgain() = runTest {
        var deleteCode = 9
        val repo = repository { method, _ ->
            when (method) {
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
            if (method == "addFoodstuff") AddFoodResponse(0, null) else throw IOException("offline")
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
