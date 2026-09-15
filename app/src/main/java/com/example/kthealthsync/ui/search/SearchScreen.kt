package com.example.kthealthsync.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.kthealthsync.AutocompleteResponseItem
import com.example.kthealthsync.domain.model.AddedFoodInfo
import com.example.kthealthsync.domain.model.LocalAppStrings
import com.example.kthealthsync.ui.search.components.AddFoodDialog
import androidx.compose.material.icons.filled.QrCodeScanner
import com.example.kthealthsync.domain.util.BarcodeScannerHelper
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    date: String,
    preselectedMealTimeGuid: String? = null,
    initialBarcode: String? = null,
    currentKcal: Double,
    currentProtein: Double,
    currentCarbs: Double,
    currentFat: Double,
    currentFiber: Double,
    currentSugar: Double,
    currentSalt: Double,
    viewModel: SearchViewModel = viewModel(),
    onBack: () -> Unit,
    onFoodAdded: (AddedFoodInfo) -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val frequentItems by viewModel.frequentItems.collectAsState()
    val favoriteItems by viewModel.favoriteItems.collectAsState()
    val myMeals by viewModel.myMeals.collectAsState()
    val isRefreshingMeals by viewModel.isRefreshingMeals.collectAsState()
    val mealsError by viewModel.mealsError.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val isFetchingDefaults by viewModel.isFetchingDefaults.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val nutrientGoals by viewModel.nutrientGoals.collectAsState()

    var selectedItem by remember { mutableStateOf<AutocompleteResponseItem?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Frequent, 1: Favorites

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(initialBarcode) {
        if (!initialBarcode.isNullOrBlank()) {
            viewModel.searchBarcode(initialBarcode) { item ->
                selectedItem = item
                showAddDialog = true
            }
        }
    }

    LaunchedEffect(Unit) {
        if (initialBarcode.isNullOrBlank()) {
            kotlinx.coroutines.delay(100)
            try {
                focusRequester.requestFocus()
                keyboardController?.show()
            } catch (_: Exception) {}
        }
    }

    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val canLoadMore by viewModel.canLoadMore.collectAsState()
    val isLoadingMoreFrequent by viewModel.isLoadingMoreFrequent.collectAsState()
    val canLoadMoreFrequent by viewModel.canLoadMoreFrequent.collectAsState()
    val isLoadingMoreFavorites by viewModel.isLoadingMoreFavorites.collectAsState()
    val canLoadMoreFavorites by viewModel.canLoadMoreFavorites.collectAsState()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(selectedTab) {
        listState.scrollToItem(0)
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem != null && totalItems > 0 && lastVisibleItem.index >= totalItems - 2
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            if (query.length >= 3) {
                viewModel.loadNextPage()
            } else if (selectedTab == 0) {
                viewModel.loadNextFrequentPage()
            } else if (selectedTab == 1) {
                viewModel.loadNextFavoritePage()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.searchPlaceholder.removeSuffix("...")) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.clearSearch()
                        onBack()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.cancel) }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.onQueryChange(it) },
                label = { Text(strings.searchPlaceholder) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                singleLine = true,
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            BarcodeScannerHelper.scanBarcode(
                                context = context,
                                onSuccess = { barcode ->
                                    viewModel.searchBarcode(barcode) { item ->
                                        selectedItem = item
                                        showAddDialog = true
                                    }
                                }
                            )
                        }) {
                            Icon(
                                Icons.Default.QrCodeScanner,
                                contentDescription = strings.scanBarcode,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.clearSearch() }) {
                                Icon(Icons.Default.Clear, contentDescription = strings.clear)
                            }
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (errorMessage != null) {
                Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { viewModel.retry() }) { Text(strings.retry) }
            }

            if (isSearching || (isFetchingDefaults && query.length < 3)) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                val listToShow = if (query.length >= 3) {
                    results
                } else {
                    if (selectedTab == 0) frequentItems else (favoriteItems + myMeals).distinctBy { it.id }
                }

                if (query.length < 3) {
                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(strings.frequentTab) })
                        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text(strings.favoritesTab) })
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (selectedTab == 1 && query.length < 3) {
                    if (isRefreshingMeals) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    mealsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }

                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(listToShow) { item ->
                        FoodListItem(item = item) {
                            selectedItem = item
                            showAddDialog = true
                        }
                        HorizontalDivider()
                    }

                    val showMoreSearch = query.length >= 3 && canLoadMore && results.isNotEmpty()
                    val showMoreFrequent = query.length < 3 && selectedTab == 0 && canLoadMoreFrequent
                    val showMoreFavorite = query.length < 3 && selectedTab == 1 && canLoadMoreFavorites
                    val isCurrentlyLoading = when {
                        query.length >= 3 -> isLoadingMore
                        selectedTab == 0 -> isLoadingMoreFrequent
                        else -> isLoadingMoreFavorites
                    }

                    if (showMoreSearch || showMoreFrequent || showMoreFavorite) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isCurrentlyLoading) {
                                        when {
                                            query.length >= 3 -> viewModel.loadNextPage()
                                            selectedTab == 0 -> viewModel.loadNextFrequentPage()
                                            else -> viewModel.loadNextFavoritePage()
                                        }
                                    }
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isCurrentlyLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                } else {
                                    Text(strings.loadMore, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog && selectedItem != null) {
        AddFoodDialog(
            item = selectedItem!!,
            date = date,
            initialMealTimeGuid = preselectedMealTimeGuid,
            currentKcal = currentKcal,
            currentProtein = currentProtein,
            currentCarbs = currentCarbs,
            currentFat = currentFat,
            currentFiber = currentFiber,
            currentSugar = currentSugar,
            currentSalt = currentSalt,
            nutrientGoals = nutrientGoals,
            viewModel = viewModel,
            onDismiss = { showAddDialog = false },
            onSuccess = { info ->
                showAddDialog = false
                viewModel.clearSearch()
                onFoodAdded(info)
            }
        )
    }
}

@Composable
fun FoodListItem(item: AutocompleteResponseItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .padding(end = 16.dp)
        ) {
            if (item.hasImage == true) {
                AsyncImage(
                    model = "https://www.kaloricketabulky.cz/file/image/foodstuff/${item.id}",
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            if (item.favorite == true) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Favorite",
                    tint = Color(0xFFFFC107),
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.BottomEnd)
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title ?: "Unknown", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            item.value?.let { energy ->
                Text("$energy kcal / 100${item.unit ?: "g"}", color = Color.Gray, fontSize = 14.sp)
            }
        }
    }
}
