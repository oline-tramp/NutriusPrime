package com.example.kthealthsync

import com.example.kthealthsync.data.repository.KalorickeRepository
import com.example.kthealthsync.data.repository.MyMealsCache
import com.example.kthealthsync.data.repository.RepositoryPreferences
import com.example.kthealthsync.domain.model.NutrientGoals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import org.junit.experimental.categories.Category
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.Properties

@Category(LiveNetworkTest::class)
class PersonalMealsLiveTest {
    @Test fun completePersonalCacheRefreshesTwiceAgainstLiveAccount() = runBlocking {
        val properties = Properties().apply { File("local.properties").inputStream().use { load(it) } }
        val suppliedCookie = System.getenv("KALORICKE_COOKIE")?.takeIf { it.isNotBlank() }
            ?: properties.getProperty("KALORICKE_COOKIE", "")
        check(suppliedCookie.isNotBlank()) { "KALORICKE_COOKIE is required" }
        val base = "https://www.kaloricketabulky.cz/".toHttpUrl()
        val sessionCookies = suppliedCookie.split(';').mapNotNull { Cookie.parse(base, it.trim()) }.toMutableList()
        val jar = object : CookieJar {
            override fun loadForRequest(url: HttpUrl) = synchronized(sessionCookies) { sessionCookies.filter { it.matches(url) } }
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                synchronized(sessionCookies) {
                    cookies.forEach { cookie ->
                        sessionCookies.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
                        sessionCookies.add(cookie)
                    }
                }
            }
        }
        // A browser token may refresh JSESSIONID on the first redirect. Never print either token.
        OkHttpClient.Builder().cookieJar(jar).build().newCall(Request.Builder().url(base).build()).execute().use {
            check(it.isSuccessful) { "Could not initialize live session: HTTP ${it.code}" }
        }
        val cookie = jar.loadForRequest(base).joinToString("; ") { "${it.name}=${it.value}" }
        val requestedPaths = mutableListOf<String>()
        val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .addInterceptor { chain ->
                synchronized(requestedPaths) { requestedPaths.add(chain.request().url.encodedPath) }
                chain.proceed(chain.request())
            }.build()
        val api = Retrofit.Builder().baseUrl(base).client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(KalorickeApiService::class.java)
        val reference = api.getMealList(cookie, limit = 100)
        val expected = checkNotNull(reference.count) { "Live personal list has no total count" }
        val prefs = object : RepositoryPreferences {
            override val cookieFlow = MutableStateFlow(cookie)
            override val emailFlow = MutableStateFlow("")
            override val passwordHashFlow = MutableStateFlow("")
            override val nutrientGoalsFlow = MutableStateFlow(NutrientGoals())
            override suspend fun saveCookie(cookie: String) { cookieFlow.value = cookie }
            override suspend fun saveNutrientGoals(goals: NutrientGoals) {}
            override fun getFoodCategory(guidOrUrl: String): String? = null
            override fun saveFoodCategory(guidOrUrl: String, categoryName: String) {}
        }
        val repository = KalorickeRepository(api, prefs)
        var refreshes = 0
        val twice = CompletableDeferred<Unit>()
        val cache = MyMealsCache(loadMeals = {
            repository.getAllMyMeals().also { meals ->
                assertEquals(expected, meals.size)
                assertEquals(expected, meals.map { it.id }.distinct().size)
                assertTrue(meals.all { it.clazz == "meal" })
                assertTrue(meals.map { it.id }.containsAll(reference.data!!.map { it.guid }))
                if (++refreshes == 2) twice.complete(Unit)
            }
        }, onError = { throw AssertionError("Live cache refresh failed", it) })
        val job = launch { cache.refreshWhileActive() }
        try {
            withTimeout(45_000) { twice.await() }
            assertEquals(expected, cache.meals.value.size)
            assertTrue(requestedPaths.all { it == "/user/settings/meal/list" })
            val first = cache.meals.value.firstOrNull()
            if (first != null) {
                val form = repository.getPersonalMealForm(requireNotNull(first.id))
                assertTrue(form.units.any { it.multiplier == -2.0 })
                assertTrue(form.units.any { it.multiplier == -1.0 })
                assertTrue(form.units.any { it.multiplier == 1.0 })
                val percentage = form.units.first { it.multiplier == -1.0 }
                val grams = form.units.first { it.multiplier == 1.0 }
                val whole = form.portion(requireNotNull(percentage.id), 100.0, "15.09.2026", "3")
                val byWeight = form.portion(requireNotNull(grams.id), whole.grams, "15.09.2026", "3")
                assertEquals(whole.kcal, byWeight.kcal, 0.001)
                assertTrue(whole.kcal > 0)
                println("Live meal form: portions, percent and grams verified; no diary writes")
            }
            println("Live personal cache: $expected meals, $refreshes successful refreshes, only personal-list requests")
        } finally {
            job.cancelAndJoin()
        }
    }
}
