package com.example.kthealthsync

import com.example.kthealthsync.domain.util.NutrientParser
import org.junit.Assert.assertEquals
import org.junit.Test

class NutrientParserTest {

    @Test
    fun testParseNutrient() {
        assertEquals(12.5, NutrientParser.parseNutrient("12,5 g"), 0.001)
        assertEquals(350.0, NutrientParser.parseNutrient("350 kcal"), 0.001)
        assertEquals(0.0, NutrientParser.parseNutrient(null), 0.001)
        assertEquals(0.0, NutrientParser.parseNutrient(""), 0.001)
        assertEquals(0.0, NutrientParser.parseNutrient("abc"), 0.001)
        assertEquals(1234.56, NutrientParser.parseNutrient("1234.56"), 0.001)
    }

    @Test
    fun testParsePortionGrams() {
        assertEquals(150.0, NutrientParser.parsePortionGrams("150 x 1 g"), 0.001)
        assertEquals(200.0, NutrientParser.parsePortionGrams("2 x 100 g"), 0.001)
        assertEquals(120.0, NutrientParser.parsePortionGrams("1 x kus (120 g)"), 0.001)
        assertEquals(260.0, NutrientParser.parsePortionGrams("2 x kus (130 g)"), 0.001)
        assertEquals(125.0, NutrientParser.parsePortionGrams("0.5 x porce (250 g)"), 0.001)
        assertEquals(500.0, NutrientParser.parsePortionGrams("1 x balení (500 g)"), 0.001)
        assertEquals(300.0, NutrientParser.parsePortionGrams("0.3 x 1 kg"), 0.001)
        assertEquals(150.0, NutrientParser.parsePortionGrams("150 g"), 0.001)
        assertEquals(250.0, NutrientParser.parsePortionGrams("0.25 kg"), 0.001)
        assertEquals(250.0, NutrientParser.parsePortionGrams("250 ml"), 0.001)
        assertEquals(200.0, NutrientParser.parsePortionGrams("1 x 200 ml"), 0.001)
        assertEquals(0.0, NutrientParser.parsePortionGrams(null), 0.001)
        assertEquals(0.0, NutrientParser.parsePortionGrams(""), 0.001)
    }

    @Test
    fun testParseSupplementCount() {
        org.junit.Assert.assertEquals(3.0, NutrientParser.parseSupplementCount("3 x 1 g"), 0.001)
        org.junit.Assert.assertEquals(1.0, NutrientParser.parseSupplementCount("1 g"), 0.001)
        org.junit.Assert.assertEquals(2.0, NutrientParser.parseSupplementCount("2 x 1 g"), 0.001)
        org.junit.Assert.assertEquals(8.0, NutrientParser.parseSupplementCount("8 x 1 g"), 0.001)
        org.junit.Assert.assertEquals(1.0, NutrientParser.parseSupplementCount("1 tableta"), 0.001)
        org.junit.Assert.assertEquals(2.0, NutrientParser.parseSupplementCount("2 kapsle"), 0.001)
        org.junit.Assert.assertEquals(1.0, NutrientParser.parseSupplementCount(null), 0.001)
        org.junit.Assert.assertEquals(1.0, NutrientParser.parseSupplementCount(""), 0.001)
    }
}
