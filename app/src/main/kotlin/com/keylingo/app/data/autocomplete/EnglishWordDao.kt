package com.keylingo.app.data.autocomplete

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EnglishWordDao {

    /**
     * Indexed trailing-wildcard prefix match, always ordered by
     * [EnglishWordEntity.frequencyRank] ascending (most common first) --
     * never alphabetical. [prefix] must already be lowercased/trimmed by
     * the caller.
     */
    @Query(
        """
        SELECT * FROM english_words
        WHERE word LIKE :prefix || '%'
        ORDER BY frequencyRank ASC
        LIMIT :limit
        """
    )
    suspend fun findByPrefix(prefix: String, limit: Int): List<EnglishWordEntity>

    @Query("SELECT COUNT(*) FROM english_words")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(words: List<EnglishWordEntity>)
}
