package com.example.kthealthsync.domain.util


object NutrientParser {

    private val NON_NUMERIC_REGEX = "[^0-9,.-]".toRegex()
    private val MULT_WITH_UNIT_REGEX = "([\\d.]+)\\s*x\\s*([\\d.]+)?\\s*([a-zA-Z]+)?".toRegex()
    private val FIRST_NUM_REGEX = "([\\d.]+)".toRegex()
    private val DIGIT_SPACE_DIGIT_REGEX = "(\\d)\\s+(\\d)".toRegex()
    private val MULT_PAREN_REGEX = "([\\d.]+)\\s*x[^(]*\\(([\\d.]+)\\s*(g|kg|ml|l)\\)".toRegex(RegexOption.IGNORE_CASE)
    private val PAREN_REGEX = "\\(([\\d.]+)\\s*(g|kg|ml|l)\\)".toRegex(RegexOption.IGNORE_CASE)
    private val MULT_REGEX = "([\\d.]+)\\s*x\\s*([\\d.]+)?\\s*(g|kg|ml|l)\\b".toRegex(RegexOption.IGNORE_CASE)
    private val SIMPLE_REGEX = "([\\d.]+)\\s*(g|kg|ml|l)\\b".toRegex(RegexOption.IGNORE_CASE)

    /**
     * Extracts numeric value from strings like "12,5 g", "350 kcal", etc.
     */
    fun parseNutrient(value: String?): Double {
        if (value.isNullOrBlank()) return 0.0
        val cleaned = value.replace(NON_NUMERIC_REGEX, "").replace(",", ".")
        return cleaned.toDoubleOrNull() ?: 0.0
    }

    /**
     * Parses the count/dose of supplements (tablets, capsules, portions, etc.).
     */
    fun parseSupplementCount(unitStr: String?): Double {
        if (unitStr.isNullOrBlank()) return 1.0
        val normalized = unitStr.replace("\u00a0", " ").replace(",", ".").trim()
        val lower = normalized.lowercase()

        // Match pattern "2 x 1000 mg" or "2 x 1 tobolka" or "1000 x 1 mg"
        val multWithUnit = MULT_WITH_UNIT_REGEX.find(lower)
        if (multWithUnit != null) {
            val count = multWithUnit.groupValues[1].toDoubleOrNull() ?: 1.0
            val subAmt = multWithUnit.groupValues[2].takeIf { it.isNotBlank() }?.toDoubleOrNull()
            val unit = multWithUnit.groupValues[3].lowercase()

            // If it is formatted as "1000 x 1 mg" or "500 x 1 mg", it represents 1 tablet of 1000mg/500mg
            if (count >= 50 && (unit == "mg" || subAmt == 1.0)) {
                return 1.0
            }
            return if (count > 0) count else 1.0
        }

        // If unit is standalone like "1000 mg", "500 mg", "1000mg", it represents 1 dose
        if (lower.endsWith("mg") || lower.endsWith("g") || lower.endsWith("ml")) {
            return 1.0
        }

        val firstNum = FIRST_NUM_REGEX.find(normalized)
        if (firstNum != null) {
            val num = firstNum.groupValues[1].toDoubleOrNull() ?: 1.0
            return if (num > 0) num else 1.0
        }
        return 1.0
    }

    /**
     * Parses the portion mass in grams from Kalorické Tabulky unit strings.
     * Supports patterns like:
     * - "150 x 1 g", "2 x 100 g", "0.5 x 1 kg"
     * - "1 x kus (120 g)", "2 x porce (200 g)", "1 x balení (0.5 kg)"
     * - "150 g", "0.25 kg", "200 ml" (1 ml ≈ 1 g for purees/smoothies)
     */
    fun parsePortionGrams(unitStr: String?): Double {
        if (unitStr.isNullOrBlank()) return 0.0
        val normalized = unitStr.replace("\u00a0", " ").replace(",", ".").trim()
        val cleaned = normalized.replace(DIGIT_SPACE_DIGIT_REGEX, "$1$2")

        // 1. Multiplier with paren: '2 x kus (150 g)' or '1 x balení (0.5 kg)'
        val multParen = MULT_PAREN_REGEX.find(cleaned)
        if (multParen != null) {
            val count = multParen.groupValues[1].toDoubleOrNull() ?: 1.0
            val amt = multParen.groupValues[2].toDoubleOrNull() ?: 0.0
            val unit = multParen.groupValues[3].lowercase()
            val scale = if (unit == "kg" || unit == "l") 1000.0 else 1.0
            return count * amt * scale
        }

        // 2. Paren only: 'kus (150 g)' or 'balení (0.5 kg)'
        val paren = PAREN_REGEX.find(cleaned)
        if (paren != null) {
            val amt = paren.groupValues[1].toDoubleOrNull() ?: 0.0
            val unit = paren.groupValues[2].lowercase()
            val scale = if (unit == "kg" || unit == "l") 1000.0 else 1.0
            return amt * scale
        }

        // 3. Multiplier: '150 x 1 g' or '2 x 100 g' or '0.5 x 1 kg'
        val mult = MULT_REGEX.find(cleaned)
        if (mult != null) {
            val count = mult.groupValues[1].toDoubleOrNull() ?: 1.0
            val amt = mult.groupValues[2].takeIf { it.isNotBlank() }?.toDoubleOrNull() ?: 1.0
            val unit = mult.groupValues[3].lowercase()
            val scale = if (unit == "kg" || unit == "l") 1000.0 else 1.0
            return count * amt * scale
        }

        // 4. Simple: '150 g' or '0.5 kg' or '200 ml'
        val simple = SIMPLE_REGEX.find(cleaned)
        if (simple != null) {
            val amt = simple.groupValues[1].toDoubleOrNull() ?: 0.0
            val unit = simple.groupValues[2].lowercase()
            val scale = if (unit == "kg" || unit == "l") 1000.0 else 1.0
            return amt * scale
        }

        return 0.0
    }
}
