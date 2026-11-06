package com.keylingo.app.data.context

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The user's personal word-sequence history. Holds genuine user data, so it
 * never uses destructive migration: every schema change ships a real
 * [Migration], covered by a migration test against the exported schemas in
 * app/schemas/.
 *
 * v1: bigrams. v2: + trigrams (additive; existing bigram history is kept).
 */
@Database(
    entities = [BigramEntity::class, TrigramEntity::class],
    version = 2,
    exportSchema = true
)
abstract class ContextDatabase : RoomDatabase() {
    abstract fun bigramDao(): BigramDao

    companion object {
        const val NAME = "keylingo_context.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `trigrams` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`secondPreviousWord` TEXT NOT NULL, `previousWord` TEXT NOT NULL, `nextWord` TEXT NOT NULL, " +
                        "`language` TEXT NOT NULL, `count` INTEGER NOT NULL, `lastUsedAtEpochMillis` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_trigrams_secondPreviousWord_previousWord_nextWord_language` " +
                        "ON `trigrams` (`secondPreviousWord`, `previousWord`, `nextWord`, `language`)"
                )
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)
    }
}
