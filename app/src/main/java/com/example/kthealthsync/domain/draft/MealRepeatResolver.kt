package com.example.kthealthsync.domain.draft

/** Recognises only complete repeat commands. Extra foods/changes must not be silently discarded. */
data class MealRepeatRequest(val mealName: String, val daysAgo: Int)

class DraftInputException(message: String) : IllegalArgumentException(message)

object MealRepeatResolver {
    private val mealNames = listOf("второй завтрак", "второй ужин", "завтрак", "обед", "полдник", "ужин")
    private val mealPattern = mealNames.joinToString("|")
    private val same = Regex("^(?:(?:сегодня )?(?:съел |добавь |повтори )?)?(?:такой же |тот же |такой же самый )?($mealPattern) (?:такой же |тот же )?как (?:и )?(вчера|позавчера)$")
    private val previous = Regex("^(?:добавь |повтори |съел )?(вчерашний|позавчерашний) ($mealPattern)$")

    private fun normalize(text: String) = text.lowercase().replace('ё', 'е')
        .replace(Regex("[,!?.«»\"]"), " ").trim().replace(Regex("\\s+"), " ")

    fun recognize(text: String): MealRepeatRequest? {
        val normalized = normalize(text)
        same.matchEntire(normalized)?.let {
            return MealRepeatRequest(it.groupValues[1].replaceFirstChar(Char::uppercase), if (it.groupValues[2] == "вчера") 1 else 2)
        }
        previous.matchEntire(normalized)?.let {
            return MealRepeatRequest(it.groupValues[2].replaceFirstChar(Char::uppercase), if (it.groupValues[1] == "вчерашний") 1 else 2)
        }
        return null
    }

    fun rejectMealNames(rows: List<FoodDraftItem>) {
        if (rows.any { normalize(it.name).removePrefix("вчерашний ").removePrefix("позавчерашний ") in mealNames }) {
            throw DraftInputException("Nano вернул название приёма пищи вместо продуктов. Для повтора напишите отдельно: «такой же обед как вчера».")
        }
    }

    fun resolve(request: MealRepeatRequest, meals: List<RecentMeal>): List<FoodDraftItem> {
        val foods = meals.filter { it.daysAgo == request.daysAgo && it.mealName == request.mealName }.flatMap { it.foods }
        if (foods.isEmpty()) throw DraftInputException("В дневнике не найден ${request.mealName.lowercase()} ${if (request.daysAgo == 1) "за вчера" else "за позавчера"}. Проверьте дату и записи.")
        require(foods.size <= FoodDraftParser.MAX_ITEMS) { "В приёме пищи слишком много продуктов для одного черновика." }
        return foods.map { food ->
            val portion = parsePortion(food.portionText)
            FoodDraftItem(
                foodId = food.id.takeIf { it.isNotBlank() }, name = food.name,
                amount = portion?.let { FoodDraftParser.decimal(it.amount) }.orEmpty(),
                unit = portion?.unit ?: DraftUnit.UNKNOWN,
                yesterday = request.daysAgo == 1,
                historyVerified = true,
                historyPortion = food.portionText
            )
        }
    }

    /** Unknown portion labels remain blank; never guess their mass. */
    fun parsePortion(text: String): DraftPortion? {
        val value = text.trim().lowercase().replace(',', '.').replace('×', 'x')
        val match = Regex("^(?:(\\d+(?:\\.\\d+)?)\\s*x\\s*)?(\\d+(?:\\.\\d+)?)\\s*(g|г|kg|кг|ml|мл|l|л|ks|kus|kusy|шт\\.?)$").matchEntire(value) ?: return null
        val count = match.groupValues[1].ifBlank { "1" }.toDouble()
        val amount = match.groupValues[2].toDouble() * count
        val (unit, scale) = when (match.groupValues[3]) {
            "g", "г" -> DraftUnit.GRAMS to 1.0
            "kg", "кг" -> DraftUnit.GRAMS to 1000.0
            "ml", "мл" -> DraftUnit.MILLILITRES to 1.0
            "l", "л" -> DraftUnit.MILLILITRES to 1000.0
            else -> DraftUnit.PIECES to 1.0
        }
        val total = amount * scale
        return if (total.isFinite() && total > 0 && total <= 100000) DraftPortion(total, unit) else null
    }
}
