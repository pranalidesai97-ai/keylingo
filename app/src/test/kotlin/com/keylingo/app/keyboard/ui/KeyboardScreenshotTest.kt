package com.keylingo.app.keyboard.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.activity.ComponentActivity
import android.graphics.Canvas
import android.view.ViewGroup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the real KeyboardScreen (real ViewModel, engine and bundled data)
 * for every theme in light and dark, writes PNGs to build/keyboard-screenshots/
 * for visual review, and checks the keyboard's height is identical everywhere.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class KeyboardScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val outDir = File(listOf(File("build"), File("app/build")).first { it.isDirectory }, "keyboard-screenshots").apply { mkdirs() }

    @Test fun renderEveryTheme() {
        val settings = MutableStateFlow(KeyLingoSettings())
        val editor = FakeEditor("I am going to call you tomor")
        val learned = FakeLearnedVocabulary(); val ctx = FakeContextPrediction()
        val vm = KeyboardViewModel(
            GenerateHintsUseCase(realHintProvider()), GenerateAutocompleteSuggestionsUseCase(realAutocompleteProvider(), learned, ctx),
            learned, ctx, realLanguageDetector(), settings, SpanishLanguagePack(), editor,
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined) // off the paused main looper; debounce delays run in real time
        )
        vm.onStartInput(InputFieldPolicy.DEFAULT, restarting = false)
        compose.setContent { Box(Modifier.width(411.dp).testTag("kb")) { KeyboardScreen(vm) } }
        val heights = mutableSetOf<Int>()
        for (preset in KeyboardThemePreset.entries) for (dark in listOf(false, true)) {
            settings.value = KeyLingoSettings(themePreset = preset, darkMode = if (dark) DarkModeOption.DARK else DarkModeOption.LIGHT)
            compose.waitForIdle()
            Thread.sleep(150); compose.waitForIdle() // let the 20/220 ms suggestion and hint refreshes land
            Thread.sleep(300); compose.waitForIdle()
            // Draw the hosting view directly (captureToImage waits for a window redraw Robolectric never reports).
            val root = compose.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))
            heights += bitmap.height
            File(outDir, "${preset.name.lowercase()}-${if (dark) "dark" else "light"}.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        assertEquals("keyboard height must not depend on the theme", 1, heights.size)
    }

    @Test fun renderGrammarRow() = renderRow("grammar", "Yesterday I go to the market. ")
    @Test fun renderTranslationRow() = renderRow("translation", "I am going to watch a movie with my sister ")

    private fun renderRow(name: String, text: String) {
        val settings = MutableStateFlow(KeyLingoSettings())
        val learned = FakeLearnedVocabulary(); val ctx = FakeContextPrediction()
        run {
            val vm = KeyboardViewModel(
                GenerateHintsUseCase(realHintProvider()), GenerateAutocompleteSuggestionsUseCase(realAutocompleteProvider(), learned, ctx),
                learned, ctx, realLanguageDetector(), settings, SpanishLanguagePack(), FakeEditor(text),
                CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            )
            vm.onStartInput(InputFieldPolicy.DEFAULT, restarting = false)
            Thread.sleep(400)
            compose.setContent { Box(Modifier.width(411.dp)) { KeyboardScreen(vm) } }
            compose.waitForIdle(); Thread.sleep(300); compose.waitForIdle()
            val root = compose.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))
            File(outDir, "row-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun renderThemePicker() {
        compose.setContent {
            com.keylingo.app.ui.theme.KeyLingoTheme(darkTheme = false) {
                androidx.compose.material3.Surface {
                    Box(Modifier.width(380.dp).padding(16.dp)) {
                        com.keylingo.app.settings.ui.ThemePickerSection(
                            selected = KeyboardThemePreset.GOLDEN_DUNE, customAccentArgb = KeyLingoSettings.DEFAULT_CUSTOM_ACCENT,
                            darkPreview = { it.prefersDark }, onSelectPreset = {}, onSelectCustom = {}
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        val root = compose.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        File(outDir, "settings-theme-picker.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
