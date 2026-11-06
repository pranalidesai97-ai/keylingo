package com.keylingo.app.di

import android.content.Context
import androidx.room.Room
import com.keylingo.app.data.context.BigramDao
import com.keylingo.app.data.context.ContextDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Wires personal word-sequence (bigram) storage. Its own module, out of
 * [LearningModule], for the same "independent feature, independent
 * module" reasoning that module documents -- and, like [LearningModule],
 * deliberately does NOT call `fallbackToDestructiveMigration()`, since
 * [ContextDatabase] holds real user data.
 */
@Module
@InstallIn(SingletonComponent::class)
object ContextModule {

    @Provides
    @Singleton
    fun provideContextDatabase(@ApplicationContext context: Context): ContextDatabase {
        return Room.databaseBuilder(context, ContextDatabase::class.java, ContextDatabase.NAME)
            .addMigrations(*ContextDatabase.ALL_MIGRATIONS)
            .build()
    }

    @Provides
    @Singleton
    fun provideBigramDao(database: ContextDatabase): BigramDao {
        return database.bigramDao()
    }
}
