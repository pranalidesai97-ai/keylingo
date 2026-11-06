package com.keylingo.app.data.learning

import com.keylingo.app.data.WordEligibility
import com.keylingo.app.domain.model.LearnedWord
import com.keylingo.app.domain.repository.LearnedVocabularyProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed personal vocabulary. Eligibility (no typos/garbage/numbers) is
 * shared with the sequence model via [WordEligibility]; the "learned"
 * threshold lives in [LearnedWordDao.findByPrefix]. Words are stored
 * lowercase; the keyboard re-applies the bundled capitalisation on display.
 */
@Singleton
class RoomLearnedVocabularyProvider @Inject constructor(
    private val dao: LearnedWordDao
) : LearnedVocabularyProvider {

    override suspend fun recordOrganicUsage(word: String, languageCode: String) = record(word, languageCode, organic = 1, accepted = 0)

    override suspend fun recordAcceptedSuggestion(word: String, languageCode: String) = record(word, languageCode, organic = 0, accepted = 1)

    /** Un-correcting a word is an explicit statement that it is intended: treat it like an acceptance. */
    override suspend fun recordRejectedCorrection(word: String, languageCode: String) = record(word, languageCode, organic = 0, accepted = 1)

    private suspend fun record(word: String, languageCode: String, organic: Int, accepted: Int) {
        val normalized = word.trim().lowercase()
        if (!WordEligibility.isEligibleWord(normalized)) return
        withContext(Dispatchers.IO) {
            dao.recordUsage(normalized, languageCode, organic, accepted, System.currentTimeMillis())
        }
    }

    override suspend fun suggest(prefix: String, languageCode: String, maxSuggestions: Int): List<LearnedWord> {
        val normalized = prefix.trim().lowercase()
        if (normalized.length < 2) return emptyList()
        // LIKE treats % and _ as wildcards; eligible words never contain them, so a prefix that does cannot match anyway.
        if (normalized.any { it == '%' || it == '_' }) return emptyList()
        return withContext(Dispatchers.IO) {
            dao.findByPrefix(normalized, languageCode, maxSuggestions).map {
                LearnedWord(it.word, it.organicCount, it.acceptedCount, it.lastUsedAtEpochMillis)
            }
        }
    }

    override suspend fun isLearned(word: String, languageCode: String): Boolean {
        val normalized = word.trim().lowercase()
        if (!WordEligibility.isEligibleWord(normalized)) return false
        return withContext(Dispatchers.IO) { dao.countLearned(normalized, languageCode) > 0 }
    }

    override suspend fun clearAll() = withContext(Dispatchers.IO) { dao.deleteAll() }
}
