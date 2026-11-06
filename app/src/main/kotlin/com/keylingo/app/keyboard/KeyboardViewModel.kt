package com.keylingo.app.keyboard

import com.keylingo.app.data.settings.KeyLingoSettings
import com.keylingo.app.data.settings.SettingsDataStore
import com.keylingo.app.domain.model.AutocompleteSuggestion
import com.keylingo.app.domain.model.HintWord
import com.keylingo.app.domain.repository.ContextPredictionProvider
import com.keylingo.app.domain.repository.LanguagePack
import com.keylingo.app.domain.repository.LearnedVocabularyProvider
import com.keylingo.app.domain.usecase.GenerateAutocompleteSuggestionsUseCase
import com.keylingo.app.domain.usecase.GenerateHintsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ShiftState { OFF, SHIFTED_ONCE, CAPS_LOCK }

data class KeyboardUiState(
    val mode: KeyboardMode = KeyboardMode.LETTERS,
    val shiftState: ShiftState = ShiftState.OFF,
    val autocompleteSuggestions: List<AutocompleteSuggestion> = emptyList(),
    val hints: List<HintWord> = emptyList(),
    val settings: KeyLingoSettings = KeyLingoSettings()
)

/**
 * Extracts the word currently being typed from the full field text: the
 * last whitespace-delimited token. Returns "" when the text is empty or
 * ends in whitespace (nothing is mid-word right now). Pure and
 * independent of Android/coroutines/DI on purpose, so it's trivially
 * testable and reusable without duplicating the logic.
 *
 * Assumption: this operates on the full field text and assumes the
 * cursor is at the end of it, matching how [KeyboardTextConnection.currentFieldText]
 * is already used for hint generation elsewhere in this class. A field
 * edited mid-text (cursor not at the end) is out of scope, same as it
 * already was for hints.
 */
private fun extractCurrentPrefix(text: String): String {
    if (text.isEmpty() || text.last().isWhitespace()) return ""
    val lastWhitespaceIndex = text.indexOfLast { it.isWhitespace() }
    return if (lastWhitespaceIndex == -1) text else text.substring(lastWhitespaceIndex + 1)
}

/**
 * Extracts the word immediately before whatever [extractCurrentPrefix]
 * would return, for bigram context. Handles both real scenarios:
 *
 * - Mid-word (e.g. "I am happ"): the current word is "happ" (non-empty),
 *   so the previous word is whatever came before it -- "am".
 * - Right after a word boundary (e.g. "I am " -- a trailing space was
 *   just committed): [extractCurrentPrefix] returns "" here (nothing
 *   typed yet for the next word), so "the previous word" is simply the
 *   last completed word in the text so far -- also "am". This is the case
 *   that matters for pure next-word prediction (prefix empty, but there's
 *   still a real word to key context off of).
 *
 * Returns "" if there's no earlier word at all (e.g. text is just "I " or empty).
 */
private fun extractPreviousCompletedWord(text: String): String {
    val currentWord = extractCurrentPrefix(text)
    return if (currentWord.isEmpty()) {
        extractCurrentPrefix(text.trimEnd())
    } else {
        val withoutCurrentWord = text.dropLast(currentWord.length).trimEnd()
        extractCurrentPrefix(withoutCurrentWord)
    }
}

/** Punctuation that ends a word/sentence -- committing one of these is treated as "the word right before it was just completed" for personal-vocabulary/bigram learning purposes. Deliberately excludes other Symbol-action characters (emoji, currency signs, etc.), which aren't word boundaries. */
private val WORD_BOUNDARY_SYMBOLS = setOf(".", ",", "!", "?", ";", ":")

