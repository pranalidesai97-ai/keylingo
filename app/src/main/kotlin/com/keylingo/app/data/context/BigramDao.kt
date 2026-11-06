package com.keylingo.app.data.context

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

/**
 * Personal n-gram DAO. All writes are atomic INSERT OR IGNORE + UPDATE
 * pairs inside a transaction (see [com.keylingo.app.data.learning.LearnedWordDao]
 * for why the old find-then-insert sequence crashed under concurrency).
 */
@Dao
abstract class BigramDao {

    @Query("SELECT * FROM bigrams WHERE previousWord = :previousWord AND nextWord = :nextWord AND language = :language LIMIT 1")
    abstract suspend fun find(previousWord: String, nextWord: String, language: String): BigramEntity?

    @Query("INSERT OR IGNORE INTO bigrams (previousWord, nextWord, language, count, lastUsedAtEpochMillis) VALUES (:previous, :next, :language, 0, :now)")
    protected abstract suspend fun insertBigramIfAbsent(previous: String, next: String, language: String, now: Long)

    @Query("UPDATE bigrams SET count = count + 1, lastUsedAtEpochMillis = :now WHERE previousWord = :previous AND nextWord = :next AND language = :language")
    protected abstract suspend fun incrementBigram(previous: String, next: String, language: String, now: Long)

    @Query(
        """
        INSERT OR IGNORE INTO trigrams (secondPreviousWord, previousWord, nextWord, language, count, lastUsedAtEpochMillis)
        VALUES (:second, :previous, :next, :language, 0, :now)
        """
    )
    protected abstract suspend fun insertTrigramIfAbsent(second: String, previous: String, next: String, language: String, now: Long)

    @Query(
        """
        UPDATE trigrams SET count = count + 1, lastUsedAtEpochMillis = :now
        WHERE secondPreviousWord = :second AND previousWord = :previous AND nextWord = :next AND language = :language
        """
    )
    protected abstract suspend fun incrementTrigram(second: String, previous: String, next: String, language: String, now: Long)

    @Transaction
    open suspend fun record(second: String?, previous: String, next: String, language: String, now: Long) {
        insertBigramIfAbsent(previous, next, language, now)
        incrementBigram(previous, next, language, now)
        if (second != null) {
            insertTrigramIfAbsent(second, previous, next, language, now)
            incrementTrigram(second, previous, next, language, now)
        }
    }

    /** All known next words for [previousWord], highest count first. Fetched once per refresh, then looked up in memory. */
    @Query("SELECT * FROM bigrams WHERE previousWord = :previousWord AND language = :language ORDER BY count DESC LIMIT :limit")
    abstract suspend fun findByPreviousWord(previousWord: String, language: String, limit: Int): List<BigramEntity>

    @Query(
        """
        SELECT * FROM trigrams WHERE secondPreviousWord = :second AND previousWord = :previous AND language = :language
        ORDER BY count DESC LIMIT :limit
        """
    )
    abstract suspend fun findByPreviousPair(second: String, previous: String, language: String, limit: Int): List<TrigramEntity>

    @Query("DELETE FROM bigrams")
    protected abstract suspend fun deleteAllBigrams()

    @Query("DELETE FROM trigrams")
    protected abstract suspend fun deleteAllTrigrams()

    @Transaction
    open suspend fun deleteAll() {
        deleteAllBigrams()
        deleteAllTrigrams()
    }

    @Query("DELETE FROM bigrams WHERE language = :language AND (previousWord = :word OR nextWord = :word)")
    protected abstract suspend fun deleteBigramsWith(word: String, language: String)

    @Query("DELETE FROM trigrams WHERE language = :language AND (secondPreviousWord = :word OR previousWord = :word OR nextWord = :word)")
    protected abstract suspend fun deleteTrigramsWith(word: String, language: String)

    @Transaction
    open suspend fun deleteWord(word: String, language: String) {
        deleteBigramsWith(word, language)
        deleteTrigramsWith(word, language)
    }

    @Query("DELETE FROM bigrams WHERE count <= 1 AND lastUsedAtEpochMillis < :cutoff")
    protected abstract suspend fun deleteSingleUseBigrams(cutoff: Long): Int

    @Query("DELETE FROM trigrams WHERE count <= 1 AND lastUsedAtEpochMillis < :cutoff")
    protected abstract suspend fun deleteSingleUseTrigrams(cutoff: Long): Int

    /** Keeps the [keep] most recently used bigrams. */
    @Query("DELETE FROM bigrams WHERE id IN (SELECT id FROM bigrams ORDER BY lastUsedAtEpochMillis DESC LIMIT -1 OFFSET :keep)")
    protected abstract suspend fun capBigrams(keep: Int): Int

    @Query("DELETE FROM trigrams WHERE id IN (SELECT id FROM trigrams ORDER BY lastUsedAtEpochMillis DESC LIMIT -1 OFFSET :keep)")
    protected abstract suspend fun capTrigrams(keep: Int): Int

    @Transaction
    open suspend fun prune(singleUseCutoff: Long, maxBigrams: Int, maxTrigrams: Int): Int =
        deleteSingleUseBigrams(singleUseCutoff) + deleteSingleUseTrigrams(singleUseCutoff) + capBigrams(maxBigrams) + capTrigrams(maxTrigrams)
}
