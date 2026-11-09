package com.keylingo.app.ui.layout

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.keylingo.app.data.settings.DarkModeOption
import com.keylingo.app.data.settings.KeyLingoSettings
import com.keylingo.app.data.settings.KeyboardThemePreset
import com.keylingo.app.ui.theme.KeyboardThemes
import com.keylingo.app.data.settings.SettingsDataStore
import com.keylingo.app.domain.model.LearnedWord
import com.keylingo.app.settings.SettingsViewModel
import com.keylingo.app.settings.ui.LearnedWordsContent
import com.keylingo.app.settings.ui.SettingsScreen
import com.keylingo.app.settings.ui.ThemeScreenContent
import com.keylingo.app.testing.FakeContextPrediction
import com.keylingo.app.testing.FakeLearnedVocabulary
import com.keylingo.app.ui.onboarding.OnboardingScreen
import com.keylingo.app.ui.theme.KeyLingoAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Full-page renders of the companion app's screens (setup, Settings incl. About,
 * Theme picker, Learned words) in light and dark, to
 * build/keyboard-screenshots/ui-*.png. Tall viewport so whole scrolling pages
 * are captured. JVM (Robolectric) renders for review, not device screenshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h3000dp-xhdpi")
class AppScreensRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(listOf(File("build"), File("app/build")).first { it.isDirectory }, "keyboard-screenshots").apply { mkdirs() }

    private var screen by mutableStateOf<(@Composable () -> Unit)?>(null)

    private fun render(name: String, content: @Composable () -> Unit) {
        if (screen == null) { screen = content; compose.setContent { key(screen) { screen?.invoke() } } } else screen = content
        compose.waitForIdle()
        val root = compose.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888).also { root.draw(Canvas(it)) }
        File(outDir, "ui-$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val words = listOf(LearnedWord("Madrid", 7, 2, 0L), LearnedWord("guacamole", 4, 1, 0L, "es"), LearnedWord("standup", 3, 0, 0L))

    private fun settingsViewModel() = SettingsViewModel(
        SettingsDataStore(ApplicationProvider.getApplicationContext()), FakeLearnedVocabulary(), FakeContextPrediction()
    )

    @Test fun renderAllScreensLightAndDark() {
        for (dark in listOf(false, true)) {
            val mode = if (dark) "dark" else "light"
            // As MainActivity: the app's primary colour is the selected keyboard theme's accent (Ocean Mist by default).
            val palette = KeyboardThemes.palette(KeyboardThemePreset.OCEAN_MIST, null, dark)
            @Composable fun Themed(content: @Composable () -> Unit) =
                KeyLingoAppTheme(darkTheme = dark, accent = palette.accent, onAccent = palette.onAccent, content = content)
            render("setup-$mode") { Themed { OnboardingScreen(onOpenSettings = {}) } }
            render("settings-$mode") { Themed { SettingsScreen(onBack = {}, viewModel = settingsViewModel()) } }
            render("themes-$mode") {
                Themed {
                    ThemeScreenContent(KeyLingoSettings(darkMode = if (dark) DarkModeOption.DARK else DarkModeOption.LIGHT), {}, {}, {})
                }
            }
            render("learned-$mode") { Themed { LearnedWordsContent(words, {}, {}, {}) } }
        }
    }
}
