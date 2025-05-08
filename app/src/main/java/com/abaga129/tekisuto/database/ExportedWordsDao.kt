package com.abaga129.tekisuto.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO for exported words operations
 */
@Dao
interface ExportedWordsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(exportedWord: ExportedWordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exportedWords: List<ExportedWordEntity>): List<Long>

    @Query("SELECT EXISTS(SELECT 1 FROM exported_words WHERE LOWER(word) = LOWER(:word) LIMIT 1)")
    suspend fun isWordExported(word: String): Boolean

    @Query("SELECT * FROM exported_words WHERE LOWER(word) = LOWER(:word) LIMIT 1")
    suspend fun getExportedWordByWord(word: String): ExportedWordEntity?

    @Query("SELECT * FROM exported_words ORDER BY dateAdded DESC")
    suspend fun getAllExportedWords(): List<ExportedWordEntity>

    @Query("SELECT COUNT(*) FROM exported_words")
    suspend fun getExportedWordCount(): Int
}