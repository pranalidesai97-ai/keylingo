package com.keylingo.app.data.context

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface BigramDao {

    @Query("SELECT * FROM bigrams WHERE previousWord = :previousWord AND nextWord = :nextWord AND language = :language LIMIT 1")
    suspend fun find(previousWord: String, nextWord: String, language: String): BigramEntity?

    @Insert
    suspend fun insert(entity: BigramEntity)

    @Update
    suspend fun update(entity: BigramEntity)

    /**
     * All known next-words for [previousWord], highest count first, capped
     * at [limit]. Called **once per suggestion refresh** by
     * [RoomContextPredictionProvider.nextWordCounts] -- the ranking code
     * then does an in-memory map lookup per candidate rather than issuing
     * one query per candidate. This is the concrete mechanism satisfying
     * "do not query the database repeatedly for every suggestion
     * candidate".
     */
    @Query(
        """
        SELECT * FROM bigrams
        WHERE previousWord = :previousWord AND language = :language
        ORDER BY count DESC
        LIMIT :limit
        """
    )
    suspend fun findByPreviousWord(previousWord: String, language: String, limit: Int): List<BigramEntity>
}
