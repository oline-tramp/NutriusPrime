package com.example.kthealthsync.domain.model

/** Per-request server locale; never changes stored authentication cookies. */
object SearchLocale {
    private const val COOKIE = "org.springframework.web.servlet.i18n.CookieLocaleResolver.LOCALE"

    fun cookie(original: String, language: AppLanguage?): String {
        if (language == null || language == AppLanguage.AUTO) return original
        val parts = original.split(';').map(String::trim).filter(String::isNotEmpty)
        val retained = parts.filterNot { it.substringBefore('=').endsWith("CookieLocaleResolver.LOCALE") }
        return (retained + "$COOKIE=${language.code}").joinToString("; ")
    }
}
