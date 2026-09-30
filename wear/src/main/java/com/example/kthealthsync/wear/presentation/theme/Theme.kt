package com.example.kthealthsync.wear.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

val EmeraldPrimary = Color(0xFF10B981)
val EmeraldVariant = Color(0xFF059669)
val AmberSecondary = Color(0xFFF59E0B)
val DarkSurface = Color(0xFF1C1C1E)
val DarkBackground = Color(0xFF000000)

val WearColors = Colors(
    primary = EmeraldPrimary,
    primaryVariant = EmeraldVariant,
    secondary = AmberSecondary,
    secondaryVariant = AmberSecondary,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun NutriusWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = WearColors,
        content = content
    )
}
