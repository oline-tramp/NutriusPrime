package com.example.kthealthsync.wear.data

object WatchNutrientParser {
    fun parsePortionGrams(unitStr: String?): Double {
        if (unitStr.isNullOrBlank()) return 0.0
        val normalized = unitStr.replace("\u00a0", " ").replace(",", ".").trim()
        val cleaned = normalized.replace("(\\d)\\s+(\\d)".toRegex(), "$1$2")

        // 1. Multiplier with paren: '2 x kus (150 g)' or '1 x balení (0.5 kg)'
        val multParen = "([\\d.]+)\\s*x[^(]*\\(([\\d.]+)\\s*(g|kg|ml|l)\\)".toRegex(RegexOption.IGNORE_CASE).find(cleaned)
        if (multParen != null) {
            val count = multParen.groupValues[1].toDoubleOrNull() ?: 1.0
            val amt = multParen.groupValues[2].toDoubleOrNull() ?: 0.0
            val unit = multParen.groupValues[3].lowercase()
            val scale = if (unit == "kg" || unit == "l") 1000.0 else 1.0
            return count * amt * scale
        }

        // 2. Paren only: 'kus (150 g)' or 'balení (0.5 kg)'
        val paren = "\\(([\\d.]+)\\s*(g|kg|ml|l)\\)".toRegex(RegexOption.IGNORE_CASE).find(cleaned)
        if (paren != null) {
            val amt = paren.groupValues[1].toDoubleOrNull() ?: 0.0
            val unit = paren.groupValues[2].lowercase()
            val scale = if (unit == "kg" || unit == "l") 1000.0 else 1.0
            return amt * scale
        }

        // 3. Multiplier: '150 x 1 g' or '2 x 100 g' or '0.5 x 1 kg'
        val mult = "([\\d.]+)\\s*x\\s*([\\d.]+)?\\s*(g|kg|ml|l)\\b".toRegex(RegexOption.IGNORE_CASE).find(cleaned)
        if (mult != null) {
            val count = mult.groupValues[1].toDoubleOrNull() ?: 1.0
            val amt = mult.groupValues[2].takeIf { it.isNotBlank() }?.toDoubleOrNull() ?: 1.0
            val unit = mult.groupValues[3].lowercase()
            val scale = if (unit == "kg" || unit == "l") 1000.0 else 1.0
            return count * amt * scale
        }

        // 4. Simple: '150 g' or '0.5 kg' or '200 ml'
        val simple = "([\\d.]+)\\s*(g|kg|ml|l)\\b".toRegex(RegexOption.IGNORE_CASE).find(cleaned)
        if (simple != null) {
            val amt = simple.groupValues[1].toDoubleOrNull() ?: 0.0
            val unit = simple.groupValues[2].lowercase()
            val scale = if (unit == "kg" || unit == "l") 1000.0 else 1.0
            return amt * scale
        }

        return 0.0
    }
}
