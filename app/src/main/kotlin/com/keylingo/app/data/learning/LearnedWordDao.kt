package com.keylingo.app.data.learning

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface LearnedWordDao {

    @Query("SELECT * FROM learned_words WHERE word = :word AND language = :language LIMIT 1")
    suspend fun find(word: String, language: String): LearnedWordEntity?

    @Insert
    suspend fun insert(entity: LearnedWordEntity)

    @Update
    suspend fun update(entity: LearnedWordEntity)

    /**
     * Prefix search restricted to words that meet the "actually learned"
     * threshold -- see [RoomLearnedVocabularyProvider] for why 3 organic
     * uses or 1 explicit acceptance is the bar, rather than surfacing a
     * word after a single incidental typing (which would risk exactly the
     * typo/garbage-string problem this feature must avoid).
     *
     * Ranked by a fixed, deterministic weighting (an explicit acceptance
     * counts as 3 organic uses -- a tap is a much stronger signal than
     * incidental typing), then by recency as a tiebreaker. Hardcoded
     * rather than parameterized: this is meant to be simple and legible,
     * not a tunable ML weight.
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
    suspend fun findByPrefix(prefix: String, language: String, limit: Int): List<LearnedWordEntity>
}
