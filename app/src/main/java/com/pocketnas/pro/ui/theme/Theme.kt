package com.pocketnas.pro.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BluePrimary = Color(0xFF2F6BFF)
val BluePurple = Color(0xFF6A5CFF)
val BgLight = Color(0xFFF3F5F9)

private val LightColors = lightColorScheme(
    primary = BluePrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8EFFF),
    onPrimaryContainer = Color(0xFF0A1F4D),
    secondary = BluePurple,
    background = BgLight,
    surface = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7AA0FF),
    onPrimary = Color(0xFF0A1F4D),
    primaryContainer = Color(0xFF1E3A8A),
    secondary = Color(0xFF9D8FFF),
    background = Color(0xFF101318),
    surface = Color(0xFF171A20),
)

@Composable
fun PocketNasTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
