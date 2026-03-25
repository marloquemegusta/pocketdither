package com.arquimea.dithercamera.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF4A261),
    onPrimary = Color(0xFF08111A),
    secondary = Color(0xFF1A6B73),
    tertiary = Color(0xFFC8553D),
    background = Color(0xFF08111A),
    surface = Color(0xFF0F1C24),
    onSurface = Color(0xFFE9F0EA),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1A6B73),
    onPrimary = Color(0xFFE9F0EA),
    secondary = Color(0xFFF4A261),
    tertiary = Color(0xFFC8553D),
    background = Color(0xFFF4F1EA),
    surface = Color(0xFFFFFBF5),
    onSurface = Color(0xFF08111A),
)

@Composable
fun DitherCameraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
