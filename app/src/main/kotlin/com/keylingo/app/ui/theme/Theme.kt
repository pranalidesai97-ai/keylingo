package com.keylingo.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Indigo40,
    onPrimary = Color.White,
    primaryContainer = IndigoContainer,
    secondary = Amber40,
    background = KeyLightBackground,
    surface = KeySurfaceLight,
    onBackground = KeyLightOnSurface,
    onSurface = KeyLightOnSurface,
    surfaceVariant = HintChipLight
)

private val DarkColors = darkColorScheme(
    primary = Indigo80,
    onPrimary = KeyDarkOnSurface,
    primaryContainer = Indigo40,
    secondary = Amber80,
    background = KeyDarkBackground,
    surface = KeySurfaceDark,
    onBackground = KeyDarkOnSurface,
    onSurface = KeyDarkOnSurface,
    surfaceVariant = HintChipDark
)

/**
 * Material theme for the companion app and for text defaults inside the
 * keyboard. [accent] (the keyboard theme's accent) tints primary elements so
 * Settings matches the chosen keyboard theme.
 */
@Composable
fun KeyLingoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: Color? = null,
    onAccent: Color? = null,
    content: @Composable () -> Unit
) {
    val base = if (darkTheme) DarkColors else LightColors
    val colorScheme = if (accent != null && onAccent != null) base.copy(primary = accent, onPrimary = onAccent) else base
    MaterialTheme(
        colorScheme = colorScheme,
        typography = KeyLingoTypography,
        content = content
    )
}
