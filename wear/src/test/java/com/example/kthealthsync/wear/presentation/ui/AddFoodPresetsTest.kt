package com.example.kthealthsync.wear.presentation.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddFoodPresetsTest {

    @Test
    fun testPresetsScale() {
        val presets = getGramPresets(100)

        // Below 10 by 1 down to 1
        for (i in 1..10) {
            assertTrue("Should contain $i", presets.contains(i))
        }

        // 10 to 50 by 10
        listOf(10, 20, 30, 40, 50).forEach {
            assertTrue("Should contain $it", presets.contains(it))
        }

        // 50 to 500 by 50
        listOf(50, 100, 150, 200, 250, 300, 350, 400, 450, 500).forEach {
            assertTrue("Should contain $it", presets.contains(it))
        }

        // 500 to 2000 by 100
        listOf(500, 600, 700, 800, 900, 1000, 1500, 2000).forEach {
            assertTrue("Should contain $it", presets.contains(it))
        }

        // Omega 3 (2g) and Water (900ml) cases
        assertTrue("Should contain 2g for omega 3", presets.contains(2))
        assertTrue("Should contain 900ml for workout water", presets.contains(900))
    }

    @Test
    fun testDynamicOffsetsAroundSelected() {
        val presets = getGramPresets(120)
        assertTrue("Should contain selected 120", presets.contains(120))
        assertTrue("Should contain 120 - 50 = 70", presets.contains(70))
        assertTrue("Should contain 120 + 50 = 170", presets.contains(170))
    }

    @Test
    fun testCalculateFineStep() {
        assertEquals(1, calculateFineStep(1))
        assertEquals(1, calculateFineStep(2))
        assertEquals(1, calculateFineStep(9))
        assertEquals(5, calculateFineStep(10))
        assertEquals(5, calculateFineStep(49))
        assertEquals(10, calculateFineStep(50))
        assertEquals(10, calculateFineStep(100))
        assertEquals(10, calculateFineStep(900))
    }
}
