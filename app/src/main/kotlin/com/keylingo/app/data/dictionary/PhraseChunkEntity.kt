package com.keylingo.app.data.dictionary

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room-persisted phrase chunk (e.g. "i am going to" -> "voy"). [wordCount]
 * is denormalized onto the row so the DAO can order by it for greedy
 * longest-match without recomputing it on every query.
 * [requiredNextForm] stores a [com.keylingo.app.domain.model.GrammaticalForm]
 * name, or null if this chunk doesn't imply a form for the next word.
 */
@Entity(
    tableName = "phrase_chunks",
    indices = [
        Index(value = ["sourcePhrase", "sourceLanguage", "targetLanguage"], unique = true)
    ]
)
data class PhraseChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourcePhrase: String,
    val targetPhrase: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val wordCount: Int,
    val requiredNextForm: String?
)
