package com.keylingo.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.keylingo.app.data.settings.KeyboardThemePreset
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Every colour the keyboard draws. One instance per (theme, light/dark). */
@Immutable
data class KeyboardColors(
    val background: Color,
    val key: Color,
    val keyText: Color,
    val specialKey: Color,
    val specialKeyText: Color,
    /** Active shift / caps lock / Enter-with-action keys, highlighted autocorrection, popup selection. */
    val accent: Color,
    val onAccent: Color,
    val strip: Color,
    val chip: Color,
    val chipText: Color,
    val translationChip: Color,
    val onTranslationChip: Color,
    /** Spanish word in a hint chip. */
    val hintText: Color,
    /** Secondary text ("working · gerund"), key hint dots. */
    val caption: Color,
    val popup: Color,
    val isDark: Boolean
)

val LocalKeyboardColors = staticCompositionLocalOf { KeyboardThemes.palette(KeyboardThemePreset.SUNLIGHT_YELLOW, null, dark = false) }

/**
 * Builds palettes from one accent colour per preset using tonal (HSL)
 * derivation, so all presets and custom colours follow the same rules:
 * tinted background, near-white (light) or tinted charcoal (dark) letter
 * keys, a stronger tint for special keys, accent for active states, and
 * text colours chosen for contrast (checked by KeyboardThemesTest against
 * WCAG ratios).
 */
object KeyboardThemes {

    /** Light-variant accent and dark-variant accent for each preset. */
    private val presetAccents: Map<KeyboardThemePreset, Pair<Color, Color>> = mapOf(
        KeyboardThemePreset.SUNLIGHT_YELLOW to (Color(0xFFF5B800) to Color(0xFFFFCC33)),
        KeyboardThemePreset.LAVENDER_PURPLE to (Color(0xFF8E6CEF) to Color(0xFFB9A2FF)),
        KeyboardThemePreset.DARK_VIOLET to (Color(0xFF6A2FD8) to Color(0xFFA57CFF)),
        KeyboardThemePreset.MINT_GREEN to (Color(0xFF2FB98A) to Color(0xFF5ED8AE)),
        KeyboardThemePreset.OCEAN_BLUE to (Color(0xFF1E78D6) to Color(0xFF5AA9FF)),
        KeyboardThemePreset.BLUSH_PINK to (Color(0xFFE5679A) to Color(0xFFFF94BD)),
    )

    /** Swatches offered for the custom colour (any ARGB works; these are just quick picks). */
    val customSwatches: List<Int> = listOf(
        0xFFF5B800, 0xFFFF8A00, 0xFFE53935, 0xFFE5679A, 0xFFAB47BC, 0xFF6A2FD8,
        0xFF3949AB, 0xFF1E78D6, 0xFF00ACC1, 0xFF2E9E8F, 0xFF43A047, 0xFF7CB342, 0xFF8D6E63, 0xFF546E7A,
    ).map { it.toInt() }

    fun accentOf(preset: KeyboardThemePreset, customArgb: Int?, dark: Boolean): Color {
        if (preset == KeyboardThemePreset.CUSTOM) {
            val base = Color(customArgb ?: 0xFF2E9E8F.toInt())
            if (!dark) return base
            val (h, s, l) = toHsl(base)
            return hsl(h, s, max(l, 0.66f))
        }
        val (light, darkAccent) = presetAccents.getValue(preset)
        return if (dark) darkAccent else light
    }

    /** Whether to use the dark variant, given the user's dark-mode choice ([systemDark] when it follows the system). */
    fun useDark(preset: KeyboardThemePreset, followSystem: Boolean, forcedDark: Boolean, systemDark: Boolean): Boolean =
        if (followSystem) systemDark || preset.prefersDark else forcedDark

