package com.keylingo.app.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.keylingo.app.data.language.SpanishLanguagePack
import com.keylingo.app.data.settings.KeyLingoSettings
import com.keylingo.app.data.settings.TypingLanguageOption
import com.keylingo.app.domain.model.EmojiCategory
import com.keylingo.app.domain.repository.EmojiProvider
import com.keylingo.app.domain.usecase.GenerateAutocompleteSuggestionsUseCase
import com.keylingo.app.domain.usecase.GenerateHintsUseCase
import com.keylingo.app.testing.FakeContextPrediction
import com.keylingo.app.testing.FakeEditor
import com.keylingo.app.testing.FakeLearnedVocabulary
import com.keylingo.app.testing.TestEmojiProvider
import com.keylingo.app.testing.realAutocompleteProvider
import com.keylingo.app.testing.realHintProvider
import com.keylingo.app.testing.realLanguageDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Emoji in the production KeyboardViewModel: insertion at the cursor, the separate emoji
 * suggestion row, search typed with the keyboard's own keys, categories, recent emoji, and
 * proof that the English and Spanish rows are unchanged by any of it.
 */
class EmojiKeyboardTest {

    private val chat = InputFieldPolicy.from(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
        InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE, EditorInfo.IME_ACTION_SEND)
    private val password = InputFieldPolicy.from(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, EditorInfo.IME_ACTION_DONE)

    private class Harness(val scope: TestScope, initial: String, policy: InputFieldPolicy, settings: KeyLingoSettings, val emoji: EmojiProvider) {
        var now = 1_000_000L
        val editor = FakeEditor(initial)
        val learned = FakeLearnedVocabulary { now }
        val vm = KeyboardViewModel(
            generateHintsUseCase = GenerateHintsUseCase(realHintProvider()),
            generateSuggestions = GenerateAutocompleteSuggestionsUseCase(realAutocompleteProvider(), learned, FakeContextPrediction()),
            learnedVocabularyProvider = learned,
            contextPredictionProvider = FakeContextPrediction(),
            languageDetector = realLanguageDetector(),
            settingsFlow = MutableStateFlow(settings),
            languagePack = SpanishLanguagePack(),
            textConnection = editor,
            scope = CoroutineScope(StandardTestDispatcher(scope.testScheduler) + Job()),
            clock = { now },
            emojiProvider = emoji
        )
        init { vm.onStartInput(policy, restarting = false); idle() }
        fun idle() = scope.testScheduler.advanceUntilIdle()
        fun key(action: KeyAction) { now += 120; vm.onKeyAction(action); idle() }
        fun type(text: String) {
            for (c in text) key(when (c) { ' ' -> KeyAction.Space; in 'a'..'z', in 'A'..'Z', in 'ñ'..'ñ' -> KeyAction.Character(c.lowercase(), c.uppercase()); else -> KeyAction.Symbol(c.toString()) })
        }
        val state get() = vm.uiState.value
    }

    private fun TestScope.harness(initial: String = "", policy: InputFieldPolicy = chat, settings: KeyLingoSettings = KeyLingoSettings(),
                                  emoji: EmojiProvider = TestEmojiProvider()) = Harness(this, initial, policy, settings, emoji)

    @Test fun panelEmojiIsInsertedAtTheCursorAtTheStartMiddleAndEnd() = runTest {
        val end = harness("Good morning")
        end.vm.onEmojiSelected("☀️"); end.idle()
        assertEquals("Good morning☀️", end.editor.text)

        val start = harness("Good morning")
        start.editor.moveCursor(0)
        start.vm.onEmojiSelected("👋"); start.idle()
        assertEquals("👋Good morning", start.editor.text)
        assertEquals("cursor after the emoji", "👋".length, start.editor.cursor)

        val middle = harness("Good morning")
        middle.editor.moveCursor(5)
        middle.vm.onEmojiSelected("😊"); middle.idle()
        assertEquals("Good 😊morning", middle.editor.text)

        // Multi-codepoint emoji (ZWJ sequence, flag) go in whole, and one Backspace removes them whole.
        val zwj = harness("Hi ")
        zwj.vm.onEmojiSelected("👨‍👩‍👧"); zwj.idle()
        zwj.vm.onEmojiSelected("🇪🇸"); zwj.idle()
        assertEquals("Hi 👨‍👩‍👧🇪🇸", zwj.editor.text)
        zwj.key(KeyAction.Backspace)
        assertEquals("Hi 👨‍👩‍👧", zwj.editor.text)
    }

    @Test fun typedWordsGetEmojiSuggestionsInTheirOwnRow() = runTest {
        val h = harness()
        h.type("I feel happy")
        assertEquals(listOf("😊", "😍", "🥳"), h.state.emojiSuggestions.take(3))
        assertTrue("the English row has no emoji", h.state.suggestions.none { s -> s.word.any { Character.getType(it) == Character.SURROGATE.toInt() } })
        // Still offered right after the space.
        h.type(" ")
        assertEquals("😊", h.state.emojiSuggestions.first())
        // Updates as the user types the next word.
        h.type("birthday")
        assertEquals(listOf("🎂", "🎉", "🎈"), h.state.emojiSuggestions.take(3))
    }

