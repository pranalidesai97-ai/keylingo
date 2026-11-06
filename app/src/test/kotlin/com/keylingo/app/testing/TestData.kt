package com.keylingo.app.testing

import com.keylingo.app.data.bundled.BundledLanguageData
import java.io.File

/**
 * Loads the REAL bundled assets from src/main/assets, so tests exercise the
 * shipped dictionary/lexicon rather than hand-made fixtures. Loaded once per
 * test JVM.
 */
object TestData {
    private val assetsDir: File by lazy {
        listOf(File("src/main/assets"), File("app/src/main/assets")).first { it.isDirectory }
    }

    val bundled: BundledLanguageData by lazy {
        BundledLanguageData.load { path -> File(assetsDir, path).readLines(Charsets.UTF_8).asSequence() }
    }

    fun assetLines(path: String): List<String> = File(assetsDir, path).readLines(Charsets.UTF_8)
}
