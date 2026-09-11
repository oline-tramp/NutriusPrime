package com.example.kthealthsync.data.repository

import com.example.kthealthsync.domain.model.NutrientGoals
import kotlinx.coroutines.flow.Flow

/** Storage needed by the repository, independent of Android and its DataStore. */
interface RepositoryPreferences {
    val cookieFlow: Flow<String>
    val emailFlow: Flow<String>
    val passwordHashFlow: Flow<String>
    val nutrientGoalsFlow: Flow<NutrientGoals>
    suspend fun saveCookie(cookie: String)
    suspend fun saveNutrientGoals(goals: NutrientGoals)
    fun getFoodCategory(guidOrUrl: String): String?
    fun saveFoodCategory(guidOrUrl: String, categoryName: String)
}
