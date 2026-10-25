package com.example.kthealthsync.wear.data

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

private const val TAG = "WatchDataManager"
private const val PREFS_NAME = "nutrius_wear_prefs"
private const val PREF_COOKIE = "cookie"
private const val PREF_EMAIL = "email"
private const val PREF_PASSWORD_HASH = "password_hash"
private const val PREF_FAVORITES = "favorites_json"

const val KT_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
const val KT_ACCEPT = "application/json, text/plain, */*"

data class DirectUnitOption(
    val id: String = "0000000000000001",
    val title: String = "1 g",
    val multiplier: Double = 1.0
)

data class DirectAddFoodRequest(
    val guid: String,
    val title: String,
    val url: String,
    val date: String,
    val multiplier: String,
    val diaryTimeGuid: String,
    val unitGuid: String = "0000000000000001",
    val unitOptions: List<DirectUnitOption> = listOf(DirectUnitOption())
)

data class DirectLoginRequest(val email: String, val password: String)
data class DirectLoginResponse(val code: Int?, val message: String?)

data class DirectSearchItem(
    val id: String?,
    val title: String?,
    val value: String?,
    val unit: String?,
    val url: String?
)

data class DirectAddResponse(
    val code: Int?,
    val message: String?
)

data class DirectFoodFormUnitOption(
    val id: String?,
    val title: String?,
    val multiplier: Double?
)

data class DirectFoodFormData(
    val title: String?,
    val url: String?,
    val unitGuid: String?,
    val multiplier: Double?,
    val unitOptions: List<DirectFoodFormUnitOption>?
)

data class DirectFoodFormResponse(
    val code: Int?,
    val message: String?,
    val data: DirectFoodFormData?
)

interface DirectKtApi {
    @GET("autocomplete/foodstuff-activity-meal?format=json")
    suspend fun searchFood(
        @Query("query") query: String,
        @Query("page") page: Int = 0,
        @Header("Cookie") cookie: String,
        @Header("User-Agent") userAgent: String = KT_USER_AGENT,
        @Header("Accept") accept: String = KT_ACCEPT
    ): List<DirectSearchItem>

    @GET("user/foodstuff/add/form/{guid}?format=json")
    suspend fun getFoodForm(
        @retrofit2.http.Path("guid") guid: String,
        @Header("Cookie") cookie: String,
        @Header("User-Agent") userAgent: String = KT_USER_AGENT,
        @Header("Accept") accept: String = KT_ACCEPT
    ): DirectFoodFormResponse

    @POST("user/foodstuff/add?format=json")
    suspend fun addFoodstuff(
        @Body request: DirectAddFoodRequest,
        @Header("Cookie") cookie: String,
        @Header("User-Agent") userAgent: String = KT_USER_AGENT,
        @Header("Accept") accept: String = KT_ACCEPT
    ): DirectAddResponse

    @POST("login/create?format=json")
    suspend fun login(
        @Body request: DirectLoginRequest,
        @Header("User-Agent") userAgent: String = KT_USER_AGENT,
        @Header("Accept") accept: String = KT_ACCEPT
    ): Response<DirectLoginResponse>
}

