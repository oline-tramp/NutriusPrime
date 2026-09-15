package com.example.kthealthsync

import com.example.kthealthsync.data.repository.MyMealsCache
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class MyMealsCacheTest {
    @Test fun refreshesEveryTwentySecondsKeepsCacheOnFailureAndRemovesDeletedMeals() = runTest {
        val meal = AutocompleteResponseItem("one", "Meal", null, null, null, null, null, null, null)
        var calls = 0
        var errors = 0
        val cache = MyMealsCache(
            loadMeals = {
                when (++calls) {
                    1 -> listOf(meal)
                    2 -> throw IOException("offline")
                    else -> emptyList()
                }
            },
            onError = { errors++ },
            nowMillis = { testScheduler.currentTime }
        )
        val job = backgroundScope.launch { cache.refreshWhileActive() }
        runCurrent()
        assertEquals(listOf(meal), cache.meals.value)
        advanceTimeBy(19_999)
        runCurrent()
        assertEquals(1, calls)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, calls)
        assertEquals(1, errors)
        assertEquals(listOf(meal), cache.meals.value)
        advanceTimeBy(20_000)
        runCurrent()
        assertTrue(cache.meals.value.isEmpty())
        job.cancel()
        advanceTimeBy(40_000)
        runCurrent()
        assertEquals(3, calls)
        backgroundScope.launch { cache.refreshWhileActive() }
        runCurrent()
        assertEquals(4, calls)
    }
}
