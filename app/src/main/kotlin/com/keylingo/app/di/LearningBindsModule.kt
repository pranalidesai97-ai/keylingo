package com.keylingo.app.di

import com.keylingo.app.data.learning.RoomLearnedVocabularyProvider
import com.keylingo.app.domain.repository.LearnedVocabularyProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the learning domain interface to its Room-backed implementation.
 * Split from [LearningModule] for the same `@Binds`-vs-`@Provides` reason
 * [AutocompleteBindsModule] is split from [AutocompleteModule].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LearningBindsModule {

    @Binds
    @Singleton
    abstract fun bindLearnedVocabularyProvider(impl: RoomLearnedVocabularyProvider): LearnedVocabularyProvider
}