    fun palette(preset: KeyboardThemePreset, customArgb: Int?, dark: Boolean): KeyboardColors {
        val accent = readableAccent(accentOf(preset, customArgb, dark), dark)
        val (h, sRaw, _) = toHsl(accent)
        val s = sRaw.coerceIn(0.15f, 0.9f)
        // Dark Violet is the deliberately rich, saturated dark theme.
        val richness = if (preset == KeyboardThemePreset.DARK_VIOLET) 1.8f else 1f
        val onAccent = bestText(accent, h)
        return if (!dark) {
            val translation = hsl(h, min(s, 0.8f), 0.28f)
            // Clearly tinted (not greige): the keyboard should read as the theme colour at a glance.
            KeyboardColors(
                background = hsl(h, s * 0.95f, 0.88f),
                key = hsl(h, s * 0.6f, 0.985f),
                keyText = hsl(h, 0.25f, 0.12f),
                specialKey = hsl(h, s * 0.9f, 0.76f),
                specialKeyText = hsl(h, 0.35f, 0.14f),
                accent = accent,
                onAccent = onAccent,
                strip = hsl(h, s * 0.95f, 0.88f),
                chip = hsl(h, s * 0.85f, 0.80f),
                chipText = hsl(h, 0.25f, 0.12f),
                translationChip = translation,
                onTranslationChip = hsl(h, 0.3f, 0.97f),
                hintText = hsl(h, min(s, 0.85f), 0.24f),
                caption = hsl(h, 0.2f, 0.30f),
                popup = hsl(h, s * 0.3f, 0.99f),
                isDark = false
            )
        } else {
            KeyboardColors(
                background = hsl(h, s * 0.25f * richness, 0.08f),
                key = hsl(h, s * 0.16f * richness, 0.17f),
                keyText = hsl(h, 0.15f, 0.93f),
                specialKey = hsl(h, s * 0.22f * richness, 0.25f),
                specialKeyText = hsl(h, 0.5f, 0.86f),
                accent = accent,
                onAccent = onAccent,
                strip = hsl(h, s * 0.25f * richness, 0.08f),
                chip = hsl(h, s * 0.2f * richness, 0.21f),
                chipText = hsl(h, 0.15f, 0.93f),
                translationChip = accent,
                onTranslationChip = onAccent,
                hintText = hsl(h, min(s * 1.1f, 0.95f), 0.76f),
                caption = hsl(h, 0.1f, 0.66f),
                popup = hsl(h, s * 0.2f * richness, 0.23f),
                isDark = true
            )
        }
    }

    /**
     * Mid-tone accents (some blues, reds, browns) reach 4.5:1 with neither white
     * nor black text. Shift lightness -- darker in light themes, lighter in dark
     * themes -- keeping the hue, until one of them does.
     */
    private fun readableAccent(accent: Color, dark: Boolean): Color {
        var (h, s, l) = toHsl(accent)
        var c = accent
        repeat(30) {
            if (contrast(bestText(c, h), c) >= 4.6) return c
            l = (if (dark) l + 0.02f else l - 0.02f).coerceIn(0.05f, 0.95f)
            c = hsl(h, s, l)
        }
        return c
    }

    /** Near-black (tinted) or near-white text, whichever contrasts better with [background]. */
    private fun bestText(background: Color, hue: Float): Color {
        val darkText = hsl(hue, 0.4f, 0.1f)
        val lightText = hsl(hue, 0.2f, 0.98f)
        return if (contrast(darkText, background) >= contrast(lightText, background)) darkText else lightText
    }

    /** WCAG 2.x contrast ratio between two colours (1..21). */
    fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return max(la, lb).toDouble() / min(la, lb).toDouble()
    }

    // ---- HSL helpers (pure; no Android dependency)

    data class Hsl(val h: Float, val s: Float, val l: Float)

    fun toHsl(c: Color): Hsl {
        val r = c.red; val g = c.green; val b = c.blue
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
        val l = (mx + mn) / 2f
        if (mx == mn) return Hsl(0f, 0f, l)
        val d = mx - mn
        val s = if (l > 0.5f) d / (2f - mx - mn) else d / (mx + mn)
        val h = when (mx) {
            r -> ((g - b) / d + (if (g < b) 6f else 0f))
            g -> ((b - r) / d + 2f)
            else -> ((r - g) / d + 4f)
        } * 60f
        return Hsl(h, s, l)
    }

    fun hsl(h: Float, s: Float, l: Float): Color {
        val sat = s.coerceIn(0f, 1f); val lig = l.coerceIn(0f, 1f)
        val c = (1f - abs(2f * lig - 1f)) * sat
        val hp = ((h % 360f) + 360f) % 360f / 60f
        val x = c * (1f - abs(hp % 2f - 1f))
        val (r1, g1, b1) = when {
            hp < 1 -> Triple(c, x, 0f); hp < 2 -> Triple(x, c, 0f); hp < 3 -> Triple(0f, c, x)
            hp < 4 -> Triple(0f, x, c); hp < 5 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
        }
        val m = lig - c / 2f
        return Color(r1 + m, g1 + m, b1 + m, 1f)
    }
}
