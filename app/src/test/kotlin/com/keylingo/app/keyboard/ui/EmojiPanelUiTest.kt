package com.keylingo.app.keyboard.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.text.InputType
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.keylingo.app.data.language.SpanishLanguagePack
import com.keylingo.app.data.settings.DarkModeOption
import com.keylingo.app.data.settings.KeyLingoSettings
import com.keylingo.app.data.settings.KeyboardThemePreset
import com.keylingo.app.domain.usecase.GenerateAutocompleteSuggestionsUseCase
import com.keylingo.app.domain.usecase.GenerateHintsUseCase
import com.keylingo.app.keyboard.InputFieldPolicy
import com.keylingo.app.keyboard.KeyAction
import com.keylingo.app.keyboard.KeyboardMode
import com.keylingo.app.keyboard.KeyboardViewModel
import com.keylingo.app.testing.FakeContextPrediction
import com.keylingo.app.testing.FakeEditor
import com.keylingo.app.testing.FakeLearnedVocabulary
import com.keylingo.app.testing.TestEmojiProvider
import com.keylingo.app.testing.realAutocompleteProvider
import com.keylingo.app.testing.realHintProvider
import com.keylingo.app.testing.realLanguageDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
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
 * The emoji panel, search and suggestion row in the real KeyboardScreen: fixed dimensions in every
 * mode, theme and light/dark setting; category tabs; search typed with the keyboard's own keys;
 * insertion at the cursor. Writes build/keyboard-screenshots/emoji-*.png (JVM renders, not device screenshots).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class EmojiPanelUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(listOf(File("build"), File("app/build")).first { it.isDirectory }, "keyboard-screenshots").apply { mkdirs() }

    private val chat = InputFieldPolicy.from(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
        InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE, EditorInfo.IME_ACTION_SEND)
    private val settings = MutableStateFlow(KeyLingoSettings())
    private val editor = FakeEditor("")
    private val vm: KeyboardViewModel by lazy {
        val learned = FakeLearnedVocabulary(); val ctx = FakeContextPrediction()
        KeyboardViewModel(
            GenerateHintsUseCase(realHintProvider()), GenerateAutocompleteSuggestionsUseCase(realAutocompleteProvider(), learned, ctx),
            learned, ctx, realLanguageDetector(), settings, SpanishLanguagePack(), editor,
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), emojiProvider = TestEmojiProvider()
        )
    }

    private fun show() {
        vm.onStartInput(chat, restarting = false)
        compose.setContent { Box(Modifier.width(411.dp).testTag("kb")) { KeyboardScreen(vm) } }
        settle()
    }

    private fun settle() { compose.waitForIdle(); Thread.sleep(300); compose.waitForIdle() }
    private fun key(action: KeyAction) { vm.onKeyAction(action); settle() }
    private fun type(text: String) = text.forEach { key(if (it == ' ') KeyAction.Space else KeyAction.Character(it.lowercase(), it.uppercase())) }
    private fun height() = compose.onNodeWithTag("kb").getBoundsInRoot().let { it.bottom - it.top }

    private fun snap(name: String) {
        val root = compose.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888).also { root.draw(Canvas(it)) }
        File(outDir, "emoji-$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun panelSearchAndLettersHaveTheSameSizeInEveryThemeAndMode() {
        show()
        val heights = mutableMapOf<String, Float>()
        for (preset in KeyboardThemePreset.entries) for (dark in listOf(false, true)) {
            val tag = "${preset.name.lowercase()}-${if (dark) "dark" else "light"}"
            settings.value = KeyLingoSettings(themePreset = preset, darkMode = if (dark) DarkModeOption.DARK else DarkModeOption.LIGHT)
            settle()
            key(KeyAction.SwitchToLetters)
            heights["$tag letters"] = height().value
            key(KeyAction.Emoji)
            heights["$tag panel"] = height().value
            assertTrue("$tag: emoji grid shown", compose.onAllNodesWithContentDescription("Insert emoji 😀").fetchSemanticsNodes().isNotEmpty())
            snap("panel-$tag")
            vm.startEmojiSearch(); settle()
            type("heart")
            heights["$tag search"] = height().value
            snap("search-$tag")
            key(KeyAction.Enter) // leave search
        }
        assertEquals("keyboard height must not change between letters, emoji panel and search, in any theme: $heights",
            1, heights.values.toSet().size)
    }

    @Test fun categoryTabsSwitchTheGridAndAnEmojiIsInsertedAtTheCursor() {
        editor.text = "Good night"; editor.moveCursor(4)
        show()
        key(KeyAction.Emoji)
        // Every tab shows its own emoji (the grid is lazy, so check each category's first emoji).
        val categories = vm.uiState.value.emojiCategories
        for (c in com.keylingo.app.domain.model.EmojiCategory.entries.filter { it != com.keylingo.app.domain.model.EmojiCategory.RECENT }) {
            compose.onNodeWithContentDescription("Emoji category ${c.label}").performClick(); settle()
            val first = categories.getValue(c).first()
            assertTrue("$c shows $first", compose.onAllNodesWithContentDescription("Insert emoji $first").fetchSemanticsNodes().isNotEmpty())
        }
        compose.onNodeWithContentDescription("Emoji category Animals").performClick(); settle()
        assertTrue(compose.onAllNodesWithContentDescription("Insert emoji 🐶").fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithContentDescription("Emoji category Smileys").performClick(); settle()
        compose.onNodeWithContentDescription("Insert emoji 😀").performClick(); settle()
        assertEquals("Good😀 night", editor.text)
        // Used emoji now appear under Recent.
        compose.onNodeWithContentDescription("Emoji category Recently used").performClick(); settle()
        assertTrue("recent=${vm.uiState.value.recentEmojis} nodes=${compose.onAllNodesWithContentDescription("Insert emoji", substring = true).fetchSemanticsNodes().size}",
            compose.onAllNodesWithContentDescription("Insert emoji 😀").fetchSemanticsNodes().isNotEmpty())
        // The search button opens search; results insert at the cursor; back returns to the categories.
        compose.onNodeWithContentDescription("Search emoji").performClick(); settle()
        type("pizza")
        compose.onNodeWithContentDescription("Emoji search: pizza").assertExists()
        assertEquals("Good😀 night", editor.text)
        compose.onAllNodesWithContentDescription("Insert emoji 🍕")[0].performClick(); settle()
        assertEquals("Good😀🍕 night", editor.text)
        compose.onNodeWithContentDescription("Back to emoji").performClick(); settle()
        assertEquals(null, vm.uiState.value.emojiSearchQuery)
        assertEquals(KeyboardMode.EMOJI, vm.uiState.value.mode)
    }

    @Test fun emojiSuggestionsHaveTheirOwnRowBelowTheLanguageRows() {
        show()
        val empty = height()
        type("I am so happy")
        val density = compose.activity.resources.displayMetrics.density
        val emojiChip = compose.onAllNodesWithContentDescription("Emoji 😊", useUnmergedTree = true)[0].getBoundsInRoot()
        assertTrue("below the English and Spanish rows", emojiChip.top >= WORD_ROW_HEIGHT + LEARNING_ROW_HEIGHT - 1.dp)
        // The English row still shows English words only.
        val english = compose.onAllNodesWithContentDescription("Suggestion ", substring = true, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(english.isNotEmpty())
        assertTrue(english.all { it.boundsInRoot.top / density < WORD_ROW_HEIGHT.value })
        assertEquals("height never changes while typing", empty, height())
        snap("suggestion-row")
        compose.onAllNodesWithContentDescription("Emoji 😊", useUnmergedTree = true)[0].performClick(); settle()
        assertEquals("I am so happy 😊", editor.text)
    }
}
