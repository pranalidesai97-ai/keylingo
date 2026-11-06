package com.keylingo.app.data.dictionary

import android.content.Context
import android.util.Log
import com.keylingo.app.domain.repository.LanguagePack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.InputStreamReader

/**
 * Populates Room from a bundled JSON asset the very first time a language
 * pack is used. This is intentionally simple (plain org.json, no extra
 * serialization dependency) and is designed to be swapped for a 5000+
 * word file, or many language files, without any code changes: just
 * point [LanguagePack.dictionaryAssetPath] at the new asset.
 *
 * Expected JSON shape (one row per lemma, multiple forms supported):
 * [
 *   { "en": "work", "es": "trabajar", "infinitive": "trabajar", "gerund": "trabajando", "pos": "verb", "rank": 40, "cefr": "A1" },
 *   { "en": "house", "es": "casa", "pos": "noun", "rank": 120, "cefr": "A1" }
 * ]
 * "es" (the target-language translation) and "en" (source word) are the
 * only fields the bundled `assets/dictionary/en_es.json` currently sets --
 * "infinitive"/"gerund"/"rank"/"cefr" are supported by this seeder and by
 * [DictionaryEntity] but are not yet populated in that asset; adding them
 * per-entry is a content task, not a code change, and is what unlocks
 * richer per-word hint forms later. "infinitive"/"gerund" would be omitted
 * for non-verbs. "rank" is a frequency heuristic (lower = more common);
 * entries without it default to a large value so they sort last. "cefr" is
 * one of A1/A2/B1/B2/C1; missing or unrecognized values default to B1 (see
 * [com.keylingo.app.domain.model.CefrLevel]).
 */
class DictionarySeeder(
    private val context: Context,
    private val dao: DictionaryDao
) {
    private val tag = "DictionarySeeder"

    suspend fun seedIfNeeded(languagePack: LanguagePack, sourceLanguage: String = "en") {
        withContext(Dispatchers.IO) {
            val existing = dao.countForLanguagePair(sourceLanguage, languagePack.languageCode)
            if (existing > 0) {
                Log.d(tag, "Dictionary for $sourceLanguage->${languagePack.languageCode} already seeded ($existing entries)")
                return@withContext
            }

            val json = readAsset(languagePack.dictionaryAssetPath) ?: run {
                Log.e(tag, "Dictionary asset not found: ${languagePack.dictionaryAssetPath}")
                return@withContext
            }

            val array = JSONArray(json)
            val entities = ArrayList<DictionaryEntity>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val source = obj.optString(sourceLanguage).trim().lowercase()
                val base = obj.optString("es").trim()
                if (source.isEmpty() || base.isEmpty()) continue
                val infinitive = obj.optString("infinitive").trim().ifEmpty { null }
                val gerund = obj.optString("gerund").trim().ifEmpty { null }
                val pos = obj.optString("pos", "other")
                val rank = if (obj.has("rank")) obj.optInt("rank", Int.MAX_VALUE) else Int.MAX_VALUE
                val cefr = obj.optString("cefr").trim().ifEmpty { "B1" }

                entities.add(
                    DictionaryEntity(
                        sourceWord = source,
                        targetBase = base,
                        targetInfinitive = infinitive,
                        targetGerund = gerund,
                        sourceLanguage = sourceLanguage,
                        targetLanguage = languagePack.languageCode,
                        partOfSpeech = pos,
                        frequencyRank = rank,
                        cefrLevel = cefr
                    )
                )
            }

            entities.chunked(500).forEach { chunk -> dao.insertAll(chunk) }
            Log.d(tag, "Seeded ${entities.size} entries for $sourceLanguage->${languagePack.languageCode}")
        }
    }

    private fun readAsset(path: String): String? {
        return try {
            context.assets.open(path).use { input ->
                InputStreamReader(input).use { reader -> reader.readText() }
            }
        } catch (e: java.io.IOException) {
            null
        }
    }
}
