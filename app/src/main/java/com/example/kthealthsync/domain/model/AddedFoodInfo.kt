package com.example.kthealthsync.domain.model

data class AddedFoodInfo(
    val title: String,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double,
    val sugar: Double,
    val salt: Double,
    val unitStr: String,
    val mealTimeId: String?
)
