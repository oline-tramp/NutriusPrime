package com.example.kthealthsync

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
    val foodstuff: List<Foodstuff>?
)

data class Foodstuff(
    val title: String?,
    val name: String?,
    val nazev: String?,
    val energy: String?,
    val protein: String?,
    val carbohydrate: String?,
    val fat: String?,
    val sugar: String?,
    val salt: String?,
    val water: String?,
    val fiber: String?,
    val foodstuff: List<Foodstuff>?,
    val items: List<Foodstuff>?,
    val ingredients: List<Foodstuff>?,
    val components: List<Foodstuff>?,
    val guid: String?,
    val guidRecipe: String?,
    val isRecipe: Boolean?
)

data class MealListResponse(
    val data: List<MealListItem>?
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

interface KalorickeApiService {
    @GET("user/diary/{date}/get?format=json")
    suspend fun getDiary(
        @Path("date") date: String,
        @Header("Cookie") cookie: String,
        @Header("User-Agent") userAgent: String = "Mozilla/5.0 (Linux; Android 14; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        @Header("Accept") accept: String = "application/json, text/plain, */*",
        @Header("Accept-Language") acceptLanguage: String = "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7",
        @Header("Accept-Encoding") acceptEncoding: String = "identity"
    ): DiaryResponse

    @POST("login/create?format=json")
    suspend fun login(
        @retrofit2.http.Body request: LoginRequest,
        @Header("User-Agent") userAgent: String = "Mozilla/5.0 (Linux; Android 14; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        @Header("Accept") accept: String = "application/json, text/plain, */*"
    ): retrofit2.Response<LoginResponse>

    @GET("user/settings/meal/list?format=json&page=0&limit=100")
    suspend fun getMealList(
        @Header("Cookie") cookie: String,
        @Header("User-Agent") userAgent: String = "Mozilla/5.0 (Linux; Android 14; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        @Header("Accept") accept: String = "application/json, text/plain, */*"
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
        @Header("Cookie") cookie: String,
        @Header("User-Agent") userAgent: String = "Mozilla/5.0",
        @Header("Accept") accept: String = "application/json"
    ): List<AutocompleteResponseItem>

    @GET("autocomplete/favorites?format=json")
    suspend fun getFavoriteFoodstuff(
        @Header("Cookie") cookie: String,
        @Header("User-Agent") userAgent: String = "Mozilla/5.0",
        @Header("Accept") accept: String = "application/json"
    ): List<AutocompleteResponseItem>

    @GET("autocomplete/meals?format=json")
    suspend fun getMyMeals(
        @Header("Cookie") cookie: String,
        @Header("User-Agent") userAgent: String = "Mozilla/5.0",
        @Header("Accept") accept: String = "application/json"
    ): List<AutocompleteResponseItem>

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
        @Header("Cookie") cookie: String,
        @Header("User-Agent") userAgent: String = "Mozilla/5.0 (Linux; Android 14; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        @Header("Accept") accept: String = "application/json, text/plain, */*"
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
}

suspend fun KalorickeApiService.getDiaryWithAutoLogin(
    date: String,
    prefs: PreferencesManager
): DiaryResponse {
    var cookie = prefs.cookieFlow.first()
    if (cookie.isBlank()) throw Exception("No cookie available")
    
    try {
        return getDiary(date, cookie)
    } catch (e: Exception) {
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
                            return getDiary(date, cookie)
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
        throw e
    }
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
