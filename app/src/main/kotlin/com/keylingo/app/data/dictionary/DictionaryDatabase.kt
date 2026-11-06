package com.keylingo.app.data.dictionary

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Schema v3 adds [PhraseChunkEntity] and a CEFR column, and reshapes [DictionaryEntity] to
 * carry multiple grammatical forms per row (see that class's KDoc). This
 * database holds only a local, disposable cache of the bundled JSON
 * dictionary/chunk assets -- not user-authored data -- so a schema bump
 * uses [androidx.room.RoomDatabase.Builder.fallbackToDestructiveMigration]
 * (wired in [com.keylingo.app.di.DatabaseModule]) instead of hand-written
 * `Migration` objects: the seeder simply repopulates everything from
 * assets on next launch, which is both correct and far simpler than
 * migrating rows that are trivially regenerable.
 */
@Database(
    entities = [DictionaryEntity::class, PhraseChunkEntity::class],
    version = 3,
    exportSchema = false
)
abstract class DictionaryDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao
    abstract fun phraseChunkDao(): PhraseChunkDao
}
