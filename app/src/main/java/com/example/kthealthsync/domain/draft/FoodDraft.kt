package com.example.kthealthsync.domain.draft

import com.example.kthealthsync.FoodFormUnitOption
import com.google.gson.JsonParser
import java.math.BigDecimal
import java.util.UUID

/** Untrusted suggestions only: no database IDs, calories or diary writes. */
data class FoodDraftItem(
    val id: String = UUID.randomUUID().toString(),
    val foodId: String? = null,
    val name: String,
    val amount: String = "",
    val unit: DraftUnit = DraftUnit.UNKNOWN,
    val yesterday: Boolean = false,
    val historyVerified: Boolean = false,
    val nameNeedsReview: Boolean = false,
    val historyPortion: String? = null
) {
    val historyMatch: Boolean get() = foodId != null || yesterday
}

enum class DraftUnit(val label: String) { GRAMS("г"), MILLILITRES("мл"), PIECES("шт."), UNKNOWN("уточнить") }

data class DraftPortion(val amount: Double, val unit: DraftUnit)

data class DraftUnitMatch(val unit: FoodFormUnitOption, val amount: Double)

object FoodDraftParser {
    const val MAX_INPUT = 1500
    const val MAX_ITEMS = 20

    /** Search only source text or a name looked up by a known history ID, never a generated translation. */
    fun parseForSearch(raw: String, source: String, historyNames: Map<String, String> = emptyMap()): List<FoodDraftItem> {
        val rows = parse(raw, source)
        val cleaned = raw.trim().replace(Regex("^```(?:json)?\\s*"), "").replace(Regex("\\s*```$"), "")
        val items = JsonParser().parse(cleaned).asJsonObject.getAsJsonArray("items")
        return rows.mapIndexed { index, row ->
            val historyName = row.foodId?.let(historyNames::get)
            if (historyName != null) row.copy(name = historyName)
            else {
                val value = items[index].asJsonObject.get("sourceName")
                val quoted = if (value != null && !value.isJsonNull && value.isJsonPrimitive && value.asJsonPrimitive.isString)
                    value.asString.trim() else row.name
                val offset = if (quoted.isNotBlank()) source.indexOf(quoted, ignoreCase = true) else -1
                if (offset < 0) row.copy(foodId = null, nameNeedsReview = true)
                else row.copy(name = source.substring(offset, offset + quoted.length), foodId = null)
            }
        }
    }

    fun parse(raw: String, source: String = ""): List<FoodDraftItem> {
        require(raw.length <= 16000) { "Ответ слишком длинный. Попробуйте описать меньше продуктов." }
        val cleaned = raw.trim().replace(Regex("^```(?:json)?\\s*"), "").replace(Regex("\\s*```$"), "")
        val root = JsonParser().parse(cleaned).asJsonObject
        val items = root.getAsJsonArray("items") ?: error("Нет списка продуктов")
        require(items.size() in 1..MAX_ITEMS) { "Опишите от 1 до 20 продуктов." }
        return items.map { element ->
            val item = element.asJsonObject
            val foodId = item.get("id")?.takeUnless { it.isJsonNull }?.asString?.trim()?.takeIf { it.isNotBlank() && it != "null" }
            val name = item.get("name")?.takeUnless { it.isJsonNull }?.let {
                require(it.isJsonPrimitive && it.asJsonPrimitive.isString)
                it.asString.trim()
            }.orEmpty()
            require(name.isNotBlank() && name.length <= 120) { "Не удалось прочитать название продукта." }
            val number = item.get("amount")?.takeUnless { it.isJsonNull }?.let {
                require(it.isJsonPrimitive && it.asJsonPrimitive.isNumber) { "Не удалось прочитать количество." }
                it.asDouble.also { value -> require(value.isFinite() && value > 0 && value <= 100000) }
            }
            val unitText = item.get("unit")?.takeUnless { it.isJsonNull }?.asString
            val (unit, factor) = when (unitText) {
                "g" -> DraftUnit.GRAMS to 1.0
                "kg" -> DraftUnit.GRAMS to 1000.0
                "ml" -> DraftUnit.MILLILITRES to 1.0
                "l" -> DraftUnit.MILLILITRES to 1000.0
                "piece" -> DraftUnit.PIECES to 1.0
                null -> DraftUnit.UNKNOWN to 1.0
                else -> DraftUnit.UNKNOWN to 1.0
            }
            val reference = item.get("ref")?.takeUnless { it.isJsonNull }?.asString
            FoodDraftItem(
                foodId = foodId,
                name = name,
                amount = if (number != null && unit != DraftUnit.UNKNOWN) decimal(number * factor) else "",
                unit = unit,
                yesterday = reference == "yesterday" || foodId != null
            )
        }
    }

    fun decimal(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

    fun portion(item: FoodDraftItem): DraftPortion? {
        val number = item.amount.replace(',', '.').toDoubleOrNull() ?: return null
        return if (number.isFinite() && number > 0 && number <= 100000 && item.unit != DraftUnit.UNKNOWN)
            DraftPortion(number, item.unit) else null
    }

    /** Match explicit dimensions only. Never equate ml and g or guess package weight. */
    fun matchUnit(portion: DraftPortion, options: List<FoodFormUnitOption>): DraftUnitMatch? {
        for (option in options) {
            if (option.id.isNullOrBlank()) continue
            val title = option.title?.trim()?.lowercase()?.replace(',', '.') ?: continue
            val match = Regex("^(?:(\\d+(?:\\.\\d+)?)\\s*)?(g|г|gram|gramy|ml|мл|l|л|kg|кг|ks|kus|kusy|шт\\.?)$").matchEntire(title) ?: continue
            val quantity = match.groupValues[1].ifEmpty { "1" }.toDouble()
            if (quantity <= 0) continue
            val (dimension, scale) = when (match.groupValues[2]) {
                "g", "г", "gram", "gramy" -> DraftUnit.GRAMS to 1.0
                "kg", "кг" -> DraftUnit.GRAMS to 1000.0
                "ml", "мл" -> DraftUnit.MILLILITRES to 1.0
                "l", "л" -> DraftUnit.MILLILITRES to 1000.0
                else -> DraftUnit.PIECES to 1.0
            }
            if (dimension == portion.unit) return DraftUnitMatch(option, portion.amount / (quantity * scale))
        }
        return null
    }
}
