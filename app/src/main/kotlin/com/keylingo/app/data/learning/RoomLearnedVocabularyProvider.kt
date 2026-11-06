package com.keylingo.app.data.learning

import com.keylingo.app.data.WordEligibility
import com.keylingo.app.domain.model.LearnedWord
import com.keylingo.app.domain.repository.LearnedVocabularyProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [LearnedVocabularyProvider], backed by Room.
 *
 * - Eligibility ("don't learn typos/garbage") is delegated to
 *   [WordEligibility], shared with [com.keylingo.app.data.context.RoomContextPredictionProvider]
 *   so both features apply identical rules to the raw words they persist.
 * - The "learned" threshold lives in [LearnedWordDao.findByPrefix]'s SQL:
 *   a word only surfaces in [suggest] after 3 organic completions OR 1
 *   explicit acceptance -- a single incidental typing is never enough on
 *   its own, which is the actual mechanism preventing one-off typos from
 *   becoming permanent "learned" vocabulary.
 */
@Singleton
class RoomLearnedVocabularyProvider @Inject constructor(
    private val dao: LearnedWordDao
) : LearnedVocabularyProvider {

    override suspend fun recordOrganicUsage(word: String, languageCode: String) {
        recordUsage(word, languageCode, isAccepted = false)
    }

    override suspend fun recordAcceptedSuggestion(word: String, languageCode: String) {
        recordUsage(word, languageCode, isAccepted = true)
    }

    private suspend fun recordUsage(word: String, languageCode: String, isAccepted: Boolean) {
        val normalized = word.trim().lowercase()
        if (!WordEligibility.isEligibleWord(normalized)) return

        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val existing = dao.find(normalized, languageCode)
            if (existing != null) {
                dao.update(
                    existing.copy(
                        organicCount = existing.organicCount + if (isAccepted) 0 else 1,
                        acceptedCount = existing.acceptedCount + if (isAccepted) 1 else 0,
                        lastUsedAtEpochMillis = now
                    )
                )
            } else {
                dao.insert(
                    LearnedWordEntity(
                        word = normalized,
                        language = languageCode,
                        organicCount = if (isAccepted) 0 else 1,
                        acceptedCount = if (isAccepted) 1 else 0,
                        lastUsedAtEpochMillis = now
                    )
                )
            }
        }
    }

    override suspend fun suggest(prefix: String, languageCode: String, maxSuggestions: Int): List<LearnedWord> {
        val normalized = prefix.trim().lowercase()
        if (normalized.length < 2) return emptyList()

        return withContext(Dispatchers.IO) {
            dao.findByPrefix(normalized, languageCode, maxSuggestions).map {
                LearnedWord(
                    word = it.word,
                    organicCount = it.organicCount,
                    acceptedCount = it.acceptedCount,
                    lastUsedAtEpochMillis = it.lastUsedAtEpochMillis
                )
            }
        }
    }
}
