package com.keylingo.app.data.dictionary

import com.keylingo.app.domain.model.GrammaticalForm
import com.keylingo.app.domain.model.PhraseChunk
import com.keylingo.app.domain.repository.PhraseChunkProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [PhraseChunkProvider] backed by Room. The chunk set for a
 * language pair is small (dozens of rows, not thousands) and effectively
 * static, so it's cached in full after the first load rather than querying
 * per lookup -- this keeps [com.keylingo.app.data.hint.SentenceAnalyzer]'s
 * greedy longest-match loop entirely in-memory with no per-token suspend
 * calls.
 */
@Singleton
class RoomPhraseChunkProvider @Inject constructor(
    private val dao: PhraseChunkDao
) : PhraseChunkProvider {

    private val cache = ConcurrentHashMap<String, List<PhraseChunk>>()

    override suspend fun allChunks(sourceLanguage: String, targetLanguage: String): List<PhraseChunk> {
        val key = "$sourceLanguage|$targetLanguage"
        cache[key]?.let { return it }

        return withContext(Dispatchers.IO) {
            val chunks = dao.allChunks(sourceLanguage, targetLanguage).map { entity ->
                PhraseChunk(
                    sourcePhrase = entity.sourcePhrase,
                    targetPhrase = entity.targetPhrase,
                    sourceLanguage = entity.sourceLanguage,
                    targetLanguage = entity.targetLanguage,
                    wordCount = entity.wordCount,
                    requiredNextForm = entity.requiredNextForm?.let { name ->
                        runCatching { GrammaticalForm.valueOf(name) }.getOrNull()
                    }
                )
            }
            cache[key] = chunks
            chunks
        }
    }
}
