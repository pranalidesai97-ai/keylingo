package com.keylingo.app.data.autocomplete

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room-persisted English autocomplete word. This is deliberately a much
 * simpler row than [com.keylingo.app.data.dictionary.DictionaryEntity]:
 * autocomplete only needs to answer "what English words start with this
 * prefix, most-common first" -- it has no translation, part of speech, or
 * CEFR concept at all. See [com.keylingo.app.domain.repository.AutocompleteProvider]
 * for why this stays a completely separate concept from the dictionary.
 *
 * [frequencyRank] follows the same convention as the dictionary's
 * frequencyRank: lower = more common, used to order prefix matches so the
 * most likely word surfaces first.
 */
@Entity(
    tableName = "english_words",
    indices = [
        Index(value = ["word"], unique = true)
    ]
)
data class EnglishWordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val frequencyRank: Int
)
