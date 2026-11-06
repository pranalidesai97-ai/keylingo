package com.keylingo.app.data.dictionary

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room-persisted dictionary entry. Schema v3: verbs now carry all three
 * surface forms as columns on a single row (base/infinitive/gerund)
 * instead of being spread across multiple rows per inflection -- see
 * [com.keylingo.app.domain.model.DictionaryEntry] for why. [frequencyRank]
 * (lower = more common) lets the hint engine prioritize common vocabulary
 * when a sentence has more translatable words than fit in the hint bar.
 * [cefrLevel] stores a [com.keylingo.app.domain.model.CefrLevel] name for
 * future difficulty-aware features (learning modes, gated vocabulary).
 */
@Entity(
    tableName = "dictionary_entries",
    indices = [
        Index(value = ["sourceWord", "sourceLanguage", "targetLanguage"], unique = true)
    ]
)
data class DictionaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceWord: String,
    val targetBase: String,
    val targetInfinitive: String?,
    val targetGerund: String?,
    val sourceLanguage: String,
    val targetLanguage: String,
    val partOfSpeech: String,
    val frequencyRank: Int,
    val cefrLevel: String
)
