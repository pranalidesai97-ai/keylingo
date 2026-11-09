package com.keylingo.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

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
 * Material theme for text defaults inside the keyboard (system font; keep as is,
 * the keyboard's rendering depends on it). The app screens use [KeyLingoAppTheme].
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

/** App screens (setup, settings, themes, learned words): softer page background so cards read as surfaces. */
private val AppLightColors = LightColors.copy(
    background = Color(0xFFF3F4F8),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEDEEF4),
    onSurfaceVariant = Color(0xFF5B5E6B),
    outlineVariant = Color(0xFFDCDEE6),
)
private val AppDarkColors = DarkColors.copy(
    // Dark text on the pale fallback primary (used only when no keyboard accent is passed): readable buttons and switch thumbs.
    onPrimary = Color(0xFF1B1D4D),
    background = Color(0xFF121317),
    surface = Color(0xFF1D1E23),
    surfaceVariant = Color(0xFF272930),
    onSurfaceVariant = Color(0xFFB4B7C2),
    outlineVariant = Color(0xFF33353D),
)

/** Consistent corner radii across the app. */
val KeyLingoAppShapes = androidx.compose.material3.Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

/**
 * Theme for the companion app's screens: Plus Jakarta Sans, app shapes and page colours.
 * The keyboard keeps [KeyLingoTheme] (system font), so its rendering is unaffected.
 */
@Composable
fun KeyLingoAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: Color? = null,
    onAccent: Color? = null,
    content: @Composable () -> Unit
) {
    val base = if (darkTheme) AppDarkColors else AppLightColors
    // Tonal buttons ("Manage learned words") use a tint of the theme accent rather than Material's default lavender,
    // so every button on a screen belongs to the selected keyboard theme, in light and dark.
    val colorScheme = if (accent != null && onAccent != null) base.copy(
        primary = accent, onPrimary = onAccent,
        secondaryContainer = lerp(base.surface, accent, if (darkTheme) 0.24f else 0.14f),
        onSecondaryContainer = base.onSurface,
    ) else base
    MaterialTheme(colorScheme = colorScheme, typography = KeyLingoAppTypography, shapes = KeyLingoAppShapes, content = content)
}
