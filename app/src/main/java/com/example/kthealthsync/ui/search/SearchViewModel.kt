package com.example.kthealthsync.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kthealthsync.*
import com.example.kthealthsync.data.repository.MyMealsCache
import com.example.kthealthsync.data.repository.KalorickeRepository
import com.example.kthealthsync.domain.model.NutrientGoals
import com.example.kthealthsync.domain.util.OpenFoodFactsHelper
import com.example.kthealthsync.ui.LatestRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)
    private val repository = KalorickeRepository(prefs = prefs)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<AutocompleteResponseItem>>(emptyList())
    private val mealsCache = MyMealsCache(
        loadMeals = {
            repository.getAllMyMeals().also {
                DebugLogger.addActionLog("Personal meals cache refreshed: ${it.size} meals")
            }
        },
        onError = { DebugLogger.addActionLog("Refresh my meals failed: ${it.message}") }
    )
    val myMeals = mealsCache.meals
    val isRefreshingMeals = mealsCache.isRefreshing
    val mealsError = mealsCache.error
    private var mealsAccount: String? = null
    val results: StateFlow<List<AutocompleteResponseItem>> = combine(_results, myMeals, _query) { remote, meals, query ->
        if (query.length < 3) emptyList()
        else (meals.filter { it.title?.contains(query, ignoreCase = true) == true } + remote).distinctBy { it.id }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    suspend fun refreshMyMealsWhileActive() {
        combine(prefs.cookieFlow.map { it.isNotBlank() }.distinctUntilChanged(), prefs.emailFlow) { loggedIn, email ->
            if (loggedIn) email else null
        }.distinctUntilChanged().collectLatest { account ->
            if (account != mealsAccount) {
                mealsCache.clear()
                mealsAccount = account
            }
            if (account != null) mealsCache.refreshWhileActive()
        }
    }

    private val _frequentItems = MutableStateFlow<List<AutocompleteResponseItem>>(emptyList())
    val frequentItems: StateFlow<List<AutocompleteResponseItem>> = _frequentItems.asStateFlow()

    private val _favoriteItems = MutableStateFlow<List<AutocompleteResponseItem>>(emptyList())
    val favoriteItems: StateFlow<List<AutocompleteResponseItem>> = _favoriteItems.asStateFlow()


    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _canLoadMore = MutableStateFlow(true)
    val canLoadMore: StateFlow<Boolean> = _canLoadMore.asStateFlow()

    private val _isLoadingMoreFrequent = MutableStateFlow(false)
    val isLoadingMoreFrequent: StateFlow<Boolean> = _isLoadingMoreFrequent.asStateFlow()

    private val _canLoadMoreFrequent = MutableStateFlow(true)
    val canLoadMoreFrequent: StateFlow<Boolean> = _canLoadMoreFrequent.asStateFlow()

    private val _isLoadingMoreFavorites = MutableStateFlow(false)
    val isLoadingMoreFavorites: StateFlow<Boolean> = _isLoadingMoreFavorites.asStateFlow()

    private val _canLoadMoreFavorites = MutableStateFlow(true)
    val canLoadMoreFavorites: StateFlow<Boolean> = _canLoadMoreFavorites.asStateFlow()

    private val _isFetchingDefaults = MutableStateFlow(true)
    val isFetchingDefaults: StateFlow<Boolean> = _isFetchingDefaults.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val nutrientGoals: StateFlow<NutrientGoals> = prefs.nutrientGoalsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = NutrientGoals()
    )

    private val searchRequest = LatestRequest()
    private val pageRequest = LatestRequest()
    private var currentPage = 0
    private var frequentPage = 0
    private var favoritePage = 0

    init {
        loadDefaults()
        viewModelScope.launch {
            var previous: com.example.kthealthsync.domain.model.AppLanguage? = null
            prefs.resolvedLanguageFlow.distinctUntilChanged().collect { language ->
                if (previous != null && previous != language) onQueryChange(_query.value)
                previous = language
            }
        }
    }

    fun loadDefaults() {
        viewModelScope.launch {
            _isFetchingDefaults.value = true
            _errorMessage.value = null
            frequentPage = 0
            favoritePage = 0
            _canLoadMoreFrequent.value = true
            _canLoadMoreFavorites.value = true
            try {
                val frequent = repository.getFrequentFoodstuff(page = 0, limit = 10)
                _frequentItems.value = frequent
                if (frequent.size < 10) {
                    _canLoadMoreFrequent.value = false
                }

                val favorites = repository.getFavoriteFoodstuff(page = 0, limit = 10)
                _favoriteItems.value = favorites
                if (favorites.size < 10) {
                    _canLoadMoreFavorites.value = false
                }

                val combined = (frequent + favorites).distinctBy { it.id }
                val cookie = prefs.cookieFlow.first()
                if (cookie.isNotBlank() && combined.isNotEmpty()) {
                    try {
                        com.example.kthealthsync.wear.WearSyncManager(getApplication()).syncDataToWatch(cookie, combined)
                    } catch (_: Exception) {}
                }

            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _errorMessage.value = e.message ?: "Failed to load defaults"
            } finally {
                _isFetchingDefaults.value = false
            }
        }
    }

    fun loadNextFrequentPage() {
        if (_isLoadingMoreFrequent.value || !_canLoadMoreFrequent.value) return

        viewModelScope.launch {
            _isLoadingMoreFrequent.value = true
            try {
                val nextPage = frequentPage + 1
                val nextItems = repository.getFrequentFoodstuff(page = nextPage, limit = 10)
                val currentIds = _frequentItems.value.mapNotNull { it.id }.toSet()
                val fresh = nextItems.filter { it.id != null && !currentIds.contains(it.id) }

                if (fresh.isEmpty()) {
                    _canLoadMoreFrequent.value = false
                } else {
                    frequentPage = nextPage
                    _frequentItems.value = _frequentItems.value + fresh
                    if (nextItems.size < 10) {
                        _canLoadMoreFrequent.value = false
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                DebugLogger.addActionLog("Load next frequent page failed: ${e.message}")
            } finally {
                _isLoadingMoreFrequent.value = false
            }
        }
    }

    fun loadNextFavoritePage() {
        if (_isLoadingMoreFavorites.value || !_canLoadMoreFavorites.value) return

        viewModelScope.launch {
            _isLoadingMoreFavorites.value = true
            try {
                val nextPage = favoritePage + 1
                val nextItems = repository.getFavoriteFoodstuff(page = nextPage, limit = 10)
                val currentIds = _favoriteItems.value.mapNotNull { it.id }.toSet()
                val fresh = nextItems.filter { it.id != null && !currentIds.contains(it.id) }

                if (fresh.isEmpty()) {
                    _canLoadMoreFavorites.value = false
                } else {
                    favoritePage = nextPage
                    _favoriteItems.value = _favoriteItems.value + fresh
                    if (nextItems.size < 10) {
                        _canLoadMoreFavorites.value = false
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                DebugLogger.addActionLog("Load next favorite page failed: ${e.message}")
            } finally {
                _isLoadingMoreFavorites.value = false
            }
        }
    }

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        resetSearch()
        if (newQuery.length >= 3) performSearch(newQuery, debounce = true)
    }

    private fun resetSearch() {
        searchRequest.cancel()
        pageRequest.cancel()
        _results.value = emptyList()
        _isSearching.value = false
        _isLoadingMore.value = false
        _errorMessage.value = null
        currentPage = 0
        _canLoadMore.value = true
    }

    fun searchBarcode(barcode: String, onSingleMatchFound: ((AutocompleteResponseItem) -> Unit)? = null) {
        val cleanBarcode = barcode.trim()
        _query.value = cleanBarcode
        resetSearch()
        performSearch(cleanBarcode, onSingleMatchFound = onSingleMatchFound)
    }

    private fun performSearch(
        queryStr: String,
        debounce: Boolean = false,
        onSingleMatchFound: ((AutocompleteResponseItem) -> Unit)? = null
    ) {
        _isSearching.value = true
        searchRequest.launch(
            scope = viewModelScope,
            load = {
                if (debounce) delay(400)
                var matches = repository.searchFood(queryStr, emptyList(), page = 0, language = prefs.resolvedLanguageFlow.first())
                val isBarcode = queryStr.all { it.isDigit() } && queryStr.length in 8..14
                if (matches.isEmpty() && isBarcode) {
                    val offName = OpenFoodFactsHelper.lookupProductName(queryStr)
                    if (!offName.isNullOrBlank()) {
                        matches = repository.searchFood(offName, emptyList(), page = 0, language = prefs.resolvedLanguageFlow.first())
                    }
                }
                matches
            },
            onSuccess = { matches ->
                _results.value = matches
                _canLoadMore.value = matches.size >= 10
                val combined = (myMeals.value.filter { it.title?.contains(queryStr, ignoreCase = true) == true } + matches).distinctBy { it.id }
                if (combined.size == 1) onSingleMatchFound?.invoke(combined.first())
            },
            onError = { e -> _errorMessage.value = e.message ?: "Search failed" },
            onFinally = { _isSearching.value = false }
        )
    }

    fun clearSearch() {
        onQueryChange("")
    }

    fun retry() {
        when {
            _query.value.length < 3 -> loadDefaults()
            _results.value.isEmpty() -> onQueryChange(_query.value)
            else -> loadNextPage()
        }
    }

    fun loadNextPage() {
        val q = _query.value
        if (q.length < 3 || _isSearching.value || _isLoadingMore.value || !_canLoadMore.value) return
        if (_results.value.isEmpty()) return
        val nextPage = currentPage + 1
        _isLoadingMore.value = true
        _errorMessage.value = null
        pageRequest.launch(
            scope = viewModelScope,
            load = { repository.searchFood(q, emptyList(), page = nextPage, language = prefs.resolvedLanguageFlow.first()) },
            onSuccess = { newMatches ->
                val currentIds = _results.value.mapNotNull { it.id }.toSet()
                val freshItems = newMatches.filter { it.id != null && !currentIds.contains(it.id) }
                if (freshItems.isEmpty()) {
                    _canLoadMore.value = false
                } else {
                    currentPage = nextPage
                    _results.value = _results.value + freshItems
                    _canLoadMore.value = newMatches.size >= 10
                }
            },
            onError = { e -> _errorMessage.value = e.message ?: "Failed to load next page" },
            onFinally = { _isLoadingMore.value = false }
        )
    }

    suspend fun getPersonalMealForm(guid: String) = repository.getPersonalMealForm(guid)

    suspend fun addPersonalMeal(request: com.google.gson.JsonObject) = repository.addPersonalMeal(request)

    suspend fun getFoodForm(guid: String): FoodFormResponse {
        return repository.getFoodForm(guid)
    }

    suspend fun getFoodMacros(itemUrl: String, formUrl: String?): Map<String, Double> {
        return repository.getFoodMacrosFromWeb(itemUrl, formUrl)
    }

    suspend fun addFood(request: AddFoodRequest): AddFoodResponse {
        return repository.addFoodstuff(request)
    }
}
