package com.example.kthealthsync.domain.model

import androidx.compose.runtime.staticCompositionLocalOf

data class AppStrings(
    val language: AppLanguage,

    // Dashboard & Macros
    val withMeals: String,
    val kcalUnit: String,
    val proteinShort: String,
    val fatShort: String,
    val carbsShort: String,
    val fiberShort: String,
    val water: String,
    val fiber: String,
    val sugar: String,
    val salt: String,
    val veg: String,
    val fruit: String,
    val vegFruitTotal: String,
    val supps: String,
    val pcs: String,
    val mealsHeader: String,
    val totalKcalFormat: String,
    val add: String,
    val noDiaryEntries: String,
    val noEntriesInMeal: String,
    val unnamedFood: String,

    // Multi-select & Actions
    val selectedCount: String,
    val deselectAll: String,
    val selectAll: String,
    val moveAction: String,
    val cloneAction: String,
    val deleteAction: String,

    // Drag & Drop
    val releaseToMove: String,
    val releaseToClone: String,
    val dragToMealHint: String,
    val sameMealHint: String,

    // Date Chips & Picker
    val dateThisDay: String,
    val dateToday: String,
    val dateYesterday: String,
    val dateTomorrow: String,
    val chooseAnotherDate: String,

    // Meal Selector
    val mealTimeLabel: String,
    val chooseMeal: String,

    // General Dialogs
    val cancel: String,
    val save: String,
    val clone: String,
    val close: String,
    val retry: String,

    // Move Dialog
    val moveTitle: String,
    val moveMultiTitle: String,
    val whereToMove: String,

    // Clone Dialog
    val cloneTitle: String,
    val cloneMultiTitle: String,
    val whereToClone: String,

    // Edit Dialog
    val editTitle: String,
    val amountLabel: String,
    val unitLabel: String,
    val portionTotal: String,

    // Delete Confirmation
    val deleteConfirmTitle: String,
    val deleteSingleConfirmText: String,
    val deleteMultiConfirmText: String,

    // Toasts & Status Messages
    val deleteSuccess: String,
    val deleteError: String,
    val moveSuccess: String,
    val moveError: String,
    val cloneSuccess: String,
    val cloneError: String,
    val saveSuccess: String,
    val saveError: String,
    val sessionExpired: String,

    // Search
    val searchPlaceholder: String,
    val recentSearches: String,
    val frequentTab: String,
    val favoritesTab: String,
    val clear: String,
    val noResults: String,
    val loadMore: String,
    val nutritionImpactTitle: String,
    val energy: String,
    val protein: String,
    val carbs: String,
    val fat: String,

    // Settings
    val settingsTitle: String,
    val appLanguageLabel: String,
    val ktCookieLabel: String,
    val healthConnectLabel: String,
    val goalsUpdatedSuccess: String,
    val goalsUpdatedError: String,

    // Share & Validation
    val shareDayTitle: String,
    val invalidAmountError: String,
    val idNotFoundError: String,

    // Fallback Meal Names
    val breakfast: String,
    val morningSnack: String,
    val lunch: String,
    val afternoonSnack: String,
    val dinner: String,
    val secondDinner: String,
    val meal: String
)