class WatchDataManager(private val context: Context) :
    DataClient.OnDataChangedListener,
    MessageClient.OnMessageReceivedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val dataClient by lazy { Wearable.getDataClient(context) }
    private val messageClient by lazy { Wearable.getMessageClient(context) }
    private val nodeClient by lazy { Wearable.getNodeClient(context) }

    private val _cookie = MutableStateFlow(prefs.getString(PREF_COOKIE, "") ?: "")
    val cookie: StateFlow<String> = _cookie.asStateFlow()

    private val _favoriteFoods = MutableStateFlow<List<WearFoodItem>>(loadSavedFavorites())
    val favoriteFoods: StateFlow<List<WearFoodItem>> = _favoriteFoods.asStateFlow()

    private val _isPhoneConnected = MutableStateFlow(false)
    val isPhoneConnected: StateFlow<Boolean> = _isPhoneConnected.asStateFlow()

    private var pendingAddResult: CompletableDeferred<WearAddFoodResult>? = null
    private var pendingSearchResult: CompletableDeferred<List<WearFoodItem>>? = null

    // Exact OkHttpClient template as main app RetrofitClient
    private val directApi: DirectKtApi by lazy {
        val client = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl("https://www.kaloricketabulky.cz/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DirectKtApi::class.java)
    }

    init {
        dataClient.addListener(this)
        messageClient.addListener(this)
        checkConnectedNodes()
    }

    fun onDestroy() {
        dataClient.removeListener(this)
        messageClient.removeListener(this)
    }

    private fun loadSavedFavorites(): List<WearFoodItem> {
        val json = prefs.getString(PREF_FAVORITES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<WearFoodItem>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun checkConnectedNodes() {
        scope.launch {
            try {
                val nodes = nodeClient.connectedNodes.await()
                _isPhoneConnected.value = nodes.isNotEmpty()
                if (nodes.isNotEmpty()) {
                    requestSyncFromPhone()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to query connected nodes: ${e.message}")
            }
        }
    }

    fun requestSyncFromPhone() {
        scope.launch {
            try {
                val nodes = nodeClient.connectedNodes.await()
                for (node in nodes) {
                    messageClient.sendMessage(node.id, WearSyncConstants.PATH_REQUEST_SYNC, ByteArray(0)).await()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Request sync failed: ${e.message}")
            }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val path = event.dataItem.uri.path
                if (path == WearSyncConstants.PATH_SYNC_DATA) {
                    val dataMapItem = DataMapItem.fromDataItem(event.dataItem)
                    val newCookie = dataMapItem.dataMap.getString(WearSyncConstants.KEY_COOKIE).orEmpty()
                    val email = dataMapItem.dataMap.getString(WearSyncConstants.KEY_EMAIL).orEmpty()
                    val passHash = dataMapItem.dataMap.getString(WearSyncConstants.KEY_PASSWORD_HASH).orEmpty()
                    val favJson = dataMapItem.dataMap.getString(WearSyncConstants.KEY_FAVORITES_JSON).orEmpty()

                    val editor = prefs.edit()
                    if (newCookie.isNotBlank()) {
                        _cookie.value = newCookie
                        editor.putString(PREF_COOKIE, newCookie)
                    }
                    if (email.isNotBlank()) {
                        editor.putString(PREF_EMAIL, email)
                    }
                    if (passHash.isNotBlank()) {
                        editor.putString(PREF_PASSWORD_HASH, passHash)
                    }

                    if (favJson.isNotBlank()) {
                        try {
                            val type = object : TypeToken<List<WearFoodItem>>() {}.type
                            val list: List<WearFoodItem> = gson.fromJson(favJson, type) ?: emptyList()
                            _favoriteFoods.value = list
                            editor.putString(PREF_FAVORITES, favJson)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing favorites JSON", e)
                        }
                    }
                    editor.apply()
                }
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            WearSyncConstants.PATH_ADD_FOOD_RESULT -> {
                try {
                    val json = String(messageEvent.data, Charsets.UTF_8)
                    val result = gson.fromJson(json, WearAddFoodResult::class.java)
                    pendingAddResult?.complete(result)
                } catch (e: Exception) {
                    pendingAddResult?.complete(WearAddFoodResult(false, e.message))
                }
            }
            WearSyncConstants.PATH_SEARCH_RESULT -> {
                try {
                    val json = String(messageEvent.data, Charsets.UTF_8)
                    val type = object : TypeToken<List<WearFoodItem>>() {}.type
                    val items: List<WearFoodItem> = gson.fromJson(json, type) ?: emptyList()
                    pendingSearchResult?.complete(items)
                } catch (e: Exception) {
                    pendingSearchResult?.complete(emptyList())
                }
            }
        }
    }

    suspend fun attemptAutoLogin(): String? {
        val email = prefs.getString(PREF_EMAIL, null) ?: return null
        val passHash = prefs.getString(PREF_PASSWORD_HASH, null) ?: return null
        if (email.isBlank() || passHash.isBlank()) {
            Log.w(TAG, "Auto-login skipped: credentials missing on watch")
            return null
        }

        return try {
            Log.i(TAG, "Attempting watch auto-login for $email...")
            val resp = directApi.login(DirectLoginRequest(email, passHash))
            if (resp.isSuccessful && resp.body()?.code == 0) {
                val jsessionCookie = resp.headers().values("Set-Cookie").firstOrNull { it.contains("JSESSIONID") }
                if (jsessionCookie != null) {
                    val newCookie = jsessionCookie.substringBefore(";")
                    _cookie.value = newCookie
                    prefs.edit().putString(PREF_COOKIE, newCookie).apply()
                    Log.i(TAG, "Watch auto-login successful, new cookie saved")
                    newCookie
                } else {
                    Log.w(TAG, "Auto-login response missing JSESSIONID cookie")
                    null
                }
            } else {
                Log.w(TAG, "Auto-login failed: HTTP ${resp.code()}, code=${resp.body()?.code}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Auto-login exception: ${e.message}", e)
            null
        }
    }

    suspend fun searchFood(query: String): List<WearFoodItem> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()

        // 1. Try via phone MessageClient first
        try {
            val nodes = nodeClient.connectedNodes.await()
            if (nodes.isNotEmpty()) {
                val deferred = CompletableDeferred<List<WearFoodItem>>()
                pendingSearchResult = deferred
                val phoneNode = nodes.first()
                messageClient.sendMessage(phoneNode.id, WearSyncConstants.PATH_SEARCH, trimmed.toByteArray(Charsets.UTF_8)).await()

                val result = withTimeoutOrNull(4000) { deferred.await() }
                if (result != null) return result
            }
        } catch (e: Exception) {
            Log.w(TAG, "Phone search failed or timed out: ${e.message}")
        }

        // 2. Direct API fallback using exact template
        var currentCookie = _cookie.value
        if (currentCookie.isNotBlank()) {
            for (attempt in 1..2) {
                try {
                    val apiResults = directApi.searchFood(
                        query = trimmed,
                        page = 0,
                        cookie = currentCookie
                    )
                    return apiResults.map {
                        WearFoodItem(
                            id = it.id ?: it.url.orEmpty(),
                            title = it.title ?: "Без названия",
                            energy = it.value,
                            unit = it.unit,
                            url = it.url
                        )
                    }
                } catch (e: Exception) {
                    val code = (e as? HttpException)?.code()
                    if ((code == 302 || code == 401) && attempt == 1) {
                        val newCookie = attemptAutoLogin()
                        if (newCookie != null) {
                            currentCookie = newCookie
                            continue
                        }
                    }
                    Log.e(TAG, "Direct search failed (HTTP $code): ${e.message}")
                    return emptyList()
                }
            }
        }

        return emptyList()
    }

    suspend fun addFood(request: WearAddFoodRequest): WearAddFoodResult {
        // 1. Try via phone MessageClient
        try {
            val nodes = nodeClient.connectedNodes.await()
            if (nodes.isNotEmpty()) {
                val deferred = CompletableDeferred<WearAddFoodResult>()
                pendingAddResult = deferred
                val phoneNode = nodes.first()
                val json = gson.toJson(request).toByteArray(Charsets.UTF_8)
                messageClient.sendMessage(phoneNode.id, WearSyncConstants.PATH_ADD_FOOD, json).await()

                val result = withTimeoutOrNull(6000) { deferred.await() }
                if (result != null) return result
            }
        } catch (e: Exception) {
            Log.w(TAG, "Phone addFood failed or timed out: ${e.message}")
        }

        // 2. Direct API fallback matching main app AddFoodRequest template
        var currentCookie = _cookie.value
        if (currentCookie.isNotBlank()) {
            val directReq = DirectAddFoodRequest(
                guid = request.guid,
                title = request.title,
                url = request.url,
                date = request.date,
                multiplier = request.multiplier,
                diaryTimeGuid = request.diaryTimeGuid,
                unitGuid = request.unitGuid
            )

            for (attempt in 1..2) {
                try {
                    val resp = directApi.addFoodstuff(directReq, currentCookie)
                    val success = resp.code == 0 || resp.code == 200 || resp.code == null
                    return WearAddFoodResult(success, resp.message ?: if (success) "Добавлено!" else "Ошибка ${resp.code}")
                } catch (e: Exception) {
                    val httpEx = e as? HttpException
                    val code = httpEx?.code()
                    if (httpEx != null && (code == 302 || code == 401)) {
                        val location = httpEx.response()?.headers()?.get("Location")
                        // If KT redirects anywhere other than login/prihlaseni, it might be an action redirect
                        if (location != null && !location.contains("login") && !location.contains("prihlaseni")) {
                            return WearAddFoodResult(true, "Добавлено!")
                        }
                        if (attempt == 1) {
                            Log.w(TAG, "Direct addFood received HTTP 302/401, attempting auto-login...")
                            val newCookie = attemptAutoLogin()
                            if (newCookie != null) {
                                currentCookie = newCookie
                                continue
                            }
                        }
                        Log.e(TAG, "Direct addFood session expired (HTTP $code)")
                        return WearAddFoodResult(false, "Сессия истекла. Откройте приложение на телефоне")
                    }
                    Log.e(TAG, "Direct addFood failed (HTTP $code): ${e.message}")
                    return WearAddFoodResult(false, e.message ?: "Ошибка соединения")
                }
            }
        }

        return WearAddFoodResult(false, "Нет соединения с телефоном или сетью")
    }

    suspend fun getFoodDefaultGrams(guid: String): Int? {
        var currentCookie = _cookie.value
        if (currentCookie.isNotBlank()) {
            for (attempt in 1..2) {
                try {
                    val resp = directApi.getFoodForm(guid, currentCookie)
                    val opts = resp.data?.unitOptions ?: emptyList()
                    val nonGram = opts.firstOrNull { it.multiplier != null && it.multiplier != 1.0 }
                    val defaultPortion = nonGram?.multiplier ?: resp.data?.multiplier
                    return if (defaultPortion != null && defaultPortion > 0) {
                        defaultPortion.roundToInt()
                    } else null
                } catch (e: Exception) {
                    val code = (e as? HttpException)?.code()
                    if ((code == 302 || code == 401) && attempt == 1) {
                        val newCookie = attemptAutoLogin()
                        if (newCookie != null) {
                            currentCookie = newCookie
                            continue
                        }
                    }
                    Log.w(TAG, "getFoodDefaultGrams failed: ${e.message}")
                    return null
                }
            }
        }
        return null
    }
}
