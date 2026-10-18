package com.example.kthealthsync

import retrofit2.http.Query
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url
import okhttp3.ResponseBody
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Body
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.Cache
import java.io.File
import okhttp3.OkHttpClient
import kotlinx.coroutines.flow.first

data class LoginRequest(val email: String, val password: String)
data class LoginResponse(val code: Int?, val message: String?)

data class DiaryResponse(
    val data: DiaryData?
)

data class DiaryData(
    val times: List<MealTime>?
)

data class MealTime(
    val title: String? = null,
    val name: String? = null,
    val id: String? = null,
    val foodstuff: List<Foodstuff>? = null
)

data class Foodstuff(
    val title: String? = null,
    val name: String? = null,
    val nazev: String? = null,
    val energy: String? = null,
    val protein: String? = null,
    val carbohydrate: String? = null,
    val fat: String? = null,
    val sugar: String? = null,
    val salt: String? = null,
    val water: String? = null,
    val fiber: String? = null,
    val foodstuff: List<Foodstuff>? = null,
    val items: List<Foodstuff>? = null,
    val ingredients: List<Foodstuff>? = null,
    val components: List<Foodstuff>? = null,
    val guid: String? = null,
    val guidRecipe: String? = null,
    val guidUserMeal: String? = null,
    val editableMeal: Boolean? = null,
    val isRecipe: Boolean? = null,
    val unit: String? = null,
    val id: String? = null,
    val guidType: String? = null,
    val type: String? = null,
    val url: String? = null
)

data class MealListResponse(
    val data: List<MealListItem>?,
    val count: Int? = null
)

data class MealListItem(
    val guid: String?,
    val title: String?,
    val energy: String?
)

data class RecipeDetailResponse(
    val data: RecipeData?
)

data class RecipeData(
    val id: String?,
    val title: String?,
    val energy: String?,
    val content: List<RecipeContentItem>?
)

data class RecipeContentItem(
    val foodstuff: Foodstuff?,
    val unit: String?
)

data class AutocompleteResponseItem(
    val id: String?,
    val title: String?,
    val url: String?,
    val unit: String?,
    val isLiquid: Boolean?,
    val value: String?,
    val favorite: Boolean?,
    val hasImage: Boolean?,
    val clazz: String?
)

data class UnitOption(
    val id: String,
    val title: String,
    val multiplier: Double
)

data class FoodFormUnitOption(
    val id: String?,
    val title: String?,
    val multiplier: Double?
)

data class FoodFormData(
    val guid: String?,
    val title: String?,
    val url: String?,
    val unitGuid: String?,
    val multiplier: Double?,
    val unitOptions: List<FoodFormUnitOption>?
)

data class FoodFormResponse(
    val code: Int?,
    val message: String?,
    val data: FoodFormData?
)

data class PersonalMealFormResponse(val code: Int?, val message: String?, val data: com.google.gson.JsonObject?)

data class AddFoodRequest(
    val guid: String,
    val title: String,
    val url: String,
    val date: String,
    val multiplier: String,
    val diaryTimeGuid: String,
    val unitGuid: String = "0000000000000001",
    val unitOptions: List<UnitOption> = listOf(
        UnitOption("0000000000000001", "1 g", 1.0)
    )
)

data class AddFoodResponse(
    val code: Int?,
    val message: String?
)

data class DiaryActionResponse(
    val code: Int?,
    val message: String?
)

data class OwnNutrientItem(
    val code: String?,
    val title: String?,
    val value: Double?,
    val computedValue: String?,
    val active: Boolean?
)

data class UserProfileFormData(
    val ownTargetEnergy: Double?,
    val computedTargetEnergy: String?,
    val drinkRegime: Double?,
    val ownNutrients: List<OwnNutrientItem>?
)

data class UserProfileFormResponse(
    val code: Int?,
    val message: String?,
    val data: UserProfileFormData?
)

