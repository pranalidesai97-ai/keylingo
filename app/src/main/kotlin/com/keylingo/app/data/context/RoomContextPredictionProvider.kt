package com.keylingo.app.data.context

import com.keylingo.app.data.WordEligibility
import com.keylingo.app.domain.repository.ContextPredictionProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [ContextPredictionProvider], backed by Room. [MAX_NEXT_WORDS]
 * bounds how many next-word candidates are ever fetched for one previous
 * word, keeping [nextWordCounts] cheap even for a previous word the user
 * has followed with many different words over time.
 */
@Singleton
class RoomContextPredictionProvider @Inject constructor(
    private val dao: BigramDao
) : ContextPredictionProvider {

    override suspend fun recordSequence(previousWord: String, nextWord: String, languageCode: String) {
        val prev = previousWord.trim().lowercase()
        val next = nextWord.trim().lowercase()
        if (!WordEligibility.isEligibleWord(prev) || !WordEligibility.isEligibleWord(next)) return

        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val existing = dao.find(prev, next, languageCode)
            if (existing != null) {
                dao.update(existing.copy(count = existing.count + 1, lastUsedAtEpochMillis = now))
            } else {
                dao.insert(
                    BigramEntity(
                        previousWord = prev,
                        nextWord = next,
                        language = languageCode,
                        count = 1,
                        lastUsedAtEpochMillis = now
                    )
                )
            }
        }
    }

    override suspend fun nextWordCounts(previousWord: String, languageCode: String): Map<String, Int> {
        val prev = previousWord.trim().lowercase()
        if (prev.isEmpty()) return emptyMap()

        return withContext(Dispatchers.IO) {
            dao.findByPreviousWord(prev, languageCode, MAX_NEXT_WORDS)
                .associate { it.nextWord to it.count }
        }
    }

    private companion object {
        const val MAX_NEXT_WORDS = 50
    }
}
