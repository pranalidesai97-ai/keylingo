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
import com.keylingo.app.domain.model.LearningAssistance
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

    @Test fun forcedSpanishModeSkipsDetection() = runTest {
        val h = harness(settings = KeyLingoSettings(typingLanguage = com.keylingo.app.data.settings.TypingLanguageOption.SPANISH))
        h.type("tambi")
        assertEquals("es", h.state.typingLanguage)
        assertTrue(h.state.suggestions.map { it.word }.toString(), h.state.suggestions.any { it.word.equals("también", ignoreCase = true) })
    }

    @Test fun grammarIsSuggestedNotAppliedAndTranslationKeepsTheIntendedTense() = runTest {
        val h = harness()
        h.type("Yesterday I go to the office. ")
        assertEquals("Yesterday I go to the office. ", h.editor.text) // never changed silently
        val grammar = h.state.assistance.grammar
        assertEquals("Yesterday I went to the office.", grammar?.corrected)
        assertEquals("Ayer fui a la oficina.", h.state.assistance.sentence?.translatedText)
        assertTrue(h.state.assistance.sentence!!.fromCorrectedSource)
        h.vm.onGrammarSuggestionApplied(grammar!!); testScheduler.advanceUntilIdle()
        assertEquals("Yesterday I went to the office. ", h.editor.text)
        assertNull(h.state.assistance.grammar)
    }

    @Test fun grammarWaitsUntilTheWordIsFinished() = runTest {
        val h = harness()
        h.type("She go")
        assertNull(h.state.assistance.grammar)
        h.type(" to office ")
        assertEquals("She goes to the office", h.state.assistance.grammar?.corrected?.trim())
    }

    @Test fun grammarSuggestionsCanBeTurnedOff() = runTest {
        val h = harness(settings = KeyLingoSettings(grammarEnabled = false))
        h.type("Yesterday I go to the office. ")
        assertNull(h.state.assistance.grammar)
        assertEquals("Ayer voy a la oficina.", h.state.assistance.sentence?.translatedText) // literal translation of what was typed
    }

    // ---- Cross-app fields (regression: translation missing in Chrome / YouTube search fields)

    private val omnibox = InputFieldPolicy.from(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
        EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN)
    private val appSearchView = InputFieldPolicy.from(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE or
        InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, EditorInfo.IME_ACTION_SEARCH)
    private val filterSearch = InputFieldPolicy.from(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_FILTER, EditorInfo.IME_ACTION_SEARCH)
    private val webSearch = InputFieldPolicy.from(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT, EditorInfo.IME_ACTION_SEARCH)
    private val chat = InputFieldPolicy.from(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
        InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE, EditorInfo.IME_ACTION_SEND)

    @Test fun translationAppearsInChatBrowserAndSearchFieldsAlike() = runTest {
        for ((name, field) in listOf("chat" to chat, "browser address bar" to omnibox, "web search" to webSearch,
                "app SearchView" to appSearchView, "filter search" to filterSearch)) {
            val h = harness(policy = field)
            h.editor.multiLine = field.multiLine
            h.type("I am drinking water")
            assertEquals("translation in $name", "Estoy bebiendo agua", h.state.assistance.sentence?.translatedText)
            assertTrue("word hints in $name", h.state.hints.isNotEmpty())
            assertTrue("word suggestions in $name", h.state.suggestions.isNotEmpty())
            assertEquals("text untouched in $name", "I am drinking water", h.editor.text.replaceFirstChar { it.uppercase() })
        }
        val yt = harness(policy = appSearchView)
        yt.type("She is working today")
        assertEquals("Ella está trabajando hoy", yt.state.assistance.sentence?.translatedText)
    }

    @Test fun searchFieldsKeepTheirActionAndAssistanceNeverEditsTheText() = runTest {
        val h = harness(policy = omnibox)
        h.editor.multiLine = false
        h.type("she go to school")
        val typed = h.editor.text
        assertNotNull("grammar suggestion offered", h.state.assistance.grammar)
        assertEquals(typed, h.editor.text) // suggested, not applied
        h.key(KeyAction.Enter)
        assertEquals("Enter performs GO", 1, h.editor.editorActions)
        assertEquals("no newline, no edit", typed, h.editor.text)
    }

    @Test fun webAddressesAndEmailsGetNoLanguageAssistance() = runTest {
        for (address in listOf("www.youtube.com", "https://example.org/a", "maria@example.com", "go to youtube.com")) {
            val h = harness(policy = omnibox)
            h.type(address)
            assertEquals("assistance for \"$address\"", LearningAssistance.EMPTY, h.state.assistance)
        }
        // An ordinary sentence with a period inside is still assisted.
        val h = harness(policy = chat)
        h.type("I am tired. I am drinking water")
        assertEquals("Estoy bebiendo agua", h.state.assistance.sentence?.translatedText)
    }

    @Test fun fieldsWithoutAnInputTypeAreAssistedButEmailAndPasswordFieldsAreNot() = runTest {
        val untyped = harness(policy = InputFieldPolicy.from(InputType.TYPE_NULL, EditorInfo.IME_ACTION_UNSPECIFIED))
        untyped.type("I am drinking water")
        assertEquals("Estoy bebiendo agua", untyped.state.assistance.sentence?.translatedText)
        for (restricted in listOf(
            InputFieldPolicy.from(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, EditorInfo.IME_ACTION_NEXT),
            InputFieldPolicy.from(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, EditorInfo.IME_ACTION_GO),
            InputFieldPolicy.from(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, EditorInfo.IME_ACTION_DONE),
        )) {
            val h = harness(policy = restricted)
            h.type("I am drinking water")
            assertEquals(LearningAssistance.EMPTY, h.state.assistance)
            assertTrue(h.learned.rows.isEmpty())
        }
    }

    @Test fun searchAndAddressTextIsStillNotLearned() = runTest {
        for (field in listOf(omnibox, filterSearch)) {
            val h = harness(policy = field)
            h.type("quokka quokka quokka quokka ")
            assertTrue(h.learned.rows.isEmpty())
            assertTrue(h.context.bigrams.isEmpty())
        }
    }

    // ---- Empty-field and between-words suggestions

    private fun TestScope.vmWith(provider: com.keylingo.app.domain.repository.AutocompleteProvider, editor: FakeEditor, policy: InputFieldPolicy,
                                 context: FakeContextPrediction = FakeContextPrediction()): KeyboardViewModel {
        val learned = FakeLearnedVocabulary()
        return KeyboardViewModel(
            GenerateHintsUseCase(realHintProvider()), GenerateAutocompleteSuggestionsUseCase(provider, learned, context),
            learned, context, realLanguageDetector(), MutableStateFlow(KeyLingoSettings()), SpanishLanguagePack(), editor,
            CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        ).also { it.onStartInput(policy, restarting = false) }
    }

    // ---- Starter phrases (empty field)

    private val en = com.keylingo.app.domain.model.StarterPhrases.ENGLISH
    private val es = com.keylingo.app.domain.model.StarterPhrases.SPANISH

    @Test fun anEmptyFieldShowsEnglishStarterPhrasesNotWordGuesses() = runTest {
        for (field in listOf(chat, omnibox, appSearchView, InputFieldPolicy.DEFAULT)) {
            val h = harness(policy = field)
            assertEquals(en, h.state.starterPhrases)
            assertTrue("no single-word guesses beside the phrases", h.state.suggestions.isEmpty())
        }
        assertTrue(en.containsAll(listOf("Hello", "Good morning", "How are you?", "You're welcome", "Have a nice day", "Learn English", "More")))
        assertTrue(en.none { it in setOf("Hi", "Him", "His", "High", "Hit") })
    }

    @Test fun spanishModeShowsSpanishStarterPhrasesOnly() = runTest {
        val h = harness(policy = chat, settings = KeyLingoSettings(typingLanguage = com.keylingo.app.data.settings.TypingLanguageOption.SPANISH))
        assertEquals(es, h.state.starterPhrases)
        assertTrue(es.containsAll(listOf("Hola", "Buenos días", "¿Cómo estás?", "Sí", "No", "¿Qué tal?", "¿Cómo está usted?", "Que tengas un buen día", "Aprende español", "Más")))
        // Never mixed: no phrase appears in both lists, and English mode shows none of the Spanish ones.
        assertTrue(en.intersect(es.toSet()).isEmpty())
        val english = harness(policy = chat, settings = KeyLingoSettings(typingLanguage = com.keylingo.app.data.settings.TypingLanguageOption.ENGLISH))
        assertTrue(english.state.starterPhrases.none { it in es })
    }

    @Test fun spanishModeShowsTheRequestedSpanishPhrasesFirst() = runTest {
        assertEquals(listOf("Hola", "Gracias", "Buenos días", "Buenas noches", "¿Cómo estás?", "Por favor", "Sí", "No", "Hasta luego", "¿Qué tal?"), es.take(10))
        val h = harness(policy = chat, settings = KeyLingoSettings(typingLanguage = com.keylingo.app.data.settings.TypingLanguageOption.SPANISH))
        assertEquals(es, h.state.starterPhrases)
        assertTrue("no word suggestions beside them", h.state.suggestions.isEmpty())
        assertEquals("space bar reads Español", "es", h.state.typingLanguage)
    }

    @Test fun spanishSettingArrivingAfterTheFieldOpensSwitchesThePhrases() = runTest {
        // On a device the saved settings load asynchronously, possibly after the field is already focused.
        val h = harness(policy = chat)
        assertEquals(en, h.state.starterPhrases)
        h.settingsFlow.value = KeyLingoSettings(typingLanguage = com.keylingo.app.data.settings.TypingLanguageOption.SPANISH)
        h.scope.testScheduler.advanceUntilIdle()
        assertEquals(es, h.state.starterPhrases)
        assertEquals("es", h.state.typingLanguage)
        // And back to English while the field is still empty.
        h.settingsFlow.value = KeyLingoSettings(typingLanguage = com.keylingo.app.data.settings.TypingLanguageOption.ENGLISH)
        h.scope.testScheduler.advanceUntilIdle()
        assertEquals(en, h.state.starterPhrases)
    }

    @Test fun anExplicitLanguageWinsOverTheLanguageLastTyped() = runTest {
        val h = harness(policy = chat, settings = KeyLingoSettings(typingLanguage = com.keylingo.app.data.settings.TypingLanguageOption.ENGLISH))
        h.type("Estoy aprendiendo español ")
        repeat(h.editor.text.length) { h.backspace() }
        assertEquals("", h.editor.text)
        assertEquals("English mode never shows Spanish phrases", en, h.state.starterPhrases)
        val s = harness(policy = chat, settings = KeyLingoSettings(typingLanguage = com.keylingo.app.data.settings.TypingLanguageOption.SPANISH))
        s.type("I am drinking water ")
        repeat(s.editor.text.length) { s.backspace() }
        assertEquals("Spanish mode never shows English phrases", es, s.state.starterPhrases)
    }

    @Test fun spanishPhrasesInsertOnceThenTypingAndDeletingBehave() = runTest {
        val h = harness(policy = chat, settings = KeyLingoSettings(typingLanguage = com.keylingo.app.data.settings.TypingLanguageOption.SPANISH))
        h.vm.onStarterPhraseSelected("¿Qué tal?"); h.scope.testScheduler.advanceUntilIdle()
        h.vm.onStarterPhraseSelected("¿Qué tal?"); h.scope.testScheduler.advanceUntilIdle()
        assertEquals("¿Qué tal? ", h.editor.text)
        assertTrue(h.state.starterPhrases.isEmpty())
        h.type("bie")
        assertTrue("Spanish autocomplete after typing", h.state.suggestions.any { it.word.lowercase().startsWith("bie") })
        repeat(h.editor.text.length) { h.backspace() }
        assertEquals(es, h.state.starterPhrases)
        // An English phrase is not insertable in Spanish mode (it is not on screen).
        h.vm.onStarterPhraseSelected("Hello")
        assertEquals("", h.editor.text)
        // Restricted fields stay empty in Spanish mode too.
        val pw = harness(policy = InputFieldPolicy.from(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, EditorInfo.IME_ACTION_DONE),
            settings = KeyLingoSettings(typingLanguage = com.keylingo.app.data.settings.TypingLanguageOption.SPANISH))
        assertTrue(pw.state.starterPhrases.isEmpty() && pw.state.suggestions.isEmpty())
        val email = harness(policy = InputFieldPolicy.from(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, EditorInfo.IME_ACTION_NEXT),
            settings = KeyLingoSettings(typingLanguage = com.keylingo.app.data.settings.TypingLanguageOption.SPANISH))
        assertTrue(email.state.starterPhrases.isEmpty())
    }

    @Test fun autoModeUsesTheLanguageLastTyped() = runTest {
        val h = harness(policy = chat)
        h.type("Estoy aprendiendo español ")
        assertEquals("es", h.state.typingLanguage)
        h.vm.onFinishInput()
        h.editor.text = ""; h.editor.moveCursor(0)
        h.vm.onStartInput(chat, restarting = false); h.scope.testScheduler.advanceUntilIdle()
        assertEquals(es, h.state.starterPhrases)
    }

    @Test fun starterPhrasesAppearBeforeTheLanguageDataHasLoaded() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val real = realAutocompleteProvider()
        val slow = object : com.keylingo.app.domain.repository.AutocompleteProvider by real {
            override suspend fun seedNextWords(previousWord: String, languageCode: String): List<String> { gate.await(); return real.seedNextWords(previousWord, languageCode) }
            override suspend fun suggest(prefix: String, languageCode: String, maxSuggestions: Int) = run { gate.await(); real.suggest(prefix, languageCode, maxSuggestions) }
        }
        val vm = vmWith(slow, FakeEditor(""), chat)
        testScheduler.advanceUntilIdle()
        assertEquals(en, vm.uiState.value.starterPhrases)
        gate.complete(Unit)
    }

    @Test fun tappingAPhraseInsertsItOnce() = runTest {
        val h = harness(policy = chat)
        h.vm.onStarterPhraseSelected("Good morning"); h.scope.testScheduler.advanceUntilIdle()
        assertEquals("Good morning ", h.editor.text)
        assertTrue("phrases leave once the field has text", h.state.starterPhrases.isEmpty())
        h.vm.onStarterPhraseSelected("Good morning"); h.scope.testScheduler.advanceUntilIdle() // double tap / stale chip
        assertEquals("Good morning ", h.editor.text)
        // Next-word prediction takes over after a phrase (here "you" has known continuations).
        val ty = harness(policy = chat)
        ty.vm.onStarterPhraseSelected("Thank you"); ty.scope.testScheduler.advanceUntilIdle()
        assertTrue(ty.state.suggestions.map { it.word }.toString(), ty.state.suggestions.isNotEmpty())
        // A question phrase ends the sentence: the next letter is capitalised, and "," does not swap with its space.
        val q = harness(policy = chat)
        q.vm.onStarterPhraseSelected("How are you?"); q.scope.testScheduler.advanceUntilIdle()
        assertEquals("How are you? ", q.editor.text)
        q.type("good")
        assertEquals("How are you? Good", q.editor.text)
        // "Hello" + "," attaches the comma to the word, like a picked suggestion.
        val hello = harness(policy = chat)
        hello.vm.onStarterPhraseSelected("Hello"); hello.scope.testScheduler.advanceUntilIdle()
        hello.type(",")
        assertEquals("Hello, ", hello.editor.text)
    }

    @Test fun phrasesNeverReplaceExistingOrSelectedText() = runTest {
        // Text present: no phrases, and a stray tap does nothing.
        val h = harness("Draft", policy = chat)
        assertTrue(h.state.starterPhrases.isEmpty())
        h.vm.onStarterPhraseSelected("Hello")
        assertEquals("Draft", h.editor.text)
        // Cursor at the start of existing text: not an empty field.
        val start = harness("Draft", policy = chat).also { it.editor.moveCursor(0); it.vm.onSelectionChanged(); it.scope.testScheduler.advanceUntilIdle() }
        assertTrue(start.state.starterPhrases.isEmpty())
        // Selected text (e.g. a browser URL selected on focus): no phrases.
        val selected = FakeEditor("https://example.org", cursor = 0).also { it.selectionEnd = 19 }
        val vm = vmWith(realAutocompleteProvider(), selected, omnibox)
        testScheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.starterPhrases.isEmpty())
        vm.onStarterPhraseSelected("Hello")
        assertEquals("https://example.org", selected.text)
    }

    @Test fun typingSwitchesFromPhrasesToAutocomplete() = runTest {
        val h = harness(policy = chat)
        assertEquals(en, h.state.starterPhrases)
        h.type("hel")
        assertTrue(h.state.starterPhrases.isEmpty())
        assertTrue(h.state.suggestions.map { it.word.lowercase() }.toString(), h.state.suggestions.any { it.word.lowercase() in setOf("hello", "help") })
        // Deleting everything brings the phrases back.
        repeat(3) { h.backspace() }
        assertEquals("", h.editor.text)
        assertEquals(en, h.state.starterPhrases)
    }

    @Test fun passwordAndEmailFieldsGetNoPhrases() = runTest {
        val pw = harness(policy = InputFieldPolicy.from(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, EditorInfo.IME_ACTION_DONE))
        assertTrue(pw.state.starterPhrases.isEmpty()); assertTrue(pw.state.suggestions.isEmpty())
        val email = harness(policy = InputFieldPolicy.from(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, EditorInfo.IME_ACTION_NEXT))
        assertTrue(email.state.starterPhrases.isEmpty())
    }

    @Test fun starterWordsAppearImmediatelyAfterASentenceWhileTheBundledDataIsStillLoading() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val real = realAutocompleteProvider()
        val slow = object : com.keylingo.app.domain.repository.AutocompleteProvider by real {
            override suspend fun seedNextWords(previousWord: String, languageCode: String): List<String> { gate.await(); return real.seedNextWords(previousWord, languageCode) }
        }
        val vm = vmWith(slow, FakeEditor("Thanks. "), chat)
        testScheduler.advanceUntilIdle()
        assertEquals("shown before the data has loaded", listOf("I", "The", "Thanks", "Hi", "Ok"), vm.uiState.value.suggestions.map { it.word })
        gate.complete(Unit); testScheduler.advanceUntilIdle()
        assertEquals("the same words once loaded", listOf("I", "The", "Thanks", "Hi", "Ok"), vm.uiState.value.suggestions.map { it.word })
    }

    @Test fun aNewSentenceStartsWithStarterWords() = runTest {
        val h = harness("Thanks. ", policy = chat)
        assertEquals(listOf("I", "The", "Thanks", "Hi", "Ok"), h.state.suggestions.map { it.word })
    }

    @Test fun afterASpacePredictionsComeFromContext() = runTest {
        val h = harness(policy = chat)
        h.type("I want ")
        assertEquals("to", h.state.suggestions.first().word)
        // Existing text before the cursor when the field opens is used the same way.
        val existing = harness("Thank you for the ", policy = chat)
        assertTrue(existing.state.suggestions.isNotEmpty())
        // Personal history predicts where the bundled data has nothing.
        val learned = harness(policy = chat)
        learned.context.bigrams[Triple("should", "go", "en")] = 4
        learned.type("We should ")
        assertEquals("go", learned.state.suggestions.first().word)
    }

    @Test fun noPredictionsAreInventedWithoutContext() = runTest {
        // "should" has no bundled continuations and no personal history: the row stays empty rather than guessing.
        val h = harness(policy = chat)
        h.type("We should ")
        assertEquals(emptyList<String>(), h.state.suggestions.map { it.word })
    }

    @Test fun predictionsNeverRepeatThePreviousWord() = runTest {
        val h = harness(policy = chat)
        h.context.bigrams[Triple("very", "very", "en")] = 9
        h.context.bigrams[Triple("very", "good", "en")] = 2
        h.type("This is very ")
        val words = h.state.suggestions.map { it.word.lowercase() }
        assertTrue(words.toString(), "very" !in words)
        assertTrue(words.toString(), "good" in words)
        assertEquals("no duplicates", words.distinct(), words)
    }

    @Test fun typingStillAutocompletesAndPasswordFieldsStayEmpty() = runTest {
        val h = harness(policy = chat)
        h.type("hel")
        assertTrue(h.state.suggestions.map { it.word.lowercase() }.toString(), h.state.suggestions.any { it.word.lowercase() in setOf("hello", "help") })
        val pw = harness(policy = InputFieldPolicy.from(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, EditorInfo.IME_ACTION_DONE))
        assertTrue("nothing in a password field", pw.state.suggestions.isEmpty() && pw.state.starterPhrases.isEmpty())
    }

    @Test fun languageDiagnosticsNeverContainTypedText() = runTest {
        val log = mutableListOf<String>()
        val learned = FakeLearnedVocabulary(); val ctx = FakeContextPrediction(); val editor = FakeEditor("")
        val vm = KeyboardViewModel(GenerateHintsUseCase(realHintProvider()), GenerateAutocompleteSuggestionsUseCase(realAutocompleteProvider(), learned, ctx),
            learned, ctx, realLanguageDetector(), MutableStateFlow(KeyLingoSettings(typingLanguage = com.keylingo.app.data.settings.TypingLanguageOption.SPANISH)),
            SpanishLanguagePack(), editor, CoroutineScope(StandardTestDispatcher(testScheduler) + Job()), diagnostics = { log += it })
        vm.onStartInput(chat, restarting = false); testScheduler.advanceUntilIdle()
        for (c in "secreto privado ") vm.onKeyAction(if (c == ' ') KeyAction.Space else KeyAction.Character(c.toString(), c.uppercase())).also { testScheduler.advanceUntilIdle() }
        assertTrue(log.toString(), log.any { it.startsWith("starter phrases: es") })
        assertTrue(log.toString(), log.any { it.startsWith("field empty=false") })
        assertTrue("typed text leaked into diagnostics: $log", log.none { it.contains("secreto") || it.contains("privado") })
        // Password fields: nothing about the field's contents is logged.
        log.clear()
        vm.onStartInput(InputFieldPolicy.from(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, EditorInfo.IME_ACTION_DONE), restarting = false)
        testScheduler.advanceUntilIdle()
        for (c in "abc") vm.onKeyAction(KeyAction.Character(c.toString(), c.uppercase())).also { testScheduler.advanceUntilIdle() }
        assertTrue(log.toString(), log.none { it.startsWith("field empty") || it.startsWith("starter phrases") })
    }
}
