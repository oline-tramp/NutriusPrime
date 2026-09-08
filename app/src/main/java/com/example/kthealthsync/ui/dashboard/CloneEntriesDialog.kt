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
import com.example.kthealthsync.domain.util.NutrientUtils
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Composable
fun CloneEntriesDialog(
    count: Int,
    currentDate: ZonedDateTime,
    onDismiss: () -> Unit,
    onConfirm: (targetTimeId: String, targetDateStr: String?) -> Unit
) {
    val context = LocalContext.current
    var selectedMealTimeId by remember { mutableStateOf("1") }
    var targetDate by remember { mutableStateOf(currentDate.toLocalDate()) }
    val formatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "📋 Клонирование ${if (count == 1) "записи" else "$count записей"}",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(
                    text = "Куда клонировать:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Date selection chips
                Text(
                    text = "Дата: ${targetDate.format(formatter)}",
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
                        label = { Text("Этот день", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = targetDate == LocalDate.now(),
                        onClick = { targetDate = LocalDate.now() },
                        label = { Text("Сегодня", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = targetDate == LocalDate.now().plusDays(1),
                        onClick = { targetDate = LocalDate.now().plusDays(1) },
                        label = { Text("Завтра", fontSize = 11.sp) }
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
                    Text("Выбрать другую дату...", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFF2C2C2C))
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Приём пищи:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(4.dp))

                NutrientUtils.STANDARD_MEAL_OPTIONS.forEach { (id, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedMealTimeId = id }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedMealTimeId == id,
                            onClick = { selectedMealTimeId = id },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            color = Color.LightGray
                        )
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
                Text("Клонировать", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
