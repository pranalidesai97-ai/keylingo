package com.keylingo.app.testing

import com.keylingo.app.data.AppDispatchers
import com.keylingo.app.data.WordEligibility
import com.keylingo.app.data.bundled.BundledAutocompleteProvider
import com.keylingo.app.data.bundled.BundledDataSupplier
import com.keylingo.app.data.bundled.BundledLanguageDetector
import com.keylingo.app.data.hint.LearningAssistant
import com.keylingo.app.domain.model.LearnedWord
import com.keylingo.app.domain.repository.ContextPredictionProvider
import com.keylingo.app.domain.repository.LearnedVocabularyProvider
import com.keylingo.app.keyboard.KeyboardTextConnection
import kotlinx.coroutines.Dispatchers

val testSupplier = BundledDataSupplier { TestData.bundled }
/** Runs "background" work inline, so coroutine tests are deterministic. */
val inlineDispatchers = AppDispatchers(Dispatchers.Unconfined, Dispatchers.Unconfined)
fun realAutocompleteProvider() = BundledAutocompleteProvider(testSupplier, inlineDispatchers)
fun realLanguageDetector() = BundledLanguageDetector(testSupplier, inlineDispatchers)
fun realHintProvider() = LearningAssistant(testSupplier, inlineDispatchers)

/** In-memory personal vocabulary with the same eligibility and threshold rules as the Room implementation. */
class FakeLearnedVocabulary(private val clock: () -> Long = { 0L }) : LearnedVocabularyProvider {
    data class Row(var organic: Int = 0, var accepted: Int = 0, var last: Long = 0)
    val rows = LinkedHashMap<Pair<String, String>, Row>()

    private fun record(word: String, lang: String, organic: Int, accepted: Int) {
        val w = word.trim().lowercase()
        if (!WordEligibility.isEligibleWord(w)) return
        val row = rows.getOrPut(w to lang) { Row() }
        row.organic += organic; row.accepted += accepted; row.last = clock()
    }

    override suspend fun recordOrganicUsage(word: String, languageCode: String) = record(word, languageCode, 1, 0)
    override suspend fun recordAcceptedSuggestion(word: String, languageCode: String) = record(word, languageCode, 0, 1)
    override suspend fun recordRejectedCorrection(word: String, languageCode: String) = record(word, languageCode, 0, 1)
    private fun learned(r: Row) = r.organic >= 3 || r.accepted >= 1
    override suspend fun suggest(prefix: String, languageCode: String, maxSuggestions: Int): List<LearnedWord> =
        rows.filter { (k, r) -> k.second == languageCode && k.first.startsWith(prefix.lowercase()) && learned(r) && prefix.length >= 2 }
            .entries.sortedByDescending { it.value.organic + 3 * it.value.accepted }
            .take(maxSuggestions).map { (k, r) -> LearnedWord(k.first, r.organic, r.accepted, r.last) }
    override suspend fun isLearned(word: String, languageCode: String) = rows[word.lowercase() to languageCode]?.let(::learned) == true
    override suspend fun clearAll() = rows.clear()
}

class FakeContextPrediction : ContextPredictionProvider {
    val bigrams = HashMap<Triple<String, String, String>, Int>()
    val trigrams = HashMap<List<String>, Int>()
    override suspend fun recordSequence(previousWord: String, nextWord: String, languageCode: String, secondPreviousWord: String?) {
        val p = previousWord.lowercase(); val n = nextWord.lowercase()
        if (!WordEligibility.isEligibleWord(p) || !WordEligibility.isEligibleWord(n)) return
        bigrams.merge(Triple(p, n, languageCode), 1, Int::plus)
        secondPreviousWord?.lowercase()?.takeIf { WordEligibility.isEligibleWord(it) }?.let { trigrams.merge(listOf(it, p, n, languageCode), 1, Int::plus) }
    }
    override suspend fun nextWordCounts(previousWord: String, languageCode: String) =
        bigrams.filterKeys { it.first == previousWord.lowercase() && it.third == languageCode }.mapKeys { it.key.second }
    override suspend fun nextWordCountsAfterPair(secondPreviousWord: String, previousWord: String, languageCode: String) =
        trigrams.filterKeys { it[0] == secondPreviousWord.lowercase() && it[1] == previousWord.lowercase() && it[3] == languageCode }.mapKeys { it.key[2] }
    override suspend fun clearAll() { bigrams.clear(); trigrams.clear() }
}

/**
 * Simulates an Android text field: content, cursor and selection, with the
 * same semantics the IME relies on (commit replaces the selection,
 * deleteSurroundingText removes chars before the cursor, Enter inserts a newline).
 */
class FakeEditor(initial: String = "", var cursor: Int = initial.length) : KeyboardTextConnection {
    var text: String = initial
    var selectionEnd: Int = cursor
    var editorActions = 0
    var delKeyEvents = 0
    var multiLine = true
    val hasSelection get() = selectionEnd != cursor
    override fun hasSelection(): Boolean = hasSelection

    override fun commitText(text: String) {
        val start = minOf(cursor, selectionEnd); val end = maxOf(cursor, selectionEnd)
        this.text = this.text.substring(0, start) + text + this.text.substring(end)
        cursor = start + text.length; selectionEnd = cursor
    }
    override fun deleteCharactersBackward(charCount: Int) {
        val n = charCount.coerceAtMost(cursor)
        text = text.substring(0, cursor - n) + text.substring(cursor)
        cursor -= n; selectionEnd = cursor
    }
    override fun deleteBackward(graphemeLength: Int) {
        when {
            hasSelection -> commitText("")
            graphemeLength > 0 -> deleteCharactersBackward(graphemeLength)
            else -> delKeyEvents++
        }
    }
    override fun performEnterAction() { if (multiLine) commitText("\n") else editorActions++ }
    override fun textBeforeCursor(maxChars: Int) = text.substring((cursor - maxChars).coerceAtLeast(0), cursor)
    override fun textAfterCursor(maxChars: Int) = text.substring(cursor, (cursor + maxChars).coerceAtMost(text.length))
    fun moveCursor(to: Int) { cursor = to; selectionEnd = to }
}
