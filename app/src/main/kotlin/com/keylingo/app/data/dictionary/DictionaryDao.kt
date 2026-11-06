package com.keylingo.app.data.dictionary

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DictionaryDao {

    @Query(
        """
        SELECT * FROM dictionary_entries
        WHERE sourceWord = :word AND sourceLanguage = :sourceLanguage AND targetLanguage = :targetLanguage
        LIMIT 1
        """
    )
    suspend fun findWord(word: String, sourceLanguage: String, targetLanguage: String): DictionaryEntity?

    @Query(
        "SELECT COUNT(*) FROM dictionary_entries WHERE sourceLanguage = :sourceLanguage AND targetLanguage = :targetLanguage"
    )
    suspend fun countForLanguagePair(sourceLanguage: String, targetLanguage: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<DictionaryEntity>)
}
