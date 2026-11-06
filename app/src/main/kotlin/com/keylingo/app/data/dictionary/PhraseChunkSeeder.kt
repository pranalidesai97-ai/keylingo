package com.keylingo.app.data.dictionary

import android.content.Context
import android.util.Log
import com.keylingo.app.domain.repository.LanguagePack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.InputStreamReader

/**
 * Populates Room's `phrase_chunks` table from a bundled JSON asset on
 * first run, mirroring [DictionarySeeder]. Kept as its own seeder (rather
 * than folded into [DictionarySeeder]) since it seeds a different table
 * from a different asset with a different shape -- single responsibility.
 *
 * Expected JSON shape:
 * [
 *   { "en": "i am", "es": "estoy", "nextForm": "GERUND" },
 *   { "en": "i want", "es": "quiero" }
 * ]
 * "nextForm" is one of "BASE" / "INFINITIVE" / "GERUND", or omitted if the
 * chunk doesn't imply a form for the word that follows it.
 */
class PhraseChunkSeeder(
    private val context: Context,
    private val dao: PhraseChunkDao
) {
    private val tag = "PhraseChunkSeeder"

    suspend fun seedIfNeeded(languagePack: LanguagePack, sourceLanguage: String = "en") {
        withContext(Dispatchers.IO) {
            val existing = dao.countForLanguagePair(sourceLanguage, languagePack.languageCode)
            if (existing > 0) {
                Log.d(tag, "Phrase chunks for $sourceLanguage->${languagePack.languageCode} already seeded ($existing entries)")
                return@withContext
            }

            val json = readAsset(languagePack.phraseChunkAssetPath) ?: run {
                Log.e(tag, "Phrase chunk asset not found: ${languagePack.phraseChunkAssetPath}")
                return@withContext
            }

            val array = JSONArray(json)
            val entities = ArrayList<PhraseChunkEntity>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val source = obj.optString(sourceLanguage).trim().lowercase()
                val target = obj.optString("es").trim()
                if (source.isEmpty() || target.isEmpty()) continue
                val nextForm = obj.optString("nextForm").trim().ifEmpty { null }

                entities.add(
                    PhraseChunkEntity(
                        sourcePhrase = source,
                        targetPhrase = target,
                        sourceLanguage = sourceLanguage,
                        targetLanguage = languagePack.languageCode,
                        wordCount = source.split(" ").size,
                        requiredNextForm = nextForm
                    )
                )
            }

            dao.insertAll(entities)
            Log.d(tag, "Seeded ${entities.size} phrase chunks for $sourceLanguage->${languagePack.languageCode}")
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
