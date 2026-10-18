package com.example.kthealthsync.domain.draft

import com.example.kthealthsync.DebugLogger
import com.example.kthealthsync.DiaryResponse
import com.example.kthealthsync.Foodstuff
import com.example.kthealthsync.MealTime
import com.example.kthealthsync.data.repository.KalorickeRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class RecentFoodHistoryItem(
    val id: String,
    val name: String,
    val portionText: String,
    val mask: String,
    val lastDaysAgo: Int
)

data class RecentMealFood(
    val id: String,
    val name: String,
    val portionText: String
)

data class RecentMeal(
    val daysAgo: Int,
    val mealName: String,
    val foods: List<RecentMealFood>
)

object RecentFoodHistoryHelper {
    const val DAYS_COUNT = 14

    fun normalizeMealName(meal: MealTime): String {
        val title = meal.title?.trim().orEmpty()
        val name = meal.name?.trim().orEmpty()
        val raw = if (title.isNotEmpty()) title else name

        return when {
            raw.contains("Dopolední", ignoreCase = true) || raw.contains("Второй завтрак", ignoreCase = true) || meal.id == "2" -> "Второй завтрак"
            raw.contains("Druhá večeře", ignoreCase = true) || raw.contains("Второй ужин", ignoreCase = true) || meal.id == "6" -> "Второй ужин"
            raw.contains("Snídaně", ignoreCase = true) || raw.contains("Завтрак", ignoreCase = true) || raw.contains("Breakfast", ignoreCase = true) || meal.id == "1" -> "Завтрак"
            raw.contains("Oběd", ignoreCase = true) || raw.contains("Обед", ignoreCase = true) || raw.contains("Lunch", ignoreCase = true) || meal.id == "3" -> "Обед"
            raw.contains("Odpolední", ignoreCase = true) || raw.contains("Полдник", ignoreCase = true) || meal.id == "4" -> "Полдник"
            raw.contains("Večeře", ignoreCase = true) || raw.contains("Ужин", ignoreCase = true) || raw.contains("Dinner", ignoreCase = true) || meal.id == "5" -> "Ужин"
            raw.isNotBlank() -> raw
            else -> "Приём пищи"
        }
    }

    /**
     * Builds a 14-character bitmask.
     * Index 0 (bit 1) = 1 day ago (yesterday).
     * Index 13 (bit 14) = 14 days ago.
     */
    fun buildMask(daysAgoSet: Set<Int>): String {
        val chars = CharArray(DAYS_COUNT) { '0' }
        for (day in daysAgoSet) {
            val idx = day - 1
            if (idx in 0 until DAYS_COUNT) {
                chars[idx] = '1'
            }
        }
        return String(chars)
    }

    fun extractMeals(daysMap: Map<Int, List<MealTime>>): List<RecentMeal> {
        val result = mutableListOf<RecentMeal>()
        for ((daysAgo, mealTimes) in daysMap.toSortedMap()) {
            for (meal in mealTimes) {
                val foods = meal.foodstuff?.mapNotNull { f ->
                    val name = (f.title ?: f.name ?: f.nazev)?.trim().orEmpty()
                    if (name.isBlank()) return@mapNotNull null
                    val id = f.guidRecipe ?: f.guid ?: f.id ?: ""
                    val portion = f.unit?.trim().takeUnless { it.isNullOrBlank() } ?: "1 порция"
                    RecentMealFood(id = id, name = name, portionText = portion)
                } ?: emptyList()

                if (foods.isNotEmpty()) {
                    result.add(RecentMeal(daysAgo = daysAgo, mealName = normalizeMealName(meal), foods = foods))
                }
            }
        }
        return result
    }

    /**
     * Aggregates food items from 14 days of diary into unique items with masks.
     */
    fun aggregate(daysMap: Map<Int, List<Foodstuff>>): List<RecentFoodHistoryItem> {
        val itemOccurrences = mutableMapOf<String, MutableList<Pair<Int, Foodstuff>>>()

        for (day in 1..DAYS_COUNT) {
            val foods = daysMap[day] ?: continue
            for (f in foods) {
                val name = f.title ?: f.name ?: f.nazev ?: continue
                if (name.isBlank()) continue
                val id = f.guidRecipe ?: f.guid ?: f.id ?: name.trim().lowercase()
                val key = if (id.isNotBlank()) id else name.trim().lowercase()
                itemOccurrences.getOrPut(key) { mutableListOf() }.add(day to f)
            }
        }

        return itemOccurrences.values.mapNotNull { occurrences ->
            val sorted = occurrences.sortedBy { it.first }
            val mostRecent = sorted.first()
            val lastDaysAgo = mostRecent.first
            val food = mostRecent.second
            val name = (food.title ?: food.name ?: food.nazev)?.trim().orEmpty()
            if (name.isBlank()) return@mapNotNull null
            val id = food.guidRecipe ?: food.guid ?: food.id ?: ""

            val daysAgoSet = occurrences.map { it.first }.toSet()
            val mask = buildMask(daysAgoSet)
            val portion = food.unit?.trim().takeUnless { it.isNullOrBlank() } ?: "1 порция"

            RecentFoodHistoryItem(
                id = id,
                name = name,
                portionText = portion,
                mask = mask,
                lastDaysAgo = lastDaysAgo
            )
        }.sortedWith(compareBy({ it.lastDaysAgo }, { it.name }))
    }