val RuStrings = AppStrings(
    language = AppLanguage.RU,
    withMeals = "С приёмами",
    kcalUnit = "ккал",
    proteinShort = "Б",
    fatShort = "Ж",
    carbsShort = "У",
    fiberShort = "Кл",
    water = "💧 Вода",
    fiber = "🌾 Клетчатка",
    sugar = "🍬 Сахар",
    salt = "🧂 Соль",
    veg = "🥦 Овощи",
    fruit = "🍎 Фрукты",
    vegFruitTotal = "🥗 Всего",
    supps = "💊 Добавки",
    pcs = "шт",
    mealsHeader = "Приёмы пищи",
    totalKcalFormat = "Всего: %d ккал",
    add = "Добавить",
    noDiaryEntries = "Нет данных дневника за этот день",
    noEntriesInMeal = "Нет записей",
    unnamedFood = "Без названия",

    selectedCount = "Выбрано: %d",
    deselectAll = "Снять всё",
    selectAll = "Все (%d)",
    moveAction = "Перенести",
    cloneAction = "Клон",
    deleteAction = "Удалить",

    releaseToMove = "Отпустите для переноса 📥",
    releaseToClone = "Отпустите для клонирования 📋",
    dragToMealHint = "Перетащите на приём пищи",
    sameMealHint = "Тот же приём",

    dateThisDay = "Этот день",
    dateToday = "Сегодня",
    dateYesterday = "Вчера",
    dateTomorrow = "Завтра",
    chooseAnotherDate = "Выбрать другую дату...",

    mealTimeLabel = "Приём пищи",
    chooseMeal = "Выберите приём",

    cancel = "Отмена",
    save = "Сохранить",
    clone = "Клонировать",
    close = "Закрыть",
    retry = "Повторить",

    moveTitle = "Перенос записи",
    moveMultiTitle = "Перенос %d записей",
    whereToMove = "Куда перенести:",

    cloneTitle = "📋 Клонирование",
    cloneMultiTitle = "📋 Клонирование %d записей",
    whereToClone = "Куда клонировать:",

    editTitle = "✏️ Редактирование",
    amountLabel = "Количество",
    unitLabel = "Единица",
    portionTotal = "Итого за порцию:",

    deleteConfirmTitle = "Удалить запись?",
    deleteSingleConfirmText = "Вы уверены, что хотите удалить «%s» из дневника?",
    deleteMultiConfirmText = "Вы уверены, что хотите удалить выбранные записи (%d шт.) из дневника?",

    deleteSuccess = "Записи удалены",
    deleteError = "Ошибка при удалении",
    moveSuccess = "Записи успешно перенесены",
    moveError = "Ошибка при переносе",
    cloneSuccess = "Записи успешно клонированы",
    cloneError = "Ошибка при клонировании",
    saveSuccess = "Запись сохранена",
    saveError = "Ошибка сохранения изменений",
    sessionExpired = "Сессия истекла. Пожалуйста, войдите снова в Настройках.",

    searchPlaceholder = "Поиск продуктов...",
    recentSearches = "Недавние поиски",
    frequentTab = "Частые",
    favoritesTab = "Избранное",
    clear = "Очистить",
    noResults = "Ничего не найдено",
    loadMore = "Загрузить ещё...",
    nutritionImpactTitle = "Влияние на суточную норму",
    energy = "Энергия",
    protein = "Белки",
    carbs = "Углеводы",
    fat = "Жиры",

    settingsTitle = "Настройки",
    appLanguageLabel = "Язык интерфейса",
    ktCookieLabel = "Cookie Kalorické Tabulky",
    healthConnectLabel = "Health Connect",
    goalsUpdatedSuccess = "Цели обновлены из профиля КТ!",
    goalsUpdatedError = "Не удалось обновить цели из КТ",

    shareDayTitle = "Поделиться данными дня",
    invalidAmountError = "Введите корректное количество",
    idNotFoundError = "ID записи не найден",

    breakfast = "Завтрак",
    morningSnack = "Второй завтрак",
    lunch = "Обед",
    afternoonSnack = "Полдник",
    dinner = "Ужин",
    secondDinner = "Второй ужин",
    meal = "Приём пищи"
)

