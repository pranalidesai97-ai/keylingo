package com.keylingo.app.di

import com.keylingo.app.data.bundled.BundledAutocompleteProvider
import com.keylingo.app.data.bundled.BundledDataSupplier
import com.keylingo.app.data.bundled.BundledLanguageDataSource
import com.keylingo.app.data.bundled.BundledLanguageDetector
import com.keylingo.app.data.hint.LearningAssistant
import com.keylingo.app.data.language.SpanishLanguagePack
import com.keylingo.app.domain.repository.AutocompleteProvider
import com.keylingo.app.domain.repository.HintProvider
import com.keylingo.app.domain.repository.LanguageDetector
import com.keylingo.app.domain.repository.LanguagePack
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the domain interfaces served by the bundled (read-only, in-memory)
 * language data. Personal-data bindings live in [LearningBindsModule] and
 * [ContextBindsModule] so the two kinds of data stay visibly separate.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds @Singleton
    abstract fun bindBundledDataSupplier(impl: BundledLanguageDataSource): BundledDataSupplier

    @Binds @Singleton
    abstract fun bindAutocompleteProvider(impl: BundledAutocompleteProvider): AutocompleteProvider

    @Binds @Singleton
    abstract fun bindHintProvider(impl: LearningAssistant): HintProvider

    @Binds @Singleton
    abstract fun bindLanguageDetector(impl: BundledLanguageDetector): LanguageDetector

    @Binds @Singleton
    abstract fun bindLanguagePack(impl: SpanishLanguagePack): LanguagePack
}
