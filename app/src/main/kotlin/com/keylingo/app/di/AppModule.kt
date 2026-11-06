package com.keylingo.app.di

import com.keylingo.app.data.dictionary.RoomDictionaryProvider
import com.keylingo.app.data.dictionary.RoomPhraseChunkProvider
import com.keylingo.app.data.hint.DefaultHintProvider
import com.keylingo.app.data.language.EnglishSourceLanguageRules
import com.keylingo.app.data.language.SpanishLanguagePack
import com.keylingo.app.domain.repository.DictionaryProvider
import com.keylingo.app.domain.repository.HintProvider
import com.keylingo.app.domain.repository.LanguagePack
import com.keylingo.app.domain.repository.PhraseChunkProvider
import com.keylingo.app.domain.repository.SourceLanguageRules
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the domain-layer interfaces to today's concrete implementations.
 * Swapping any of these later -- a bigger dictionary, smarter NLP, a new
 * source or target language -- means changing only the class bound here.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindDictionaryProvider(impl: RoomDictionaryProvider): DictionaryProvider

    @Binds
    @Singleton
    abstract fun bindPhraseChunkProvider(impl: RoomPhraseChunkProvider): PhraseChunkProvider

    @Binds
    @Singleton
    abstract fun bindHintProvider(impl: DefaultHintProvider): HintProvider

    @Binds
    @Singleton
    abstract fun bindLanguagePack(impl: SpanishLanguagePack): LanguagePack

    @Binds
    @Singleton
    abstract fun bindSourceLanguageRules(impl: EnglishSourceLanguageRules): SourceLanguageRules
}
