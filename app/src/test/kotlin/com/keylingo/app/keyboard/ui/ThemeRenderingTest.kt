package com.keylingo.app.keyboard.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import com.keylingo.app.data.language.SpanishLanguagePack
import com.keylingo.app.data.settings.DarkModeOption
import com.keylingo.app.data.settings.KeyLingoSettings
import com.keylingo.app.data.settings.KeyboardThemePreset
import com.keylingo.app.domain.usecase.GenerateAutocompleteSuggestionsUseCase
import com.keylingo.app.domain.usecase.GenerateHintsUseCase
import com.keylingo.app.keyboard.InputFieldPolicy
import com.keylingo.app.keyboard.KeyboardViewModel
import com.keylingo.app.testing.FakeContextPrediction
import com.keylingo.app.testing.FakeEditor
import com.keylingo.app.testing.FakeLearnedVocabulary
import com.keylingo.app.testing.realAutocompleteProvider
import com.keylingo.app.testing.realHintProvider
import com.keylingo.app.testing.realLanguageDetector
import com.keylingo.app.ui.theme.KeyboardThemes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Checks the pixels of the real KeyboardScreen, not just the palette values:
 * the gradient is drawn on the keyboard itself (background, special keys),
 * letter keys stay flat off-white (Ocean Mist included), and choosing a
 * theme recolours the open keyboard immediately.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ThemeRenderingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val settings = MutableStateFlow(KeyLingoSettings(themePreset = KeyboardThemePreset.SUNSET_BLOOM, darkMode = DarkModeOption.LIGHT))

    private fun showKeyboard() {
        val learned = FakeLearnedVocabulary(); val ctx = FakeContextPrediction()
        val vm = KeyboardViewModel(
            GenerateHintsUseCase(realHintProvider()), GenerateAutocompleteSuggestionsUseCase(realAutocompleteProvider(), learned, ctx),
            learned, ctx, realLanguageDetector(), settings, SpanishLanguagePack(), FakeEditor("x"),
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        )
        vm.onStartInput(InputFieldPolicy.DEFAULT, restarting = false)
        compose.setContent { Box(Modifier.width(411.dp)) { KeyboardScreen(vm) } }
        settle()
    }

    private fun settle() { compose.waitForIdle(); Thread.sleep(300); compose.waitForIdle() }

    private fun bitmap(): Bitmap {
        val root = compose.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        return Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888).also { root.draw(Canvas(it)) }
    }

    private fun Bitmap.at(x: Int, y: Int) = Color(getPixel(x.coerceIn(0, width - 1), y.coerceIn(0, height - 1)))

    private fun close(a: Color, b: Color, tol: Float = 2.5f / 255f) =
        abs(a.red - b.red) <= tol && abs(a.green - b.green) <= tol && abs(a.blue - b.blue) <= tol

    private fun distance(a: Color, b: Color) = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)

    /** Pixel inside the key with [description], at [fx] of its width and mid-height (avoiding the label). */
    private fun keyPixel(bmp: Bitmap, description: String, fx: Float): Color {
        val density = compose.activity.resources.displayMetrics.density
        val b = compose.onNodeWithContentDescription(description).getBoundsInRoot()
        val x = ((b.left + (b.right - b.left) * fx).value * density).toInt()
        val y = (((b.top + b.bottom) / 2f).value * density).toInt()
        return bmp.at(x, y)
    }

    @Test fun gradientIsDrawnOnTheKeyboardAndThemeChangesApplyImmediately() {
        showKeyboard()
        val sunset = KeyboardThemes.palette(KeyboardThemePreset.SUNSET_BLOOM, null, dark = false)
        var bmp = bitmap()
        // Background: opposite corners of the diagonal gradient differ clearly, near the first and last stops.
        val topLeft = bmp.at(2, 2); val bottomRight = bmp.at(bmp.width - 3, bmp.height - 3)
        assertTrue("background is not a gradient: $topLeft vs $bottomRight", distance(topLeft, bottomRight) > 0.08f)
        assertTrue("top-left $topLeft", distance(topLeft, sunset.backgroundGradient.first()) < distance(topLeft, sunset.backgroundGradient.last()))
        assertTrue("bottom-right $bottomRight", distance(bottomRight, sunset.backgroundGradient.last()) < distance(bottomRight, sunset.backgroundGradient.first()))
        // Special key (Shift): its left and right edges differ (gradient face).
        val shiftLeft = keyPixel(bmp, "Shift", 0.12f); val shiftRight = keyPixel(bmp, "Shift", 0.88f)
        assertTrue("Shift face is flat: $shiftLeft vs $shiftRight", distance(shiftLeft, shiftRight) > 0.03f)
        // Letter keys stay the flat off-white key colour.
        assertTrue("letter key ${keyPixel(bmp, "q", 0.2f)} vs ${sunset.key}", close(keyPixel(bmp, "q", 0.2f), sunset.key))

        // Selecting another theme recolours the open keyboard without a restart.
        settings.value = settings.value.copy(themePreset = KeyboardThemePreset.AQUA_BREEZE)
        settle()
        val aqua = KeyboardThemes.palette(KeyboardThemePreset.AQUA_BREEZE, null, dark = false)
        bmp = bitmap()
        assertNotEquals(topLeft, bmp.at(2, 2))
        assertTrue("letter key now ${keyPixel(bmp, "q", 0.2f)} vs ${aqua.key}", close(keyPixel(bmp, "q", 0.2f), aqua.key))
        assertTrue(distance(bmp.at(2, 2), aqua.backgroundGradient.first()) < distance(bmp.at(2, 2), sunset.backgroundGradient.first()))

        // Ocean Mist: approved colours with a visible gradient -- corners and the Shift face differ; letter keys stay the approved key colour.
        settings.value = settings.value.copy(themePreset = KeyboardThemePreset.OCEAN_MIST)
        settle()
        val mist = KeyboardThemes.palette(KeyboardThemePreset.OCEAN_MIST, null, dark = false)
        bmp = bitmap()
        assertTrue("Ocean Mist background is flat", distance(bmp.at(2, 2), bmp.at(bmp.width - 3, bmp.height - 3)) > 0.08f)
        assertTrue("Ocean Mist Shift face is flat", distance(keyPixel(bmp, "Shift", 0.12f), keyPixel(bmp, "Shift", 0.88f)) > 0.02f)
        assertTrue(close(keyPixel(bmp, "q", 0.2f), mist.key))
    }
}
