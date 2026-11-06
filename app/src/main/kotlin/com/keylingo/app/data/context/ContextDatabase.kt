package com.keylingo.app.data.context

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Holds the user's personal word-sequence (bigram) history. Kept as its
 * own database, separate from [com.keylingo.app.data.learning.LearningDatabase]
 * (personal vocabulary) -- "does the user know this word" and "what word
 * tends to follow this one" are different questions with different query
 * shapes and different growth patterns (bigram rows can grow much faster
 * than vocabulary rows for an active typist), so keeping them in separate
 * tables/databases means one feature's data volume or schema evolution
 * never affects the other.
 *
 * Like [LearningDatabase][com.keylingo.app.data.learning.LearningDatabase],
 * this holds genuine user data and deliberately does **not** use
 * `fallbackToDestructiveMigration()` -- see that class's KDoc for the
 * reasoning, which applies identically here.
 */
@Database(
    entities = [BigramEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ContextDatabase : RoomDatabase() {
    abstract fun bigramDao(): BigramDao
}
