package com.keylingo.app.ui.theme

import com.keylingo.app.data.settings.KeyboardThemePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every preset, both variants, and every custom swatch must stay readable (WCAG contrast ratios). */
class KeyboardThemesTest {

    private fun palettes(): List<Pair<String, KeyboardColors>> =
        KeyboardThemePreset.entries.filter { it != KeyboardThemePreset.CUSTOM }.flatMap { p ->
            listOf(false, true).map { dark -> "$p dark=$dark" to KeyboardThemes.palette(p, null, dark) }
        } + KeyboardThemes.customSwatches.flatMap { argb ->
            listOf(false, true).map { dark -> "custom ${Integer.toHexString(argb)} dark=$dark" to KeyboardThemes.palette(KeyboardThemePreset.CUSTOM, argb, dark) }
        }

    @Test fun textIsReadableOnEverySurface() {
        val failures = mutableListOf<String>()
        for ((name, c) in palettes()) {
            fun need(label: String, fg: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color, min: Double) {
                val ratio = KeyboardThemes.contrast(fg, bg)
                if (ratio < min) failures += "$name $label ${"%.2f".format(ratio)} < $min"
            }
            need("key text", c.keyText, c.key, 7.0)
            need("special key text", c.specialKeyText, c.specialKey, 4.5)
            need("text on accent", c.onAccent, c.accent, 4.5)
            need("chip text", c.chipText, c.chip, 4.5)
            need("translation chip", c.onTranslationChip, c.translationChip, 4.5)
            need("hint text", c.hintText, c.strip, 4.5)
            need("caption", c.caption, c.strip, 4.5)
            // Keys must be visibly distinct from the keyboard background.
            need("key vs background", c.key, c.background, 1.1)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun sunlightYellowIsTheDefaultAndLooksYellow() {
        assertEquals(KeyboardThemePreset.SUNLIGHT_YELLOW, com.keylingo.app.data.settings.KeyLingoSettings().themePreset)
        val hue = KeyboardThemes.toHsl(KeyboardThemes.palette(KeyboardThemePreset.SUNLIGHT_YELLOW, null, false).accent).h
        assertTrue("hue $hue", hue in 35f..55f)
    }

    @Test fun darkVioletPrefersDarkWhenFollowingTheSystem() {
        assertTrue(KeyboardThemes.useDark(KeyboardThemePreset.DARK_VIOLET, followSystem = true, forcedDark = false, systemDark = false))
        assertEquals(false, KeyboardThemes.useDark(KeyboardThemePreset.MINT_GREEN, followSystem = true, forcedDark = false, systemDark = false))
        assertEquals(false, KeyboardThemes.useDark(KeyboardThemePreset.DARK_VIOLET, followSystem = false, forcedDark = false, systemDark = true))
    }

    @Test fun hslRoundTrip() {
        val c = KeyboardThemes.hsl(200f, 0.6f, 0.4f)
        val (h, s, l) = KeyboardThemes.toHsl(c)
        assertEquals(200f, h, 0.5f); assertEquals(0.6f, s, 0.01f); assertEquals(0.4f, l, 0.01f)
    }
}
