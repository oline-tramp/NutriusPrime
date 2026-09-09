package com.example.kthealthsync.domain.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.JsonParser
import java.util.concurrent.TimeUnit

object OpenFoodFactsHelper {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun lookupProductName(barcode: String): String? = withContext(Dispatchers.IO) {
        try {
            val cleanBarcode = barcode.trim()
            val request = Request.Builder()
                .url("https://world.openfoodfacts.org/api/v2/product/$cleanBarcode.json")
                .header("User-Agent", "NutriusPrime - Android")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val json = com.google.gson.Gson().fromJson(body, com.google.gson.JsonObject::class.java) ?: return@withContext null
                val status = json.get("status")?.asInt ?: -1
                if (status != 1) return@withContext null

                val product = json.getAsJsonObject("product") ?: return@withContext null

                fun getSafeString(key: String): String? {
                    val el = product.get(key) ?: return null
                    return if (el.isJsonPrimitive) el.asString.ifBlank { null } else null
                }

                // Prioritize Czech and Russian product names, then default/English
                val name = getSafeString("product_name_cs")
                    ?: getSafeString("product_name_ru")
                    ?: getSafeString("product_name")
                    ?: getSafeString("product_name_en")

                val brand = getSafeString("brands")

                when {
                    !name.isNullOrBlank() && !brand.isNullOrBlank() && !name.contains(brand, ignoreCase = true) -> "$name $brand"
                    !name.isNullOrBlank() -> name
                    else -> null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
