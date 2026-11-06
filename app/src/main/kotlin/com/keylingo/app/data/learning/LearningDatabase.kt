package com.keylingo.app.data.learning

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Holds the user's personal learned vocabulary. Deliberately a separate
 * database from [com.keylingo.app.data.autocomplete.AutocompleteDatabase]
 * and [com.keylingo.app.data.dictionary.DictionaryDatabase], and
 * deliberately **not** configured with `fallbackToDestructiveMigration()`
 * in [com.keylingo.app.di.LearningModule] -- those two other databases
 * only ever hold a disposable local cache of a bundled JSON asset, so
 * destroying and reseeding on a schema bump is correct for them. This
 * database holds genuine user-generated data (words the user actually
 * typed), and the whole point of this feature is that it survives app
 * restarts and device restarts -- destroying it on a future schema change
 * would silently violate that guarantee. Any future schema change here
 * needs a real `Migration`, not a shortcut.
 */
@Database(
    entities = [LearnedWordEntity::class],
    version = 1,
    exportSchema = false
)
abstract class LearningDatabase : RoomDatabase() {
    abstract fun learnedWordDao(): LearnedWordDao
}
