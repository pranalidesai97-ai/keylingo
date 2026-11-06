package com.keylingo.app.data.learning

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

/**
 * Personal-vocabulary DAO. Writes are atomic upserts: the previous
 * "find, then insert or update" sequence raced when two word-boundary
 * events for the same new word ran concurrently -- both saw "absent", the
 * second insert violated the unique (word, language) index, and the
 * exception escaped a fire-and-forget coroutine and crashed the keyboard.
 * INSERT OR IGNORE followed by an arithmetic UPDATE, inside one
 * transaction, cannot race (works on SQLite 3.19 / API 26, which lacks UPSERT).
 */
@Dao
abstract class LearnedWordDao {

    @Query("SELECT * FROM learned_words WHERE word = :word AND language = :language LIMIT 1")
    abstract suspend fun find(word: String, language: String): LearnedWordEntity?

    @Query(
        """
        INSERT OR IGNORE INTO learned_words (word, language, organicCount, acceptedCount, lastUsedAtEpochMillis)
        VALUES (:word, :language, 0, 0, :now)
        """
    )
    protected abstract suspend fun insertIfAbsent(word: String, language: String, now: Long)

    @Query(
        """
        UPDATE learned_words
        SET organicCount = organicCount + :organicDelta,
            acceptedCount = acceptedCount + :acceptedDelta,
            lastUsedAtEpochMillis = :now
        WHERE word = :word AND language = :language
        """
    )
    protected abstract suspend fun increment(word: String, language: String, organicDelta: Int, acceptedDelta: Int, now: Long)

    @Transaction
    open suspend fun recordUsage(word: String, language: String, organicDelta: Int, acceptedDelta: Int, now: Long) {
        insertIfAbsent(word, language, now)
        increment(word, language, organicDelta, acceptedDelta, now)
    }

    /**
     * Prefix search restricted to words past the "actually learned" bar:
     * 3 organic uses or 1 explicit acceptance. A single incidental typing
     * (often a typo) never surfaces. Ranked by acceptance-weighted
     * frequency, then recency.
     */
    @Query(
        """
        SELECT * FROM learned_words
        WHERE language = :language
          AND word LIKE :prefix || '%'
          AND (organicCount >= 3 OR acceptedCount >= 1)
        ORDER BY (organicCount + acceptedCount * 3) DESC, lastUsedAtEpochMillis DESC
        LIMIT :limit
        """
    )
    abstract suspend fun findByPrefix(prefix: String, language: String, limit: Int): List<LearnedWordEntity>

    @Query("SELECT COUNT(*) FROM learned_words WHERE word = :word AND language = :language AND (organicCount >= 3 OR acceptedCount >= 1)")
    abstract suspend fun countLearned(word: String, language: String): Int

    @Query("DELETE FROM learned_words")
    abstract suspend fun deleteAll()

    @Query(
        """
        SELECT * FROM learned_words
        WHERE organicCount >= 3 OR acceptedCount >= 1
        ORDER BY lastUsedAtEpochMillis DESC
        LIMIT :limit
        """
    )
    abstract suspend fun listLearned(limit: Int): List<LearnedWordEntity>

    @Query("DELETE FROM learned_words WHERE word = :word AND language = :language")
    abstract suspend fun delete(word: String, language: String)

    /** Words that never reached the learned bar and have not been typed since [cutoff]. */
    @Query("DELETE FROM learned_words WHERE organicCount < 3 AND acceptedCount = 0 AND lastUsedAtEpochMillis < :cutoff")
    abstract suspend fun deleteUnlearnedOlderThan(cutoff: Long): Int
}
