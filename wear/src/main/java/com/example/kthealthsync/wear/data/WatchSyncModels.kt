package com.example.kthealthsync.wear.data

data class WearFoodItem(
    val id: String,
    val title: String,
    val energy: String? = null,
    val protein: String? = null,
    val carbohydrate: String? = null,
    val fat: String? = null,
    val unit: String? = null,
    val multiplier: Double = 1.0,
    val url: String? = null
)

data class WearAddFoodRequest(
    val guid: String,
    val title: String,
    val multiplier: String,
    val diaryTimeGuid: String,
    val date: String,
    val unitGuid: String = "0000000000000001",
    val url: String = ""
)

data class WearAddFoodResult(
    val success: Boolean,
    val message: String? = null
)

object WearSyncConstants {
    const val PATH_SYNC_DATA = "/nutrius/sync"
    const val PATH_ADD_FOOD = "/nutrius/add_food"
    const val PATH_ADD_FOOD_RESULT = "/nutrius/add_food_result"
    const val PATH_SEARCH = "/nutrius/search"
    const val PATH_SEARCH_RESULT = "/nutrius/search_result"
    const val PATH_REQUEST_SYNC = "/nutrius/request_sync"

    const val KEY_COOKIE = "cookie"
    const val KEY_EMAIL = "email"
    const val KEY_PASSWORD_HASH = "password_hash"
    const val KEY_FAVORITES_JSON = "favorites_json"
    const val KEY_TIMESTAMP = "timestamp"
}
