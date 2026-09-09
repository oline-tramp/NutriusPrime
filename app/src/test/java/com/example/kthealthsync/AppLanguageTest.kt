package com.example.kthealthsync

import com.example.kthealthsync.domain.model.AppLanguage
import com.example.kthealthsync.domain.model.getAppStrings
import com.example.kthealthsync.domain.util.NutrientUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLanguageTest {

    @Test
    fun testExtractLocaleFromCookie() {
        val csCookie = "JSESSIONID=abc1234; org.springframework.web.servlet.i18n.CookieLocaleResolver.LOCALE=cs; other=value"
        assertEquals(AppLanguage.CS, AppLanguage.extractLocaleFromCookie(csCookie))

        val ruCookie = "foo=bar; org.springframework.web.servlet.i18n.CookieLocaleResolver.LOCALE=ru"
        assertEquals(AppLanguage.RU, AppLanguage.extractLocaleFromCookie(ruCookie))

        val enCookie = "org.springframework.web.servlet.i18n.CookieLocaleResolver.LOCALE=en_US"
        assertEquals(AppLanguage.EN, AppLanguage.extractLocaleFromCookie(enCookie))

        val skCookie = "org.springframework.web.servlet.i18n.CookieLocaleResolver.LOCALE=sk"
        assertEquals(AppLanguage.CS, AppLanguage.extractLocaleFromCookie(skCookie))

        assertNull(AppLanguage.extractLocaleFromCookie(null))
        assertNull(AppLanguage.extractLocaleFromCookie("JSESSIONID=abc1234"))
    }

    @Test
    fun testMealTimeDisplayNamePrioritizesKtTitle() {
        val ruStrings = getAppStrings(AppLanguage.RU)
        val csMeal = MealTime(
            id = "1",
            title = "Snídaně",
            name = null,
            foodstuff = emptyList()
        )
        // Even with RU strings, KT's meal title from diary should be preserved!
        assertEquals("Snídaně", NutrientUtils.getMealTimeDisplayName(csMeal, ruStrings))

        // When title & name are null, fallback to localized name
        val fallbackMeal = MealTime(
            id = "1",
            title = null,
            name = null,
            foodstuff = emptyList()
        )
        assertEquals("Завтрак", NutrientUtils.getMealTimeDisplayName(fallbackMeal, ruStrings))
        val csStrings = getAppStrings(AppLanguage.CS)
        assertEquals("Snídaně", NutrientUtils.getMealTimeDisplayName(fallbackMeal, csStrings))
    }

    @Test
    fun testStandardMealOptionsLocalized() {
        val csStrings = getAppStrings(AppLanguage.CS)
        val csOptions = NutrientUtils.getStandardMealOptions(csStrings)
        assertEquals("Snídaně", csOptions.find { it.first == "1" }?.second)
        assertEquals("Oběd", csOptions.find { it.first == "3" }?.second)
        assertEquals("Večeře", csOptions.find { it.first == "5" }?.second)

        val enStrings = getAppStrings(AppLanguage.EN)
        val enOptions = NutrientUtils.getStandardMealOptions(enStrings)
        assertEquals("Breakfast", enOptions.find { it.first == "1" }?.second)
        assertEquals("Lunch", enOptions.find { it.first == "3" }?.second)
        assertEquals("Dinner", enOptions.find { it.first == "5" }?.second)
    }
}