interface KalorickeApiService {
    @GET("user/diary/{date}/get?format=json")
    suspend fun getDiary(
        @Path("date") date: String,
        @Header("Cookie") cookie: String,
        @Header("User-Agent") userAgent: String = "Mozilla/5.0 (Linux; Android 14; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        @Header("Accept") accept: String = "application/json, text/plain, */*",
        @Header("Accept-Language") acceptLanguage: String? = null,
        @Header("Accept-Encoding") acceptEncoding: String = "identity"
    ): DiaryResponse

    @POST("login/create?format=json")
    suspend fun login(
        @retrofit2.http.Body request: LoginRequest,
        @Header("User-Agent") userAgent: String = "Mozilla/5.0 (Linux; Android 14; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        @Header("Accept") accept: String = "application/json, text/plain, */*"
    ): retrofit2.Response<LoginResponse>

    @GET("user/settings/meal/list?format=json")
    suspend fun getMealList(
        @Header("Cookie") cookie: String,
        @Header("User-Agent") userAgent: String = "Mozilla/5.0 (Linux; Android 14; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        @Header("Accept") accept: String = "application/json, text/plain, */*",
        @Query("page") page: Int = 0,
        @Query("limit") limit: Int = 100
    ): MealListResponse

    @GET("recipe/detail/{guid}?format=json&unit=null&multiplier=null")
    suspend fun getRecipeDetail(
        @Path("guid") guid: String,
        @Header("Cookie") cookie: String,
        @Header("User-Agent") userAgent: String = "Mozilla/5.0 (Linux; Android 14; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        @Header("Accept") accept: String = "application/json, text/plain, */*"
    ): RecipeDetailResponse

    @GET("autocomplete/frequent-foodstuff?format=json")
    suspend fun getFrequentFoodstuff(
        @retrofit2.http.Query("page") page: Int = 0,
        @retrofit2.http.Query("limit") limit: Int = 10,
        @Header("Cookie") cookie: String,
        @Header("User-Agent") userAgent: String = "Mozilla/5.0",
        @Header("Accept") accept: String = "application/json"
    ): List<AutocompleteResponseItem>

    @GET("autocomplete/favorites?format=json")
    suspend fun getFavoriteFoodstuff(
        @retrofit2.http.Query("page") page: Int = 0,
        @retrofit2.http.Query("limit") limit: Int = 10,
        @Header("Cookie") cookie: String,
        @Header("User-Agent") userAgent: String = "Mozilla/5.0",
        @Header("Accept") accept: String = "application/json"
    ): List<AutocompleteResponseItem>

    @GET("autocomplete/meals?format=json")
    suspend fun getMyMeals(
        @retrofit2.http.Query("page") page: Int = 0,
        @retrofit2.http.Query("limit") limit: Int = 10,
        @Header("Cookie") cookie: String,
        @Header("User-Agent") userAgent: String = "Mozilla/5.0",
        @Header("Accept") accept: String = "application/json"
    ): List<AutocompleteResponseItem>

    @GET("user/meal/add/form/{guid}?format=json")
    suspend fun getPersonalMealForm(@Path("guid") guid: String, @Header("Cookie") cookie: String): PersonalMealFormResponse

    @POST("user/meal/add?format=json")
    suspend fun addPersonalMeal(@Body request: com.google.gson.JsonObject, @Header("Cookie") cookie: String): AddFoodResponse

    @GET("user/foodstuff/add/form/{guid}?format=json")
    suspend fun getFoodForm(
        @retrofit2.http.Path("guid") guid: String,
        @Header("Cookie") cookie: String,
        @Header("User-Agent") userAgent: String = "Mozilla/5.0",
        @Header("Accept") accept: String = "application/json"
    ): FoodFormResponse

    @GET("autocomplete/foodstuff-activity-meal?format=json")
    suspend fun searchFood(
        @retrofit2.http.Query("query") query: String,
        @retrofit2.http.Query("page") page: Int = 0,
        @Header("Cookie") cookie: String,
        @Header("User-Agent") userAgent: String = "Mozilla/5.0 (Linux; Android 14; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        @Header("Accept") accept: String = "application/json, text/plain, */*",
        @Header("Accept-Language") language: String? = null
    ): List<AutocompleteResponseItem>

    @POST("user/foodstuff/add?format=json")
    suspend fun addFoodstuff(
        @Body request: AddFoodRequest,
        @Header("Cookie") cookie: String,
        @Header("User-Agent") userAgent: String = "Mozilla/5.0 (Linux; Android 14; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        @Header("Accept") accept: String = "application/json, text/plain, */*"
    ): AddFoodResponse