    @Test fun tappingAnEmojiSuggestionKeepsTheTypedWord() = runTest {
        val h = harness()
        h.type("I love")
        h.vm.onEmojiSuggestionSelected("🥰"); h.idle()
        assertEquals("I love 🥰", h.editor.text)
        // After a space, no second space.
        val s = harness()
        s.type("thanks ")
        s.vm.onEmojiSuggestionSelected("🙏"); s.idle()
        assertEquals("Thanks 🙏", s.editor.text)
        // An emoji that is not on screen is never inserted.
        s.vm.onEmojiSuggestionSelected("🦄"); s.idle()
        assertEquals("Thanks 🙏", s.editor.text)
    }

    @Test fun spanishWordsGetEmojiToo() = runTest {
        val h = harness(settings = KeyLingoSettings(typingLanguage = TypingLanguageOption.SPANISH))
        h.type("Muchas gracias")
        assertEquals("es", h.state.typingLanguage)
        assertEquals("🙏", h.state.emojiSuggestions.first())
    }

    @Test fun englishAndSpanishRowsAreIdenticalWithAndWithoutEmojiSuggestions() = runTest {
        for (settings in listOf(KeyLingoSettings(), KeyLingoSettings(typingLanguage = TypingLanguageOption.SPANISH))) {
            for (text in listOf("I am so happy for you", "Thank you for the birthday cake", "Hello i am doing thi", "Estoy muy feliz hoy", "Feliz cumplea")) {
                val with = harness(settings = settings)
                val without = harness(settings = settings, emoji = EmojiProvider.NONE)
                with.type(text); without.type(text)
                assertEquals("$text: same text", without.editor.text, with.editor.text)
                assertEquals("$text: English/Spanish row", without.state.suggestions, with.state.suggestions)
                assertEquals("$text: autocorrection", without.state.autoCorrection, with.state.autoCorrection)
                assertEquals("$text: learning row", without.state.assistance, with.state.assistance)
                assertEquals("$text: language", without.state.typingLanguage, with.state.typingLanguage)
            }
        }
    }

    @Test fun searchUsesTheKeyboardKeysWithoutTouchingTheTextField() = runTest {
        val h = harness("Look ")
        h.key(KeyAction.Emoji)
        assertEquals(KeyboardMode.EMOJI, h.state.mode)
        assertTrue("categories loaded", h.state.emojiCategories.getValue(EmojiCategory.ANIMALS).contains("🐶"))
        h.vm.startEmojiSearch(); h.idle()
        assertEquals("", h.state.emojiSearchQuery)
        h.type("hearz")
        h.key(KeyAction.Backspace)
        h.type("t")
        assertEquals("heart", h.state.emojiSearchQuery)
        assertEquals("keys never reach the field while searching", "Look ", h.editor.text)
        assertTrue(h.state.emojiSearchResults.toString(), "❤️" in h.state.emojiSearchResults.take(12))
        h.type(" red")
        assertEquals("❤️", h.state.emojiSearchResults.first())
        // Picking a result inserts it at the cursor; search stays open for more.
        h.vm.onEmojiSelected("❤️"); h.idle()
        assertEquals("Look ❤️", h.editor.text)
        // Enter (or ABC / the emoji key) leaves search; typing goes to the field again.
        h.key(KeyAction.Enter)
        assertNull(h.state.emojiSearchQuery)
        assertEquals(KeyboardMode.EMOJI, h.state.mode)
        h.key(KeyAction.SwitchToLetters)
        assertEquals(KeyboardMode.LETTERS, h.state.mode)
        h.type("ok")
        assertEquals("Look ❤️ok", h.editor.text)
    }

    @Test fun recentEmojiAreKeptMostUsedFirst() = runTest {
        val provider = TestEmojiProvider()
        val h = harness(emoji = provider)
        h.vm.onEmojiSelected("😂"); h.idle()
        h.vm.onEmojiSelected("❤️"); h.idle()
        h.vm.onEmojiSelected("❤️"); h.idle()
        h.vm.onEmojiSelected("🎉"); h.idle()
        assertEquals(listOf("❤️", "🎉", "😂"), h.state.recentEmojis)
        // A new keyboard session sees the same history (it lives in the provider/store, not the ViewModel).
        val next = harness(emoji = provider)
        assertEquals(listOf("❤️", "🎉", "😂"), next.state.recentEmojis)
        // With no emoji-related word, the emoji row offers the most used.
        next.type("ok see you ")
        assertEquals(listOf("❤️", "🎉", "😂"), next.state.emojiSuggestions.take(3))
    }

    @Test fun privateFieldsGetNoEmojiRowAndRecordNothing() = runTest {
        val provider = TestEmojiProvider()
        val h = harness(policy = password, emoji = provider)
        assertFalse(h.state.showEmojiRow)
        h.type("happy")
        assertTrue(h.state.emojiSuggestions.isEmpty())
        h.key(KeyAction.Emoji)
        h.vm.onEmojiSelected("😊"); h.idle()
        assertTrue("password fields never add to the history", provider.uses.value.isEmpty())
    }
}
