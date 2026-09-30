package com.example.kthealthsync.wear.presentation

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.example.kthealthsync.wear.data.WatchDataManager
import com.example.kthealthsync.wear.data.WearFoodItem
import com.example.kthealthsync.wear.presentation.theme.NutriusWearTheme
import com.example.kthealthsync.wear.presentation.ui.AddFoodScreen
import com.example.kthealthsync.wear.presentation.ui.HomeScreen
import com.example.kthealthsync.wear.presentation.ui.SearchScreen

class MainActivity : ComponentActivity() {
    private lateinit var dataManager: WatchDataManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dataManager = WatchDataManager(applicationContext)

        setContent {
            NutriusWearTheme {
                val navController = rememberSwipeDismissableNavController()
                var selectedFood by remember { mutableStateOf<WearFoodItem?>(null) }

                SwipeDismissableNavHost(
                    navController = navController,
                    startDestination = "home"
                ) {
                    composable("home") {
                        HomeScreen(
                            dataManager = dataManager,
                            onFoodSelected = { food ->
                                selectedFood = food
                                navController.navigate("add_food")
                            },
                            onSearchQuery = { query ->
                                navController.navigate("search/${Uri.encode(query)}")
                            }
                        )
                    }

                    composable("search/{query}") { backStackEntry ->
                        val query = Uri.decode(backStackEntry.arguments?.getString("query").orEmpty())
                        SearchScreen(
                            initialQuery = query,
                            dataManager = dataManager,
                            onFoodSelected = { food ->
                                selectedFood = food
                                navController.navigate("add_food")
                            }
                        )
                    }

                    composable("add_food") {
                        selectedFood?.let { food ->
                            AddFoodScreen(
                                food = food,
                                dataManager = dataManager,
                                onSuccess = {
                                    navController.popBackStack("home", inclusive = false)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        dataManager.checkConnectedNodes()
    }

    override fun onGenericMotionEvent(event: android.view.MotionEvent?): Boolean {
        if (event?.action == android.view.MotionEvent.ACTION_SCROLL &&
            event.isFromSource(android.view.InputDevice.SOURCE_ROTARY_ENCODER)
        ) {
            android.util.Log.d("MainActivity", "Rotary motion: axis_scroll=${event.getAxisValue(android.view.MotionEvent.AXIS_SCROLL)}")
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onDestroy() {
        super.onDestroy()
        dataManager.onDestroy()
    }
}
