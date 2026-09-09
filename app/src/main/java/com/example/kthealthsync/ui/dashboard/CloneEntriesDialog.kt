package com.example.kthealthsync.ui.dashboard

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kthealthsync.domain.model.LocalAppStrings
import com.example.kthealthsync.domain.util.NutrientUtils
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloneEntriesDialog(
    count: Int,
    currentDate: ZonedDateTime,
    onDismiss: () -> Unit,
    onConfirm: (targetTimeId: String, targetDateStr: String?) -> Unit
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    var selectedMealTimeId by remember { mutableStateOf("1") }
    var mealExpanded by remember { mutableStateOf(false) }
    val mealOptions = remember(strings) { NutrientUtils.getStandardMealOptions(strings) }
    var targetDate by remember { mutableStateOf(currentDate.toLocalDate()) }
    val formatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (count == 1) strings.cloneTitle else String.format(strings.cloneMultiTitle, count),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(
                    text = strings.whereToClone,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Date selection chips
                Text(
                    text = "${strings.dateToday.substringBefore(" ")}: ${targetDate.format(formatter)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = targetDate == currentDate.toLocalDate(),
                        onClick = { targetDate = currentDate.toLocalDate() },
                        label = { Text(strings.dateThisDay, fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = targetDate == LocalDate.now(),
                        onClick = { targetDate = LocalDate.now() },
                        label = { Text(strings.dateToday, fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = targetDate == LocalDate.now().plusDays(1),
                        onClick = { targetDate = LocalDate.now().plusDays(1) },
                        label = { Text(strings.dateTomorrow, fontSize = 11.sp) }
                    )
                }

                TextButton(
                    onClick = {
                        val dialog = DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                targetDate = LocalDate.of(year, month + 1, dayOfMonth)
                            },
                            targetDate.year,
                            targetDate.monthValue - 1,
                            targetDate.dayOfMonth
                        )
                        dialog.show()
                    },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(strings.chooseAnotherDate, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFF2C2C2C))
                Spacer(modifier = Modifier.height(12.dp))

                // Meal Time Combobox
                ExposedDropdownMenuBox(
                    expanded = mealExpanded,
                    onExpandedChange = { mealExpanded = !mealExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = mealOptions.find { it.first == selectedMealTimeId }?.second ?: strings.chooseMeal,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(strings.mealTimeLabel, fontSize = 12.sp) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = mealExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color(0xFF444444)
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = mealExpanded,
                        onDismissRequest = { mealExpanded = false }
                    ) {
                        mealOptions.forEach { (id, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    selectedMealTimeId = id
                                    mealExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(selectedMealTimeId, targetDate.format(formatter))
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2E7D32),
                    contentColor = Color.White
                )
            ) {
                Text(strings.clone, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}
