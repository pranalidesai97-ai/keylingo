package com.keylingo.app.data.autocomplete

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * A separate Room database (own `.db` file) from [com.keylingo.app.data.dictionary.DictionaryDatabase]
 * -- deliberate, so the autocomplete feature is independent of the Spanish
 * hint pipeline at the storage layer, not just in Kotlin interfaces. See
 * [com.keylingo.app.di.AutocompleteModule] for wiring.
 *
 * Like the dictionary database, this is a disposable local cache of a
 * bundled JSON asset, not user data, so schema bumps should use
 * [androidx.room.RoomDatabase.Builder.fallbackToDestructiveMigration]
 * rather than hand-written Migrations.
 */
@Database(
    entities = [EnglishWordEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AutocompleteDatabase : RoomDatabase() {
    abstract fun englishWordDao(): EnglishWordDao
}
