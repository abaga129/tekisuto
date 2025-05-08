package com.abaga129.tekisuto.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO for dictionary metadata operations
 */
@Dao
interface DictionaryMetadataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(metadata: DictionaryMetadataEntity): Long

    @Query("SELECT * FROM dictionary_metadata ORDER BY priority DESC")
    suspend fun getAllDictionaries(): List<DictionaryMetadataEntity>

    @Query("SELECT id FROM dictionary_metadata")
    suspend fun getAllDictionaryIds(): List<Long>

    @Query("SELECT * FROM dictionary_metadata WHERE id = :id")
    suspend fun getDictionaryById(id: Long): DictionaryMetadataEntity?

    @Query("DELETE FROM dictionary_metadata WHERE id = :id")
    suspend fun deleteDictionary(id: Long)

    @Query("DELETE FROM dictionary_metadata")
    suspend fun deleteAll()

    @Query("UPDATE dictionary_metadata SET priority = :priority WHERE id = :id")
    suspend fun updatePriority(id: Long, priority: Int)

    @Query("UPDATE dictionary_metadata SET title = :title, author = :author, description = :description, sourceLanguage = :sourceLanguage, targetLanguage = :targetLanguage, entryCount = :entryCount, priority = :priority, importDate = :importDate WHERE id = :id")
    suspend fun updateMetadata(id: Long, title: String, author: String, description: String, sourceLanguage: String, targetLanguage: String, entryCount: Int, priority: Int, importDate: Long): Int

    @Query("UPDATE dictionary_metadata SET entryCount = :count WHERE id = :id")
    suspend fun updateEntryCount(id: Long, count: Int)
}