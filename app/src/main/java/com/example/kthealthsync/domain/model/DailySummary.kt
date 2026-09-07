package com.example.kthealthsync.domain.model

data class DailySummary(
    val kcal: Double,
    val protein: Double,
    val fat: Double,
    val carb: Double,
    val sugar: Double,
    val salt: Double,
    val water: Double,
    val fiber: Double,
    val date: String,
    val kcalBreakdown: List<FoodBreakdown> = emptyList(),
    val proteinBreakdown: List<FoodBreakdown> = emptyList(),
    val fatBreakdown: List<FoodBreakdown> = emptyList(),
    val carbBreakdown: List<FoodBreakdown> = emptyList(),
    val sugarBreakdown: List<FoodBreakdown> = emptyList(),
    val saltBreakdown: List<FoodBreakdown> = emptyList(),
    val waterBreakdown: List<FoodBreakdown> = emptyList(),
    val fiberBreakdown: List<FoodBreakdown> = emptyList(),
    val vegGrams: Double = 0.0,
    val fruitGrams: Double = 0.0,
    val vegBreakdown: List<FoodBreakdown> = emptyList(),
    val fruitBreakdown: List<FoodBreakdown> = emptyList(),
    val vegFruitBreakdown: List<FoodBreakdown> = emptyList(),
    val supplementCount: Int = 0,
    val supplementBreakdown: List<FoodBreakdown> = emptyList()
)
