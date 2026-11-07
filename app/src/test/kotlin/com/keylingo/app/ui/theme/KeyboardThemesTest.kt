package com.keylingo.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.keylingo.app.data.settings.KeyLingoSettings
import com.keylingo.app.data.settings.KeyboardThemePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The theme set: Ocean Mist (unchanged approved design), four gradient themes and a custom colour.
 * Readability (WCAG contrast) is checked against every gradient stop a text colour sits on.
 */
class KeyboardThemesTest {

    private val gradientPresets = listOf(KeyboardThemePreset.OCEAN_MIST, KeyboardThemePreset.SUNSET_BLOOM, KeyboardThemePreset.AQUA_BREEZE,
        KeyboardThemePreset.GOLDEN_DUNE, KeyboardThemePreset.SLATE_GREY)

    private fun palettes(): List<Pair<String, KeyboardColors>> =
        KeyboardThemePreset.entries.filter { it != KeyboardThemePreset.CUSTOM }.flatMap { p ->
            listOf(false, true).map { dark -> "$p dark=$dark" to KeyboardThemes.palette(p, null, dark) }
        } + KeyboardThemes.customSwatches.flatMap { argb ->
            listOf(false, true).map { dark -> "custom ${Integer.toHexString(argb)} dark=$dark" to KeyboardThemes.palette(KeyboardThemePreset.CUSTOM, argb, dark) }
        }

    @Test fun theFiveApprovedThemesAreOfferedInOrder() {
        assertEquals(
            listOf(KeyboardThemePreset.OCEAN_MIST, KeyboardThemePreset.SUNSET_BLOOM, KeyboardThemePreset.AQUA_BREEZE,
                KeyboardThemePreset.GOLDEN_DUNE, KeyboardThemePreset.SLATE_GREY),
            KeyboardThemePreset.entries.filter { it != KeyboardThemePreset.CUSTOM }
        )
        assertEquals(KeyboardThemePreset.OCEAN_MIST, KeyLingoSettings().themePreset)
    }

