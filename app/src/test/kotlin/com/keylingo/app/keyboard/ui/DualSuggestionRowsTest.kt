package com.keylingo.app.keyboard.ui

import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.keylingo.app.data.language.SpanishLanguagePack
import com.keylingo.app.data.settings.KeyLingoSettings
import com.keylingo.app.data.settings.TypingLanguageOption
import com.keylingo.app.domain.usecase.GenerateAutocompleteSuggestionsUseCase
import com.keylingo.app.domain.usecase.GenerateHintsUseCase
import com.keylingo.app.keyboard.InputFieldPolicy
import com.keylingo.app.keyboard.KeyAction
import com.keylingo.app.keyboard.KeyboardViewModel
import com.keylingo.app.testing.FakeContextPrediction
import com.keylingo.app.testing.FakeEditor
import com.keylingo.app.testing.FakeLearnedVocabulary
import com.keylingo.app.testing.TestData
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

/**
 * Regression for the device bug "Spanish words in the English row, empty Spanish row"
 * (Typing language = Spanish: typing "Hello i am doing this" showed tiene/tiempo/tus/tienen
 * on top and nothing below). Drives the production KeyboardViewModel and reads what the
 * real KeyboardScreen renders, assigning every chip to a row by its on-screen position:
 *
 * - TOP row (English): completions, corrections, predictions, English starter phrases;
 * - BOTTOM row (Spanish): sentence translation, word hints, Spanish starter phrases.
 *
 * Each scenario runs in every typing-language mode (Auto, English, Spanish), because the
 * bug was the setting leaking into the rows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class DualSuggestionRowsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val chat = InputFieldPolicy.from(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
        InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE, EditorInfo.IME_ACTION_SEND)

    private val settings = MutableStateFlow(KeyLingoSettings())
    private lateinit var vm: KeyboardViewModel
    private lateinit var editor: FakeEditor
    /** One composition per test (a Compose rule allows one setContent); each scenario swaps in a fresh ViewModel. */
    private var shown by mutableStateOf<KeyboardViewModel?>(null)

    private fun show(typingLanguage: TypingLanguageOption) {
        settings.value = KeyLingoSettings(typingLanguage = typingLanguage)
        val learned = FakeLearnedVocabulary(); val ctx = FakeContextPrediction()
        editor = FakeEditor("")
        vm = KeyboardViewModel(
            GenerateHintsUseCase(realHintProvider()), GenerateAutocompleteSuggestionsUseCase(realAutocompleteProvider(), learned, ctx),
            learned, ctx, realLanguageDetector(), settings, SpanishLanguagePack(), editor,
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined) // real-time debounces, as on the device
        )
        vm.onStartInput(chat, restarting = false)
        if (shown == null) compose.setContent { Box(Modifier.width(411.dp)) { shown?.let { key(it) { KeyboardScreen(it) } } } }
        shown = vm
        settle()
    }

    /** Lets the 20 ms suggestion and 220 ms learning-row debounces run and the UI recompose. */
    private fun settle() { compose.waitForIdle(); Thread.sleep(320); compose.waitForIdle(); Thread.sleep(80); compose.waitForIdle() }

    private fun type(text: String) {
        for (c in text) vm.onKeyAction(if (c == ' ') KeyAction.Space else KeyAction.Character(c.lowercase(), c.uppercase()))
        settle()
    }

    private fun backspace(times: Int) { repeat(times) { vm.onKeyAction(KeyAction.Backspace) }; settle() }

    private class Rows(val top: List<String>, val bottom: List<String>) {
        override fun toString() = "TOP=$top BOTTOM=$bottom"
    }

    /** What the rendered strip shows, by row. Chips are told apart by their accessibility labels. */
    private fun rows(): Rows {
        val density = compose.activity.resources.displayMetrics.density
        val split = WORD_ROW_HEIGHT.value * density
        val top = mutableListOf<String>(); val bottom = mutableListOf<String>()
        val labelled = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription), useUnmergedTree = true).fetchSemanticsNodes()
        for (node in labelled) {
            val label: String = node.config[SemanticsProperties.ContentDescription].firstOrNull() ?: continue
            val text = when {
                label.startsWith("Suggestion ") -> label.removePrefix("Suggestion ")
                label.startsWith("Autocorrect to ") -> label.removePrefix("Autocorrect to ")
                label.startsWith("Insert phrase ") -> label.removePrefix("Insert phrase ")
                label.startsWith("Translation: ") -> label.removePrefix("Translation: ").substringBefore(". ")
                label.contains(": ") && label.contains(", ") && !label.startsWith("Grammar") -> label.substringAfter(": ").substringBefore(", ") // hint "Hello: hola, phrase"
                else -> continue
            }
            if (node.boundsInRoot.center.y < split) top += text else bottom += text
        }
        return Rows(top, bottom)
    }

    private val english = TestData.bundled.englishLexicon
    private val spanish = TestData.bundled.spanishLexicon

    /** A word only Spanish knows: the contamination the device showed (tiene, tiempo, tus, tienen). */
    private fun spanishOnly(word: String) = english.find(word) == null && spanish.findByKey(word).isNotEmpty()

    private fun assertEnglishTop(rows: Rows, context: String) {
        assertTrue("$context: Spanish word in the English row: $rows", rows.top.none { spanishOnly(it) })
    }

    private fun eachMode(block: (TypingLanguageOption) -> Unit) = TypingLanguageOption.entries.forEach(block)

    @Test fun emptyFieldShowsEnglishStartersOnTopAndSpanishBelow() = eachMode { mode ->
        show(mode)
        val r = rows()
        assertEquals("$mode $r", listOf("Hello", "Thanks", "Good morning", "Good night", "How are you?"), r.top.take(5))
        assertEquals("$mode $r", listOf("Hola", "Gracias", "Buenos días", "Buenas noches", "¿Cómo estás?"), r.bottom.take(5))
    }

    @Test fun helloKeepsEnglishOnTopAndShowsHolaBelow() = eachMode { mode ->
        show(mode)
        type("Hel")
        assertTrue("$mode ${rows()}", "Hello" in rows().top)
        assertEnglishTop(rows(), "$mode Hel")
        type("lo")
        val r = rows()
        assertTrue("$mode $r", r.bottom.any { it.equals("hola", ignoreCase = true) })
        assertEnglishTop(r, "$mode Hello")
        type(" ")
        assertEquals("$mode: Hello must not be autocorrected", "Hello ", editor.text)
    }

    @Test fun thankYouShowsGraciasBelow() = eachMode { mode ->
        show(mode)
        type("Thank yo")
        assertTrue("$mode ${rows()}", "you" in rows().top)
        type("u")
        val r = rows()
        assertTrue("$mode $r", "Gracias" in r.bottom)
        assertEnglishTop(r, "$mode Thank you")
    }

    @Test fun goodMorningShowsBothLanguages() = eachMode { mode ->
        show(mode)
        type("Good mor")
        assertTrue("$mode ${rows()}", "morning" in rows().top)
        type("ning")
        val r = rows()
        assertTrue("$mode $r", "Buenos días" in r.bottom)
        assertEnglishTop(r, "$mode Good morning")
    }

    @Test fun theDeviceSentenceKeepsEnglishAutocompleteAndTranslatesBelow() = eachMode { mode ->
        show(mode)
        type("I am doing thi")
        val typing = rows()
        assertTrue("$mode $typing", "this" in typing.top)
        assertTrue("$mode $typing", typing.top.none { it in setOf("tiene", "tiempo", "tus", "tienen") })
        type("s")
        val r = rows()
        assertTrue("$mode $r", "Estoy haciendo esto" in r.bottom)
        assertEnglishTop(r, "$mode I am doing this")
        // The exact phrase from the device report.
        show(mode)
        type("Hello i am doing thi")
        assertTrue("$mode ${rows()}", "this" in rows().top)
        assertEnglishTop(rows(), "$mode Hello i am doing thi")
        type("s")
        assertTrue("$mode ${rows()}", "Hola estoy haciendo esto" in rows().bottom)
    }

    @Test fun unknownEnglishWordKeepsTheEnglishRowWorking() = eachMode { mode ->
        show(mode)
        type("I like zqxv")
        val r = rows()
        assertTrue("$mode $r", r.bottom.none { it.contains("zqxv", ignoreCase = true) && it != "zqxv" }) // no invented translation
        type(" th")
        val after = rows()
        assertTrue("$mode $after", after.top.any { it == "the" || it == "this" || it == "that" })
        assertEnglishTop(after, "$mode after an unknown word")
    }

    @Test fun deletingTextUpdatesBothRowsAndAnEmptyFieldBringsBackTheStarters() = eachMode { mode ->
        show(mode)
        type("Thank you")
        assertTrue("$mode ${rows()}", "Gracias" in rows().bottom)
        backspace(3) // "Thank "
        val partial = rows()
        assertTrue("$mode translation must update after deleting: $partial", "Gracias" !in partial.bottom)
        assertEnglishTop(partial, "$mode after deleting")
        type("s") // "Thank s" -> English completions again
        assertEnglishTop(rows(), "$mode retyping")
        backspace(editor.text.length)
        assertEquals("", editor.text)
        val empty = rows()
        assertEquals("$mode $empty", listOf("Hello", "Thanks", "Good morning", "Good night", "How are you?"), empty.top.take(5))
        assertEquals("$mode $empty", listOf("Hola", "Gracias", "Buenos días", "Buenas noches", "¿Cómo estás?"), empty.bottom.take(5))
    }

    @Test fun switchingTypingAndLearningSettingsNeverContaminatesTheRows() {
        show(TypingLanguageOption.AUTO)
        type("Hello i am doing thi")
        for (mode in listOf(TypingLanguageOption.SPANISH, TypingLanguageOption.ENGLISH, TypingLanguageOption.AUTO, TypingLanguageOption.SPANISH)) {
            settings.value = settings.value.copy(typingLanguage = mode)
            settle()
            type("s"); backspace(1) // a keystroke after the switch recomputes both rows
            val r = rows()
            assertTrue("$mode $r", "this" in r.top)
            assertEnglishTop(r, "after switching to $mode")
            assertTrue("$mode $r", r.bottom.any { it.equals("haciendo", ignoreCase = true) })
        }
        // Translate into (learning language) stays Spanish and does not touch the English row.
        settings.value = settings.value.copy(targetLanguageCode = "es")
        settle()
        type("s")
        val r = rows()
        assertTrue("$r", "Hola estoy haciendo esto" in r.bottom)
        assertEnglishTop(r, "after the learning-language setting")
    }

    /** Renders the strip for review: build/keyboard-screenshots/dual-rows-*.png (JVM renders, not device screenshots). */
    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun rendersBothRowsForReview() {
        val out = java.io.File(listOf(java.io.File("build"), java.io.File("app/build")).first { it.isDirectory }, "keyboard-screenshots").apply { mkdirs() }
        fun snap(name: String) {
            val root = compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
            val bmp = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
            root.draw(android.graphics.Canvas(bmp))
            java.io.File(out, "dual-rows-$name.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        show(TypingLanguageOption.SPANISH)
        snap("empty-field-spanish-mode")
        type("Hello i am doing thi")
        snap("typing-spanish-mode")
        type("s")
        snap("translated-spanish-mode")
        assertTrue(rows().toString(), "Hola estoy haciendo esto" in rows().bottom)
    }
}
