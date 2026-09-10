package com.example.kthealthsync.domain.model

import com.example.kthealthsync.Foodstuff

data class FoodBreakdown(
    val name: String,
    val amount: Double,
    val components: List<FoodBreakdown> = emptyList(),
    val originalFood: Foodstuff? = null
)
