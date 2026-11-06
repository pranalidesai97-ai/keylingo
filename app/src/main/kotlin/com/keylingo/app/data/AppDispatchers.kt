package com.keylingo.app.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dispatchers for off-main-thread work. Production uses [Dispatchers.Default]
 * for CPU work (lexicon search, translation) and [Dispatchers.IO] for disk;
 * tests pass a test dispatcher so virtual time controls everything.
 */
@Singleton
class AppDispatchers(val compute: CoroutineDispatcher, val io: CoroutineDispatcher) {
    @Inject constructor() : this(Dispatchers.Default, Dispatchers.IO)
}
