package com.example.kthealthsync

import com.example.kthealthsync.domain.util.OpenFoodFactsHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.experimental.categories.Category

@Category(LiveNetworkTest::class)
class OpenFoodFactsLiveTest {
    @Test
    fun lookupKnownAndUnknownBarcode() = runBlocking {
        val nutella = OpenFoodFactsHelper.lookupProductName("8000500179864")
        assertNotNull(nutella)
        assertTrue(nutella!!.contains("Nutella", ignoreCase = true))
        assertNull(OpenFoodFactsHelper.lookupProductName("0000000000000"))
    }
}
