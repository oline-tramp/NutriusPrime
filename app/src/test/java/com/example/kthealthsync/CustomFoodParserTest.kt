package com.example.kthealthsync

import com.example.kthealthsync.data.nano.CustomFoodParsed
import com.example.kthealthsync.data.nano.parseCustomFoodJson
import org.junit.Assert.*
import org.junit.Test

class CustomFoodParserTest {

    @Test
    fun parseBareJson() {
        val json = """{"title":"Протеиновый батончик","energy":210.0,"protein":20.0,"carbs":15.0,"fat":5.0,"fiber":10.0,"sugar":2.0,"satFat":1.5,"salt":0.2}"""
        val result = parseCustomFoodJson(json)
        assertNotNull(result)
        assertEquals("Протеиновый батончик", result?.title)
        assertEquals(210.0, result?.energy ?: 0.0, 1e-4)
        assertEquals(20.0, result?.protein ?: 0.0, 1e-4)
        assertEquals(15.0, result?.carbs ?: 0.0, 1e-4)
        assertEquals(5.0, result?.fat ?: 0.0, 1e-4)
        assertEquals(10.0, result?.fiber ?: 0.0, 1e-4)
        assertEquals(2.0, result?.sugar ?: 0.0, 1e-4)
        assertEquals(1.5, result?.satFat ?: 0.0, 1e-4)
        assertEquals(0.2, result?.salt ?: 0.0, 1e-4)
    }

    @Test
    fun parseMarkdownJsonFenced() {
        val markdown = """
            Here is the result:
            ```json
            {
              "title": "Protein Bar",
              "energy": 200,
              "protein": 22.5,
              "carbs": 8,
              "fat": 6.2
            }
            ```
        """.trimIndent()
        val result = parseCustomFoodJson(markdown)
        assertNotNull(result)
        assertEquals("Protein Bar", result?.title)
        assertEquals(200.0, result?.energy ?: 0.0, 1e-4)
        assertEquals(22.5, result?.protein ?: 0.0, 1e-4)
        assertEquals(8.0, result?.carbs ?: 0.0, 1e-4)
        assertEquals(6.2, result?.fat ?: 0.0, 1e-4)
        assertNull(result?.fiber)
    }

    @Test
    fun parseInvalidReturnsNull() {
        assertNull(parseCustomFoodJson(""))
        assertNull(parseCustomFoodJson("   "))
        assertNull(parseCustomFoodJson("No json here!"))
        assertNull(parseCustomFoodJson("{not valid json"))
    }
}
