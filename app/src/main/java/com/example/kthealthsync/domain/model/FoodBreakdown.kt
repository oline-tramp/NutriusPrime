package com.example.kthealthsync.domain.model

data class FoodBreakdown(
    val name: String,
    val amount: Double,
    val components: List<FoodBreakdown> = emptyList()
)