val CsStrings = AppStrings(
    language = AppLanguage.CS,
    withMeals = "S jídly",
    kcalUnit = "kcal",
    proteinShort = "B",
    fatShort = "T",
    carbsShort = "S",
    fiberShort = "Vl",
    water = "💧 Voda",
    fiber = "🌾 Vláknina",
    sugar = "🍬 Cukr",
    salt = "🧂 Sůl",
    veg = "🥦 Zelenina",
    fruit = "🍎 Ovoce",
    vegFruitTotal = "🥗 Celkem",
    supps = "💊 Doplňky",
    pcs = "ks",
    mealsHeader = "Jídla dne",
    totalKcalFormat = "Celkem: %d kcal",
    add = "Přidat",
    noDiaryEntries = "Žádné záznamy v deníku pro tento den",
    noEntriesInMeal = "Žádné položky",
    unnamedFood = "Bez názvu",

    selectedCount = "Vybráno: %d",
    deselectAll = "Zrušit vše",
    selectAll = "Vše (%d)",
    moveAction = "Přesunout",
    cloneAction = "Klonovat",
    deleteAction = "Smazat",

    releaseToMove = "Pusťte pro přesun 📥",
    releaseToClone = "Pusťte pro klonování 📋",
    dragToMealHint = "Přetáhněte na jídlo",
    sameMealHint = "Stejné jídlo",

    dateThisDay = "Tento den",
    dateToday = "Dnes",
    dateYesterday = "Včera",
    dateTomorrow = "Zítra",
    chooseAnotherDate = "Vybrat jiné datum...",

    mealTimeLabel = "Jídlo",
    chooseMeal = "Vyberte jídlo",

    cancel = "Zrušit",
    save = "Uložit",
    clone = "Klonovat",
    close = "Zavřít",
    retry = "Zkusit znovu",

    moveTitle = "Přesun položky",
    moveMultiTitle = "Přesun %d položek",
    whereToMove = "Kam přesunout:",

    cloneTitle = "📋 Klonování",
    cloneMultiTitle = "📋 Klonování %d položek",
    whereToClone = "Kam klonovat:",

    editTitle = "✏️ Úprava",
    amountLabel = "Množství",
    unitLabel = "Jednotka",
    portionTotal = "Celkem za porci:",

    deleteConfirmTitle = "Smazat položku?",
    deleteSingleConfirmText = "Opravdu chcete smazat «%s» z deníku?",
    deleteMultiConfirmText = "Opravdu chcete smazat vybrané položky (%d ks) z deníku?",

    deleteSuccess = "Položky smazány",
    deleteError = "Chyba při mazání",
    moveSuccess = "Položky úspěšně přesunuty",
    moveError = "Chyba při přesunu",
    cloneSuccess = "Položky úspěšně naklonovány",
    cloneError = "Chyba při klonování",
    saveSuccess = "Položka uložena",
    saveError = "Chyba při ukládání",
    sessionExpired = "Platnost relace vypršela. Přihlaste se prosím znovu v Nastavení.",

    searchPlaceholder = "Hledat potraviny...",
    recentSearches = "Nedávná hledání",
    frequentTab = "Časté",
    favoritesTab = "Oblíbené",
    clear = "Vymazat",
    noResults = "Nebylo nic nalezeno",
    loadMore = "Načíst další...",
    nutritionImpactTitle = "Vliv na denní příjem",
    energy = "Energie",
    protein = "Bílkoviny",
    carbs = "Sacharidy",
    fat = "Tuky",

    settingsTitle = "Nastavení",
    appLanguageLabel = "Jazyk rozhraní",
    ktCookieLabel = "Cookie Kalorické Tabulky",
    healthConnectLabel = "Health Connect",
    goalsUpdatedSuccess = "Cíle aktualizovány z profilu KT!",
    goalsUpdatedError = "Nepodařilo se aktualizovat cíle z KT",

    shareDayTitle = "Sdílet data dne",
    invalidAmountError = "Zadejte platné množství",
    idNotFoundError = "ID záznamu nebylo nalezeno",

    breakfast = "Snídaně",
    morningSnack = "Dopolední svačina",
    lunch = "Oběd",
    afternoonSnack = "Odpolední svačina",
    dinner = "Večeře",
    secondDinner = "Druhá večeře",
    meal = "Jídlo"
)

