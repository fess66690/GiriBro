package ru.petproject.giribro.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Lime,
    onPrimary = OnLime,
    primaryContainer = LimeDark,
    onPrimaryContainer = Lime,
    secondary = Amber,
    onSecondary = Color(0xFF1A1300),
    background = BackgroundDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    error = Color(0xFFFF8A80)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF5A6B00),
    onPrimary = Color.White,
    primaryContainer = Lime,
    onPrimaryContainer = OnLime,
    secondary = Color(0xFFA66800),
    background = BackgroundLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    error = Color(0xFFB3261E)
)

@Composable
fun GiriBroTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = GiriBroTypography,
        content = content
    )
}
