package com.keylingo.app.data.autocomplete

import com.keylingo.app.domain.model.AutocompleteSuggestion
import com.keylingo.app.domain.repository.AutocompleteProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [AutocompleteProvider] backed by Room. Unlike
 * [com.keylingo.app.data.dictionary.RoomDictionaryProvider], this does not
 * add an in-memory cache: prefix queries change on almost every keystroke
 * (unlike repeated whole-word dictionary lookups), so a cache keyed by
 * prefix would rarely hit and isn't worth the complexity here.
 */
@Singleton
class RoomAutocompleteProvider @Inject constructor(
    private val dao: EnglishWordDao
) : AutocompleteProvider {

    override suspend fun suggest(prefix: String, maxSuggestions: Int): List<AutocompleteSuggestion> {
        val normalized = prefix.trim().lowercase()
        if (normalized.isEmpty()) return emptyList()

        return withContext(Dispatchers.IO) {
            val rows = dao.findByPrefix(normalized, maxSuggestions)
            rows.map {
                AutocompleteSuggestion(word = it.word, frequencyRank = it.frequencyRank)
            }
        }
    }

    override suspend fun isReady(): Boolean {
        return wordCount() > 0
    }

    override suspend fun wordCount(): Int {
        return withContext(Dispatchers.IO) {
            dao.count()
        }
    }
}
