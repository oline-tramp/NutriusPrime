package com.example.kthealthsync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.kthealthsync.domain.model.AppLanguage
import com.example.kthealthsync.domain.model.NutrientGoals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesManager(private val context: Context) {
    companion object {
        val COOKIE_KEY = stringPreferencesKey("kt_cookie")
        val EMAIL_KEY = stringPreferencesKey("kt_email")
        val PASSWORD_HASH_KEY = stringPreferencesKey("kt_password_hash")
        val SYNC_HOUR_KEY = intPreferencesKey("sync_hour")
        val SYNC_MINUTE_KEY = intPreferencesKey("sync_minute")
        val AUTO_SYNC_ENABLED_KEY = booleanPreferencesKey("auto_sync_enabled")
        val APP_LANGUAGE_KEY = stringPreferencesKey("app_language")

        // Nutritional Goals Keys
        val GOAL_KCAL_KEY = doublePreferencesKey("goal_kcal")
        val GOAL_PROTEIN_KEY = doublePreferencesKey("goal_protein")
        val GOAL_CARBS_KEY = doublePreferencesKey("goal_carbs")
        val GOAL_FAT_KEY = doublePreferencesKey("goal_fat")
        val GOAL_FIBER_KEY = doublePreferencesKey("goal_fiber")
        val GOAL_SUGAR_KEY = doublePreferencesKey("goal_sugar")
        val GOAL_SALT_KEY = doublePreferencesKey("goal_salt")
        val GOAL_VEG_FRUIT_KEY = doublePreferencesKey("goal_veg_fruit")
        val GOAL_WATER_KEY = doublePreferencesKey("goal_water")

        private const val CATEGORY_CACHE_VERSION = 2
        private const val PREF_CATEGORY_CACHE_VERSION = "__cat_cache_ver__"
    }

    private val categoryPrefs by lazy {
        val sharedPrefs = context.getSharedPreferences("food_categories", Context.MODE_PRIVATE)
        val currentVersion = sharedPrefs.getInt(PREF_CATEGORY_CACHE_VERSION, 1)
        if (currentVersion < CATEGORY_CACHE_VERSION) {
            val editor = sharedPrefs.edit()
            // Invalidate entries previously cached as "OTHER"
            // because in earlier versions, supplements were classified as OTHER
            for ((key, value) in sharedPrefs.all) {
                if (value == "OTHER") {
                    editor.remove(key)
                }
            }
            editor.putInt(PREF_CATEGORY_CACHE_VERSION, CATEGORY_CACHE_VERSION)
            editor.apply()
        }
        sharedPrefs
    }

    val cookieFlow: Flow<String> = context.dataStore.data.map { it[COOKIE_KEY] ?: "" }
    val emailFlow: Flow<String> = context.dataStore.data.map { it[EMAIL_KEY] ?: "" }
    val passwordHashFlow: Flow<String> = context.dataStore.data.map { it[PASSWORD_HASH_KEY] ?: "" }
    val syncHourFlow: Flow<Int> = context.dataStore.data.map { it[SYNC_HOUR_KEY] ?: 6 }
    val syncMinuteFlow: Flow<Int> = context.dataStore.data.map { it[SYNC_MINUTE_KEY] ?: 0 }
    val autoSyncEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[AUTO_SYNC_ENABLED_KEY] ?: false }
    val appLanguageFlow: Flow<AppLanguage> = context.dataStore.data.map { prefs ->
        AppLanguage.fromCode(prefs[APP_LANGUAGE_KEY])
    }
    val resolvedLanguageFlow: Flow<AppLanguage> = context.dataStore.data.map { prefs ->
        val userChoice = AppLanguage.fromCode(prefs[APP_LANGUAGE_KEY])
        if (userChoice == AppLanguage.AUTO) {
            AppLanguage.resolveAppLanguage(prefs[COOKIE_KEY])
        } else {
            userChoice
        }
    }

    val nutrientGoalsFlow: Flow<NutrientGoals> = context.dataStore.data.map { prefs ->
        val defaults = NutrientGoals()
        NutrientGoals(
            kcal = prefs[GOAL_KCAL_KEY] ?: defaults.kcal,
            protein = prefs[GOAL_PROTEIN_KEY] ?: defaults.protein,
            carbs = prefs[GOAL_CARBS_KEY] ?: defaults.carbs,
            fat = prefs[GOAL_FAT_KEY] ?: defaults.fat,
            fiber = prefs[GOAL_FIBER_KEY] ?: defaults.fiber,
            sugar = prefs[GOAL_SUGAR_KEY] ?: defaults.sugar,
            salt = prefs[GOAL_SALT_KEY] ?: defaults.salt,
            vegFruit = prefs[GOAL_VEG_FRUIT_KEY] ?: defaults.vegFruit,
            water = prefs[GOAL_WATER_KEY] ?: defaults.water
        )
    }

    suspend fun saveCookie(cookie: String) {
        context.dataStore.edit { it[COOKIE_KEY] = cookie }
    }
    
    suspend fun saveCredentials(email: String, passwordHash: String) {
        context.dataStore.edit {
            it[EMAIL_KEY] = email
            it[PASSWORD_HASH_KEY] = passwordHash
        }
    }

    suspend fun saveSyncTime(hour: Int, minute: Int) {
        context.dataStore.edit { 
            it[SYNC_HOUR_KEY] = hour
            it[SYNC_MINUTE_KEY] = minute
        }
    }

    suspend fun setAutoSync(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_SYNC_ENABLED_KEY] = enabled }
    }

    suspend fun saveAppLanguage(language: AppLanguage) {
        context.dataStore.edit { it[APP_LANGUAGE_KEY] = language.code }
    }

    suspend fun saveNutrientGoals(goals: NutrientGoals) {
        context.dataStore.edit { prefs ->
            prefs[GOAL_KCAL_KEY] = goals.kcal
            prefs[GOAL_PROTEIN_KEY] = goals.protein
            prefs[GOAL_CARBS_KEY] = goals.carbs
            prefs[GOAL_FAT_KEY] = goals.fat
            prefs[GOAL_FIBER_KEY] = goals.fiber
            prefs[GOAL_SUGAR_KEY] = goals.sugar
            prefs[GOAL_SALT_KEY] = goals.salt
            prefs[GOAL_VEG_FRUIT_KEY] = goals.vegFruit
            prefs[GOAL_WATER_KEY] = goals.water
        }
    }

    fun getFoodCategory(guidOrUrl: String): String? {
        return categoryPrefs.getString(guidOrUrl, null)
    }

    fun saveFoodCategory(guidOrUrl: String, categoryName: String) {
        categoryPrefs.edit().putString(guidOrUrl, categoryName).apply()
    }

    fun clearFoodCategories() {
        categoryPrefs.edit().clear().putInt(PREF_CATEGORY_CACHE_VERSION, CATEGORY_CACHE_VERSION).apply()
    }
}
