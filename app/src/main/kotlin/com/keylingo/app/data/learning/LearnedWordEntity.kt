package com.keylingo.app.data.learning

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single word the user has personally typed or accepted, tracked
 * separately from the bundled dictionaries. This is genuine user data
 * (not a disposable cache of a bundled asset like [com.keylingo.app.data.autocomplete.EnglishWordEntity]/
 * [com.keylingo.app.data.dictionary.DictionaryEntity]), so it lives in its
 * own [LearningDatabase] that never uses destructive migration -- see
 * that class's KDoc for why this distinction matters.
 *
 * [organicCount] counts how many times the word was typed and then
 * completed naturally (space/punctuation after it), independent of
 * whether it was ever tapped as a suggestion. [acceptedCount] counts how
 * many times the user explicitly tapped this word as an autocomplete
 * suggestion -- a stronger, more deliberate signal than organic typing,
 * so it's weighted higher in [com.keylingo.app.data.learning.RoomLearnedVocabularyProvider]'s
 * ranking rather than merged into one count.
 */
@Entity(
    tableName = "learned_words",
    indices = [Index(value = ["word", "language"], unique = true)]
)
data class LearnedWordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val language: String,
    val organicCount: Int,
    val acceptedCount: Int,
    val lastUsedAtEpochMillis: Long
)
