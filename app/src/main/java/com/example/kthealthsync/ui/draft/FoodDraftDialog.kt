package com.example.kthealthsync.ui.draft

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.kthealthsync.domain.draft.DraftUnit
import com.example.kthealthsync.domain.draft.FoodDraftItem
import com.example.kthealthsync.domain.draft.FoodDraftParser
import com.google.mlkit.genai.common.FeatureStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodDraftDialog(model: FoodDraftViewModel, onClose: () -> Unit, onSearch: (FoodDraftItem) -> Unit) {
    val input by model.input.collectAsState()
    val draftSource by model.draftSource.collectAsState()
    val rows by model.items.collectAsState()
    val busy by model.busy.collectAsState()
    val message by model.message.collectAsState()
    val availability by model.availability.collectAsState()
    var confirmReplace by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val language = com.example.kthealthsync.domain.model.LocalAppStrings.current.language
    val clearLabel = when (language) {
        com.example.kthealthsync.domain.model.AppLanguage.CS -> "Vymazat koncept"
        com.example.kthealthsync.domain.model.AppLanguage.EN -> "Clear draft"
        else -> "Очистить черновик"
    }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) { model.check() }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) model.cancel()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); model.cancel() }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            modifier = Modifier.fillMaxSize().imePadding(),
            topBar = {
                TopAppBar(title = { Text("Черновик еды · альфа") }, navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                })
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    Text("Опишите, что съели. Можно надиктовать через микрофон клавиатуры. Nano готовит черновик на телефоне; каждое добавление вы подтверждаете отдельно.")
                }
                item {
                    OutlinedTextField(
                        value = input, onValueChange = model::editInput,
                        modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 6,
                        enabled = !busy,
                        label = { Text("Что съели?") },
                        placeholder = { Text("Борщ 200 г, один помидор 150 г и Kinder Joy") },
                        supportingText = { Text("${input.length}/${FoodDraftParser.MAX_INPUT}") },
                        trailingIcon = {
                            if (input.isNotEmpty() && !busy) {
                                IconButton(onClick = { model.editInput("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Очистить")
                                }
                            }
                        }
                    )
                }
                item {
                    val status = when (availability) {
                        FeatureStatus.AVAILABLE -> "Nano готов"
                        FeatureStatus.DOWNLOADABLE -> "Нужно скачать модель"
                        FeatureStatus.DOWNLOADING -> "Модель загружается"
                        FeatureStatus.UNAVAILABLE -> "Nano недоступен на этом устройстве. Обычный поиск работает."
                        else -> "Проверка Nano…"
                    }
                    Text(status, style = MaterialTheme.typography.bodySmall)
                    message?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }
                    if (busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                        TextButton(onClick = model::cancel) { Text("Остановить") }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (availability == FeatureStatus.DOWNLOADABLE) {
                                Button(onClick = model::download) { Text("Скачать модель") }
                            } else if (availability == FeatureStatus.AVAILABLE) {
                                Button(enabled = input.isNotBlank(), onClick = {
                                    if (rows.isEmpty()) model.generate() else confirmReplace = true
                                }) { Text(if (rows.isEmpty()) "Подготовить черновик" else "Разобрать заново") }
                            }
                            TextButton(onClick = model::check) { Text("Проверить Nano") }
                        }
                    }
                }
                if (rows.isNotEmpty() || input.isNotBlank()) item {
                    TextButton(onClick = { confirmClear = true }, enabled = !busy) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(clearLabel)
                    }
                }
                if (rows.isNotEmpty()) item {
                    if (input.trim() != draftSource.trim()) {
                        Text("Предыдущий черновик — он не относится к текущему тексту.", color = MaterialTheme.colorScheme.error)
                    }
                    Text("Проверьте продукты и количества", style = MaterialTheme.typography.titleMedium)
                    Text("Удалите лишнее. Затем выберите каждый продукт в базе и проверьте порцию в форме добавления.")
                }
                items(rows, key = { it.id }) { row ->
                    DraftRow(row, enabled = !busy, onEdit = model::edit, onDelete = { model.remove(row.id) }, onSearch = { onSearch(row) })
                }
            }
        }
        if (confirmClear) AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(clearLabel + "?") },
            text = { Text(when (language) {
                com.example.kthealthsync.domain.model.AppLanguage.CS -> "Odstranit všechny položky konceptu a původní text? Již přidané jídlo zůstane v deníku."
                com.example.kthealthsync.domain.model.AppLanguage.EN -> "Remove all draft items and the original text? Food already added will remain in the diary."
                else -> "Удалить все строки черновика и исходный текст? Уже добавленная еда останется в дневнике."
            }) },
            confirmButton = { TextButton(enabled = !busy, onClick = { model.clear(); confirmClear = false }) { Text(clearLabel) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(com.example.kthealthsync.domain.model.LocalAppStrings.current.cancel) } }
        )
        if (confirmReplace) AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text("Заменить черновик?") },
            text = { Text("Новый разбор заменит оставшиеся строки и ваши правки. Уже добавленная еда останется в дневнике.") },
            confirmButton = { TextButton(onClick = { confirmReplace = false; model.generate() }) { Text("Заменить") } },
            dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun DraftRow(row: FoodDraftItem, enabled: Boolean, onEdit: (FoodDraftItem) -> Unit, onDelete: () -> Unit, onSearch: () -> Unit) {
    var unitsOpen by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row {
                OutlinedTextField(
                    value = row.name, onValueChange = { onEdit(row.copy(name = it.take(120), nameNeedsReview = false)) },
                    enabled = enabled, label = { Text("Продукт") }, modifier = Modifier.weight(1f), singleLine = true
                )
                IconButton(onClick = onDelete, enabled = enabled) { Icon(Icons.Default.Delete, "Удалить ${row.name}") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = row.amount, onValueChange = { onEdit(row.copy(amount = it.take(12))) }, enabled = enabled,
                    label = { Text("Количество") }, modifier = Modifier.weight(1f), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Box {
                    OutlinedButton(onClick = { unitsOpen = true }, enabled = enabled) { Text(row.unit.label) }
                    DropdownMenu(expanded = unitsOpen, onDismissRequest = { unitsOpen = false }) {
                        DraftUnit.entries.forEach { unit ->
                            DropdownMenuItem(text = { Text(unit.label) }, onClick = { onEdit(row.copy(unit = unit)); unitsOpen = false })
                        }
                    }
                }
            }
            if (row.nameNeedsReview) {
                Text("Nano изменил название. Сверьте его с исходным текстом или исправьте.", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { onEdit(row.copy(nameNeedsReview = false)) }, enabled = enabled) { Text("Название верное") }
            }
            if (row.historyVerified) {
                Text("Из дневника · исходная порция: ${row.historyPortion.orEmpty()}", color = MaterialTheme.colorScheme.primary)
            } else if (row.foodId != null) {
                Text("Nano предложил совпадение с историей. Проверьте продукт и порцию.", color = MaterialTheme.colorScheme.primary)
            } else if (row.yesterday) {
                Text("Ссылка на прошлый прием: выберите рецепт в поиске.", color = MaterialTheme.colorScheme.primary)
            }
            if (FoodDraftParser.portion(row) == null) Text("Количество нужно уточнить в форме добавления.")
            Button(onClick = onSearch, enabled = enabled && row.name.isNotBlank() && !row.nameNeedsReview, modifier = Modifier.fillMaxWidth()) { Text("Найти и проверить порцию") }
        }
    }
}
