package com.keylingo.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
    val isDark: Boolean,
    /**
     * Gradient stops, drawn with [toBrush]. One stop means a flat colour, which is
     * what the derived palettes (Ocean Mist, custom colour) use: they render
     * exactly as before. The single colours above stay the representative
     * (middle) tone for contrast checks and small surfaces.
     */
    val backgroundGradient: List<Color> = listOf(background),
    val specialKeyGradient: List<Color> = listOf(specialKey),
    val chipGradient: List<Color> = listOf(chip),
    val accentGradient: List<Color> = listOf(accent),
    val translationGradient: List<Color> = listOf(translationChip),
)

/** A flat colour for one stop, otherwise a diagonal (top-start to bottom-end) multi-stop gradient. */
fun List<Color>.toBrush(): Brush = if (size == 1) SolidColor(first()) else Brush.linearGradient(this)

val LocalKeyboardColors = staticCompositionLocalOf { KeyboardThemes.palette(KeyboardThemePreset.DEFAULT, null, dark = false) }

/**
 * Keyboard palettes.
 * - Ocean Mist's colours (the approved former "Ocean Blue") and the custom
 *   colour are derived from one accent colour by tonal (HSL) rules, unchanged:
 *   tinted background, near-white (light) or tinted charcoal (dark) letter
 *   keys, a stronger tint for special keys, accent for active states. Ocean
 *   Mist adds gradient stops around those colours ([oceanMistGradients]).
 * - Sunset Bloom, Aqua Breeze, Golden Dune and Slate Grey are hand-tuned
 *   three-stop gradients ([gradientThemes]) for the keyboard background,
 *   special keys, chips, accent and translation surfaces, with off-white
 *   letter keys. Every text colour reaches WCAG AA (4.5:1) against every
 *   stop it sits on; KeyboardThemesTest checks this.
 */
object KeyboardThemes {

    /** Light-variant accent and dark-variant accent for each preset. */
    private val presetAccents: Map<KeyboardThemePreset, Pair<Color, Color>> = mapOf(
        // Ocean Mist keeps the approved Ocean Blue accents exactly.
        KeyboardThemePreset.OCEAN_MIST to (Color(0xFF1E78D6) to Color(0xFF5AA9FF)),
    )

    /** Light and dark gradient variants of one preset. */
    class GradientTheme(val light: GradientSpec, val dark: GradientSpec)

    class GradientSpec(
        val background: List<Color>, val specialKey: List<Color>, val chip: List<Color>,
        val accent: List<Color>, val onAccent: Color, val translation: List<Color>, val onTranslation: Color,
        val key: Color, val keyText: Color, val specialKeyText: Color, val chipText: Color,
        val hintText: Color, val caption: Color, val popup: Color
    )

