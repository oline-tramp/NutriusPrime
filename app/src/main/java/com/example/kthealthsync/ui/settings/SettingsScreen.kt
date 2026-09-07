package com.example.kthealthsync.ui.settings

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kthealthsync.domain.model.NutrientGoals
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    onBack: () -> Unit,
    onRequestPermissions: () -> Unit,
    onLoginClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    val cookie by viewModel.cookie.collectAsState()
    val syncHour by viewModel.syncHour.collectAsState()
    val syncMinute by viewModel.syncMinute.collectAsState()
    val autoSyncEnabled by viewModel.autoSyncEnabled.collectAsState()
    val nutrientGoals by viewModel.nutrientGoals.collectAsState()
    val isSyncingGoals by viewModel.isSyncingGoals.collectAsState()

    var showGoalsDialog by remember { mutableStateOf(false) }

    val timePickerDialog = remember(syncHour, syncMinute) {
        TimePickerDialog(
            context,
            { _, hourOfDay, minuteOfHour ->
                viewModel.updateSyncTime(hourOfDay, minuteOfHour)
            },
            syncHour,
            syncMinute,
            true
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            SettingSectionHeader("Authentication")
            SettingItem(
                icon = Icons.Default.Info,
                title = "Kalorické Tabulky Login",
                subtitle = if (cookie.isEmpty()) "Not logged in. Tap to log in." else "Logged in. Tap to re-login if sync fails.",
                onClick = onLoginClick
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFF2A2A2A))

            SettingSectionHeader("Background Synchronization")
            SettingItem(
                icon = Icons.Default.Settings,
                title = "Enable Auto-Sync",
                subtitle = "Automatically fetch yesterday's data in the background.",
                onClick = { viewModel.toggleAutoSync(!autoSyncEnabled) },
                trailingContent = {
                    Switch(
                        checked = autoSyncEnabled,
                        onCheckedChange = { viewModel.toggleAutoSync(it) }
                    )
                }
            )

            if (autoSyncEnabled) {
                SettingItem(
                    icon = Icons.Default.Info,
                    title = "Sync Time",
                    subtitle = "Current time: ${String.format(Locale.US, "%02d:%02d", syncHour, syncMinute)}.\nTap to choose when the background sync should run.",
                    onClick = { timePickerDialog.show() }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFF2A2A2A))

            SettingSectionHeader("Daily Nutritional Goals")
            SettingItem(
                icon = Icons.Default.Edit,
                title = "Target Macros & Micros",
                subtitle = "${nutrientGoals.kcal.toInt()} kcal | P: ${nutrientGoals.protein.toInt()}g, C: ${nutrientGoals.carbs.toInt()}g, F: ${nutrientGoals.fat.toInt()}g\nAuto-synced with KT. Tap to edit manually.",
                onClick = { showGoalsDialog = true }
            )
            SettingItem(
                icon = Icons.Default.Settings,
                title = "Force Sync Goals from KT",
                subtitle = if (cookie.isEmpty()) "Log in first to fetch goals" else "Goals auto-sync in background. Tap to force update immediately.",
                onClick = {
                    if (cookie.isNotEmpty()) {
                        viewModel.syncGoalsFromApi { success ->
                            val msg = if (success) "Goals updated from your KT profile!" else "Failed to fetch goals from KT"
                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        android.widget.Toast.makeText(context, "Please log in first", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                trailingContent = {
                    if (isSyncingGoals) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFF2A2A2A))

            SettingSectionHeader("System")
            SettingItem(
                icon = Icons.Default.Info,
                title = "Health Connect Permissions",
                subtitle = "Review or grant permissions required to write data to Health Connect.",
                onClick = onRequestPermissions
            )
        }
    }

    if (showGoalsDialog) {
        NutrientGoalsDialog(
            currentGoals = nutrientGoals,
            onDismiss = { showGoalsDialog = false },
            onSave = { updated ->
                viewModel.updateNutrientGoals(updated)
                showGoalsDialog = false
            }
        )
    }
}

@Composable
fun NutrientGoalsDialog(
    currentGoals: NutrientGoals,
    onDismiss: () -> Unit,
    onSave: (NutrientGoals) -> Unit
) {
    var kcal by remember { mutableStateOf(currentGoals.kcal.toInt().toString()) }
    var protein by remember { mutableStateOf(currentGoals.protein.toInt().toString()) }
    var carbs by remember { mutableStateOf(currentGoals.carbs.toInt().toString()) }
    var fat by remember { mutableStateOf(currentGoals.fat.toInt().toString()) }
    var fiber by remember { mutableStateOf(currentGoals.fiber.toInt().toString()) }
    var sugar by remember { mutableStateOf(currentGoals.sugar.toInt().toString()) }
    var salt by remember { mutableStateOf(currentGoals.salt.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily Nutritional Goals") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(value = kcal, onValueChange = { kcal = it }, label = { Text("Calories (kcal)") }, singleLine = true)
                OutlinedTextField(value = protein, onValueChange = { protein = it }, label = { Text("Protein (g)") }, singleLine = true)
                OutlinedTextField(value = carbs, onValueChange = { carbs = it }, label = { Text("Carbohydrates (g)") }, singleLine = true)
                OutlinedTextField(value = fat, onValueChange = { fat = it }, label = { Text("Fat (g)") }, singleLine = true)
                OutlinedTextField(value = fiber, onValueChange = { fiber = it }, label = { Text("Fiber (g)") }, singleLine = true)
                OutlinedTextField(value = sugar, onValueChange = { sugar = it }, label = { Text("Sugar (g)") }, singleLine = true)
                OutlinedTextField(value = salt, onValueChange = { salt = it }, label = { Text("Salt (g)") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                val updated = NutrientGoals(
                    kcal = kcal.toDoubleOrNull() ?: currentGoals.kcal,
                    protein = protein.toDoubleOrNull() ?: currentGoals.protein,
                    carbs = carbs.toDoubleOrNull() ?: currentGoals.carbs,
                    fat = fat.toDoubleOrNull() ?: currentGoals.fat,
                    fiber = fiber.toDoubleOrNull() ?: currentGoals.fiber,
                    sugar = sugar.toDoubleOrNull() ?: currentGoals.sugar,
                    salt = salt.toDoubleOrNull() ?: currentGoals.salt
                )
                onSave(updated)
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SettingSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailingContent: @Composable (() -> Unit)? = null
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color.LightGray)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Spacer(modifier = Modifier.height(4.dp))
                Text(subtitle, fontSize = 13.sp, color = Color.Gray, lineHeight = 18.sp)
            }
            if (trailingContent != null) {
                Spacer(modifier = Modifier.width(16.dp))
                trailingContent()
            }
        }
    }
}
