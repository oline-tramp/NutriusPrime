package com.example.kthealthsync

import com.example.kthealthsync.domain.draft.*
import org.junit.Assert.*
import org.junit.Test

class MealRepeatResolverTest {
    @Test fun usersExactPhraseAndPunctuationSelectYesterdayLunch() {
        for (phrase in listOf("такой же обед как вчера", "Такой же обед, как и вчера!", "повтори вчерашний обед")) {
            assertEquals(MealRepeatRequest("Обед", 1), MealRepeatResolver.recognize(phrase))
        }
        assertEquals(MealRepeatRequest("Ужин", 2), MealRepeatResolver.recognize("ужин как позавчера"))
    }

    @Test fun additionalFoodsAndChangesAreNeverSilentlyDiscarded() {
        for (text in listOf("такой же обед как вчера и яблоко", "обед как вчера, но без хлеба", "не повторяй вчерашний обед", "вчерашний борщ 200 г", "на обед помидор")) {
            assertNull(text, MealRepeatResolver.recognize(text))
        }
    }

    @Test fun wholeMealExpandsIntoActualFoodsWithThatDaysPortions() {
        val history = listOf(
            RecentMeal(1, "Обед", listOf(RecentMealFood("soup", "Борщ", "350 g"), RecentMealFood("bread", "Хлеб", "2 x 25 g"))),
            RecentMeal(2, "Обед", listOf(RecentMealFood("soup", "Борщ", "200 g"))),
            RecentMeal(1, "Ужин", listOf(RecentMealFood("cake", "Торт", "150 g")))
        )
        val rows = MealRepeatResolver.resolve(MealRepeatRequest("Обед", 1), history)
        assertEquals(listOf("Борщ", "Хлеб"), rows.map { it.name })
        assertEquals(listOf("350", "50"), rows.map { it.amount })
        assertTrue(rows.all { it.historyVerified })
        assertEquals("200", MealRepeatResolver.resolve(MealRepeatRequest("Обед", 2), history).single().amount)
    }

    @Test fun missingMealIsAnErrorNotAFoodNamedLunch() {
        assertThrows(DraftInputException::class.java) { MealRepeatResolver.resolve(MealRepeatRequest("Обед", 1), emptyList()) }
        assertThrows(DraftInputException::class.java) { MealRepeatResolver.rejectMealNames(listOf(FoodDraftItem(name = "Обед"))) }
        MealRepeatResolver.rejectMealNames(listOf(FoodDraftItem(name = "Борщ")))
    }

    @Test fun unknownPortionIsPreservedForReviewButNotInvented() {
        val row = MealRepeatResolver.resolve(MealRepeatRequest("Обед", 1), listOf(RecentMeal(1, "Обед", listOf(RecentMealFood("soup", "Суп", "1 порция"))))).single()
        assertEquals("", row.amount)
        assertEquals(DraftUnit.UNKNOWN, row.unit)
        assertEquals("1 порция", row.historyPortion)
        assertNull(MealRepeatResolver.parsePortion("0 g"))
        assertEquals(DraftPortion(500.0, DraftUnit.MILLILITRES), MealRepeatResolver.parsePortion("0,5 l"))
    }

    @Test fun secondMealsAreNotMergedWithFirstMeals() {
        assertEquals("Второй завтрак", RecentFoodHistoryHelper.normalizeMealName(MealTime(id = "2", title = "Второй завтрак")))
        assertEquals("Второй ужин", RecentFoodHistoryHelper.normalizeMealName(MealTime(id = "6", title = "Druhá večeře")))
        assertEquals("Обед", RecentFoodHistoryHelper.normalizeMealName(MealTime(id = "3", title = "Oběd")))
    }
}
