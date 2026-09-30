package com.example.kthealthsync

import com.example.kthealthsync.domain.draft.DraftLanguagePolicy
import com.example.kthealthsync.domain.draft.FoodDraftParser
import com.example.kthealthsync.domain.model.AppLanguage
import com.example.kthealthsync.domain.model.SearchLocale
import org.junit.Assert.*
import org.junit.Test

class DraftLanguageTest {
    @Test fun searchUsesSourceQuoteEvenWhenNanoTranslatesDisplayName() {
        val row = FoodDraftParser.parseForSearch("""{"items":[{"name":"огурец салатный","sourceName":"okurka salátová","amount":150,"unit":"g"}]}""", "okurka salátová 150 g").single()
        assertEquals("okurka salátová", row.name)
    }

    @Test fun translatedOrInventedNamesRequireExplicitReviewBeforeSearch() {
        val row = FoodDraftParser.parseForSearch("""{"items":[{"id":"invented","name":"огурец salatova","amount":150,"unit":"g"}]}""", "okurka salatova 150 g").single()
        assertTrue(row.nameNeedsReview)
        assertNull(row.foodId)
    }

    @Test fun knownHistoryIdUsesActualCatalogNameInsteadOfModelsTranslation() {
        val row = FoodDraftParser.parseForSearch("""{"items":[{"id":"known","name":"огурец","amount":150,"unit":"g"}]}""", "как вчера", mapOf("known" to "okurka salátová")).single()
        assertEquals("okurka salátová", row.name)
    }
    @Test fun languagePolicyUsesResolvedInterfaceChoiceAndPreservesSourceNames() {
        for ((language, label) in listOf(AppLanguage.CS to "Czech (cs)", AppLanguage.RU to "Russian (ru)", AppLanguage.EN to "English (en)")) {
            val instruction = DraftLanguagePolicy.instruction(language)
            assertTrue(instruction.contains("Interface language: $label"))
            assertTrue(instruction.contains("Do NOT translate Czech food names"))
        }
        assertThrows(IllegalArgumentException::class.java) { DraftLanguagePolicy.instruction(AppLanguage.AUTO) }
    }

    @Test fun czechNameIsUnchangedBetweenModelResponseAndDraft() {
        for (name in listOf("okurka salatova", "okurka salátová", "Birell Pomelo & Grep")) {
            val item = FoodDraftParser.parse("""{"items":[{"id":null,"name":"$name","amount":150,"unit":"g"}]}""", "$name 150 g").single()
            assertEquals(name, item.name)
        }
    }

    @Test fun localeOverridePreservesOtherCookiesAndDoesNotDuplicateLocale() {
        val cookie = "auth=abc=123; org.springframework.web.servlet.i18n.CookieLocaleResolver.LOCALE=ru; theme=dark"
        val updated = SearchLocale.cookie(cookie, AppLanguage.CS)
        assertEquals("auth=abc=123; theme=dark; org.springframework.web.servlet.i18n.CookieLocaleResolver.LOCALE=cs", updated)
        assertEquals(updated, SearchLocale.cookie(updated, AppLanguage.CS))
        assertEquals(cookie, SearchLocale.cookie(cookie, null))
        assertEquals(cookie, SearchLocale.cookie(cookie, AppLanguage.AUTO))
    }
}