    @Test fun textIsReadableOnEverySurfaceAndEveryGradientStop() {
        val failures = mutableListOf<String>()
        for ((name, c) in palettes()) {
            fun need(label: String, fg: Color, bgs: List<Color>, min: Double) = bgs.forEachIndexed { i, bg ->
                val ratio = KeyboardThemes.contrast(fg, bg)
                if (ratio < min) failures += "$name $label (stop $i) ${"%.2f".format(ratio)} < $min"
            }
            need("key text", c.keyText, listOf(c.key), 7.0)
            need("special key text", c.specialKeyText, c.specialKeyGradient, 4.5)
            need("text on accent", c.onAccent, c.accentGradient, 4.5)
            need("chip text", c.chipText, c.chipGradient, 4.5)
            need("translation chip", c.onTranslationChip, c.translationGradient, 4.5)
            need("hint text on strip", c.hintText, c.backgroundGradient.takeIf { it.size == 1 } ?: listOf(c.strip), 4.5)
            need("caption on strip", c.caption, c.backgroundGradient.takeIf { it.size == 1 } ?: listOf(c.strip), 4.5)
            if (c.chipGradient.size > 1) { // gradient themes: hint words and captions sit on chips
                need("hint text on chip", c.hintText, c.chipGradient, 4.5)
                need("caption on chip", c.caption, c.chipGradient, 4.5)
            }
            need("popup text", c.keyText, listOf(c.popup), 4.5)
            // Keys must be visibly distinct from the keyboard background, everywhere on the gradient.
            need("key vs background", c.key, c.backgroundGradient, 1.1)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun gradientThemesHaveVisibleMultiStopGradients() {
        val failures = mutableListOf<String>()
        for (preset in gradientPresets) for (dark in listOf(false, true)) {
            val c = KeyboardThemes.palette(preset, null, dark)
            mapOf("background" to c.backgroundGradient, "special keys" to c.specialKeyGradient, "chips" to c.chipGradient,
                "accent" to c.accentGradient, "translation" to c.translationGradient).forEach { (surface, stops) ->
                if (stops.size < 3) failures += "$preset dark=$dark $surface has ${stops.size} stops"
                if (stops.distinct().size != stops.size) failures += "$preset dark=$dark $surface repeats a stop"
            }
            // Visible, not flat: the background stops differ clearly in hue or lightness.
            val hsl = c.backgroundGradient.map { KeyboardThemes.toHsl(it) }
            val hueSpread = hsl.maxOf { a -> hsl.maxOf { b -> hueDistance(a.h, b.h) } }
            val lightSpread = hsl.maxOf { it.l } - hsl.minOf { it.l }
            if (hueSpread < 10f && lightSpread < 0.05f) failures += "$preset dark=$dark background gradient too flat (hue $hueSpread, lightness $lightSpread)"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
        // The four gradient themes are distinct from each other.
        val signatures = gradientPresets.map { KeyboardThemes.palette(it, null, false).backgroundGradient }
        assertEquals(signatures.size, signatures.distinct().size)
    }

    @Test fun gradientHuesMatchTheThemeNames() {
        fun hues(p: KeyboardThemePreset) = KeyboardThemes.palette(p, null, false).backgroundGradient.map { KeyboardThemes.toHsl(it) }
        // Sunset Bloom: rose pink -> coral -> warm peach.
        hues(KeyboardThemePreset.SUNSET_BLOOM).let { assertTrue(it[0].h > 320f); assertTrue(it[1].h in 0f..20f); assertTrue(it[2].h in 18f..40f) }
        // Aqua Breeze: turquoise -> aqua -> mint green.
        hues(KeyboardThemePreset.AQUA_BREEZE).let { assertTrue(it[0].h in 170f..185f); assertTrue(it[1].h in 180f..195f); assertTrue(it[2].h in 135f..160f) }
        // Golden Dune: butter yellow -> honey gold -> warm cream (the honey stop is the deepest).
        hues(KeyboardThemePreset.GOLDEN_DUNE).let { h -> assertTrue(h.all { it.h in 35f..55f }); assertTrue(h[1].l < h[0].l && h[1].l < h[2].l) }
        // Slate Grey: silver grey -> cool grey -> soft blue-grey (low saturation, blue-grey hue).
        hues(KeyboardThemePreset.SLATE_GREY).let { h -> assertTrue(h.all { it.s < 0.3f && it.h in 200f..225f }) }
    }

    private fun hueDistance(a: Float, b: Float) = minOf(kotlin.math.abs(a - b), 360f - kotlin.math.abs(a - b))

    /** Ocean Mist keeps the approved Ocean Blue colours exactly (values recorded before any change); only gradient stops were added around them. */
    @Test fun oceanMistIsTheUnchangedApprovedDesign() {
        val expected = mapOf(false to mapOf("background" to 0xFFCAE0F6L, "key" to 0xFFF9FBFDL, "keyText" to 0xFF171E26L, "specialKey" to 0xFF98C1ECL, "specialKeyText" to 0xFF172330L, "accent" to 0xFF1B6EC4L, "onAccent" to 0xFFF9FAFBL, "strip" to 0xFFCAE0F6L, "chip" to 0xFFABCBEDL, "chipText" to 0xFF171E26L, "translationChip" to 0xFF11467EL, "onTranslationChip" to 0xFFF5F7FAL, "hintText" to 0xFF0F3C6CL, "caption" to 0xFF3D4C5CL, "popup" to 0xFFFCFCFDL),
            true to mapOf("background" to 0xFF101419L, "key" to 0xFF252B32L, "keyText" to 0xFFEAEDF0L, "specialKey" to 0xFF333F4CL, "specialKeyText" to 0xFFC9DBEDL, "accent" to 0xFF5AA9FFL, "onAccent" to 0xFF0F1924L, "strip" to 0xFF101419L, "chip" to 0xFF2C353FL, "chipText" to 0xFFEAEDF0L, "translationChip" to 0xFF5AA9FFL, "onTranslationChip" to 0xFF0F1924L, "hintText" to 0xFF88BFFCL, "caption" to 0xFFA0A8B1L, "popup" to 0xFF303A45L))
        for ((dark, values) in expected) {
            val c = KeyboardThemes.palette(KeyboardThemePreset.OCEAN_MIST, null, dark)
            val actual = mapOf("background" to c.background, "key" to c.key, "keyText" to c.keyText, "specialKey" to c.specialKey,
                "specialKeyText" to c.specialKeyText, "accent" to c.accent, "onAccent" to c.onAccent, "strip" to c.strip, "chip" to c.chip,
                "chipText" to c.chipText, "translationChip" to c.translationChip, "onTranslationChip" to c.onTranslationChip,
                "hintText" to c.hintText, "caption" to c.caption, "popup" to c.popup).mapValues { it.value.toArgb().toLong() and 0xFFFFFFFFL }
            assertEquals("Ocean Mist dark=$dark", values, actual)
            // Gradients are built around the approved colours: each middle stop is the approved colour.
            listOf(c.backgroundGradient to c.background, c.specialKeyGradient to c.specialKey, c.chipGradient to c.chip,
                c.accentGradient to c.accent, c.translationGradient to c.translationChip).forEach { (stops, approved) ->
                assertEquals(3, stops.size)
                assertEquals("Ocean Mist dark=$dark middle stop", approved, stops[1])
            }
        }
    }

    /** Golden Dune and Sunset Bloom are approved as they are: every gradient stop and colour is pinned. */
    @Test fun goldenDuneAndSunsetBloomAreUnchanged() {
        val pinned = mapOf(
            "GOLDEN_DUNE light" to listOf(0xFFFBE6A4L, 0xFFF3CF7CL, 0xFFF8EBD0L, 0xFFF1D07AL, 0xFFE6B457L, 0xFFEBD2A0L, 0xFFFDF3D3L, 0xFFFAE7BBL, 0xFFFCF5E7L, 0xFFE9B444L, 0xFFDC9A30L, 0xFFE2B76BL, 0xFF2E2108L, 0xFF76560FL, 0xFF875714L, 0xFF7A612EL, 0xFFFFFBF0L, 0xFFFFFCF3L, 0xFF3A2E12L, 0xFF3A2E12L, 0xFF3A2E12L, 0xFF784D12L, 0xFF605743L, 0xFFFFFCF3L),
            "GOLDEN_DUNE dark" to listOf(0xFF251F0EL, 0xFF322913L, 0xFF3B3019L, 0xFF584E31L, 0xFF574A33L, 0xFF554A35L, 0xFF474029L, 0xFF473E2AL, 0xFF453C2BL, 0xFFE9B444L, 0xFFDC9A30L, 0xFFE2B76BL, 0xFF2E2108L, 0xFFE9B444L, 0xFFDC9A30L, 0xFFE2B76BL, 0xFF2E2108L, 0xFF443F34L, 0xFFECEFF2L, 0xFFF0E8D6L, 0xFFECEFF2L, 0xFFE8D7B0L, 0xFFBCB6A9L, 0xFF50493AL),
            "SUNSET_BLOOM light" to listOf(0xFFF4C6D3L, 0xFFF7C2B5L, 0xFFFAD9C1L, 0xFFEBA9BBL, 0xFFEFA694L, 0xFFF3BE9CL, 0xFFFBE4EBL, 0xFFFCE3DBL, 0xFFFDEEE3L, 0xFFE0708FL, 0xFFE8806AL, 0xFFEE9C6CL, 0xFF2B1117L, 0xFF8C2D4EL, 0xFFA03D2EL, 0xFFA35528L, 0xFFFFF7F5L, 0xFFFFF8F7L, 0xFF3A1E25L, 0xFF3A1E25L, 0xFF3A1E25L, 0xFF6B291FL, 0xFF604943L, 0xFFFFF8F7L),
            "SUNSET_BLOOM dark" to listOf(0xFF231015L, 0xFF321913L, 0xFF3D2717L, 0xFF54363EL, 0xFF573A33L, 0xFF584132L, 0xFF452C33L, 0xFF46312AL, 0xFF47362AL, 0xFFE0708FL, 0xFFE8806AL, 0xFFEE9C6CL, 0xFF2B1117L, 0xFFE0708FL, 0xFFE8806AL, 0xFFEE9C6CL, 0xFF2B1117L, 0xFF443734L, 0xFFECEFF2L, 0xFFF0DBD6L, 0xFFECEFF2L, 0xFFE8BBB0L, 0xFFBCADA9L, 0xFF503E3AL),
        )
        for ((key, expected) in pinned) {
            val (name, mode) = key.split(" ")
            val theme = KeyboardThemes.gradientThemes.getValue(KeyboardThemePreset.valueOf(name))
            val g = if (mode == "dark") theme.dark else theme.light
            val actual = (g.background + g.specialKey + g.chip + g.accent + listOf(g.onAccent) + g.translation +
                listOf(g.onTranslation, g.key, g.keyText, g.specialKeyText, g.chipText, g.hintText, g.caption, g.popup))
                .map { it.toArgb().toLong() and 0xFFFFFFFFL }
            assertEquals(key, expected, actual)
        }
    }

    /**
     * Gradient visibility, measured perceptually (CIELAB delta E between the first and last background stops,
     * i.e. the keyboard's opposite corners). The approved Golden Dune and Sunset Bloom set the bar; the refined
     * Ocean Mist, Aqua Breeze and Slate Grey must be at least as visible in light and dark.
     */
    @Test fun refinedThemesAreAsVisibleAsTheApprovedOnes() {
        fun deltaE(a: Color, b: Color): Double {
            fun lab(c: Color): DoubleArray {
                fun lin(v: Float) = if (v <= 0.04045f) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
                val r = lin(c.red); val g = lin(c.green); val bl = lin(c.blue)
                val x = (0.4124 * r + 0.3576 * g + 0.1805 * bl) / 0.95047; val y = 0.2126 * r + 0.7152 * g + 0.0722 * bl
                val z = (0.0193 * r + 0.1192 * g + 0.9505 * bl) / 1.08883
                fun f(t: Double) = if (t > 0.008856) Math.cbrt(t) else 7.787 * t + 16.0 / 116
                return doubleArrayOf(116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z)))
            }
            val p = lab(a); val q = lab(b)
            return Math.sqrt((0..2).sumOf { (p[it] - q[it]) * (p[it] - q[it]) })
        }
        for (dark in listOf(false, true)) {
            fun spread(preset: KeyboardThemePreset) = KeyboardThemes.palette(preset, null, dark).backgroundGradient.let { deltaE(it.first(), it.last()) }
            val bar = minOf(spread(KeyboardThemePreset.GOLDEN_DUNE), spread(KeyboardThemePreset.SUNSET_BLOOM))
            for (preset in listOf(KeyboardThemePreset.OCEAN_MIST, KeyboardThemePreset.AQUA_BREEZE, KeyboardThemePreset.SLATE_GREY)) {
                val d = spread(preset)
                assertTrue("$preset dark=$dark gradient delta E ${"%.1f".format(d)} < approved themes' ${"%.1f".format(bar)}", d >= bar)
                assertTrue("$preset dark=$dark gradient delta E ${"%.1f".format(d)} below target", d >= if (dark) 12.0 else 20.0)
            }
        }
    }

    @Test fun themesSavedByEarlierVersionsMapToTheirSuccessors() {
        assertEquals(KeyboardThemePreset.OCEAN_MIST, KeyboardThemePreset.fromStored("OCEAN_BLUE"))
        assertEquals(KeyboardThemePreset.GOLDEN_DUNE, KeyboardThemePreset.fromStored("SUNLIGHT_YELLOW"))
        assertEquals(KeyboardThemePreset.SUNSET_BLOOM, KeyboardThemePreset.fromStored("BLUSH_PINK"))
        assertEquals(KeyboardThemePreset.AQUA_BREEZE, KeyboardThemePreset.fromStored("MINT_GREEN"))
        assertEquals(KeyboardThemePreset.SLATE_GREY, KeyboardThemePreset.fromStored("LAVENDER_PURPLE"))
        assertEquals(KeyboardThemePreset.SLATE_GREY, KeyboardThemePreset.fromStored("DARK_VIOLET"))
        assertEquals(KeyboardThemePreset.CUSTOM, KeyboardThemePreset.fromStored("CUSTOM"))
        KeyboardThemePreset.entries.forEach { assertEquals(it, KeyboardThemePreset.fromStored(it.name)) }
        assertEquals(null, KeyboardThemePreset.fromStored("NO_SUCH_THEME"))
    }

    @Test fun hslRoundTrip() {
        val c = KeyboardThemes.hsl(200f, 0.6f, 0.4f)
        val (h, s, l) = KeyboardThemes.toHsl(c)
        assertEquals(200f, h, 0.5f); assertEquals(0.6f, s, 0.01f); assertEquals(0.4f, l, 0.01f)
    }
}
