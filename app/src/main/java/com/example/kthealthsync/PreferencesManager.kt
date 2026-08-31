package com.example.kthealthsync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
    }

    val cookieFlow: Flow<String> = context.dataStore.data.map { it[COOKIE_KEY] ?: "" }
    val emailFlow: Flow<String> = context.dataStore.data.map { it[EMAIL_KEY] ?: "" }
    val passwordHashFlow: Flow<String> = context.dataStore.data.map { it[PASSWORD_HASH_KEY] ?: "" }
    val syncHourFlow: Flow<Int> = context.dataStore.data.map { it[SYNC_HOUR_KEY] ?: 6 }
    val syncMinuteFlow: Flow<Int> = context.dataStore.data.map { it[SYNC_MINUTE_KEY] ?: 0 }
    val autoSyncEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[AUTO_SYNC_ENABLED_KEY] ?: false }

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
}
