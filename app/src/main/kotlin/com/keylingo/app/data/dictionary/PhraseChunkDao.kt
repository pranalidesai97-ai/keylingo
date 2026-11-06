package com.keylingo.app.data.dictionary

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PhraseChunkDao {

    @Query(
        """
        SELECT * FROM phrase_chunks
        WHERE sourceLanguage = :sourceLanguage AND targetLanguage = :targetLanguage
        ORDER BY wordCount DESC
        """
    )
    suspend fun allChunks(sourceLanguage: String, targetLanguage: String): List<PhraseChunkEntity>

    @Query(
        "SELECT COUNT(*) FROM phrase_chunks WHERE sourceLanguage = :sourceLanguage AND targetLanguage = :targetLanguage"
    )
    suspend fun countForLanguagePair(sourceLanguage: String, targetLanguage: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chunks: List<PhraseChunkEntity>)
}