    // Values generated from a palette prototype that checked every text/surface contrast pair.
    internal val gradientThemes: Map<KeyboardThemePreset, GradientTheme> = mapOf(
        KeyboardThemePreset.SUNSET_BLOOM to GradientTheme(
            light = GradientSpec(
                background = listOf(Color(0xFFF4C6D3), Color(0xFFF7C2B5), Color(0xFFFAD9C1)),
                specialKey = listOf(Color(0xFFEBA9BB), Color(0xFFEFA694), Color(0xFFF3BE9C)),
                chip = listOf(Color(0xFFFBE4EB), Color(0xFFFCE3DB), Color(0xFFFDEEE3)),
                accent = listOf(Color(0xFFE0708F), Color(0xFFE8806A), Color(0xFFEE9C6C)), onAccent = Color(0xFF2B1117),
                translation = listOf(Color(0xFF8C2D4E), Color(0xFFA03D2E), Color(0xFFA35528)), onTranslation = Color(0xFFFFF7F5),
                key = Color(0xFFFFF8F7), keyText = Color(0xFF3A1E25), specialKeyText = Color(0xFF3A1E25), chipText = Color(0xFF3A1E25),
                hintText = Color(0xFF6B291F), caption = Color(0xFF604943), popup = Color(0xFFFFF8F7)
            ),
            dark = GradientSpec(
                background = listOf(Color(0xFF231015), Color(0xFF321913), Color(0xFF3D2717)),
                specialKey = listOf(Color(0xFF54363E), Color(0xFF573A33), Color(0xFF584132)),
                chip = listOf(Color(0xFF452C33), Color(0xFF46312A), Color(0xFF47362A)),
                accent = listOf(Color(0xFFE0708F), Color(0xFFE8806A), Color(0xFFEE9C6C)), onAccent = Color(0xFF2B1117),
                translation = listOf(Color(0xFFE0708F), Color(0xFFE8806A), Color(0xFFEE9C6C)), onTranslation = Color(0xFF2B1117),
                key = Color(0xFF443734), keyText = Color(0xFFECEFF2), specialKeyText = Color(0xFFF0DBD6), chipText = Color(0xFFECEFF2),
                hintText = Color(0xFFE8BBB0), caption = Color(0xFFBCADA9), popup = Color(0xFF503E3A)
            )
        ),
        KeyboardThemePreset.AQUA_BREEZE to GradientTheme(
            light = GradientSpec(
                background = listOf(Color(0xFF86D3D1), Color(0xFFB4EAF0), Color(0xFFD6F7E3)),
                specialKey = listOf(Color(0xFF71C8C4), Color(0xFF84D5DD), Color(0xFF95DCB5)),
                chip = listOf(Color(0xFFDDF5F4), Color(0xFFE1F8F9), Color(0xFFE3F8EC)),
                accent = listOf(Color(0xFF33B5AE), Color(0xFF3CC3D0), Color(0xFF4CC493)), onAccent = Color(0xFF0B2523),
                translation = listOf(Color(0xFF0B615D), Color(0xFF0E6673), Color(0xFF156B49)), onTranslation = Color(0xFFF3FCFB),
                key = Color(0xFFF6FDFC), keyText = Color(0xFF10302E), specialKeyText = Color(0xFF10302E), chipText = Color(0xFF10302E),
                hintText = Color(0xFF0B5560), caption = Color(0xFF324A4B), popup = Color(0xFFF6FDFC)
            ),
            dark = GradientSpec(
                background = listOf(Color(0xFF0A191A), Color(0xFF102C35), Color(0xFF173D2F)),
                specialKey = listOf(Color(0xFF3A504F), Color(0xFF375153), Color(0xFF385244)),
                chip = listOf(Color(0xFF2F4141), Color(0xFF2D4243), Color(0xFF2E4237)),
                accent = listOf(Color(0xFF33B5AE), Color(0xFF3CC3D0), Color(0xFF4CC493)), onAccent = Color(0xFF0B2523),
                translation = listOf(Color(0xFF33B5AE), Color(0xFF3CC3D0), Color(0xFF4CC493)), onTranslation = Color(0xFF0B2523),
                key = Color(0xFF3D4F50), keyText = Color(0xFFECEFF2), specialKeyText = Color(0xFFD6EEF0), chipText = Color(0xFFECEFF2),
                hintText = Color(0xFFB0E5E8), caption = Color(0xFFA9BBBC), popup = Color(0xFF405556)
            )
        ),
        KeyboardThemePreset.GOLDEN_DUNE to GradientTheme(
            light = GradientSpec(
                background = listOf(Color(0xFFFBE6A4), Color(0xFFF3CF7C), Color(0xFFF8EBD0)),
                specialKey = listOf(Color(0xFFF1D07A), Color(0xFFE6B457), Color(0xFFEBD2A0)),
                chip = listOf(Color(0xFFFDF3D3), Color(0xFFFAE7BB), Color(0xFFFCF5E7)),
                accent = listOf(Color(0xFFE9B444), Color(0xFFDC9A30), Color(0xFFE2B76B)), onAccent = Color(0xFF2E2108),
                translation = listOf(Color(0xFF76560F), Color(0xFF875714), Color(0xFF7A612E)), onTranslation = Color(0xFFFFFBF0),
                key = Color(0xFFFFFCF3), keyText = Color(0xFF3A2E12), specialKeyText = Color(0xFF3A2E12), chipText = Color(0xFF3A2E12),
                hintText = Color(0xFF784D12), caption = Color(0xFF605743), popup = Color(0xFFFFFCF3)
            ),
            dark = GradientSpec(
                background = listOf(Color(0xFF251F0E), Color(0xFF322913), Color(0xFF3B3019)),
                specialKey = listOf(Color(0xFF584E31), Color(0xFF574A33), Color(0xFF554A35)),
                chip = listOf(Color(0xFF474029), Color(0xFF473E2A), Color(0xFF453C2B)),
                accent = listOf(Color(0xFFE9B444), Color(0xFFDC9A30), Color(0xFFE2B76B)), onAccent = Color(0xFF2E2108),
                translation = listOf(Color(0xFFE9B444), Color(0xFFDC9A30), Color(0xFFE2B76B)), onTranslation = Color(0xFF2E2108),
                key = Color(0xFF443F34), keyText = Color(0xFFECEFF2), specialKeyText = Color(0xFFF0E8D6), chipText = Color(0xFFECEFF2),
                hintText = Color(0xFFE8D7B0), caption = Color(0xFFBCB6A9), popup = Color(0xFF50493A)
            )
        ),
        KeyboardThemePreset.SLATE_GREY to GradientTheme(
            light = GradientSpec(
                background = listOf(Color(0xFFE7EAEE), Color(0xFFC6CCD5), Color(0xFFA8B9CD)),
                specialKey = listOf(Color(0xFFC3C9D1), Color(0xFFAEB6C1), Color(0xFF9BADC2)),
                chip = listOf(Color(0xFFF4F5F7), Color(0xFFE3E7EC), Color(0xFFDDE4ED)),
                accent = listOf(Color(0xFF5E6A79), Color(0xFF525D6C), Color(0xFF56708F)), onAccent = Color(0xFFF7F9FB),
                translation = listOf(Color(0xFF3A4451), Color(0xFF333D4A), Color(0xFF374B62)), onTranslation = Color(0xFFF7F9FB),
                key = Color(0xFFF9FAFB), keyText = Color(0xFF1E242C), specialKeyText = Color(0xFF1E242C), chipText = Color(0xFF1E242C),
                hintText = Color(0xFF384352), caption = Color(0xFF434F60), popup = Color(0xFFF9FAFB)
            ),
            dark = GradientSpec(
                background = listOf(Color(0xFF111316), Color(0xFF1D2127), Color(0xFF29333F)),
                specialKey = listOf(Color(0xFF3E4146), Color(0xFF44484E), Color(0xFF47515E)),
                chip = listOf(Color(0xFF35383B), Color(0xFF36383B), Color(0xFF33373D)),
                accent = listOf(Color(0xFF5E6A79), Color(0xFF525D6C), Color(0xFF56708F)), onAccent = Color(0xFFF7F9FB),
                translation = listOf(Color(0xFF5E6A79), Color(0xFF525D6C), Color(0xFF56708F)), onTranslation = Color(0xFFF7F9FB),
                key = Color(0xFF3A414B), keyText = Color(0xFFECEFF2), specialKeyText = Color(0xFFD6E1F0), chipText = Color(0xFFECEFF2),
                hintText = Color(0xFFB0C7E8), caption = Color(0xFFA9B1BC), popup = Color(0xFF404853)
            )
        )
    )

