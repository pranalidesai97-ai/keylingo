package com.keylingo.app.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.keylingo.app.data.context.ContextDatabase
import com.keylingo.app.data.context.RoomContextPredictionProvider
import com.keylingo.app.data.learning.LearningDatabase
import com.keylingo.app.data.learning.RoomLearnedVocabularyProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The two databases that hold genuine user data, on real SQLite (Robolectric):
 * concurrency safety of the upserts, persistence semantics, clearing, and the
 * v1 -> v2 context migration against the exported schemas.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserDatabasesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val learningDb = Room.inMemoryDatabaseBuilder(context, LearningDatabase::class.java).build()
    private val contextDb = Room.inMemoryDatabaseBuilder(context, ContextDatabase::class.java).build()
    private val vocabulary = RoomLearnedVocabularyProvider(learningDb.learnedWordDao())
    private val sequences = RoomContextPredictionProvider(contextDb.bigramDao())

    @get:Rule
    val migrations = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), ContextDatabase::class.java)

    @After fun close() { learningDb.close(); contextDb.close() }

    @Test fun concurrentFirstUsesOfANewWordNeverCollide() = runBlocking {
        // Before the fix, two simultaneous "first" recordings both inserted and violated the unique index.
        (1..60).map { i ->
            async(Dispatchers.IO) {
                if (i % 3 == 0) vocabulary.recordAcceptedSuggestion("concurrency") else vocabulary.recordOrganicUsage("concurrency")
            }
        }.awaitAll()
        val row = learningDb.learnedWordDao().find("concurrency", "en")!!
        assertEquals(40, row.organicCount)
        assertEquals(20, row.acceptedCount)
    }

    @Test fun wordsBecomeSuggestableOnlyPastTheLearningThreshold() = runBlocking {
        vocabulary.recordOrganicUsage("Zorbix")
        assertTrue(vocabulary.suggest("zor").isEmpty())
        assertFalse(vocabulary.isLearned("zorbix"))
        vocabulary.recordOrganicUsage("zorbix"); vocabulary.recordOrganicUsage("zorbix")
        assertEquals(listOf("zorbix"), vocabulary.suggest("zor").map { it.word })
        assertTrue(vocabulary.isLearned("ZORBIX"))
    }

    @Test fun invalidInputIsNeverStored() = runBlocking {
        listOf("a", "aaaaaa", "abc123", "hello!", "x".repeat(40), "50%_off").forEach { vocabulary.recordOrganicUsage(it) }
        assertTrue(learningDb.learnedWordDao().findByPrefix("", "en", 100).isEmpty())
        assertTrue(vocabulary.suggest("50%").isEmpty())
    }

    @Test fun languagesAreKeptSeparate() = runBlocking {
        vocabulary.recordAcceptedSuggestion("casa", "es")
        assertTrue(vocabulary.suggest("ca", "en").isEmpty())
        assertEquals(listOf("casa"), vocabulary.suggest("ca", "es").map { it.word })
    }

    @Test fun bigramsAndTrigramsAccumulateAtomically() = runBlocking {
        (1..30).map { async(Dispatchers.IO) { sequences.recordSequence("going", "to", secondPreviousWord = "am") } }.awaitAll()
        sequences.recordSequence("going", "home", secondPreviousWord = "is")
        assertEquals(30, sequences.nextWordCounts("going")["to"])
        assertEquals(1, sequences.nextWordCounts("going")["home"])
        assertEquals(mapOf("to" to 30), sequences.nextWordCountsAfterPair("am", "going"))
    }

    @Test fun clearingDeletesAllPersonalData() = runBlocking {
        vocabulary.recordAcceptedSuggestion("secret")
        sequences.recordSequence("my", "secret", secondPreviousWord = "is")
        vocabulary.clearAll(); sequences.clearAll()
        assertTrue(vocabulary.suggest("sec").isEmpty())
        assertTrue(sequences.nextWordCounts("my").isEmpty())
        assertTrue(sequences.nextWordCountsAfterPair("is", "my").isEmpty())
    }

    @Test fun migratingContextDatabaseFromV1KeepsExistingHistory() {
        val name = "migration-test.db"
        migrations.createDatabase(name, 1).apply {
            execSQL("INSERT INTO bigrams (previousWord, nextWord, language, count, lastUsedAtEpochMillis) VALUES ('good', 'morning', 'en', 7, 1)")
            close()
        }
        // Validates the migrated schema against app/schemas/.../2.json (fails on any mismatch).
        val db = migrations.runMigrationsAndValidate(name, 2, true, ContextDatabase.MIGRATION_1_2)
        db.query("SELECT count FROM bigrams WHERE previousWord = 'good' AND nextWord = 'morning'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(7, c.getInt(0))
        }
        db.execSQL("INSERT INTO trigrams (secondPreviousWord, previousWord, nextWord, language, count, lastUsedAtEpochMillis) VALUES ('very', 'good', 'morning', 'en', 1, 1)")
        db.close()
    }

    // ---- Phase 5: retention, listing and forgetting

    private val day = 24L * 60 * 60 * 1000

    @Test fun retentionForgetsIncidentalWordsButKeepsLearnedOnes() = runBlocking {
        vocabulary.recordOrganicUsage("zorbix")               // typed once: incidental
        vocabulary.recordAcceptedSuggestion("keylingo")       // accepted once: learned
        repeat(3) { vocabulary.recordOrganicUsage("quokka") } // typed 3 times: learned
        val now = System.currentTimeMillis()
        assertEquals(0, vocabulary.pruneStale(now + 29 * day)) // not old enough yet
        assertEquals(1, vocabulary.pruneStale(now + 31 * day))
        assertEquals(null, learningDb.learnedWordDao().find("zorbix", "en"))
        assertTrue(vocabulary.isLearned("keylingo") && vocabulary.isLearned("quokka"))
    }

    @Test fun singleUseSequencesExpireAndStorageIsCapped() = runBlocking {
        repeat(2) { sequences.recordSequence("good", "morning") }
        sequences.recordSequence("purple", "elephant", secondPreviousWord = "the")
        val now = System.currentTimeMillis()
        sequences.pruneStale(now + 61 * day)
        assertEquals(mapOf("morning" to 2), sequences.nextWordCounts("good"))
        assertTrue(sequences.nextWordCounts("purple").isEmpty())
        assertTrue(sequences.nextWordCountsAfterPair("the", "purple").isEmpty())

        val dao = contextDb.bigramDao()
        dao.record(null, "a", "one", "en", 1_000)
        dao.record(null, "a", "two", "en", 2_000)
        dao.record(null, "a", "three", "en", 3_000)
        dao.prune(singleUseCutoff = 0, maxBigrams = 2, maxTrigrams = 10) // keeps the 2 most recently used: good->morning and a->three
        assertEquals(setOf("three"), sequences.nextWordCounts("a").keys)
        assertEquals(mapOf("morning" to 2), sequences.nextWordCounts("good")) // newest rows survive the cap
    }

    @Test fun listingShowsOnlyLearnedWordsNewestFirst() = runBlocking {
        vocabulary.recordOrganicUsage("once")
        vocabulary.recordAcceptedSuggestion("casa", "es")
        Thread.sleep(5)
        vocabulary.recordAcceptedSuggestion("quokka")
        val listed = vocabulary.listLearned()
        assertEquals(listOf("quokka" to "en", "casa" to "es"), listed.map { it.word to it.languageCode })
    }

    @Test fun forgettingAWordRemovesItFromSuggestionsAndPredictions() = runBlocking {
        vocabulary.recordAcceptedSuggestion("quokka")
        sequences.recordSequence("cute", "quokka", secondPreviousWord = "a")
        sequences.recordSequence("quokka", "island")
        sequences.recordSequence("cute", "cat")
        vocabulary.forget("Quokka", "en")
        sequences.forgetWord("quokka", "en")
        assertFalse(vocabulary.isLearned("quokka"))
        assertTrue(vocabulary.suggest("quo").isEmpty())
        assertEquals(setOf("cat"), sequences.nextWordCounts("cute").keys)
        assertTrue(sequences.nextWordCounts("quokka").isEmpty())
        assertTrue(sequences.nextWordCountsAfterPair("a", "cute").isEmpty())
    }
}