    @GET
    suspend fun getFoodHtml(
        @Url url: String,
        @Header("Cookie") cookie: String,
        @Header("User-Agent") userAgent: String = "Mozilla/5.0"
    ): ResponseBody

    @GET("user/settings/profile/form?format=json")
    suspend fun getUserProfileForm(
        @Header("Cookie") cookie: String,
        @Header("User-Agent") userAgent: String = "Mozilla/5.0 (Linux; Android 14; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        @Header("Accept") accept: String = "application/json, text/plain, */*",
        @Header("Accept-Encoding") acceptEncoding: String = "identity"
    ): UserProfileFormResponse

    @GET("user/diary/foodstuff/delete/{ids}?format=json")
    suspend fun deleteFoodstuffDiary(
        @Path("ids") ids: String,
        @Header("Cookie") cookie: String,
        @Header("User-Agent") userAgent: String = "Mozilla/5.0 (Linux; Android 14; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        @Header("Accept") accept: String = "application/json, text/plain, */*"
    ): DiaryActionResponse

    @GET("user/diary/{id}/{timeId}/{date}/dragndrop?format=json")
    suspend fun moveFoodstuffDiary(
        @Path("id") id: String,
        @Path("timeId") timeId: String,
        @Path("date") date: String,
        @Header("Cookie") cookie: String,
        @Header("User-Agent") userAgent: String = "Mozilla/5.0 (Linux; Android 14; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        @Header("Accept") accept: String = "application/json, text/plain, */*"
    ): DiaryActionResponse
}

suspend fun KalorickeApiService.getDiaryWithAutoLogin(
    date: String,
    prefs: PreferencesManager,
    maxRetries: Int = 3
): DiaryResponse {
    var cookie = prefs.cookieFlow.first()
    if (cookie.isBlank()) throw Exception("No cookie available")
    
    var lastEx: Exception? = null
    for (attempt in 1..maxRetries) {
        try {
            return getDiary(date, cookie)
        } catch (e: Exception) {
            lastEx = e
            val code = (e as? retrofit2.HttpException)?.code()
            val errorMsg = e.message ?: ""
            val isAuthError = code == 302 || code == 401 || errorMsg.contains("302") || errorMsg.contains("401")
            
            if (isAuthError) {
                val email = prefs.emailFlow.first()
                val passHash = prefs.passwordHashFlow.first()
                DebugLogger.addActionLog("Session expired (302/401). Saved creds: email=${email.isNotEmpty()}, hash=${passHash.isNotEmpty()}")
                
                if (email.isNotEmpty() && passHash.isNotEmpty()) {
                    DebugLogger.addActionLog("Attempting auto-login...")
                    try {
                        val loginResp = login(LoginRequest(email, passHash))
                        if (loginResp.isSuccessful && loginResp.body()?.code == 0) {
                            val jsessionCookie = loginResp.headers().values("Set-Cookie").firstOrNull { it.contains("JSESSIONID") }
                            if (jsessionCookie != null) {
                                cookie = jsessionCookie.substringBefore(";")
                                prefs.saveCookie(cookie)
                                DebugLogger.addActionLog("Auto-login successful, retrying fetch...")
                                continue
                            }
                        } else {
                            DebugLogger.addActionLog("Auto-login failed. Server returned code ${loginResp.code()}")
                        }
                    } catch (loginEx: Exception) {
                        DebugLogger.addActionLog("Auto-login request failed: ${loginEx.message}")
                    }
                } else {
                    DebugLogger.addActionLog("Cannot auto-login: No credentials saved.")
                }
            } else {
                DebugLogger.addActionLog("Fetch error (not 302): $errorMsg")
            }
            if (attempt < maxRetries) {
                kotlinx.coroutines.delay(attempt * 350L)
            }
        }
    }
    throw lastEx ?: Exception("Failed to fetch diary after $maxRetries attempts")
}

object RetrofitClient {
    private const val BASE_URL = "https://www.kaloricketabulky.cz/"

    val instance: KalorickeApiService by lazy {
        val client = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor(LoggingInterceptor())
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(KalorickeApiService::class.java)
    }
}

object MacroCache {
    private val cache = mutableMapOf<String, Map<String, Double>>()
    
    fun get(url: String) = cache[url]
    fun put(url: String, data: Map<String, Double>) {
        cache[url] = data
    }
}