/**
 * Holds all keyboard + suggestion-bar state and turns [KeyAction]s into
 * real text commits via [KeyboardTextConnection]. This is a plain Kotlin
 * class rather than an androidx.lifecycle.ViewModel because an
 * InputMethodService has no Activity/Fragment-style ViewModelStoreOwner
 * to host one -- the IME service supplies a long-lived [CoroutineScope]
 * instead and cancels it in onDestroy().
 *
 * Three independent learning/data sources feed [generateAutocompleteSuggestionsUseCase]
 * (which itself merges them -- see that use case's KDoc): the static
 * bundled word list, personal learned vocabulary, and personal
 * word-sequence (bigram) history. This class's job is just to report the
 * two real signals each of those needs -- a word being completed
 * ([recordWordBoundary]) and a suggestion being explicitly tapped
 * ([onAutocompleteSelected]) -- and to read back a merged, ranked result
 * for [KeyboardUiState]. It has no ranking logic of its own.
 *
 * Hints ([generateHintsUseCase]) remain a fully separate pipeline, driven
 * by the full field text, active only when [KeyLingoSettings.learningEnabled]
 * is on -- unchanged from before.
 */
class KeyboardViewModel(
    private val generateHintsUseCase: GenerateHintsUseCase,
    private val generateAutocompleteSuggestionsUseCase: GenerateAutocompleteSuggestionsUseCase,
    private val learnedVocabularyProvider: LearnedVocabularyProvider,
    private val contextPredictionProvider: ContextPredictionProvider,
    private val settingsDataStore: SettingsDataStore,
    private val languagePack: LanguagePack,
    private val textConnection: KeyboardTextConnection,
    private val scope: CoroutineScope
) {
    private val _uiState = MutableStateFlow(KeyboardUiState())
    val uiState: StateFlow<KeyboardUiState> = _uiState.asStateFlow()

    private var suggestionsDebounceJob: Job? = null
    private var lastShiftPressAtMillis = 0L
    private val doubleTapWindowMillis = 300L
    private var lastKeyWasSpace = false
    private var lastSpacePressAtMillis = 0L
    private val doubleSpaceWindowMillis = 500L

    init {
        scope.launch {
            settingsDataStore.settingsFlow.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
                refreshSuggestions()
            }
        }
    }

    /** Called by the IME whenever the field content changes underneath us (focus change, etc). */
    fun onInputFieldContentChanged() {
        scheduleSuggestionsRefresh()
    }

    fun onKeyAction(action: KeyAction) {
        val wasSpace = lastKeyWasSpace
        lastKeyWasSpace = false

        when (action) {
            is KeyAction.Character -> {
                val useUpper = _uiState.value.shiftState != ShiftState.OFF
                textConnection.commitText(if (useUpper) action.upper else action.lower)
                consumeOneShotShift()
                maybeAutoCapitalize()
                scheduleSuggestionsRefresh()
            }
            is KeyAction.Symbol -> {
                if (action.value in WORD_BOUNDARY_SYMBOLS) {
                    recordWordBoundary(textConnection.currentFieldText())
                }
                textConnection.commitText(action.value)
                maybeAutoCapitalize()
                scheduleSuggestionsRefresh()
            }
            KeyAction.Space -> {
                val now = System.currentTimeMillis()
                val text = textConnection.currentFieldText()
                // Double-space-to-period: only if the previous key press was
                // also Space, within the double-tap window, the field
                // genuinely ends with a single space right now (the one that
                // press just committed), and there's a non-whitespace
                // character right before it -- so this never fires at the
                // start of a field or after already-punctuated/blank text.
                val isDoubleSpace = wasSpace &&
                    (now - lastSpacePressAtMillis) < doubleSpaceWindowMillis &&
                    text.isNotEmpty() && text.last() == ' ' &&
                    text.length >= 2 && !text[text.length - 2].isWhitespace()

                if (isDoubleSpace) {
                    // The word before this space was already recorded when
                    // the first of the two spaces was pressed (see below);
                    // don't double-record it here.
                    textConnection.deleteOneCharacterBackward() // remove the space just committed
                    textConnection.commitText(". ")
                } else {
                    recordWordBoundary(text)
                    textConnection.commitText(" ")
                }
                lastKeyWasSpace = true
                lastSpacePressAtMillis = now
                maybeAutoCapitalize()
                scheduleSuggestionsRefresh()
            }
            KeyAction.Backspace -> {
                // Deletion never records anything -- word-boundary
                // learning only fires from the specific key actions above
                // and below, never from the debounced refresh, so
                // backspacing through text cannot create incorrect
                // sequence/vocabulary records.
                textConnection.deleteOneCharacterBackward()
                maybeAutoCapitalize()
                scheduleSuggestionsRefresh()
            }
            KeyAction.Enter -> {
                recordWordBoundary(textConnection.currentFieldText())
                textConnection.performEnterAction()
                maybeAutoCapitalize()
                scheduleSuggestionsRefresh()
            }
            KeyAction.Shift -> handleShiftPress()
            KeyAction.SwitchToSymbols -> setMode(
                if (_uiState.value.mode == KeyboardMode.SYMBOLS_1) KeyboardMode.SYMBOLS_2 else KeyboardMode.SYMBOLS_1
            )
            KeyAction.SwitchToLetters -> setMode(KeyboardMode.LETTERS)
            KeyAction.SwitchToNumbers -> setMode(KeyboardMode.SYMBOLS_1)
            KeyAction.Emoji -> setMode(KeyboardMode.EMOJI)
        }
    }

    /**
     * Auto-capitalization: after a sentence boundary -- start of field, a
     * newline, or a "."/"!"/"?" as the last non-space character before the
     * cursor (fires as soon as the punctuation itself is typed, whether or
     * not a space follows yet) -- arms a one-shot shift so the next letter
     * typed comes out uppercase without the user pressing Shift -- reusing
     * [ShiftState.SHIFTED_ONCE] and [consumeOneShotShift] rather than a
     * separate mechanism. Only ever upgrades from OFF; never touches an
     * existing SHIFTED_ONCE or CAPS_LOCK the user set manually, so it
     * can't fight a deliberate manual shift/caps-lock choice.
     */
    private fun maybeAutoCapitalize() {
        if (_uiState.value.shiftState != ShiftState.OFF) return
        val text = textConnection.currentFieldText()
        val shouldCapitalize = if (text.isEmpty()) {
            true
        } else if (text.endsWith("\n")) {
            true
        } else {
            val trimmed = text.trimEnd(' ')
            trimmed.isEmpty() || trimmed.last() == '.' || trimmed.last() == '!' || trimmed.last() == '?'
        }
        if (shouldCapitalize) {
            _uiState.update { it.copy(shiftState = ShiftState.SHIFTED_ONCE) }
        }
    }

    /**
     * Fire-and-forget record of a naturally-completed word boundary:
     * reports the completed word to [learnedVocabularyProvider] (personal
     * vocabulary) *and* the (previousWord -> completedWord) pair to
     * [contextPredictionProvider] (bigram history), both from the same
     * text snapshot so they're always consistent with each other. Both
     * providers own their own eligibility rules (via the shared
     * `WordEligibility` check) and thresholds -- this call site doesn't
     * duplicate any of that, it just reports "this boundary happened".
     *
     * [text] must be the field's content *before* the boundary character
     * (space/enter/punctuation) is committed -- callers pass the snapshot
     * they already read for that purpose.
     */
    private fun recordWordBoundary(text: String) {
        val completedWord = extractCurrentPrefix(text)
        if (completedWord.isBlank()) return

        val previousWord = extractPreviousCompletedWord(text)

        scope.launch {
            learnedVocabularyProvider.recordOrganicUsage(completedWord)
            if (previousWord.isNotBlank()) {
                contextPredictionProvider.recordSequence(previousWord, completedWord)
            }
        }
    }

    /**
     * Called when the user taps a suggestion in the autocomplete row.
     * Replaces only the in-progress word (never the whole field): deletes
     * exactly the current prefix's length immediately before the cursor,
     * commits the chosen word, then a single trailing space so typing can
     * continue immediately. Also records this as an explicit acceptance --
     * a stronger learning signal than organic typing (see
     * [LearnedVocabularyProvider.recordAcceptedSuggestion]) -- and, if
     * there's a previous word to key off, records the bigram too, exactly
     * as if the word had been typed and completed organically.
     */
    fun onAutocompleteSelected(suggestion: AutocompleteSuggestion) {
        val textBefore = textConnection.currentFieldText()
        val prefix = extractCurrentPrefix(textBefore)
        val previousWord = extractPreviousCompletedWord(textBefore)

        if (prefix.isNotEmpty()) {
            textConnection.deleteCharactersBackward(prefix.length)
        }
        textConnection.commitText("${suggestion.word} ")

        scope.launch {
            learnedVocabularyProvider.recordAcceptedSuggestion(suggestion.word)
            if (previousWord.isNotBlank()) {
                contextPredictionProvider.recordSequence(previousWord, suggestion.word)
            }
        }

        lastKeyWasSpace = false
        scheduleSuggestionsRefresh()
    }

    private fun setMode(mode: KeyboardMode) {
        _uiState.update { it.copy(mode = mode) }
    }

    private fun handleShiftPress() {
        val now = System.currentTimeMillis()
        val isDoubleTap = now - lastShiftPressAtMillis < doubleTapWindowMillis
        lastShiftPressAtMillis = now

        val current = _uiState.value.shiftState
        val next = when {
            isDoubleTap -> ShiftState.CAPS_LOCK
            current == ShiftState.OFF -> ShiftState.SHIFTED_ONCE
            else -> ShiftState.OFF
        }
        _uiState.update { it.copy(shiftState = next) }
    }

    private fun consumeOneShotShift() {
        if (_uiState.value.shiftState == ShiftState.SHIFTED_ONCE) {
            _uiState.update { it.copy(shiftState = ShiftState.OFF) }
        }
    }

    private fun scheduleSuggestionsRefresh() {
        suggestionsDebounceJob?.cancel()
        suggestionsDebounceJob = scope.launch {
            delay(150)
            refreshSuggestions()
        }
    }

    /**
     * Single shared text read, then two independent pipelines merged into
     * one state update. Autocomplete (itself now driven by both the
     * current prefix *and* the previous completed word, for bigram
     * context) runs regardless of [KeyLingoSettings.learningEnabled];
     * hints keep their pre-existing gating on it, unchanged.
     */
    private suspend fun refreshSuggestions() {
        val text = textConnection.currentFieldText()
        val settings = _uiState.value.settings

        val prefix = extractCurrentPrefix(text)
        val previousWord = extractPreviousCompletedWord(text)
        val autocomplete = if (prefix.isEmpty() && previousWord.isBlank()) {
            emptyList()
        } else {
            generateAutocompleteSuggestionsUseCase(prefix = prefix, previousWord = previousWord)
        }

        val hints = if (settings.learningEnabled) {
            generateHintsUseCase(
                text = text,
                sourceLanguage = "en",
                targetLanguage = languagePack.languageCode,
                learningMode = settings.learningMode
            )
        } else {
            emptyList()
        }

        // Avoid a no-op StateFlow emission (and the recomposition it would
        // trigger downstream) when the results are identical to what's
        // already shown -- e.g. the debounce firing again mid-pause with
        // the same prefix, or backspacing back to text that resolves to
        // the same suggestions. List equality here is structural
        // (List.equals), not identity, so this correctly catches "same
        // content, new list instance" too.
        val current = _uiState.value
        if (current.autocompleteSuggestions != autocomplete || current.hints != hints) {
            _uiState.update { it.copy(autocompleteSuggestions = autocomplete, hints = hints) }
        }
    }
}
