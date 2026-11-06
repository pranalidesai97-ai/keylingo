package com.keylingo.app.data.bundled

import com.keylingo.app.domain.model.AutocompleteSuggestion
import com.keylingo.app.domain.model.SuggestionKind
import com.keylingo.app.domain.repository.AutocompleteProvider
import com.keylingo.app.domain.repository.CorrectionCandidate
import com.keylingo.app.data.AppDispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AutocompleteProvider] over the in-memory bundled lexicons (English: SCOWL
 * derived; Spanish: generated from the dictionary and conjugator). All work
 * runs on [Dispatchers.Default] -- never on the IME's main thread.
 * Returns empty results (not errors) until the data has loaded.
 */
@Singleton
class BundledAutocompleteProvider @Inject constructor(
    private val source: BundledDataSupplier,
    private val dispatchers: AppDispatchers
) : AutocompleteProvider {

    private suspend fun <T> withData(empty: T, block: (BundledLanguageData) -> T): T {
        val data = source.get() ?: return empty
        return withContext(dispatchers.compute) { block(data) }
    }

    override suspend fun suggest(prefix: String, languageCode: String, maxSuggestions: Int): List<AutocompleteSuggestion> =
        withData(emptyList()) { data ->
            data.lexiconFor(languageCode).prefixMatches(prefix, maxSuggestions)
                .map { AutocompleteSuggestion(it.display, it.rank, SuggestionKind.COMPLETION, languageCode) }
        }

    override suspend fun corrections(typed: String, languageCode: String, maxSuggestions: Int, includeCompletions: Boolean): List<CorrectionCandidate> =
        withData(emptyList()) { data ->
            data.lexiconFor(languageCode).corrections(typed, maxSuggestions, includeCompletions)
                .map { CorrectionCandidate(it.word.display, it.word.rank, it.distance, it.isPrefixMatch) }
        }

    override suspend fun isKnownWord(word: String, languageCode: String): Boolean =
        withData(false) { it.lexiconFor(languageCode).contains(word) }

    override suspend fun spellingVariants(word: String, languageCode: String): List<String> =
        withData(emptyList()) { data ->
            data.lexiconFor(languageCode).findByKey(word).filter { it.suggestable }.sortedBy { it.rank }.map { it.display }
        }

    override suspend fun knownMisspelling(word: String, languageCode: String): String? =
        if (languageCode != "en") null else withData(null) { it.englishMisspellings[word.lowercase()] }

    override suspend fun seedNextWords(previousWord: String, languageCode: String): List<String> =
        withData(emptyList()) { it.nextWordsFor(languageCode).after(previousWord) }

    override suspend fun rankOf(word: String, languageCode: String): Int? =
        withData(null) { it.lexiconFor(languageCode).find(word)?.rank }

    override suspend fun isReady(): Boolean = source.get() != null

    override suspend fun wordCount(): Int = withData(0) { it.englishLexicon.size }
}
