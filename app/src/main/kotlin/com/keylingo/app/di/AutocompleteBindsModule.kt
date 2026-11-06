package com.keylingo.app.di

import com.keylingo.app.data.autocomplete.RoomAutocompleteProvider
import com.keylingo.app.domain.repository.AutocompleteProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the autocomplete domain interface to its Room-backed
 * implementation. Split from [AutocompleteModule] for the same reason
 * [AppModule] (`@Binds`, abstract class) is split from [DatabaseModule]
 * (`@Provides`, `object`) on the dictionary side: `@Binds` requires an
 * abstract class/interface method, which can't live in the same `object`
 * as [AutocompleteModule]'s `@Provides` database/DAO/seeder methods.
 * Still deliberately kept out of [AppModule] itself so the autocomplete
 * dependency graph stays visibly separate from the Spanish hint pipeline.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AutocompleteBindsModule {

    @Binds
    @Singleton
    abstract fun bindAutocompleteProvider(impl: RoomAutocompleteProvider): AutocompleteProvider
}
