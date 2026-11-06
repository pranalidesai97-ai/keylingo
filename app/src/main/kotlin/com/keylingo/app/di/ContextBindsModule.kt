package com.keylingo.app.di

import com.keylingo.app.data.context.RoomContextPredictionProvider
import com.keylingo.app.domain.repository.ContextPredictionProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ContextBindsModule {

    @Binds
    @Singleton
    abstract fun bindContextPredictionProvider(impl: RoomContextPredictionProvider): ContextPredictionProvider
}
