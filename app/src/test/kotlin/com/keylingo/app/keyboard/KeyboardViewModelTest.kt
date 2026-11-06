package com.keylingo.app.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.keylingo.app.data.language.SpanishLanguagePack
import com.keylingo.app.data.settings.KeyLingoSettings
import com.keylingo.app.domain.usecase.GenerateAutocompleteSuggestionsUseCase
import com.keylingo.app.domain.usecase.GenerateHintsUseCase
import com.keylingo.app.testing.FakeContextPrediction
import com.keylingo.app.testing.FakeEditor
import com.keylingo.app.testing.FakeLearnedVocabulary
import com.keylingo.app.testing.realAutocompleteProvider
import com.keylingo.app.testing.realHintProvider
import com.keylingo.app.testing.realLanguageDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Note: the ViewModel gets its own scope on a StandardTestDispatcher sharing
 * the test scheduler (backgroundScope work would not run under advanceUntilIdle),
 * so virtual time drives every debounce deterministically.
 *
 * End-to-end keyboard behaviour: key actions go through the real ViewModel,
 * suggestion engine, hint pipeline and bundled data into a simulated text
 * field, and assertions check the resulting text -- exactly what a user sees.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KeyboardViewModelTest {

    private class Harness(val scope: TestScope, initial: String = "", policy: InputFieldPolicy = InputFieldPolicy.DEFAULT,
                          settings: KeyLingoSettings = KeyLingoSettings()) {
        var now = 1_000_000L
        val editor = FakeEditor(initial)
        val learned = FakeLearnedVocabulary { now }
        val context = FakeContextPrediction()
        val settingsFlow = MutableStateFlow(settings)
        val vm = KeyboardViewModel(
            generateHintsUseCase = GenerateHintsUseCase(realHintProvider()),
            generateSuggestions = GenerateAutocompleteSuggestionsUseCase(realAutocompleteProvider(), learned, context),
            learnedVocabularyProvider = learned,
            contextPredictionProvider = context,
            languageDetector = realLanguageDetector(),
            settingsFlow = settingsFlow,
            languagePack = SpanishLanguagePack(),
            textConnection = editor,
            scope = CoroutineScope(StandardTestDispatcher(scope.testScheduler) + Job()),
            clock = { now }
        )

        init { vm.onStartInput(policy, restarting = false); scope.testScheduler.advanceUntilIdle() }

        fun key(action: KeyAction) { now += 120; vm.onKeyAction(action); scope.testScheduler.advanceUntilIdle() }
        fun type(text: String) {
            for (c in text) when (c) {
                ' ' -> key(KeyAction.Space)
                in 'a'..'z', in 'A'..'Z' -> key(KeyAction.Character(c.lowercase(), c.uppercase()))
                else -> key(KeyAction.Symbol(c.toString()))
            }
        }
        fun backspace() = key(KeyAction.Backspace)
        val state get() = vm.uiState.value
    }

    private fun TestScope.harness(initial: String = "", policy: InputFieldPolicy = InputFieldPolicy.DEFAULT, settings: KeyLingoSettings = KeyLingoSettings()) =
        Harness(this, initial, policy, settings)

    @Test fun typesAndCapitalisesTheFirstLetterOfASentence() = runTest {
        val h = harness()
        h.type("hello there. how")
        assertEquals("Hello there. How", h.editor.text)
    }

    @Test fun autoShiftTurnsOffAgainWhenNoLongerAtSentenceStart() = runTest {
        val h = harness()
        h.type("hi. ")
        assertEquals(ShiftState.SHIFTED_ONCE, h.state.shiftState)
        h.backspace() // "Hi." -- cursor right after the period, not a sentence start any more
        assertEquals(ShiftState.OFF, h.state.shiftState)
    }

    @Test fun backspaceDeletesWholeEmojiAndAccentedLetters() = runTest {
        val h = harness("ok 👍🏽")
        h.backspace()
        assertEquals("ok ", h.editor.text)
        val h2 = harness("dev 👩‍💻")
        h2.backspace()
        assertEquals("dev ", h2.editor.text)
    }

    @Test fun backspaceInAnEmptyFieldSendsADeleteKey() = runTest {
        val h = harness()
        h.backspace()
        assertEquals(1, h.editor.delKeyEvents)
    }

    @Test fun doubleSpaceInsertsAPeriodOnlyAfterAWord() = runTest {
        val h = harness()
        h.type("I am here  ")
        assertEquals("I am here. ", h.editor.text)
        val h2 = harness()
        h2.type("Wow!  ")
        assertEquals("Wow!  ", h2.editor.text)
    }

    @Test fun autocorrectsOnSpaceAndBackspaceUndoesIt() = runTest {
        val h = harness("I saw ")
        h.type("teh")
        assertEquals("the", h.state.autoCorrection?.word)
        h.key(KeyAction.Space)
        assertEquals("I saw the ", h.editor.text)
        h.backspace()
        assertEquals("I saw teh", h.editor.text)
        h.key(KeyAction.Space) // the user meant it: no second correction
        assertEquals("I saw teh ", h.editor.text)
    }

    @Test fun autocorrectsOnPunctuationToo() = runTest {
        val h = harness("I ")
        h.type("dont,")
        assertEquals("I don't,", h.editor.text)
    }

    @Test fun autocorrectCanBeTurnedOff() = runTest {
        val h = harness("I saw ", settings = KeyLingoSettings(autocorrectEnabled = false))
        h.type("teh ")
        assertEquals("I saw teh ", h.editor.text)
    }

    @Test fun pickingASuggestionKeepsTheTypedCapitalAndAddsASpace() = runTest {
        val h = harness()
        h.type("Tomo")
        val tomorrow = h.state.suggestions.first { it.word == "Tomorrow" }
        h.vm.onAutocompleteSelected(tomorrow); testScheduler.advanceUntilIdle()
        assertEquals("Tomorrow ", h.editor.text)
        h.type(".")
        assertEquals("Tomorrow. ", h.editor.text) // the space moved behind the punctuation
    }

    @Test fun pickingASuggestionReplacesOnlyTheWordAtTheCursor() = runTest {
        val h = harness("I lik apples")
        h.editor.moveCursor(5) // after "lik"
        h.vm.onSelectionChanged(); testScheduler.advanceUntilIdle()
        val like = h.state.suggestions.first { it.word == "like" }
        h.vm.onAutocompleteSelected(like); testScheduler.advanceUntilIdle()
        assertEquals("I like apples", h.editor.text)
    }

    @Test fun predictsTheNextWordAfterASpace() = runTest {
        val h = harness("I ")
        h.vm.onSelectionChanged(); testScheduler.advanceUntilIdle()
        assertTrue(h.state.suggestions.map { it.word }.contains("am"))
    }

    @Test fun learnsTypedWordsAndSequences() = runTest {
        val h = harness()
        h.type("Zorbix rocks. Zorbix rocks. Zorbix rocks. ")
        assertEquals(3, h.learned.rows["zorbix" to "en"]?.organic)
        assertEquals(3, h.context.bigrams[Triple("zorbix", "rocks", "en")])
        h.type("Zor")
        assertTrue(h.state.suggestions.map { it.word }.toString(), h.state.suggestions.any { it.word.equals("Zorbix", ignoreCase = true) })
    }

    @Test fun passwordFieldsGetNoSuggestionsAndNothingIsLearned() = runTest {
        val password = InputFieldPolicy.from(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, EditorInfo.IME_ACTION_DONE)
        val h = harness(policy = password)
        h.type("secret words here ")
        assertTrue(h.state.suggestions.isEmpty())
        assertTrue(h.state.hints.isEmpty())
        assertTrue(h.learned.rows.isEmpty())
        assertTrue(h.context.bigrams.isEmpty())
        assertEquals("secret words here ", h.editor.text) // and no auto-capitalisation either
    }

    @Test fun incognitoFieldsShowSuggestionsButLearnNothing() = runTest {
        val incognito = InputFieldPolicy.from(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
        val h = harness(policy = incognito)
        h.type("hello world ")
        assertTrue(h.learned.rows.isEmpty())
    }

    @Test fun emailFieldsAreNotAutocorrectedOrCapitalised() = runTest {
        val email = InputFieldPolicy.from(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, EditorInfo.IME_ACTION_NEXT)
        val h = harness(policy = email)
        h.type("teh ")
        assertEquals("teh ", h.editor.text)
    }

    @Test fun enterPerformsTheFieldActionInSingleLineFields() = runTest {
        val search = InputFieldPolicy.from(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_SEARCH)
        assertEquals(EnterKeyAction.SEARCH, search.enterAction)
        val h = harness(policy = search)
        h.editor.multiLine = false
        h.key(KeyAction.Enter)
        assertEquals(1, h.editor.editorActions)
    }

    @Test fun numberFieldsOpenTheSymbolsLayer() = runTest {
        val h = harness(policy = InputFieldPolicy.from(InputType.TYPE_CLASS_NUMBER, EditorInfo.IME_ACTION_DONE))
        assertEquals(KeyboardMode.SYMBOLS_1, h.state.mode)
    }

    @Test fun doubleTapShiftLocksCaps() = runTest {
        val h = harness("x ")
        h.vm.onKeyAction(KeyAction.Shift)
        h.now += 100
        h.vm.onKeyAction(KeyAction.Shift)
        assertEquals(ShiftState.CAPS_LOCK, h.state.shiftState)
        h.type("ok")
        assertEquals("x OK", h.editor.text)
    }

    @Test fun showsSentenceTranslationAndContextualHints() = runTest {
        val h = harness()
        h.type("I am working ")
        assertEquals("Estoy trabajando", h.state.assistance.sentence?.translatedText)
        assertTrue(h.state.hints.any { it.translatedWord == "trabajando" })
    }

    @Test fun spanishTypingGetsSpanishSuggestionsAndEnglishGlosses() = runTest {
        val h = harness()
        h.type("yo quiero comer tambi")
        assertEquals("es", h.state.typingLanguage)
        assertTrue(h.state.suggestions.map { it.word }.toString(), h.state.suggestions.any { it.word == "también" })
        assertTrue(h.state.hints.any { it.originalWord == "quiero" && it.translatedWord == "I want" })
    }

    @Test fun learningRowCanBeTurnedOff() = runTest {
        val h = harness(settings = KeyLingoSettings(learningEnabled = false))
        h.type("I am work")
        assertNull(h.state.assistance.sentence)
        assertTrue(h.state.hints.isEmpty())
        assertNotNull(h.state.suggestions.firstOrNull()) // typing assistance is independent of the learning row
    }
}
