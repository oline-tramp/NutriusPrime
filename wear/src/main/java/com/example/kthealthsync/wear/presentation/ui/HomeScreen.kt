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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
fun HomeScreen(
    dataManager: WatchDataManager,
    onFoodSelected: (WearFoodItem) -> Unit,
    onSearchQuery: (String) -> Unit
) {
    val listState = rememberScalingLazyListState()
    val favorites by dataManager.favoriteFoods.collectAsState()
    val isPhoneConnected by dataManager.isPhoneConnected.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    val searchLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val query = InputHelper.extractQuery(result.data)
            if (!query.isNullOrBlank()) {
                onSearchQuery(query)
            }
        }
    }

    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            autoCentering = androidx.wear.compose.foundation.lazy.AutoCenteringParams(itemIndex = 0),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Search Button
            item {
                Chip(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    onClick = {
                        searchLauncher.launch(InputHelper.createSearchIntent(context))
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Поиск",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colors.primary
                        )
                    },
                    label = {
                        Text(
                            text = "Поиск продукта",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    secondaryLabel = {
                        Text(
                            text = "Голос или текст",
                            fontSize = 11.sp,
                            color = Color.LightGray
                        )
                    },
                    colors = ChipDefaults.chipColors(
                        backgroundColor = Color(0xFF26262B)
                    )
                )
            }

            // Header
            item {
                ListHeader {
                    Text(
                        text = "Любимые и частые",
                        color = MaterialTheme.colors.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (favorites.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Список пуст.\nОткройте Nutrius на телефоне для синхронизации.",
                            textAlign = TextAlign.Center,
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        CompactChip(
                            onClick = { dataManager.requestSyncFromPhone() },
                            label = { Text("Синхронизировать", fontSize = 11.sp) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Sync",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
            } else {
                items(favorites) { food ->
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
                        modifier = Modifier.padding(top = 4.dp),
                        onClick = { dataManager.requestSyncFromPhone() },
                        label = { Text("Обновить список", fontSize = 11.sp) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sync",
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }
            }
        }
    }
}
