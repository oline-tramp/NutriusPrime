package com.example.kthealthsync.domain.draft

import com.example.kthealthsync.domain.model.AppLanguage

object DraftLanguagePolicy {
    fun instruction(language: AppLanguage): String {
        require(language != AppLanguage.AUTO) { "Resolve interface language before inference" }
        val label = when (language) {
            AppLanguage.CS -> "Czech (cs)"
            AppLanguage.RU -> "Russian (ru)"
            else -> "English (en)"
        }
        return """
            Interface language: $label. Use it to interpret ambiguous input, not to translate food names.
            The user may write Czech, Russian, English, or mix languages.
            Keep each food name in the language and spelling used by the user; it will be sent directly to food search.
            Do NOT translate Czech food names into Russian or English, regardless of interface language.
            Keep brand names and flavours. For a verified history match, keep the exact history name.
            JSON keys and unit codes remain exactly as specified by the schema.
            Example: "okurka salatova 150 g" -> name="okurka salatova", amount=150, unit="g".
            Example: "огурец 150 г" -> name="огурец", amount=150, unit="g".
        """.trimIndent()
    }
}
