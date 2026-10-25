package com.example.kthealthsync.wear

import com.example.kthealthsync.AddFoodRequest
import com.example.kthealthsync.DebugLogger
import com.example.kthealthsync.PreferencesManager
import com.example.kthealthsync.UnitOption
import com.example.kthealthsync.data.repository.KalorickeRepository
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PhoneWearableListenerService : WearableListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val path = messageEvent.path
        val senderNodeId = messageEvent.sourceNodeId
        val payload = messageEvent.data

        when (path) {
            WearSyncConstants.PATH_ADD_FOOD -> {
                scope.launch {
                    handleAddFood(senderNodeId, payload)
                }
            }
            WearSyncConstants.PATH_SEARCH -> {
                scope.launch {
                    handleSearch(senderNodeId, payload)
                }
            }
            WearSyncConstants.PATH_REQUEST_SYNC -> {
                scope.launch {
                    handleRequestSync()
                }
            }
            else -> super.onMessageReceived(messageEvent)
        }
    }

    private suspend fun handleAddFood(senderNodeId: String, payload: ByteArray) {
        val syncManager = WearSyncManager(applicationContext)
        try {
            val json = String(payload, Charsets.UTF_8)
            val request = gson.fromJson(json, WearAddFoodRequest::class.java)
            DebugLogger.addActionLog("Wear request received: Add food ${request.title}, multiplier=${request.multiplier}")

            val prefs = PreferencesManager(applicationContext)
            val repo = KalorickeRepository(prefs = prefs)

            val addRequest = AddFoodRequest(
                guid = request.guid,
                title = request.title,
                url = request.url,
                date = request.date,
                multiplier = request.multiplier,
                diaryTimeGuid = request.diaryTimeGuid,
                unitGuid = request.unitGuid,
                unitOptions = listOf(UnitOption(request.unitGuid, "1 g", 1.0))
            )

            val response = repo.addFoodstuff(addRequest)
            val isSuccess = response.code == 0 || response.code == 200 || response.code == null
            if (isSuccess) {
                com.example.kthealthsync.data.repository.DiaryUpdateEvents.notifyDiaryUpdated()
            }
            val result = WearAddFoodResult(
                success = isSuccess,
                message = response.message ?: if (isSuccess) "Добавлено!" else "Ошибка ${response.code}"
            )
            val responseJson = gson.toJson(result).toByteArray(Charsets.UTF_8)
            syncManager.sendResult(senderNodeId, WearSyncConstants.PATH_ADD_FOOD_RESULT, responseJson)
            DebugLogger.addActionLog("Wear add food result sent: success=$isSuccess")
        } catch (e: Exception) {
            DebugLogger.addActionLog("Wear add food failed: ${e.message}")
            val code = (e as? retrofit2.HttpException)?.code()
            val userMsg = if (code == 302 || code == 401) {
                "Сессия истекла. Откройте приложение на телефоне"
            } else {
                e.message ?: "Ошибка добавления"
            }
            val errorResult = WearAddFoodResult(success = false, message = userMsg)
            syncManager.sendResult(
                senderNodeId,
                WearSyncConstants.PATH_ADD_FOOD_RESULT,
                gson.toJson(errorResult).toByteArray(Charsets.UTF_8)
            )
        }
    }

    private suspend fun handleSearch(senderNodeId: String, payload: ByteArray) {
        val syncManager = WearSyncManager(applicationContext)
        try {
            val query = String(payload, Charsets.UTF_8).trim()
            if (query.isBlank()) return

            val prefs = PreferencesManager(applicationContext)
            val repo = KalorickeRepository(prefs = prefs)
            val results = repo.searchFood(query, emptyList(), page = 0, language = prefs.resolvedLanguageFlow.first())

            val wearItems = results.take(15).map { item ->
                WearFoodItem(
                    id = item.id ?: item.url.orEmpty(),
                    title = item.title ?: "Без названия",
                    energy = item.value,
                    unit = item.unit,
                    url = item.url
                )
            }

            val responseJson = gson.toJson(wearItems).toByteArray(Charsets.UTF_8)
            syncManager.sendResult(senderNodeId, WearSyncConstants.PATH_SEARCH_RESULT, responseJson)
        } catch (e: Exception) {
            DebugLogger.addActionLog("Wear search error: ${e.message}")
        }
    }

    private suspend fun handleRequestSync() {
        val prefs = PreferencesManager(applicationContext)
        val cookie = prefs.cookieFlow.first()
        if (cookie.isBlank()) return

        val repo = KalorickeRepository(prefs = prefs)
        try {
            val frequent = repo.getFrequentFoodstuff(limit = 15)
            val favorites = repo.getFavoriteFoodstuff(limit = 15)
            val combined = (frequent + favorites).distinctBy { it.id }
            WearSyncManager(applicationContext).syncDataToWatch(cookie, combined)
        } catch (e: Exception) {
            DebugLogger.addActionLog("Wear handleRequestSync error: ${e.message}")
        }
    }
}