    private fun GradientSpec.toColors(dark: Boolean) = KeyboardColors(
        background = background[1], key = key, keyText = keyText,
        specialKey = specialKey[1], specialKeyText = specialKeyText,
        accent = accent[1], onAccent = onAccent,
        strip = background[0], chip = chip[1], chipText = chipText,
        translationChip = translation[1], onTranslationChip = onTranslation,
        hintText = hintText, caption = caption, popup = popup, isDark = dark,
        backgroundGradient = background, specialKeyGradient = specialKey, chipGradient = chip,
        accentGradient = accent, translationGradient = translation
    )

    /** Swatches offered for the custom colour (any ARGB works; these are just quick picks). */
    val customSwatches: List<Int> = listOf(
        0xFFF5B800, 0xFFFF8A00, 0xFFE53935, 0xFFE5679A, 0xFFAB47BC, 0xFF6A2FD8,
        0xFF3949AB, 0xFF1E78D6, 0xFF00ACC1, 0xFF2E9E8F, 0xFF43A047, 0xFF7CB342, 0xFF8D6E63, 0xFF546E7A,
    ).map { it.toInt() }

    fun accentOf(preset: KeyboardThemePreset, customArgb: Int?, dark: Boolean): Color {
        gradientThemes[preset]?.let { return (if (dark) it.dark else it.light).accent[1] }
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

    /**
     * Ocean Mist gradient stops. The middle stop of each is the approved Ocean Mist
     * colour (unchanged, still derived below); the outer stops add the visible
     * light-to-deep transition across the keyboard.
     */
    internal val oceanMistGradients: Map<Boolean, List<List<Color>>> = mapOf(
        false to listOf(listOf(Color(0xFFE2EFFC), Color(0xFFCAE0F6), Color(0xFFA3C7EF)), listOf(Color(0xFFA9CCF0), Color(0xFF98C1EC), Color(0xFF82B1E6)), listOf(Color(0xFFBDD7F2), Color(0xFFABCBED), Color(0xFF97BEEA)),
            listOf(Color(0xFF1E72C8), Color(0xFF1B6EC4), Color(0xFF1764B6)), listOf(Color(0xFF16508D), Color(0xFF11467E), Color(0xFF0E3C6E))),
        true to listOf(listOf(Color(0xFF07090D), Color(0xFF101419), Color(0xFF17212D)), listOf(Color(0xFF2E3946), Color(0xFF333F4C), Color(0xFF3A4A5E)), listOf(Color(0xFF283039), Color(0xFF2C353F), Color(0xFF323E4C)),
            listOf(Color(0xFF6AB2FF), Color(0xFF5AA9FF), Color(0xFF4B9DF5)), listOf(Color(0xFF6AB2FF), Color(0xFF5AA9FF), Color(0xFF4B9DF5))),
    )

    fun palette(preset: KeyboardThemePreset, customArgb: Int?, dark: Boolean): KeyboardColors {
        gradientThemes[preset]?.let { return (if (dark) it.dark else it.light).toColors(dark) }
        if (preset == KeyboardThemePreset.OCEAN_MIST) {
            val (bg, special, chip, accent, translation) = oceanMistGradients.getValue(dark)
            return derivedPalette(preset, null, dark).copy(
                backgroundGradient = bg, specialKeyGradient = special, chipGradient = chip,
                accentGradient = accent, translationGradient = translation
            )
        }
        return derivedPalette(preset, customArgb, dark)
    }

    /** Ocean Mist's approved colours and the custom colour: derived from one accent by HSL rules (unchanged). */
    private fun derivedPalette(preset: KeyboardThemePreset, customArgb: Int?, dark: Boolean): KeyboardColors {
        val accent = readableAccent(accentOf(preset, customArgb, dark), dark)
        val (h, sRaw, _) = toHsl(accent)
        val s = sRaw.coerceIn(0.15f, 0.9f)
        val richness = 1f
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
