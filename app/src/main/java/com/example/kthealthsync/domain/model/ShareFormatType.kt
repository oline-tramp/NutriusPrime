package com.example.kthealthsync.domain.model

enum class ShareFormatType(
    val title: String,
    val subtitle: String,
    val icon: String
) {
    WHOOP_BEHAVIORS_LLM(
        title = "Журнал Whoop (ИИ)",
        subtitle = "Промпт для Whoop AI Coach: инструкция и сводка дня",
        icon = "⚡"
    ),
    HOW_TO_HIT_GOALS_LLM(
        title = "Как добить до нормы (ИИ)",
        subtitle = "Промпт для нутрициолога: что доесть для закрытия дефицитов",
        icon = "🎯"
    ),
    DAILY_SUMMARY(
        title = "Общая сводка дня",
        subtitle = "Чистый отчёт для тренера, заметок или архива (без ИИ-инструкций)",
        icon = "📊"
    ),
    DIET_ANALYSIS_LLM(
        title = "Анализ качества рациона (ИИ)",
        subtitle = "Промпт для ИИ: баланс, качество продуктов и рекомендации",
        icon = "🧠"
    ),
    FOOD_LIST_ONLY(
        title = "Только список продуктов",
        subtitle = "Краткий дневник съеденного по приёмам пищи без цифр норм",
        icon = "📋"
    ),
    JSON_EXPORT(
        title = "Экспорт в JSON",
        subtitle = "Структурированные данные дня для интеграций, Notion и скриптов",
        icon = "💻"
    )
}
