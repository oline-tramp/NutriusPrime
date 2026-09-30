package com.example.kthealthsync.wear.presentation.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.example.kthealthsync.wear.data.WatchDataManager
import com.example.kthealthsync.wear.data.WearFoodItem
import com.example.kthealthsync.wear.presentation.InputHelper

@Composable
fun SearchScreen(
    initialQuery: String,
    dataManager: WatchDataManager,
    onFoodSelected: (WearFoodItem) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var query by remember { mutableStateOf(initialQuery) }
    var isLoading by remember { mutableStateOf(true) }
    var results by remember { mutableStateOf<List<WearFoodItem>>(emptyList()) }
    val listState = rememberScalingLazyListState()

    val searchLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val newQuery = InputHelper.extractQuery(result.data)
            if (!newQuery.isNullOrBlank()) {
                query = newQuery
            }
        }
    }

    LaunchedEffect(query) {
        isLoading = true
        results = dataManager.searchFood(query)
        isLoading = false
    }

    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        if (isLoading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    indicatorColor = MaterialTheme.colors.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Поиск: \"$query\"",
                    fontSize = 12.sp,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        } else {
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                autoCentering = androidx.wear.compose.foundation.lazy.AutoCenteringParams(itemIndex = 0),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    ListHeader {
                        Text(
                            text = "Результаты: $query",
                            color = MaterialTheme.colors.primary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (results.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Ничего не найдено",
                                fontSize = 13.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            CompactChip(
                                onClick = { searchLauncher.launch(InputHelper.createSearchIntent(context)) },
                                label = { Text("Искать снова") },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                } else {
                    items(results) { food ->
                        Chip(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            onClick = { onFoodSelected(food) },
                            label = {
                                Text(
                                    text = food.title,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            secondaryLabel = {
                                val energyText = food.energy?.let { "$it" } ?: ""
                                val unitText = food.unit?.let { "/ $it" } ?: ""
                                Text(
                                    text = "$energyText $unitText".trim(),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colors.primary
                                )
                            },
                            colors = ChipDefaults.chipColors(
                                backgroundColor = Color(0xFF1E1E22)
                            )
                        )
                    }

                    item {
                        CompactChip(
                            modifier = Modifier.padding(top = 6.dp),
                            onClick = { searchLauncher.launch(InputHelper.createSearchIntent(context)) },
                            label = { Text("Другой запрос") },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "New Search",
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