    fun formatForPrompt(
        items: List<RecentFoodHistoryItem>,
        recentMeals: List<RecentMeal> = emptyList(),
        limit: Int = 30
    ): String {
        if (items.isEmpty() && recentMeals.isEmpty()) return ""

        val sb = StringBuilder()

        if (recentMeals.isNotEmpty()) {
            sb.append("Недавние приёмы пищи (блюда внутри каждого приёма):\n")
            // Group by daysAgo for recent days (up to 3 days ago)
            val byDay = recentMeals.filter { it.daysAgo <= 3 }.groupBy { it.daysAgo }
            for ((daysAgo, meals) in byDay) {
                val dayLabel = when (daysAgo) {
                    1 -> "Вчера (1 день назад)"
                    2 -> "Позавчера (2 дня назад)"
                    else -> "$daysAgo дня назад"
                }
                sb.append("$dayLabel:\n")
                for (meal in meals) {
                    val foodsStr = meal.foods.joinToString(", ") { f ->
                        val idStr = if (f.id.isNotBlank()) "[id: \"${f.id}\"] " else ""
                        "$idStr\"${f.name}\" ${f.portionText}"
                    }
                    sb.append("- ${meal.mealName}: $foodsStr\n")
                }
            }
            sb.append("\n")
        }

        if (items.isNotEmpty()) {
            sb.append("Все продукты за 14 дней (маска: бит 1 = 1 день назад (вчера), бит 14 = 14 дней назад):\n")
            for (item in items.take(limit)) {
                val idStr = if (item.id.isNotBlank()) "[id: \"${item.id}\"] " else ""
                sb.append("- $idStr\"${item.name}\", ${item.portionText}, маска: ${item.mask} (посл: ${item.lastDaysAgo} дн назад)\n")
            }
        }

        return sb.toString().trimEnd()
    }
}

class RecentFoodHistoryProvider(private val repository: KalorickeRepository) {
    // In-memory cache for past days: YYYY-MM-DD -> DiaryResponse
    private val diaryCache = ConcurrentHashMap<String, DiaryResponse>()

    suspend fun getRecentHistory(
        today: LocalDate = LocalDate.now(),
        daysBack: Int = RecentFoodHistoryHelper.DAYS_COUNT
    ): Pair<List<RecentFoodHistoryItem>, List<RecentMeal>> {
        val daysFoodsMap = mutableMapOf<Int, List<Foodstuff>>()
        val daysMealsMap = mutableMapOf<Int, List<MealTime>>()
        val semaphore = Semaphore(3) // limit concurrency to avoid server 429

        coroutineScope {
            val deferreds = (1..daysBack).map { daysAgo ->
                async {
                    val dateStr = today.minusDays(daysAgo.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)
                    val cached = diaryCache[dateStr]
                    val diary = if (cached != null) {
                        cached
                    } else {
                        semaphore.withPermit {
                            runCatching {
                                repository.getDiaryWithAutoLogin(dateStr).also { resp ->
                                    diaryCache[dateStr] = resp
                                    DebugLogger.addActionLog("FoodHistory: cached diary for $dateStr")
                                }
                            }.onFailure { err ->
                                DebugLogger.addActionLog("FoodHistory: error loading $dateStr: ${err.message}")
                            }.getOrNull()
                        }
                    }
                    val mealTimes = diary?.data?.times ?: emptyList()
                    val foods = mealTimes.flatMap { it.foodstuff ?: emptyList() }
                    daysAgo to (foods to mealTimes)
                }
            }
            deferreds.awaitAll().forEach { (day, pair) ->
                val (foods, mealTimes) = pair
                if (foods.isNotEmpty()) {
                    daysFoodsMap[day] = foods
                }
                if (mealTimes.isNotEmpty()) {
                    daysMealsMap[day] = mealTimes
                }
            }
        }

        val items = RecentFoodHistoryHelper.aggregate(daysFoodsMap)
        val meals = RecentFoodHistoryHelper.extractMeals(daysMealsMap)
        DebugLogger.addActionLog("FoodHistory: total unique foods=${items.size}, meals=${meals.size}")
        return items to meals
    }
}
