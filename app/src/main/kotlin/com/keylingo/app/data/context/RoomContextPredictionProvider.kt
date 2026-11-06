package com.keylingo.app.data.context

import com.keylingo.app.domain.model.PersonalDataRetention
import com.keylingo.app.data.WordEligibility
import com.keylingo.app.domain.repository.ContextPredictionProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Room-backed personal bigram/trigram history. Reads are bounded by [MAX_NEXT_WORDS]. */
@Singleton
class RoomContextPredictionProvider @Inject constructor(
    private val dao: BigramDao
) : ContextPredictionProvider {

    override suspend fun recordSequence(previousWord: String, nextWord: String, languageCode: String, secondPreviousWord: String?) {
        val prev = previousWord.trim().lowercase()
        val next = nextWord.trim().lowercase()
        if (!WordEligibility.isEligibleWord(prev) || !WordEligibility.isEligibleWord(next)) return
        val second = secondPreviousWord?.trim()?.lowercase()?.takeIf { WordEligibility.isEligibleWord(it) }
        withContext(Dispatchers.IO) { dao.record(second, prev, next, languageCode, System.currentTimeMillis()) }
    }

    override suspend fun nextWordCounts(previousWord: String, languageCode: String): Map<String, Int> {
        val prev = previousWord.trim().lowercase()
        if (prev.isEmpty()) return emptyMap()
        return withContext(Dispatchers.IO) {
            dao.findByPreviousWord(prev, languageCode, MAX_NEXT_WORDS).associate { it.nextWord to it.count }
        }
    }

    override suspend fun nextWordCountsAfterPair(secondPreviousWord: String, previousWord: String, languageCode: String): Map<String, Int> {
        val second = secondPreviousWord.trim().lowercase()
        val prev = previousWord.trim().lowercase()
        if (second.isEmpty() || prev.isEmpty()) return emptyMap()
        return withContext(Dispatchers.IO) {
            dao.findByPreviousPair(second, prev, languageCode, MAX_NEXT_WORDS).associate { it.nextWord to it.count }
        }
    }

    override suspend fun clearAll() = withContext(Dispatchers.IO) { dao.deleteAll() }

    override suspend fun forgetWord(word: String, languageCode: String) = withContext(Dispatchers.IO) {
        dao.deleteWord(word.trim().lowercase(), languageCode)
    }

    override suspend fun pruneStale(now: Long): Int = withContext(Dispatchers.IO) {
        dao.prune(now - PersonalDataRetention.SINGLE_USE_SEQUENCE_TTL_MS, PersonalDataRetention.MAX_BIGRAMS, PersonalDataRetention.MAX_TRIGRAMS)
    }

    private companion object {
        const val MAX_NEXT_WORDS = 50
    }
}
