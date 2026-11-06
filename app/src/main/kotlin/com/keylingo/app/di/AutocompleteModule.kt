package com.keylingo.app.di

import android.content.Context
import androidx.room.Room
import com.keylingo.app.data.autocomplete.AutocompleteDatabase
import com.keylingo.app.data.autocomplete.EnglishWordDao
import com.keylingo.app.data.autocomplete.EnglishWordSeeder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Wires the autocomplete feature's storage. Deliberately kept out of
 * [AppModule]/[DatabaseModule] -- which wire the Spanish hint pipeline --
 * so the dependency graph itself documents that autocomplete is
 * independent of that pipeline, not just separate in Kotlin interfaces.
 *
 * Data-layer wiring (database/DAO/seeder). The domain-layer `@Binds` for
 * `AutocompleteProvider` -> `RoomAutocompleteProvider` lives in
 * [AutocompleteBindsModule] instead of here -- `@Binds` requires an
 * abstract class/interface method, which can't coexist with this
 * `object`'s `@Provides` methods.
 */
@Module
@InstallIn(SingletonComponent::class)
object AutocompleteModule {

    @Provides
    @Singleton
    fun provideAutocompleteDatabase(@ApplicationContext context: Context): AutocompleteDatabase {
        return Room.databaseBuilder(
            context,
            AutocompleteDatabase::class.java,
            "keylingo_autocomplete.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideEnglishWordDao(database: AutocompleteDatabase): EnglishWordDao {
        return database.englishWordDao()
    }

    @Provides
    @Singleton
    fun provideEnglishWordSeeder(
        @ApplicationContext context: Context,
        dao: EnglishWordDao
    ): EnglishWordSeeder {
        return EnglishWordSeeder(context, dao)
    }
}
