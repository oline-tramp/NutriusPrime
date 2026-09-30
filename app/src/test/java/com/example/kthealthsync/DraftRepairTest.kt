package com.example.kthealthsync

import com.example.kthealthsync.domain.draft.*
import org.junit.Assert.*
import org.junit.Test

class DraftRepairTest {
    @Test fun malformedUnitAndInflectedNameDoNotDiscardTheEntireDraft() {
        val rows = FoodDraftParser.parseForSearch("""{"items":[
            {"name":"okurka salátová","sourceName":"okurka salátová","amount":200,"unit":"g"},
            {"name":"rajčátko","sourceName":"rajčátko","amount":150,"unit":"krámů"},
            {"name":"avokáda","sourceName":"avokáda","amount":70,"unit":"g"}
        ]}""", "okurku salátovou 200 g rajčátko 150 krámů avokáda 70 g")
        assertEquals(3, rows.size)
        assertTrue(rows[0].nameNeedsReview)
        assertEquals("200", rows[0].amount)
        assertNull(FoodDraftParser.portion(rows[1]))
        assertEquals(DraftUnit.UNKNOWN, rows[1].unit)
        assertEquals("70", rows[2].amount)
        assertFalse(rows[2].nameNeedsReview)
    }
}
