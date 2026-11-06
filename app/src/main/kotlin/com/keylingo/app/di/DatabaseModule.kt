package com.keylingo.app.di

import android.content.Context
import androidx.room.Room
import com.keylingo.app.data.dictionary.DictionaryDao
import com.keylingo.app.data.dictionary.DictionaryDatabase
import com.keylingo.app.data.dictionary.DictionarySeeder
import com.keylingo.app.data.dictionary.PhraseChunkDao
import com.keylingo.app.data.dictionary.PhraseChunkSeeder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDictionaryDatabase(@ApplicationContext context: Context): DictionaryDatabase {
        return Room.databaseBuilder(
            context,
            DictionaryDatabase::class.java,
            "keylingo_dictionary.db"
        )
            // This database is a disposable local cache of bundled JSON
            // assets, not user-authored data -- see DictionaryDatabase's
            // KDoc. On a schema bump, destroying and letting the seeders
            // repopulate from assets is correct and far simpler than
            // hand-written Migrations for rows that regenerate themselves.
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideDictionaryDao(database: DictionaryDatabase): DictionaryDao {
        return database.dictionaryDao()
    }

    @Provides
    @Singleton
    fun providePhraseChunkDao(database: DictionaryDatabase): PhraseChunkDao {
        return database.phraseChunkDao()
    }

    @Provides
    @Singleton
    fun provideDictionarySeeder(
        @ApplicationContext context: Context,
        dao: DictionaryDao
    ): DictionarySeeder {
        return DictionarySeeder(context, dao)
    }

    @Provides
    @Singleton
    fun providePhraseChunkSeeder(
        @ApplicationContext context: Context,
        dao: PhraseChunkDao
    ): PhraseChunkSeeder {
        return PhraseChunkSeeder(context, dao)
    }
}
