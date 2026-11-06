package com.keylingo.app.keyboard

import com.keylingo.app.data.settings.KeyLingoSettings
import com.keylingo.app.domain.model.AutocompleteSuggestion
import com.keylingo.app.domain.model.HintWord
import com.keylingo.app.domain.model.LearningAssistance
import com.keylingo.app.domain.model.SuggestionKind
import com.keylingo.app.domain.repository.ContextPredictionProvider
import com.keylingo.app.domain.repository.LanguageDetector
import com.keylingo.app.domain.repository.LanguagePack
import com.keylingo.app.domain.repository.LearnedVocabularyProvider
import com.keylingo.app.domain.usecase.GenerateAutocompleteSuggestionsUseCase
import com.keylingo.app.domain.usecase.GenerateHintsUseCase
import com.keylingo.app.domain.usecase.SuggestionRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ShiftState { OFF, SHIFTED_ONCE, CAPS_LOCK }

data class KeyboardUiState(
    val mode: KeyboardMode = KeyboardMode.LETTERS,
    val shiftState: ShiftState = ShiftState.OFF,
    val suggestions: List<AutocompleteSuggestion> = emptyList(),
    /** The candidate that will replace the current word on the next space/punctuation, if any. */
    val autoCorrection: AutocompleteSuggestion? = null,
    val assistance: LearningAssistance = LearningAssistance.EMPTY,
    val typingLanguage: String = "en",
    val policy: InputFieldPolicy = InputFieldPolicy.DEFAULT,
    val settings: KeyLingoSettings = KeyLingoSettings()
) {
    val autocompleteSuggestions: List<AutocompleteSuggestion> get() = suggestions
    val hints: List<HintWord> get() = assistance.hints
}

/** Punctuation that ends a word (triggers learning and autocorrect) and is followed by a space after a picked suggestion. */
private val WORD_SEPARATOR_PUNCTUATION = setOf(".", ",", "!", "?", ";", ":")

/**
 * Holds all keyboard state and turns [KeyAction]s into edits through
 * [KeyboardTextConnection]. A plain class (not androidx ViewModel): an
 * InputMethodService has no ViewModelStoreOwner, so the service owns
 * [scope] and cancels it in onDestroy(). Runs on the main thread; every
 * expensive step (lexicon search, translation, database I/O) happens in
 * suspend functions that switch dispatchers inside the data layer.
 */
