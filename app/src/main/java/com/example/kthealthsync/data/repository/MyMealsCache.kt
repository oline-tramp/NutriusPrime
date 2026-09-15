package com.example.kthealthsync.data.repository

import com.example.kthealthsync.AutocompleteResponseItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory snapshot, replaced only after every page has loaded successfully. */
internal class MyMealsCache(
    private val loadMeals: suspend () -> List<AutocompleteResponseItem>,
    private val onError: (Exception) -> Unit,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 }
) {
    private val _meals = MutableStateFlow<List<AutocompleteResponseItem>>(emptyList())
    val meals = _meals.asStateFlow()

    fun clear() {
        _meals.value = emptyList()
    }

    /** The lifecycle owner runs one loop; long requests never overlap the next refresh. */
    suspend fun refreshWhileActive() {
        while (true) {
            val startedAt = nowMillis()
            try {
                val updated = loadMeals()
                currentCoroutineContext().ensureActive()
                _meals.value = updated
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                onError(e)
            }
            delay((20_000L - (nowMillis() - startedAt)).coerceAtLeast(1L))
        }
    }
}
