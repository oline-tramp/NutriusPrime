package com.example.kthealthsync.wear

import android.content.Context
import com.example.kthealthsync.AutocompleteResponseItem
import com.example.kthealthsync.DebugLogger
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

class WearSyncManager(private val context: Context) {
    private val dataClient by lazy { Wearable.getDataClient(context) }
    private val messageClient by lazy { Wearable.getMessageClient(context) }
    private val gson = Gson()

    suspend fun syncDataToWatch(
        cookie: String,
        items: List<AutocompleteResponseItem>
    ) {
        if (cookie.isBlank()) return
        try {
            val wearItems = items.take(25).map { item ->
                WearFoodItem(
                    id = item.id ?: item.url.orEmpty(),
                    title = item.title ?: "Без названия",
                    energy = item.value,
                    unit = item.unit,
                    url = item.url
                )
            }
            val json = gson.toJson(wearItems)

            val prefs = com.example.kthealthsync.PreferencesManager(context)
            val email = prefs.emailFlow.first()
            val passHash = prefs.passwordHashFlow.first()

            val putDataMapReq = PutDataMapRequest.create(WearSyncConstants.PATH_SYNC_DATA).apply {
                dataMap.putString(WearSyncConstants.KEY_COOKIE, cookie)
                dataMap.putString(WearSyncConstants.KEY_EMAIL, email)
                dataMap.putString(WearSyncConstants.KEY_PASSWORD_HASH, passHash)
                dataMap.putString(WearSyncConstants.KEY_FAVORITES_JSON, json)
                dataMap.putLong(WearSyncConstants.KEY_TIMESTAMP, System.currentTimeMillis())
            }
            val request = putDataMapReq.asPutDataRequest().setUrgent()
            dataClient.putDataItem(request).await()
            DebugLogger.addActionLog("WearSyncManager: Synced ${wearItems.size} items to watch")
        } catch (e: Exception) {
            DebugLogger.addActionLog("WearSyncManager: Failed to sync data to watch: ${e.message}")
        }
    }

    suspend fun sendResult(nodeId: String, path: String, payload: ByteArray) {
        try {
            messageClient.sendMessage(nodeId, path, payload).await()
        } catch (e: Exception) {
            DebugLogger.addActionLog("WearSyncManager: Error sending result to $nodeId: ${e.message}")
        }
    }
}