class KeyboardViewModel(
    private val generateHintsUseCase: GenerateHintsUseCase,
    private val generateSuggestions: GenerateAutocompleteSuggestionsUseCase,
    private val learnedVocabularyProvider: LearnedVocabularyProvider,
    private val contextPredictionProvider: ContextPredictionProvider,
    private val languageDetector: LanguageDetector,
    private val settingsFlow: Flow<KeyLingoSettings>,
    private val languagePack: LanguagePack,
    private val textConnection: KeyboardTextConnection,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val _uiState = MutableStateFlow(KeyboardUiState())
    val uiState: StateFlow<KeyboardUiState> = _uiState.asStateFlow()

    private var suggestionJob: Job? = null
    private var hintJob: Job? = null

    /** The word the cached suggestions/autocorrection were computed for; autocorrect only applies if it still matches. */
    private var suggestionsComputedFor: String? = null

    private data class AppliedCorrection(val original: String, val replacement: String, val separator: String)
    private var lastCorrection: AppliedCorrection? = null
    private val rejectedCorrections = mutableSetOf<String>()

    /** True right after a picked suggestion committed "word " -- punctuation typed next swaps with that space. */
    private var phantomSpace = false
    private var autoShifted = false
    private var lastShiftPressAt = 0L
    private var lastKeyWasSpace = false
    private var lastSpacePressAt = 0L
    private var lastOwnEditAt = 0L

    init {
        scope.launch {
            settingsFlow.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
                scheduleRefresh(immediate = true)
            }
        }
    }

    private fun context(): TextContext =
        TextContext(textConnection.textBeforeCursor(CONTEXT_WINDOW), textConnection.textAfterCursor(4))

    private val state get() = _uiState.value
    private val policy get() = state.policy

    // ------------------------------------------------------------ lifecycle

    /** A new field gained focus (or the same field restarted). */
    fun onStartInput(newPolicy: InputFieldPolicy, restarting: Boolean) {
        lastCorrection = null
        phantomSpace = false
        lastKeyWasSpace = false
        suggestionsComputedFor = null
        val mode = if (restarting) state.mode else if (newPolicy.startInSymbols) KeyboardMode.SYMBOLS_1 else KeyboardMode.LETTERS
        _uiState.update {
            it.copy(policy = newPolicy, mode = mode, shiftState = ShiftState.OFF, suggestions = emptyList(),
                autoCorrection = null, assistance = LearningAssistance.EMPTY)
        }
        autoShifted = false
        updateAutoShift()
        scheduleRefresh(immediate = true)
    }

    /** The field lost focus: forget everything derived from its text. */
    fun onFinishInput() {
        suggestionJob?.cancel(); hintJob?.cancel()
        lastCorrection = null
        phantomSpace = false
        _uiState.update { it.copy(suggestions = emptyList(), autoCorrection = null, assistance = LearningAssistance.EMPTY) }
    }

    /** The cursor/selection moved. Ignores the echo of our own edits; an external move cancels undo/space-swap state. */
    fun onSelectionChanged() {
        if (clock() - lastOwnEditAt > OWN_EDIT_ECHO_MILLIS) {
            lastCorrection = null
            phantomSpace = false
            updateAutoShift()
        }
        scheduleRefresh()
    }

    /** Legacy entry point kept for callers that only know "content changed". */
    fun onInputFieldContentChanged() = scheduleRefresh()

    // ------------------------------------------------------------ key input

    fun onKeyAction(action: KeyAction) {
        val wasSpace = lastKeyWasSpace
        lastKeyWasSpace = false
        lastOwnEditAt = clock()
        when (action) {
            is KeyAction.Character -> {
                lastCorrection = null
                phantomSpace = false
                val upper = state.shiftState != ShiftState.OFF || policy.capitalizeAll
                textConnection.commitText(if (upper) action.upper else action.lower)
                consumeOneShotShift()
                updateAutoShift()
                scheduleRefresh()
            }
            is KeyAction.Symbol -> onSymbol(action.value)
            KeyAction.Space -> onSpace(wasSpace)
            KeyAction.Backspace -> onBackspace()
            KeyAction.Enter -> {
                val ctx = context()
                recordWordBoundary(ctx)
                lastCorrection = null
                phantomSpace = false
                textConnection.performEnterAction()
                updateAutoShift()
                scheduleRefresh()
            }
            KeyAction.Shift -> handleShiftPress()
            KeyAction.SwitchToSymbols -> setMode(if (state.mode == KeyboardMode.SYMBOLS_1) KeyboardMode.SYMBOLS_2 else KeyboardMode.SYMBOLS_1)
            KeyAction.SwitchToLetters -> setMode(KeyboardMode.LETTERS)
            KeyAction.SwitchToNumbers -> setMode(KeyboardMode.SYMBOLS_1)
            KeyAction.Emoji -> setMode(KeyboardMode.EMOJI)
        }
    }

    private fun onSymbol(value: String) {
        if (value in WORD_SEPARATOR_PUNCTUATION) {
            textConnection.beginBatchEdit()
            val ctx = context()
            val corrected = applyAutoCorrection(ctx, separator = value)
            if (!corrected) {
                recordWordBoundary(ctx)
                lastCorrection = null
                if (phantomSpace && ctx.before.endsWith(" ")) {
                    // "word |" + "." -> "word. " (the space we added after a suggestion moves behind the punctuation)
                    textConnection.deleteCharactersBackward(1)
                    textConnection.commitText("$value ")
                } else {
                    textConnection.commitText(value)
                }
            }
            textConnection.endBatchEdit()
        } else {
            lastCorrection = null
            textConnection.commitText(value)
        }
        phantomSpace = false
        updateAutoShift()
        scheduleRefresh()
    }

    private fun onSpace(previousKeyWasSpace: Boolean) {
        val now = clock()
        val ctx = context()
        val before = ctx.before
        // Double-space -> ". " only right after a word character, never after existing punctuation.
        val isDoubleSpace = previousKeyWasSpace && now - lastSpacePressAt < DOUBLE_SPACE_WINDOW_MILLIS &&
            before.length >= 2 && before.last() == ' ' && before[before.length - 2].isLetterOrDigit() && policy.allowAutoCorrect
        textConnection.beginBatchEdit()
        if (isDoubleSpace) {
            textConnection.deleteCharactersBackward(1)
            textConnection.commitText(". ")
            lastCorrection = null
        } else if (!applyAutoCorrection(ctx, separator = " ")) {
            recordWordBoundary(ctx)
            lastCorrection = null
            textConnection.commitText(" ")
        }
        textConnection.endBatchEdit()
        phantomSpace = false
        lastKeyWasSpace = true
        lastSpacePressAt = now
        updateAutoShift()
        scheduleRefresh()
    }

    private fun onBackspace() {
        val ctx = context()
        val undo = lastCorrection
        if (undo != null && ctx.before.endsWith(undo.replacement + undo.separator)) {
            // One Backspace right after an autocorrection restores exactly what was typed and remembers the choice.
            textConnection.beginBatchEdit()
            textConnection.deleteCharactersBackward(undo.replacement.length + undo.separator.length)
            textConnection.commitText(undo.original)
            textConnection.endBatchEdit()
            rejectedCorrections.add(undo.original.lowercase())
            if (policy.allowLearning) {
                val lang = state.typingLanguage
                scope.launch { learnedVocabularyProvider.recordRejectedCorrection(undo.original, lang) }
            }
        } else {
            textConnection.deleteBackward(ctx.lastGraphemeLength)
        }
        lastCorrection = null
        phantomSpace = false
        updateAutoShift()
        scheduleRefresh()
    }

    /**
     * Replaces the word before the cursor with the pending autocorrection
     * (if it is still valid for exactly this word) and commits [separator].
     * Returns false when nothing was corrected.
     */
    private fun applyAutoCorrection(ctx: TextContext, separator: String): Boolean {
        val correction = state.autoCorrection ?: return false
        val word = ctx.currentWord
        if (word.isEmpty() || ctx.cursorInsideWord || word != suggestionsComputedFor) return false
        if (!state.settings.autocorrectEnabled || !policy.allowAutoCorrect) return false
        if (correction.word == word) return false
        textConnection.deleteCharactersBackward(word.length)
        textConnection.commitText(correction.word + separator)
        lastCorrection = AppliedCorrection(word, correction.word, separator)
        recordWordBoundary(ctx, overrideWord = correction.word)
        _uiState.update { it.copy(autoCorrection = null) }
        suggestionsComputedFor = null
        return true
    }

    /** Learning: report a completed word (and its preceding words) -- only where the field allows it. */
    private fun recordWordBoundary(ctx: TextContext, overrideWord: String? = null) {
        if (!policy.allowLearning) return
        val word = overrideWord ?: ctx.currentWord
        if (word.isBlank() || ctx.cursorInsideWord) return
        val previous = ctx.previousWord
        val second = ctx.secondPreviousWord
        val lang = state.typingLanguage
        scope.launch {
            learnedVocabularyProvider.recordOrganicUsage(word, lang)
            if (!previous.isNullOrBlank()) contextPredictionProvider.recordSequence(previous, word, lang, second)
        }
    }

    // ------------------------------------------------------------ suggestions

    /** The user tapped a suggestion chip. Replaces only the partial word before the cursor. */
    fun onAutocompleteSelected(suggestion: AutocompleteSuggestion) {
        lastOwnEditAt = clock()
        val ctx = context()
        val prefix = if (ctx.cursorInsideWord) "" else ctx.currentWord
        val word = if (prefix.isEmpty() && ctx.atSentenceStart && policy.autoCapitalize && suggestion.kind == SuggestionKind.PREDICTION)
            suggestion.word.replaceFirstChar { it.titlecase() } else suggestion.word
        val nextChar = ctx.after.firstOrNull()
        val needsSpace = nextChar == null || !(nextChar.isWhitespace() || nextChar.toString() in WORD_SEPARATOR_PUNCTUATION)

        textConnection.beginBatchEdit()
        if (prefix.isNotEmpty()) textConnection.deleteCharactersBackward(prefix.length)
        textConnection.commitText(if (needsSpace) "$word " else word)
        textConnection.endBatchEdit()

        if (policy.allowLearning) {
            val lang = suggestion.languageCode
            val previous = ctx.previousWord
            val second = ctx.secondPreviousWord
            scope.launch {
                if (suggestion.kind == SuggestionKind.LITERAL) learnedVocabularyProvider.recordRejectedCorrection(word, lang)
                else learnedVocabularyProvider.recordAcceptedSuggestion(word, lang)
                if (!previous.isNullOrBlank()) contextPredictionProvider.recordSequence(previous, word, lang, second)
            }
        }
        if (suggestion.kind == SuggestionKind.LITERAL) rejectedCorrections.add(word.lowercase())
        lastCorrection = null
        phantomSpace = needsSpace
        lastKeyWasSpace = false
        consumeOneShotShift()
        updateAutoShift()
        scheduleRefresh(immediate = true)
    }

    // ------------------------------------------------------------ shift / mode

    private fun setMode(mode: KeyboardMode) = _uiState.update { it.copy(mode = mode) }

    private fun handleShiftPress() {
        val now = clock()
        val isDoubleTap = now - lastShiftPressAt < DOUBLE_TAP_SHIFT_MILLIS
        lastShiftPressAt = now
        val next = when {
            isDoubleTap && state.shiftState != ShiftState.CAPS_LOCK -> ShiftState.CAPS_LOCK
            state.shiftState == ShiftState.OFF -> ShiftState.SHIFTED_ONCE
            else -> ShiftState.OFF
        }
        autoShifted = false
        _uiState.update { it.copy(shiftState = next) }
    }

    private fun consumeOneShotShift() {
        if (state.shiftState == ShiftState.SHIFTED_ONCE) {
            autoShifted = false
            _uiState.update { it.copy(shiftState = ShiftState.OFF) }
        }
    }

    /**
     * Arms a one-shot shift at a sentence start (or word start, for
     * name-style fields), and -- unlike before -- also disarms an automatic
     * shift once the context no longer calls for it (e.g. after Backspace).
     * Never overrides a shift the user set manually or caps lock.
     */
    private fun updateAutoShift() {
        val current = state.shiftState
        if (current == ShiftState.CAPS_LOCK) return
        if (current == ShiftState.SHIFTED_ONCE && !autoShifted) return
        val ctx = context()
        val shouldShift = when {
            policy.capitalizeEveryWord -> ctx.currentWord.isEmpty() && (ctx.before.isEmpty() || ctx.before.last().isWhitespace())
            policy.autoCapitalize -> ctx.atSentenceStart
            else -> false
        }
        if (shouldShift && current == ShiftState.OFF) {
            autoShifted = true
            _uiState.update { it.copy(shiftState = ShiftState.SHIFTED_ONCE) }
        } else if (!shouldShift && autoShifted && current == ShiftState.SHIFTED_ONCE) {
            autoShifted = false
            _uiState.update { it.copy(shiftState = ShiftState.OFF) }
        }
    }

    // ------------------------------------------------------------ refresh

    private fun scheduleRefresh(immediate: Boolean = false) {
        suggestionJob?.cancel()
        suggestionJob = scope.launch {
            if (!immediate) delay(SUGGESTION_DEBOUNCE_MILLIS)
            refreshSuggestions()
        }
        hintJob?.cancel()
        hintJob = scope.launch {
            delay(if (immediate) 0 else HINT_DEBOUNCE_MILLIS)
            refreshHints()
        }
    }

    private suspend fun refreshSuggestions() {
        if (!policy.showSuggestions) {
            suggestionsComputedFor = null
            if (state.suggestions.isNotEmpty() || state.autoCorrection != null) _uiState.update { it.copy(suggestions = emptyList(), autoCorrection = null) }
            return
        }
        val ctx = context()
        val language = languageDetector.detect(ctx.currentSentence.ifEmpty { ctx.currentWord })
        val prefix = if (ctx.cursorInsideWord) "" else ctx.currentWord
        // Predict the next word only right after a space (not straight after "word," or mid-punctuation).
        val canPredict = prefix.isNotEmpty() || ctx.before.isEmpty() || ctx.before.last().isWhitespace()
        val result = if (!canPredict) com.keylingo.app.domain.model.SuggestionResult.EMPTY else generateSuggestions(
            SuggestionRequest(
                prefix = prefix,
                previousWord = ctx.previousWord,
                secondPreviousWord = ctx.secondPreviousWord,
                languageCode = language,
                maxSuggestions = MAX_SUGGESTIONS,
                allowAutoCorrect = state.settings.autocorrectEnabled && policy.allowAutoCorrect && !ctx.cursorInsideWord,
                allowPersonalData = policy.allowLearning,
                rejectedCorrections = rejectedCorrections
            ),
            now = clock()
        )
        val capitalised = if (prefix.isEmpty() && ctx.atSentenceStart && policy.autoCapitalize) {
            result.suggestions.map { it.copy(word = it.word.replaceFirstChar { c -> c.titlecase() }) }
        } else result.suggestions
        suggestionsComputedFor = prefix
        val s = state
        if (s.suggestions != capitalised || s.autoCorrection != result.autoCorrection || s.typingLanguage != language) {
            _uiState.update { it.copy(suggestions = capitalised, autoCorrection = result.autoCorrection, typingLanguage = language) }
        }
    }

    private suspend fun refreshHints() {
        val s = state
        if (!s.settings.learningEnabled || !policy.allowLearningHints) {
            if (s.assistance != LearningAssistance.EMPTY) _uiState.update { it.copy(assistance = LearningAssistance.EMPTY) }
            return
        }
        val ctx = context()
        // Keep showing the sentence just finished ("I am tired." ) until the next one starts.
        val sentence = ctx.currentSentence.ifEmpty {
            val head = ctx.before.trimEnd()
            val end = head.length
            val start = TextContext.lastSentenceBoundary(head.dropLast(1))
            if (end > 0) head.substring(start, end).trim() else ""
        }
        val assistance = generateHintsUseCase(sentence, languagePack.languageCode, s.settings.learningMode)
        if (state.assistance != assistance) _uiState.update { it.copy(assistance = assistance) }
    }

    companion object {
        const val CONTEXT_WINDOW = 240
        const val MAX_SUGGESTIONS = 5
        const val SUGGESTION_DEBOUNCE_MILLIS = 20L
        const val HINT_DEBOUNCE_MILLIS = 220L
        const val DOUBLE_SPACE_WINDOW_MILLIS = 500L
        const val DOUBLE_TAP_SHIFT_MILLIS = 300L
        const val OWN_EDIT_ECHO_MILLIS = 250L
    }
}
