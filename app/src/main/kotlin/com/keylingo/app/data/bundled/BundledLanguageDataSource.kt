package com.keylingo.app.data.bundled

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads [BundledLanguageData] exactly once, on [Dispatchers.Default], and
 * hands the same instance to every caller. Callers simply `await` it --
 * there is no separate "seeding" step, so the old cold-start race (the
 * keyboard querying Room before the seeder had filled it) cannot happen.
 *
 * If an asset cannot be read (corrupt install) the failure is logged
 * without any user text and [get] returns null; the keyboard keeps working
 * as a plain keyboard with no suggestions rather than crashing.
 */
@Singleton
class BundledLanguageDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val mutex = Mutex()
    private var deferred: CompletableDeferred<BundledLanguageData?>? = null
    @Volatile private var loaded: BundledLanguageData? = null

    /** Starts loading in the background if not already started. Cheap to call repeatedly. */
    suspend fun get(): BundledLanguageData? {
        val existing = mutex.withLock {
            deferred ?: CompletableDeferred<BundledLanguageData?>().also { deferred = it; it.startLoad() }
        }
        return existing.await()
    }

    /** Non-suspending peek: the data if already loaded, else null. */
    fun peek(): BundledLanguageData? = loaded

    private suspend fun CompletableDeferred<BundledLanguageData?>.startLoad() {
        val result = withContext(Dispatchers.Default) {
            runCatching {
                val start = System.nanoTime()
                val data = BundledLanguageData.load { path ->
                    context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readLines() }.asSequence()
                }
                Log.i(TAG, "Bundled data loaded in ${(System.nanoTime() - start) / 1_000_000} ms " +
                    "(en=${data.englishLexicon.size}, es=${data.spanishLexicon.size}, dict=${data.dictionary.size})")
                data
            }.onFailure { Log.e(TAG, "Failed to load bundled language data", it) }.getOrNull()
        }
        loaded = result
        complete(result)
    }

    private companion object {
        const val TAG = "KeyLingoData"
    }
}
