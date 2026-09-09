package com.example.kthealthsync.domain.model

import java.util.Locale

enum class AppLanguage(val code: String, val title: String) {
    AUTO("auto", "Авто"),
    RU("ru", "Русский"),
    CS("cs", "Čeština"),
    EN("en", "English");

    fun toLocale(): Locale = when (this) {
        RU -> Locale("ru")
        CS -> Locale("cs")
        EN -> Locale.ENGLISH
        AUTO -> Locale.getDefault()
    }

    companion object {
        fun fromCode(code: String?): AppLanguage {
            return when (code?.lowercase()) {
                "ru" -> RU
                "cs", "cz" -> CS
                "en" -> EN
                else -> AUTO
            }
        }

        fun extractLocaleFromCookie(cookie: String?): AppLanguage? {
            if (cookie.isNullOrBlank()) return null

            // Kalorické Tabulky uses org.springframework.web.servlet.i18n.CookieLocaleResolver.LOCALE=cs
            val regex = Regex("""CookieLocaleResolver\.LOCALE=([a-zA-Z_]+)""")
            val match = regex.find(cookie) ?: return null
            val cookieLocale = match.groupValues.getOrNull(1)?.lowercase()?.take(2) ?: return null

            return when (cookieLocale) {
                "cs", "cz", "sk" -> CS
                "ru", "uk", "be" -> RU
                "en" -> EN
                else -> null
            }
        }

        fun resolveAppLanguage(cookie: String?): AppLanguage {
            return extractLocaleFromCookie(cookie) ?: run {
                val sysLang = Locale.getDefault().language.lowercase()
                when {
                    sysLang.startsWith("ru") || sysLang.startsWith("uk") || sysLang.startsWith("be") -> RU
                    sysLang.startsWith("cs") || sysLang.startsWith("sk") -> CS
                    else -> EN
                }
            }
        }
    }
}
