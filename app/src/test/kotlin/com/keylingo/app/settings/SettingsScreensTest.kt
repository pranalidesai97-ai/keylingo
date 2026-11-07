package com.keylingo.app.settings

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.keylingo.app.data.settings.KeyLingoSettings
import com.keylingo.app.data.settings.SettingsDataStore
import com.keylingo.app.data.settings.TypingLanguageOption
import com.keylingo.app.domain.model.LearnedWord
import com.keylingo.app.settings.ui.LearnedWordsContent
import com.keylingo.app.settings.ui.SettingsScreen
import com.keylingo.app.testing.FakeContextPrediction
import com.keylingo.app.testing.FakeLearnedVocabulary
import com.keylingo.app.ui.theme.KeyLingoTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Settings and the learned-words manager: interactions through the real
 * Compose UI (Robolectric) plus rendered PNGs in build/keyboard-screenshots/
 * for visual review. These are JVM renders, not device screenshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class SettingsScreensTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(listOf(File("build"), File("app/build")).first { it.isDirectory }, "keyboard-screenshots").apply { mkdirs() }

    private val sample = listOf(
        LearnedWord("keylingo", 0, 2, 3_000, "en"),
        LearnedWord("quokka", 4, 0, 2_000, "en"),
        LearnedWord("tlayuda", 3, 1, 1_000, "es"),
    )

    private fun snapshot(name: String) {
        compose.waitForIdle()
        val root = compose.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun learnedWordsCanBeFilteredAndDeletedOneByOne() {
        val forgotten = mutableListOf<String>()
        compose.setContent {
            KeyLingoTheme(darkTheme = false) { Surface { LearnedWordsContent(sample, {}, { forgotten += it.word }, {}) } }
        }
        snapshot("settings-learned-words")
        compose.onNodeWithText("Used 2 times", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Delete quokka").performClick()
        assertEquals(listOf("quokka"), forgotten)
        compose.onNodeWithText("Filter").performTextInput("tla")
        compose.onNodeWithText("tlayuda").assertIsDisplayed()
        assertTrue(compose.onAllNodes(hasText("keylingo")).fetchSemanticsNodes().isEmpty())
    }

    @Test fun deleteAllAsksForConfirmation() {
        var cleared = false
        compose.setContent {
            KeyLingoTheme(darkTheme = false) { Surface { LearnedWordsContent(sample, {}, {}, { cleared = true }) } }
        }
        compose.onNodeWithText("Delete learned words").performClick()
        assertEquals(false, cleared)
        compose.onAllNodesWithText("Delete learned words")[1].performClick() // the dialog's confirm button
        assertEquals(true, cleared)
    }

    @Test fun emptyStateExplainsHowWordsAreLearned() {
        compose.setContent { KeyLingoTheme(darkTheme = false) { Surface { LearnedWordsContent(emptyList(), {}, {}, {}) } } }
        compose.onNodeWithText("No learned words yet", substring = true).assertIsDisplayed()
    }

    @Config(qualifiers = "w411dp-h3400dp-xxhdpi")
    @Test fun settingsScreenIsGroupedAndChangesPersist() {
        val store = SettingsDataStore(ApplicationProvider.getApplicationContext())
        val vm = SettingsViewModel(store, FakeLearnedVocabulary(), FakeContextPrediction())
        var openedLearnedWords = false
        compose.setContent {
            KeyLingoTheme(darkTheme = false) { SettingsScreen(onBack = {}, onOpenLearnedWords = { openedLearnedWords = true }, viewModel = vm) }
        }
        snapshot("settings-screen")
        listOf("TYPING", "APPEARANCE", "LEARNING & TRANSLATION", "PERSONAL DICTIONARY", "ABOUT").forEach {
            compose.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
        assertEquals(true, KeyLingoSettings().grammarEnabled)
        compose.onNodeWithText("Grammar suggestions").performScrollTo().performClick() // the whole row toggles
        compose.onAllNodesWithText("Spanish")[0].performScrollTo().performClick()      // typing language
        compose.onNodeWithText("Manage learned words").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(openedLearnedWords)
        // The DataStore write resumes on the main looper, which runBlocking would block: let it run first.
        repeat(10) { Thread.sleep(50); compose.waitForIdle() }
        val saved = runBlocking { store.settingsFlow.first() }
        assertEquals(TypingLanguageOption.SPANISH, saved.typingLanguage)
        assertEquals(false, saved.grammarEnabled)
        assertEquals(false, saved.hapticsEnabled) // vibration stays off unless the user turns it on
    }

    // ---- Theme selection (discoverable screen, current-theme marker, persistence)

    private val fiveThemes = listOf("Ocean Mist", "Sunset Bloom", "Aqua Breeze", "Golden Dune", "Slate Grey")

    @Test fun themeScreenShowsAllFiveThemesAndMarksTheCurrentOne() {
        var picked: com.keylingo.app.data.settings.KeyboardThemePreset? = null
        compose.setContent {
            KeyLingoTheme(darkTheme = false) { Surface { com.keylingo.app.settings.ui.ThemeScreenContent(KeyLingoSettings(), {}, { picked = it }, {}) } }
        }
        snapshot("settings-theme-screen")
        fiveThemes.forEach { name -> assertTrue("missing $name", compose.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty()) }
        // Header names the current theme, and exactly one card is marked selected.
        assertEquals(2, compose.onAllNodesWithText("Ocean Mist").fetchSemanticsNodes().size) // header + card (the default)
        assertEquals(1, compose.onAllNodesWithText("✓ Selected").fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodes(androidx.compose.ui.test.isSelected()).fetchSemanticsNodes().size)
        compose.onNodeWithText("Sunset Bloom").performClick()
        assertEquals(com.keylingo.app.data.settings.KeyboardThemePreset.SUNSET_BLOOM, picked)
    }

    @Test fun selectedThemeIsKeptAfterRestart() {
        val store = SettingsDataStore(ApplicationProvider.getApplicationContext())
        val vm = SettingsViewModel(store, FakeLearnedVocabulary(), FakeContextPrediction())
        compose.setContent { KeyLingoTheme(darkTheme = false) { com.keylingo.app.settings.ui.ThemeScreen(onBack = {}, viewModel = vm) } }
        compose.onNodeWithText("Aqua Breeze").performClick()
        repeat(10) { Thread.sleep(50); compose.waitForIdle() } // DataStore write resumes on the main looper
        // A fresh DataStore reader stands in for an app/keyboard restart.
        val restarted = runBlocking { SettingsDataStore(ApplicationProvider.getApplicationContext()).settingsFlow.first() }
        assertEquals(com.keylingo.app.data.settings.KeyboardThemePreset.AQUA_BREEZE, restarted.themePreset)
        assertEquals(2, compose.onAllNodesWithText("Aqua Breeze").fetchSemanticsNodes().size) // header now names it
        assertEquals(false, restarted.hapticsEnabled) // unrelated defaults untouched
        runBlocking { store.setThemePreset(com.keylingo.app.data.settings.KeyboardThemePreset.DEFAULT) }
    }

    @Test fun settingsOpensWithAThemeEntryAtTheTop() {
        val store = SettingsDataStore(ApplicationProvider.getApplicationContext())
        val vm = SettingsViewModel(store, FakeLearnedVocabulary(), FakeContextPrediction())
        var opened = false
        compose.setContent { KeyLingoTheme(darkTheme = false) { SettingsScreen(onBack = {}, onOpenThemes = { opened = true }, viewModel = vm) } }
        snapshot("settings-top-theme-entry")
        // Visible without scrolling, with the current theme's name.
        compose.onNodeWithText("Keyboard theme").assertIsDisplayed()
        compose.onNodeWithText("Ocean Mist").assertIsDisplayed()
        compose.onNodeWithText("Keyboard theme").performClick()
        assertTrue(opened)
    }

    @Test fun setupScreenOffersTheThemeStep() {
        var opened = false
        compose.setContent { KeyLingoTheme(darkTheme = false) { com.keylingo.app.ui.onboarding.OnboardingScreen(onOpenSettings = {}, onOpenThemes = { opened = true }) } }
        compose.onNodeWithText("4. Choose a theme").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Choose keyboard theme").performScrollTo().performClick()
        assertTrue(opened)
    }
}
