package com.example.kthealthsync

import com.example.kthealthsync.domain.draft.*
import org.junit.Assert.*
import org.junit.Test

class FoodDraftParserTest {
    private fun parse(item: String, source: String = "еда") = FoodDraftParser.parse("""{"items":[$item]}""", source).single()

    @Test fun sourceLitresAndKilogramsAreConvertedInCode() {
        val water = parse("""{"name":"вода","amount":0.5,"unit":"l"}""")
        assertEquals("500", water.amount)
        assertEquals(DraftUnit.MILLILITRES, water.unit)
        assertEquals("200", parse("""{"name":"курица","amount":0.2,"unit":"kg"}""").amount)
    }

    @Test fun unknownGlassNeverGetsAnAutomaticPortion() {
        val item = parse("""{"name":"кефир","amount":null,"unit":null}""")
        assertEquals("", item.amount)
        assertNull(FoodDraftParser.portion(item))
    }

    @Test fun foodIdAndHistoryMatchAreParsedFromModelResponse() {
        val raw = """{"id":"food_123","name":"борщ","amount":350,"unit":"g"}"""
        val item = parse(raw)
        assertEquals("food_123", item.foodId)
        assertEquals("350", item.amount)
        assertEquals(DraftUnit.GRAMS, item.unit)
        assertTrue(item.historyMatch)
    }

    @Test fun newFoodWithoutHistoryHasNullFoodId() {
        val raw = """{"id":null,"name":"яблоко","amount":150,"unit":"g"}"""
        val item = parse(raw)
        assertNull(item.foodId)
        assertEquals("яблоко", item.name)
        assertFalse(item.historyMatch)
    }

    @Test fun markdownIsAcceptedWithoutRequiringRedundantClarificationFlag() {
        val rows = FoodDraftParser.parse("""```json
            {"items":[{"name":"Kinder Joy","amount":1,"unit":"piece"}]}
            ```""", "один киндер")
        assertEquals(DraftUnit.PIECES, rows.single().unit)
    }

    @Test fun invalidModelAmountsAreRejected() {
        for (amount in listOf("-1", "0", "1e309", "\"200\"")) {
            assertThrows(Exception::class.java) { parse("""{"name":"еда","amount":$amount,"unit":"g"}""") }
        }
    }

    @Test fun truncatedResponseDoesNotProducePartialDraft() {
        assertThrows(Exception::class.java) { FoodDraftParser.parse("""{"items":[{"name":"еда"""", "еда") }
    }

    @Test fun millilitresDoNotMatchGramsOrAnUnknownPortion() {
        val options = listOf(FoodFormUnitOption("g", "1 g", 1.0), FoodFormUnitOption("p", "porce", -2.0))
        assertNull(FoodDraftParser.matchUnit(DraftPortion(500.0, DraftUnit.MILLILITRES), options))
        assertNull(FoodDraftParser.matchUnit(DraftPortion(1.0, DraftUnit.PIECES), options))
    }

    @Test fun explicitlyLabelledUnitsAreScaledAndKeepTheirServerId() {
        val options = listOf(FoodFormUnitOption("ml100", "100 ml", 100.0))
        val match = FoodDraftParser.matchUnit(DraftPortion(500.0, DraftUnit.MILLILITRES), options)!!
        assertEquals("ml100", match.unit.id)
        assertEquals(5.0, match.amount, 0.0001)
    }
}
