package com.keylingo.app.data.context

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Personal three-word sequence: ([secondPreviousWord], [previousWord]) was
 * followed by [nextWord] [count] times. Added in [ContextDatabase] v2 via a
 * non-destructive migration; sharper than bigrams once enough history exists
 * ("I am going" -> "to", while "am" alone predicts many things).
 */
@Entity(
    tableName = "trigrams",
    indices = [Index(value = ["secondPreviousWord", "previousWord", "nextWord", "language"], unique = true)]
)
data class TrigramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val secondPreviousWord: String,
    val previousWord: String,
    val nextWord: String,
    val language: String,
    val count: Int,
    val lastUsedAtEpochMillis: Long
)
