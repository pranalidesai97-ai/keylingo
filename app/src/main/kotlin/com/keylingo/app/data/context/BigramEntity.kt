package com.keylingo.app.data.context

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One learned word-sequence relationship: [previousWord] was followed by
 * [nextWord] [count] times for this user, in [language]. This is genuine
 * user-generated data (the user's own typing history), not a bundled
 * asset -- see [ContextDatabase]'s KDoc for why that means no destructive
 * migration.
 *
 * Deliberately a separate table/database from [com.keylingo.app.data.learning.LearnedWordEntity]
 * even though both are "personal, persisted, learned from typing" --
 * personal *vocabulary* (does this user know this word at all) and
 * personal *word-sequence* (what tends to follow what) are different
 * questions with different query shapes, and keeping them separate avoids
 * one feature's schema needs distorting the other's.
 *
 * Unique-indexed on (previousWord, nextWord, language) -- both for the
 * uniqueness constraint itself (one row per pair, updated in place rather
 * than duplicated) and because it doubles as the lookup index for the
 * ranking query's actual access pattern: "given the word just typed
 * (previousWord, the leading index column) and the language, what tends
 * to follow it" -- never the reverse.
 */
@Entity(
    tableName = "bigrams",
    indices = [Index(value = ["previousWord", "nextWord", "language"], unique = true)]
)
data class BigramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val previousWord: String,
    val nextWord: String,
    val language: String,
    val count: Int,
    val lastUsedAtEpochMillis: Long
)