val EnStrings = AppStrings(
    language = AppLanguage.EN,
    withMeals = "With meals",
    kcalUnit = "kcal",
    proteinShort = "P",
    fatShort = "F",
    carbsShort = "C",
    fiberShort = "Fib",
    water = "💧 Water",
    fiber = "🌾 Fiber",
    sugar = "🍬 Sugar",
    salt = "🧂 Salt",
    veg = "🥦 Veg",
    fruit = "🍎 Fruit",
    vegFruitTotal = "🥗 Total",
    supps = "💊 Supps",
    pcs = "pcs",
    mealsHeader = "Meals",
    totalKcalFormat = "Total: %d kcal",
    add = "Add",
    noDiaryEntries = "No diary data for this day",
    noEntriesInMeal = "No entries",
    unnamedFood = "Unnamed",

    selectedCount = "Selected: %d",
    deselectAll = "Deselect all",
    selectAll = "All (%d)",
    moveAction = "Move",
    cloneAction = "Clone",
    deleteAction = "Delete",

    releaseToMove = "Release to move 📥",
    releaseToClone = "Release to clone 📋",
    dragToMealHint = "Drag to a meal",
    sameMealHint = "Same meal",

    dateThisDay = "This day",
    dateToday = "Today",
    dateYesterday = "Yesterday",
    dateTomorrow = "Tomorrow",
    chooseAnotherDate = "Choose another date...",

    mealTimeLabel = "Meal",
    chooseMeal = "Select meal",

    cancel = "Cancel",
    save = "Save",
    clone = "Clone",
    close = "Close",
    retry = "Retry",

    moveTitle = "Move entry",
    moveMultiTitle = "Move %d entries",
    whereToMove = "Move to:",

    cloneTitle = "📋 Clone",
    cloneMultiTitle = "📋 Clone %d entries",
    whereToClone = "Clone to:",

    editTitle = "✏️ Edit",
    amountLabel = "Amount",
    unitLabel = "Unit",
    portionTotal = "Total for portion:",

    deleteConfirmTitle = "Delete entry?",
    deleteSingleConfirmText = "Are you sure you want to delete «%s» from diary?",
    deleteMultiConfirmText = "Are you sure you want to delete %d selected entries?",

    deleteSuccess = "Entries deleted",
    deleteError = "Error deleting entries",
    moveSuccess = "Entries moved successfully",
    moveError = "Error moving entries",
    cloneSuccess = "Entries cloned successfully",
    cloneError = "Error cloning entries",
    saveSuccess = "Entry saved",
    saveError = "Error saving changes",
    sessionExpired = "Session expired. Please log in again in Settings.",

    searchPlaceholder = "Search food...",
    recentSearches = "Recent searches",
    frequentTab = "Frequent",
    favoritesTab = "Favorites",
    clear = "Clear",
    noResults = "No results found",
    loadMore = "Load more...",
    nutritionImpactTitle = "Daily intake impact",
    energy = "Energy",
    protein = "Protein",
    carbs = "Carbohydrates",
    fat = "Fat",

    settingsTitle = "Settings",
    appLanguageLabel = "App Language",
    ktCookieLabel = "Kalorické Tabulky Cookie",
    healthConnectLabel = "Health Connect",
    goalsUpdatedSuccess = "Goals updated from KT profile!",
    goalsUpdatedError = "Failed to update goals from KT",

    shareDayTitle = "Share daily data",
    invalidAmountError = "Enter a valid amount",
    idNotFoundError = "Entry ID not found",

    breakfast = "Breakfast",
    morningSnack = "Morning snack",
    lunch = "Lunch",
    afternoonSnack = "Afternoon snack",
    dinner = "Dinner",
    secondDinner = "Second dinner",
    meal = "Meal"
)

fun getAppStrings(language: AppLanguage): AppStrings {
    return when (language) {
        AppLanguage.CS -> CsStrings
        AppLanguage.EN -> EnStrings
        AppLanguage.RU -> RuStrings
        AppLanguage.AUTO -> RuStrings // Resolved language should be passed, fallback to RU
    }
}

val LocalAppStrings = staticCompositionLocalOf { RuStrings }
