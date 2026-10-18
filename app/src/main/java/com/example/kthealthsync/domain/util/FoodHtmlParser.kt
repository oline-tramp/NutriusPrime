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

    private val CRUMB_REGEX = "class=\"crumb-link\"\\s+href=\"(/[^\"]+)\"".toRegex()

    /**
     * Extracts food category directly from Kalorické Tabulky category URL slug.
     * Examples:
     * - "zelenina/mrkev" -> VEGETABLE
     * - "ovoce/jablko" -> FRUIT
     * - "napoje/voda-neperliva" -> BEVERAGE
     * - "doplnky-stravy/omega-3" -> SUPPLEMENT
     * - "potraviny/brambory" -> OTHER (tuber)
     */
    fun parseCategoryFromUrl(url: String?, foodTitle: String? = null): FoodCategory? {
        val cleanUrl = (url ?: "").trim().trimStart('/')
        if (cleanUrl.isBlank()) return null
        val lowerUrl = cleanUrl.lowercase()

        // 1. Tubers exclusion (potatoes, topinambur) per WHO guidelines
        if (lowerUrl.contains("brambor") || lowerUrl.contains("topinambur")) {
            return FoodCategory.OTHER
        }

        // 2. Strict non-produce sections
        if (lowerUrl.startsWith("mlecne-vyrobky") || lowerUrl.startsWith("zmrzlin") ||
            lowerUrl.startsWith("pecivo") || lowerUrl.startsWith("cukrovinky") ||
            lowerUrl.startsWith("maso") || lowerUrl.startsWith("sladkosti") ||
            lowerUrl.startsWith("jidla-a-prilohy")
        ) {
            return FoodCategory.OTHER
        }

        // 3. Positive official KT categories
        if (lowerUrl.startsWith("zelenina")) {
            return FoodCategory.VEGETABLE
        }
        if (lowerUrl.startsWith("ovoce")) {
            return FoodCategory.FRUIT
        }
        if (lowerUrl.contains("napoj")) {
            return FoodCategory.BEVERAGE
        }
        if (lowerUrl.startsWith("doplnky-vyzivy") || lowerUrl.startsWith("doplnky-stravy")) {
            val titleLower = (foodTitle ?: "").lowercase()
            if (isProteinPowderOrBar(titleLower)) {
                return FoodCategory.OTHER
            }
            return FoodCategory.SUPPLEMENT
        }

        return null
    }

    /**
     * Extracts food category from official Kalorické Tabulky breadcrumb links.
     * Breadcrumbs follow patterns like:
     * - href="/zelenina" or href="/zelenina/zelenina-cerstva"
     * - href="/ovoce" or href="/ovoce/ovoce-cerstve"
     * - href="/napoje"
     * - href="/doplnky-vyzivy"
     */
    fun parseCategoryFromHtml(html: String, foodTitle: String? = null): FoodCategory {
        val allHrefs = if (html.isNotBlank()) {
            CRUMB_REGEX.findAll(html).map { it.groupValues[1].lowercase() }.toList()
        } else emptyList()

        // 1. Tubers exclusion (potatoes, topinambur) per WHO guidelines across any breadcrumb
        if (allHrefs.any { it.contains("brambor") || it.contains("topinambur") }) {
            return FoodCategory.OTHER
        }

        // 2. Strict non-produce sections across any breadcrumb
        if (allHrefs.any { href ->
            href.startsWith("/mlecne-vyrobky") || href.startsWith("/zmrzlin") ||
            href.startsWith("/pecivo") || href.startsWith("/cukrovinky") ||
            href.startsWith("/maso") || href.startsWith("/sladkosti") ||
            href.startsWith("/jidla-a-prilohy")
        }) {
            return FoodCategory.OTHER
        }

        for (href in allHrefs) {
            val cat = parseCategoryFromUrl(href, foodTitle)
            if (cat != null) return cat
        }

        return FoodCategory.OTHER
    }

    fun isProteinPowderOrBar(titleLower: String): Boolean {
        return titleLower.contains("protein") ||
                titleLower.contains("isolate") ||
                titleLower.contains("izolát") ||
                titleLower.contains("izolat") ||
                titleLower.contains("whey") ||
                titleLower.contains("casein") ||
                titleLower.contains("kasein") ||
                titleLower.contains("gainer") ||
                titleLower.contains("syrovátkový") ||
                titleLower.contains("syrovatkovy")
    }
}
