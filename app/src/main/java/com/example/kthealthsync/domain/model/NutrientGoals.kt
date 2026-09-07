package com.example.kthealthsync.domain.model

data class NutrientGoals(
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    val fiber: Double = 0.0,
    val sugar: Double = 0.0,
    val salt: Double = 0.0,
    val vegFruit: Double = 400.0,
    val water: Double = 0.0
)
