package com.example.kthealthsync

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.widthIn
import org.json.JSONObject
import com.example.kthealthsync.MacroCache
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    date: String,
    currentKcal: Double,
    currentProtein: Double,
    currentCarbs: Double,
    currentFat: Double,
    currentFiber: Double,
    currentSugar: Double,
    currentSalt: Double,
    onBack: () -> Unit,
    onFoodAdded: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<AutocompleteResponseItem>>(emptyList()) }
    var frequentItems by remember { mutableStateOf<List<AutocompleteResponseItem>>(emptyList()) }
    var favoriteItems by remember { mutableStateOf<List<AutocompleteResponseItem>>(emptyList()) }
    var myMeals by remember { mutableStateOf<List<AutocompleteResponseItem>>(emptyList()) }
    
    var isSearching by remember { mutableStateOf(false) }
    var isFetchingDefaults by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    var selectedItem by remember { mutableStateOf<AutocompleteResponseItem?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    
    var selectedTab by remember { mutableStateOf(0) } // 0: Frequent, 1: Favorites

    // Load defaults on start
    LaunchedEffect(Unit) {
        try {
            val cookie = prefs.cookieFlow.first()
            if (cookie.isNotEmpty()) {
                frequentItems = RetrofitClient.instance.getFrequentFoodstuff(cookie).filter { it.id != null }
                favoriteItems = RetrofitClient.instance.getFavoriteFoodstuff(cookie).filter { it.id != null }
                myMeals = RetrofitClient.instance.getMyMeals(cookie).filter { it.id != null }
            }
        } catch (e: Exception) {
            errorMessage = e.message ?: "Failed to load defaults"
        } finally {
            isFetchingDefaults = false
        }
    }

    LaunchedEffect(query) {
        if (query.length < 3) {
            results = emptyList()
            return@LaunchedEffect
        }
        
        delay(500) // Debounce
        isSearching = true
        errorMessage = null
        
        try {
            val cookie = prefs.cookieFlow.first()
            if (cookie.isNotEmpty()) {
                val response = RetrofitClient.instance.searchFood(query, cookie)
                val serverResults = response.filter { it.id != null }
                
                // Also search locally in myMeals (custom meals) since the API doesn't return them
                val localMatches = myMeals.filter { 
                    it.title?.contains(query, ignoreCase = true) == true 
                }
                
                // Combine and remove duplicates by ID
                val combined = (localMatches + serverResults).distinctBy { it.id }
                results = combined
            }
        } catch (e: Exception) {
            errorMessage = e.message ?: "Search failed"
        } finally {
            isSearching = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search Food/Water") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search (e.g. voda, chleba)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (isSearching || (isFetchingDefaults && query.length < 3)) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else if (errorMessage != null) {
                Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
            } else {
                val listToShow = if (query.length >= 3) {
                    results
                } else {
                    if (selectedTab == 0) frequentItems else (favoriteItems + myMeals).distinctBy { it.id }
                }
                
                if (query.length < 3) {
                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Frequent") })
                        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Favorites") })
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(listToShow) { item ->
                        FoodListItem(item = item) {
                            selectedItem = item
                            showAddDialog = true
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
    
    if (showAddDialog && selectedItem != null) {
        AddFoodDialog(
            item = selectedItem!!,
            date = date,
            currentKcal = currentKcal,
            currentProtein = currentProtein,
            currentCarbs = currentCarbs,
            currentFat = currentFat,
            currentFiber = currentFiber,
            currentSugar = currentSugar,
            currentSalt = currentSalt,
            onDismiss = { showAddDialog = false },
            onSuccess = {
                showAddDialog = false
                onFoodAdded()
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
        // Dummy image / placeholder logic for now
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .padding(end = 16.dp)
        ) {
            if (item.hasImage == true) {
                // Image URL without /thumb/ as the thumb endpoint returns 0 bytes
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
            val energy = item.value ?: "0"
            Text("$energy kcal / 100${item.unit ?: "g"}", color = Color.Gray, fontSize = 14.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodDialog(
    item: AutocompleteResponseItem,
    date: String,
    currentKcal: Double,
    currentProtein: Double,
    currentCarbs: Double,
    currentFat: Double,
    currentFiber: Double,
    currentSugar: Double,
    currentSalt: Double,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { PreferencesManager(context) }
    
    var unitOptions by remember { mutableStateOf<List<FoodFormUnitOption>>(emptyList()) }
    var selectedUnit by remember { mutableStateOf<FoodFormUnitOption?>(null) }
    
    var amountText by remember { mutableStateOf("") }
    var debugStr by remember { mutableStateOf("") }
    var actualDefaultBaseAmount by remember { mutableStateOf(1.0) }
    var selectedTimeGuid by remember { mutableStateOf("1") }
    
    var isLoadingOptions by remember { mutableStateOf(true) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    
    // Macros per 100g
    var proteinPer100 by remember { mutableStateOf(0.0) }
    var carbsPer100 by remember { mutableStateOf(0.0) }
    var fatPer100 by remember { mutableStateOf(0.0) }
    var fiberPer100 by remember { mutableStateOf(0.0) }
    var sugarPer100 by remember { mutableStateOf(0.0) }
    var saltPer100 by remember { mutableStateOf(0.0) }
    
    val timeOptions = listOf(
        "1" to "Breakfast (Snídaně)",
        "2" to "Morning Snack (Dopolední svačina)",
        "3" to "Lunch (Oběd)",
        "4" to "Afternoon Snack (Odpolední svačina)",
        "5" to "Dinner (Večeře)",
        "6" to "Second Dinner (Druhá večeře)"
    )

    LaunchedEffect(Unit) {
        try {
            val cookie = prefs.cookieFlow.first()
            
            // 1. Fetch Form
            val res = RetrofitClient.instance.getFoodForm(item.id!!, cookie)
            val opts = res.data?.unitOptions ?: emptyList()
            unitOptions = opts
            
            if (opts.isNotEmpty()) {
                val nonGramOpt = opts.firstOrNull { it.multiplier != 1.0 }
                actualDefaultBaseAmount = nonGramOpt?.multiplier ?: 1.0
                selectedUnit = opts.find { it.multiplier == 1.0 } ?: opts.first()
            }            // 2. Fetch HTML for macros (with Cache & JSON)
            if (item.url != null) {
                val cached = MacroCache.get(item.url)
                if (cached != null) {
                    proteinPer100 = cached["bílkoviny"] ?: 0.0
                    carbsPer100 = cached["sacharidy"] ?: 0.0
                    fatPer100 = cached["tuky"] ?: 0.0
                    fiberPer100 = cached["vláknina"] ?: 0.0
                    sugarPer100 = cached["cukry"] ?: 0.0
                    saltPer100 = cached["sůl"] ?: 0.0
                } else {
                    try {
                        val fullUrl = res.data?.url ?: if (item.url?.contains("/") == true) item.url else "potraviny/${item.url}"
                        val htmlUrl = "https://www.kaloricketabulky.cz/$fullUrl"
                        val htmlResp = RetrofitClient.instance.getFoodHtml(htmlUrl, cookie).string()
                        
                                                                        val startTag = "<script type=\"application/ld+json\">"
                        val endTag = "</script>"
                        val startIndex = htmlResp.indexOf(startTag)
                        if (startIndex != -1) {
                            val jsonStart = startIndex + startTag.length
                            val endIndex = htmlResp.indexOf(endTag, jsonStart)
                            if (endIndex != -1) {
                                val jsonStr = htmlResp.substring(jsonStart, endIndex).trim()
                                try {
                                    val jsonObj = JSONObject(jsonStr)
                                    if (jsonObj.has("keywords")) {
                                        val keywordsArr = jsonObj.getJSONArray("keywords")
                                        val parsedMap = mutableMapOf<String, Double>()
                                        for (i in 0 until keywordsArr.length()) {
                                            val kw = keywordsArr.getString(i)
                                            val parts = kw.split(":").map { it.trim() }
                                            if (parts.size >= 2) {
                                                val key = parts[0].lowercase()
                                                val rawVal = parts[1].replace(",", ".").replace("g", "").replace("mg", "").trim()
                                                val num = rawVal.toDoubleOrNull() ?: 0.0
                                                parsedMap[key] = num
                                                when (key) {
                                                    "bílkoviny" -> proteinPer100 = num
                                                    "sacharidy" -> carbsPer100 = num
                                                    "tuky" -> fatPer100 = num
                                                    "vláknina" -> fiberPer100 = num
                                                    "cukry" -> sugarPer100 = num
                                                    "sůl" -> saltPer100 = num
                                                }
                                            }
                                        }
                                        MacroCache.put(item.url, parsedMap)
                                        debugStr = "Success: ${parsedMap.size} macros"
                                    } else {
                                        debugStr = "No keywords in JSON"
                                    }
                                } catch (e: Exception) {
                                    debugStr = "JSON Error: ${e.message}"
                                }
                            } else {
                                debugStr = "No end tag"
                            }
                        } else {
                            debugStr = "No LD+JSON script found in HTML"
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            
        } catch (e: Exception) {
            errorMsg = "Failed to load form details"
        } finally {
            isLoadingOptions = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.title ?: "Add item") },
        text = {
            if (isLoadingOptions) {
                CircularProgressIndicator(modifier = Modifier.padding(32.dp))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (errorMsg != null) {
                        Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
                    }
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Amount
                        Column(modifier = Modifier.weight(0.4f)) {
                            Text("Amount", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                            OutlinedTextField(
                                value = amountText,
                                onValueChange = { amountText = it },
                                placeholder = {
                                    val mult = selectedUnit?.multiplier ?: 1.0
                                    val pVal = if (mult == 1.0) actualDefaultBaseAmount else 1.0
                                    val pTxt = if (pVal % 1.0 == 0.0) pVal.toInt().toString() else pVal.toString()
                                    Text(pTxt)
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        
                        // Unit Dropdown
                        var unitExpanded by remember { mutableStateOf(false) }
                        Column(modifier = Modifier.weight(0.6f)) {
                            Text("Unit", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                            ExposedDropdownMenuBox(
                                expanded = unitExpanded,
                                onExpandedChange = { unitExpanded = !unitExpanded }
                            ) {
                                OutlinedTextField(
                                    readOnly = true,
                                    value = selectedUnit?.title ?: "",
                                    onValueChange = {},
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded) },
                                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                    modifier = Modifier.menuAnchor().fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = unitExpanded,
                                    onDismissRequest = { unitExpanded = false }
                                ) {
                                    unitOptions.forEach { opt ->
                                        DropdownMenuItem(
                                            text = { Text(opt.title ?: "") },
                                            onClick = {
                                                selectedUnit = opt
                                                unitExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    // Meal Dropdown
                    var mealExpanded by remember { mutableStateOf(false) }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Meal Time", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                        ExposedDropdownMenuBox(
                            expanded = mealExpanded,
                            onExpandedChange = { mealExpanded = !mealExpanded }
                        ) {
                            OutlinedTextField(
                                readOnly = true,
                                value = timeOptions.find { it.first == selectedTimeGuid }?.second ?: "",
                                onValueChange = {},
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = mealExpanded) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = mealExpanded,
                                onDismissRequest = { mealExpanded = false }
                            ) {
                                timeOptions.forEach { (guid, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            selectedTimeGuid = guid
                                            mealExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    val itemKcal = item.value?.replace(Regex("[^0-9.]"), "")?.toDoubleOrNull() ?: 0.0
                    val mult = selectedUnit?.multiplier ?: 1.0
                    
                    val pVal = if (mult == 1.0) actualDefaultBaseAmount else 1.0
                    val inputVal = amountText.toDoubleOrNull() ?: pVal
                    
                    val factor = (inputVal * mult) / 100.0
                    val addedKcal = factor * itemKcal
                    
                                            Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Vliv na příjem",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                CircularImpactIndicator(title = "Energie", current = currentKcal, added = addedKcal, max = 2300.0, unit = "kcal", color = Color(0xFFFFCA28))
                                CircularImpactIndicator(title = "Bílkoviny", current = currentProtein, added = proteinPer100 * factor, max = 130.0, unit = "g", color = Color(0xFFEF5350))
                                CircularImpactIndicator(title = "Sacharidy", current = currentCarbs, added = carbsPer100 * factor, max = 285.0, unit = "g", color = Color(0xFFFFCA28))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                CircularImpactIndicator(title = "Tuky", current = currentFat, added = fatPer100 * factor, max = 70.0, unit = "g", color = Color(0xFF66BB6A))
                                CircularImpactIndicator(title = "Vláknina", current = currentFiber, added = fiberPer100 * factor, max = 30.0, unit = "g", color = Color(0xFFFFCA28))
                                CircularImpactIndicator(title = "Cukry", current = currentSugar, added = sugarPer100 * factor, max = 70.0, unit = "g", color = Color(0xFF66BB6A))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                CircularImpactIndicator(title = "Sůl", current = currentSalt, added = saltPer100 * factor, max = 5.0, unit = "g", color = Color(0xFFEF5350))
                                Spacer(modifier = Modifier.width(90.dp))
                                Spacer(modifier = Modifier.width(90.dp))
                            }
                        }
                    }
                }
        },
        confirmButton = {
            Button(
                onClick = {
                    coroutineScope.launch {
                        isSubmitting = true
                        errorMsg = null
                        try {
                            val cookie = prefs.cookieFlow.first()
                            
                            val mult = selectedUnit?.multiplier ?: 1.0
                            val pVal = if (mult == 1.0) actualDefaultBaseAmount else 1.0
                            val inputVal = amountText.toDoubleOrNull() ?: pVal
                            val finalAmount = (inputVal * mult).toString() // the API accepts raw grams/ml

                            val request = AddFoodRequest(
                                guid = item.id!!,
                                title = item.title ?: "",
                                url = item.url ?: "",
                                date = date,
                                multiplier = finalAmount,
                                diaryTimeGuid = selectedTimeGuid
                            )
                            val response = RetrofitClient.instance.addFoodstuff(request, cookie)
                            if (response.code == 0) {
                                onSuccess()
                            } else {
                                errorMsg = response.message ?: "Failed with code ${response.code}"
                            }
                        } catch (e: Exception) {
                            errorMsg = e.message ?: "Error saving food"
                        } finally {
                            isSubmitting = false
                        }
                    }
                },
                enabled = !isSubmitting && !isLoadingOptions
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun MacroBadge(label: String, value: Double, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(8.dp)
            .widthIn(min = 60.dp)
    ) {
        Text(label, fontSize = 12.sp, color = color, fontWeight = FontWeight.SemiBold)
        val formatted = if (value % 1.0 < 0.1) String.format("%.0f g", value) else String.format("%.1f g", value)
        Text(formatted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CircularImpactIndicator(title: String, current: Double, added: Double, max: Double, unit: String, color: Color) {
    val currentSweep = ((current / max) * 360f).toFloat().coerceIn(0f, 360f)
    val addedSweep = ((added / max) * 360f).toFloat().coerceIn(0f, 360f - currentSweep)
    
    val totalPct = if (added > 0) ((added / max) * 100).toInt() else 0
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(90.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.Bold)
        Text("+${if (added % 1.0 == 0.0) added.toInt().toString() else String.format("%.1f", added)}", style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.Bold)
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(56.dp)) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeW = 6.dp.toPx()
                // Track
                drawArc(
                    color = Color(0xFF333333),
                    startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(strokeW)
                )
                // Current (Bold)
                drawArc(
                    color = color,
                    startAngle = -90f, sweepAngle = currentSweep, useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(strokeW, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
                // Added (Semi-transparent)
                drawArc(
                    color = color.copy(alpha = 0.4f),
                    startAngle = -90f + currentSweep, sweepAngle = addedSweep, useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(strokeW, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
            }
            Text("+${totalPct}%", style = MaterialTheme.typography.bodySmall, color = color.copy(alpha = 0.7f), fontSize = 10.sp)
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        Text("z ${if (max % 1.0 == 0.0) max.toInt().toString() else max.toString()} $unit", style = MaterialTheme.typography.bodySmall, color = Color.Gray, fontSize = 11.sp)
    }
}
