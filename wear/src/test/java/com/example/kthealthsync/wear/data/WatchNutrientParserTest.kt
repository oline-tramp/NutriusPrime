package com.example.kthealthsync.wear.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchNutrientParserTest {

    @Test
    fun parsePortionGrams_variousFormats() {
        assertEquals(20.0, WatchNutrientParser.parsePortionGrams("kus (20 g)"), 0.01)
        assertEquals(20.0, WatchNutrientParser.parsePortionGrams("1 kus (20 g)"), 0.01)
        assertEquals(300.0, WatchNutrientParser.parsePortionGrams("2 x kus (150 g)"), 0.01)
        assertEquals(500.0, WatchNutrientParser.parsePortionGrams("balení (0.5 kg)"), 0.01)
        assertEquals(250.0, WatchNutrientParser.parsePortionGrams("250 ml"), 0.01)
        assertEquals(900.0, WatchNutrientParser.parsePortionGrams("900 ml"), 0.01)
        assertEquals(1000.0, WatchNutrientParser.parsePortionGrams("1 l"), 0.01)
        assertEquals(2.0, WatchNutrientParser.parsePortionGrams("2 g"), 0.01)
        assertEquals(1.5, WatchNutrientParser.parsePortionGrams("1 tableta (1,5 g)"), 0.01)
        assertEquals(0.0, WatchNutrientParser.parsePortionGrams(null), 0.01)
        assertEquals(0.0, WatchNutrientParser.parsePortionGrams(""), 0.01)
    }
}
