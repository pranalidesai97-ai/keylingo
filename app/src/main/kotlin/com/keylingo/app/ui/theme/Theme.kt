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

@Composable
fun KeyLingoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = KeyLingoTypography,
        content = content
    )
}
