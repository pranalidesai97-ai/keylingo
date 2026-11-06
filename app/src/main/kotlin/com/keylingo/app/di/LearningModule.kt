package com.keylingo.app.di

import android.content.Context
import androidx.room.Room
import com.keylingo.app.data.learning.LearnedWordDao
import com.keylingo.app.data.learning.LearningDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Wires personal-vocabulary-learning storage. Kept in its own module, out
 * of [AppModule]/[DatabaseModule]/[AutocompleteModule], for the same
 * "independent feature, independent module" reason those already follow.
 *
 * Deliberately does NOT call `fallbackToDestructiveMigration()` -- unlike
 * [AutocompleteModule]/[DatabaseModule], this database holds real user
 * data. See [LearningDatabase]'s KDoc.
 */
@Module
@InstallIn(SingletonComponent::class)
object LearningModule {

    @Provides
    @Singleton
    fun provideLearningDatabase(@ApplicationContext context: Context): LearningDatabase {
        return Room.databaseBuilder(
            context,
            LearningDatabase::class.java,
            "keylingo_learning.db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideLearnedWordDao(database: LearningDatabase): LearnedWordDao {
        return database.learnedWordDao()
    }
}
