package com.example.kthealthsync.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kthealthsync.*
import com.example.kthealthsync.data.repository.KalorickeRepository
import com.example.kthealthsync.domain.model.NutrientGoals
import com.example.kthealthsync.domain.util.OpenFoodFactsHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)
    private val repository = KalorickeRepository(prefs = prefs)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<AutocompleteResponseItem>>(emptyList())
    val results: StateFlow<List<AutocompleteResponseItem>> = _results.asStateFlow()

    private val _frequentItems = MutableStateFlow<List<AutocompleteResponseItem>>(emptyList())
    val frequentItems: StateFlow<List<AutocompleteResponseItem>> = _frequentItems.asStateFlow()

    private val _favoriteItems = MutableStateFlow<List<AutocompleteResponseItem>>(emptyList())
    val favoriteItems: StateFlow<List<AutocompleteResponseItem>> = _favoriteItems.asStateFlow()

    private val _myMeals = MutableStateFlow<List<AutocompleteResponseItem>>(emptyList())
    val myMeals: StateFlow<List<AutocompleteResponseItem>> = _myMeals.asStateFlow()

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

    private var searchJob: Job? = null
    private var currentPage = 0
    private var frequentPage = 0
    private var favoritePage = 0

    init {
        loadDefaults()
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

                _myMeals.value = repository.getMyMeals(page = 0, limit = 50)
            } catch (e: Exception) {
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
                DebugLogger.addActionLog("Load next favorite page failed: ${e.message}")
            } finally {
                _isLoadingMoreFavorites.value = false
            }
        }
    }

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        searchJob?.cancel()

        if (newQuery.length < 3) {
            _results.value = emptyList()
            _isSearching.value = false
            currentPage = 0
            _canLoadMore.value = true
            return
        }

        searchJob = viewModelScope.launch {
            delay(400)
            performSearch(newQuery)
        }
    }

    fun searchBarcode(barcode: String, onSingleMatchFound: ((AutocompleteResponseItem) -> Unit)? = null) {
        val cleanBarcode = barcode.trim()
        _query.value = cleanBarcode
        searchJob?.cancel()

        searchJob = viewModelScope.launch {
            performSearch(cleanBarcode, onSingleMatchFound)
        }
    }

    private suspend fun performSearch(
        queryStr: String,
        onSingleMatchFound: ((AutocompleteResponseItem) -> Unit)? = null
    ) {
        _isSearching.value = true
        _errorMessage.value = null
        currentPage = 0
        _canLoadMore.value = true
        try {
            var matches = repository.searchFood(queryStr, _myMeals.value, page = 0)
            val isBarcode = queryStr.all { it.isDigit() } && queryStr.length in 8..14

            if (matches.isEmpty() && isBarcode) {
                // Try Open Food Facts fallback to resolve barcode to product name
                val offName = OpenFoodFactsHelper.lookupProductName(queryStr)
                if (!offName.isNullOrBlank()) {
                    matches = repository.searchFood(offName, _myMeals.value, page = 0)
                }
            }

            _results.value = matches
            if (matches.size < 10) {
                _canLoadMore.value = false
            }

            if (matches.size == 1 && onSingleMatchFound != null) {
                onSingleMatchFound(matches.first())
            }
        } catch (e: Exception) {
            _errorMessage.value = e.message ?: "Search failed"
        } finally {
            _isSearching.value = false
        }
    }

    fun clearSearch() {
        onQueryChange("")
    }

    fun loadNextPage() {
        val q = _query.value
        if (q.length < 3 || _isSearching.value || _isLoadingMore.value || !_canLoadMore.value) return

        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val nextPage = currentPage + 1
                val newMatches = repository.searchFood(q, _myMeals.value, page = nextPage)
                val currentIds = _results.value.mapNotNull { it.id }.toSet()
                val freshItems = newMatches.filter { it.id != null && !currentIds.contains(it.id) }

                if (freshItems.isEmpty()) {
                    _canLoadMore.value = false
                } else {
                    currentPage = nextPage
                    _results.value = _results.value + freshItems
                    if (newMatches.size < 10) {
                        _canLoadMore.value = false
                    }
                }
            } catch (e: Exception) {
                DebugLogger.addActionLog("Load next page failed: ${e.message}")
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

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
