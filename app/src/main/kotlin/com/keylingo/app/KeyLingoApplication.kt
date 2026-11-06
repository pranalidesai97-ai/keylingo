package com.keylingo.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Annotated for Hilt so that dependency graphs
 * (Room database, DataStore, dictionary/hint providers) are available
 * both to the launcher Activity and to the InputMethodService process.
 */
@HiltAndroidApp
class KeyLingoApplication : Application()
