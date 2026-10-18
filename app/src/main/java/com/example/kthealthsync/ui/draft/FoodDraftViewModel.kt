package com.example.kthealthsync.ui.draft

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.kthealthsync.PreferencesManager
import com.example.kthealthsync.data.nano.NanoDraftClient
import com.example.kthealthsync.data.repository.KalorickeRepository
import com.example.kthealthsync.domain.draft.FoodDraftItem
import com.example.kthealthsync.domain.draft.FoodDraftParser
import com.example.kthealthsync.domain.draft.RecentFoodHistoryHelper
import com.example.kthealthsync.domain.draft.RecentFoodHistoryProvider
import com.google.gson.Gson
import com.google.mlkit.genai.common.FeatureStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class FoodDraftViewModel(
    application: Application,
    private val saved: SavedStateHandle
) : AndroidViewModel(application) {
    private val client = NanoDraftClient()
    private val prefs = PreferencesManager(application)
    private val repository = KalorickeRepository(prefs = prefs)
    private val historyProvider = RecentFoodHistoryProvider(repository)
    private val gson = Gson()
    val input = saved.getStateFlow("input", "")
    val draftSource = saved.getStateFlow("draftSource", "")
    val selectedId = saved.getStateFlow<String?>("selected", null)
    private val _items = MutableStateFlow(restore())
    val items = _items.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _availability = MutableStateFlow<Int?>(null)
    val availability = _availability.asStateFlow()
    private var task: Job? = null

    private fun restore(): List<FoodDraftItem> = runCatching {
        gson.fromJson(saved.get<String>("items") ?: "[]", Array<FoodDraftItem>::class.java).toList()
    }.getOrDefault(emptyList())

    fun editInput(value: String) { saved["input"] = value.take(FoodDraftParser.MAX_INPUT) }
    private fun store(rows: List<FoodDraftItem>) {
        _items.value = rows
        saved["items"] = gson.toJson(rows)
    }
    fun edit(item: FoodDraftItem) { store(_items.value.map { if (it.id == item.id) item else it }) }
    fun clear() {
        if (_busy.value) return
        store(emptyList())
        saved["selected"] = null
        saved["input"] = ""
        saved["draftSource"] = ""
        _message.value = null
    }
    fun remove(id: String) {
        store(_items.value.filterNot { it.id == id })
        if (selectedId.value == id) saved["selected"] = null
    }
    fun select(id: String?) { saved["selected"] = id }
    fun selected(): FoodDraftItem? = _items.value.find { it.id == selectedId.value }

    fun cancel() {
        if (task?.isActive == true) {
            task?.cancel()
            _message.value = "Остановлено. Текст и черновик сохранены."
        }
    }

    private fun run(timeoutMs: Long, block: suspend () -> Unit) {
        if (task?.isActive == true) return
        task = viewModelScope.launch(start = CoroutineStart.LAZY) {
            _busy.value = true
            _message.value = null
            try {
                withTimeout(timeoutMs) { block() }
            } catch (_: TimeoutCancellationException) {
                _message.value = "Nano не ответил вовремя. Попробуйте позже или сократите текст."
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                _message.value = e.message
            } catch (e: com.google.mlkit.genai.common.GenAiException) {
                com.example.kthealthsync.DebugLogger.addActionLog("NanoDraft: SDK error code=${e.errorCode}")
                _message.value = if (e.errorCode == 9) "Nano отклонил запрос: лимит использования или приложение ушло в фон (код 9). Оставьте экран открытым и попробуйте позже."
                    else "Ошибка Nano (код ${e.errorCode}). Попробуйте позже."
            } catch (e: Exception) {
                com.example.kthealthsync.DebugLogger.addActionLog("NanoDraft: failure type=${e.javaClass.simpleName}")
                // Do not log raw text/responses or expose SDK implementation errors to the user.
                _message.value = "Не удалось подготовить черновик. Возможны лимит Nano или ошибка ответа. Попробуйте позже или сократите текст."
            } finally { if (task == coroutineContext[Job]) _busy.value = false }
        }
        task?.start()
    }

    fun check() = run(20_000) {
        _availability.value = client.status()
    }

    fun download() = run(600_000) {
        _availability.value = client.status()
        if (_availability.value == FeatureStatus.DOWNLOADABLE) {
            _message.value = "Загрузка модели… Оставьте экран открытым."
            client.download { _message.value = it }
        }
        _availability.value = client.status()
        _message.value = if (_availability.value == FeatureStatus.AVAILABLE) "Модель готова." else "Модель ещё не готова. Проверьте позже."
    }

    fun generate() {
        val text = input.value.trim()
        if (text.isEmpty()) return
        // Cancel prefetch if still active so generate proceeds immediately
        if (task?.isActive == true) {
            task?.cancel()
        }
        run(90_000) {
            _availability.value = client.status()
            if (_availability.value != FeatureStatus.AVAILABLE) {
                _message.value = "Модель пока недоступна. Проверьте её состояние."
                return@run
            }
            // Load 14-day food history with binary masks and meal breakdowns
            val (historyItems, historyMeals) = runCatching { historyProvider.getRecentHistory() }
                .getOrDefault(emptyList<com.example.kthealthsync.domain.draft.RecentFoodHistoryItem>() to emptyList<com.example.kthealthsync.domain.draft.RecentMeal>())
            val historyPrompt = RecentFoodHistoryHelper.formatForPrompt(historyItems, historyMeals)
            val historyNames = (historyItems.map { it.id to it.name } +
                historyMeals.flatMap { meal -> meal.foods.map { it.id to it.name } })
                .filter { it.first.isNotBlank() }.toMap()
            val rows = client.parse(text, prefs.resolvedLanguageFlow.first(), historyPrompt, historyNames)
            store(rows)
            saved["draftSource"] = text
            saved["selected"] = null
            _message.value = "Проверьте каждую строку: названия, количества и лишние продукты."
        }
    }
}
