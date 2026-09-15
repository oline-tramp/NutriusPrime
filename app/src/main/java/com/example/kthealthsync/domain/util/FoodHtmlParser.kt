package com.example.kthealthsync.domain.util

import com.example.kthealthsync.domain.model.FoodCategory

object FoodHtmlParser {

    /**
     * Safely extracts nutritional keywords from HTML containing <script type="application/ld+json">.
     * Uses Gson for pure Java portability (doesn't depend on Android stub classes in JVM tests).
     */
    fun parseLdJsonMacros(html: String): Map<String, Double> {
        val startTag = "<script type=\"application/ld+json\">"
        val endTag = "</script>"
        val startIndex = html.indexOf(startTag)
        if (startIndex == -1) return emptyMap()

        val jsonStart = startIndex + startTag.length
        val endIndex = html.indexOf(endTag, jsonStart)
        if (endIndex == -1) return emptyMap()

        val jsonStr = html.substring(jsonStart, endIndex).trim()
        val result = mutableMapOf<String, Double>()
        try {
            val jsonObj = com.google.gson.Gson().fromJson(jsonStr, com.google.gson.JsonObject::class.java)
            if (jsonObj != null && jsonObj.has("keywords") && jsonObj.get("keywords").isJsonArray) {
                val keywordsArr = jsonObj.getAsJsonArray("keywords")
                for (i in 0 until keywordsArr.size()) {
                    val kw = keywordsArr.get(i).asString
                    val parts = kw.split(":").map { s -> s.trim() }
                    if (parts.size >= 2) {
                        val key = parts[0].lowercase()
                        val rawVal = parts[1].replace(",", ".").replace("g", "").replace("mg", "").trim()
                        val num = rawVal.toDoubleOrNull() ?: 0.0
                        result[key] = num
                    }
                }
            }
        } catch (_: Exception) {
            // Return whatever was successfully parsed
        }
        return result
    }

    /**
     * Extracts food category from official Kalorické Tabulky breadcrumb links.
     * Breadcrumbs follow patterns like:
     * - href="/zelenina" or href="/zelenina/zelenina-cerstva"
     * - href="/ovoce" or href="/ovoce/ovoce-cerstve"
     *
     * Starchy tubers like potatoes (/zelenina/brambory-a-topinambury or /jidla-a-prilohy/prilohy-brambory*)
     * are excluded from the WHO 400g/day fruit & vegetable recommendation.
     */
    fun parseCategoryFromHtml(html: String, foodTitle: String? = null): FoodCategory {
        val crumbRegex = "class=\"crumb-link\"\\s+href=\"(/[^\"]+)\"".toRegex()
        val allHrefs = crumbRegex.findAll(html).map { it.groupValues[1].lowercase() }.toList()

        // If any crumb marks it as potatoes/topinambur, exclude from 400g quota (starchy tubers per WHO)
        if (allHrefs.any { it.contains("brambor") || it.contains("topinambur") }) {
            return FoodCategory.OTHER
        }

        for (href in allHrefs) {
            if (href.startsWith("/zelenina")) {
                return FoodCategory.VEGETABLE
            }
            if (href.startsWith("/ovoce")) {
                return FoodCategory.FRUIT
            }
            if (href.startsWith("/doplnky-vyzivy")) {
                val titleLower = (foodTitle ?: "").lowercase()
                if (isProteinPowderOrBar(titleLower)) {
                    return FoodCategory.OTHER
                }
                return FoodCategory.SUPPLEMENT
            }
        }
        return FoodCategory.OTHER
    }

    fun isProteinPowderOrBar(titleLower: String): Boolean {
        if (titleLower.contains("protein") ||
            titleLower.contains("isolate") ||
            titleLower.contains("izolát") ||
            titleLower.contains("izolat") ||
            titleLower.contains("whey") ||
            titleLower.contains("casein") ||
            titleLower.contains("kasein") ||
            titleLower.contains("gainer") ||
            titleLower.contains("syrovátkový") ||
            titleLower.contains("syrovatkovy")
        ) {
            return true
        }
        return false
    }
}
