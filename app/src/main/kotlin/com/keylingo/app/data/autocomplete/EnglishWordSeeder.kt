package com.keylingo.app.data.autocomplete

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException
import java.io.InputStreamReader

/**
 * Populates Room from the bundled `assets/autocomplete/en_words.json` the
 * first time the keyboard runs. Unlike [com.keylingo.app.data.dictionary.DictionarySeeder],
 * there is no per-language-pack asset path to resolve -- autocomplete is
 * English-only regardless of which language is being taught, so the asset
 * path is fixed.
 *
 * Expected JSON shape (flat list, no nesting):
 * [
 *   { "word": "the", "rank": 1 },
 *   { "word": "house", "rank": 120 }
 * ]
 * "rank" is a frequency heuristic (lower = more common); entries without
 * it default to a large value so they sort last.
 */
class EnglishWordSeeder(
    private val context: Context,
    private val dao: EnglishWordDao
) {
    private val tag = "EnglishWordSeeder"
    private val assetPath = "autocomplete/en_words.json"

    suspend fun seedIfNeeded() {
        withContext(Dispatchers.IO) {
            val existing = dao.count()
            if (existing > 0) {
                Log.d(tag, "Autocomplete word list already seeded ($existing words)")
                return@withContext
            }

            val json = readAsset(assetPath) ?: run {
                Log.e(tag, "Autocomplete asset not found: $assetPath")
                return@withContext
            }

            val array = JSONArray(json)
            val entities = ArrayList<EnglishWordEntity>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val word = obj.optString("word").trim().lowercase()
                if (word.isEmpty()) continue
                val rank = if (obj.has("rank")) obj.optInt("rank", Int.MAX_VALUE) else Int.MAX_VALUE
                entities.add(EnglishWordEntity(word = word, frequencyRank = rank))
            }

            entities.chunked(500).forEach { chunk -> dao.insertAll(chunk) }
            Log.d(tag, "Seeded ${entities.size} autocomplete words")
        }
    }

    private fun readAsset(path: String): String? {
        return try {
            context.assets.open(path).use { input ->
                InputStreamReader(input).use { reader -> reader.readText() }
            }
        } catch (e: IOException) {
            null
        }
    }
}
