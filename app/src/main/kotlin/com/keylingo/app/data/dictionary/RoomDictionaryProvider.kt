package com.keylingo.app.data.dictionary

import com.keylingo.app.domain.model.CefrLevel
import com.keylingo.app.domain.model.DictionaryEntry
import com.keylingo.app.domain.model.GrammaticalForm
import com.keylingo.app.domain.model.PartOfSpeech
import com.keylingo.app.domain.repository.DictionaryProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [DictionaryProvider] backed by Room. Adds a small in-memory
 * cache on top of the indexed Room query so repeated lookups of the same
 * word (very common while a sentence is being typed and re-scanned)
 * resolve in microseconds instead of hitting SQLite again.
 *
 * A single row now carries every grammatical form a word has (base /
 * infinitive / gerund), so the cache key deliberately does NOT include
 * [GrammaticalForm] -- one row lookup serves every form. The [form]
 * parameter is accepted (per the [DictionaryProvider] contract) but
 * unused here for that reason; it exists so a future provider that *does*
 * need to fetch per-form (e.g. a remote conjugation API) can use it
 * without another interface change.
 *
 * [java.util.concurrent.ConcurrentHashMap] cannot hold null values, and we
 * deliberately want to cache "this word has no translation" results too --
 * so cache entries are wrapped in [CacheSlot] instead of storing a raw
 * nullable [DictionaryEntry].
 */
@Singleton
class RoomDictionaryProvider @Inject constructor(
    private val dao: DictionaryDao
) : DictionaryProvider {

    private sealed class CacheSlot {
        data class Hit(val entry: DictionaryEntry) : CacheSlot()
        object Miss : CacheSlot()
    }

    // Cache key: "sourceWord|sourceLanguage|targetLanguage"
    private val cache = ConcurrentHashMap<String, CacheSlot>()

    override suspend fun lookup(
        word: String,
        sourceLanguage: String,
        targetLanguage: String,
        form: GrammaticalForm
    ): DictionaryEntry? {
        val normalized = word.trim().lowercase()
        if (normalized.isEmpty()) return null
        val key = "$normalized|$sourceLanguage|$targetLanguage"

        cache[key]?.let { slot ->
            return when (slot) {
                is CacheSlot.Hit -> slot.entry
                CacheSlot.Miss -> null
            }
        }

        return withContext(Dispatchers.IO) {
            val entity = dao.findWord(normalized, sourceLanguage, targetLanguage)
            val entry = entity?.let {
                DictionaryEntry(
                    id = it.id,
                    sourceWord = it.sourceWord,
                    baseTranslation = it.targetBase,
                    infinitiveTranslation = it.targetInfinitive,
                    gerundTranslation = it.targetGerund,
                    sourceLanguage = it.sourceLanguage,
                    targetLanguage = it.targetLanguage,
                    partOfSpeech = PartOfSpeech.fromCode(it.partOfSpeech),
                    frequencyRank = it.frequencyRank,
                    cefrLevel = CefrLevel.fromCode(it.cefrLevel)
                )
            }
            cache[key] = if (entry != null) CacheSlot.Hit(entry) else CacheSlot.Miss
            entry
        }
    }

    override suspend fun isReady(sourceLanguage: String, targetLanguage: String): Boolean {
        return entryCount(sourceLanguage, targetLanguage) > 0
    }

    override suspend fun entryCount(sourceLanguage: String, targetLanguage: String): Int {
        return withContext(Dispatchers.IO) {
            dao.countForLanguagePair(sourceLanguage, targetLanguage)
        }
    }
}
