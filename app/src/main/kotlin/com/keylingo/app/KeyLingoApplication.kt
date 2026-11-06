package com.keylingo.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import kotlin.concurrent.thread

/**
 * Application entry point (Hilt). Also removes the obsolete bundled-data
 * cache databases from earlier versions: those only ever held copies of
 * the bundled assets (now loaded straight into memory), never user data.
 * User data databases (keylingo_learning.db, keylingo_context.db) are untouched.
 */
@HiltAndroidApp
class KeyLingoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        thread(name = "keylingo-legacy-cleanup", isDaemon = true) {
            for (name in LEGACY_CACHE_DATABASES) {
                if (getDatabasePath(name).exists()) deleteDatabase(name)
            }
        }
    }

    private companion object {
        val LEGACY_CACHE_DATABASES = listOf("keylingo_dictionary.db", "keylingo_autocomplete.db")
    }
}
