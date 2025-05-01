package com.abaga129.tekisuto.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity for storing word frequency data separate from dictionary entries
 */
@Entity(
    tableName = "word_frequencies",
    foreignKeys = [
        ForeignKey(
            entity = DictionaryMetadataEntity::class,
            parentColumns = ["id"],
            childColumns = ["dictionaryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("dictionaryId"),
        Index("word", unique = false)
    ]
)
data class WordFrequencyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dictionaryId: Long,
    val word: String,
    val frequency: Int
)

/**
 * DAO for word frequency operations
 */
@androidx.room.Dao
interface WordFrequencyDao {
    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insert(entity: WordFrequencyEntity): Long
    
    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<WordFrequencyEntity>): List<Long>
    
    @androidx.room.Query("SELECT * FROM word_frequencies WHERE dictionaryId = :dictionaryId")
    suspend fun getFrequenciesForDictionary(dictionaryId: Long): List<WordFrequencyEntity>
    
    @androidx.room.Query("SELECT * FROM word_frequencies WHERE word = :word LIMIT 1")
    suspend fun getFrequencyForWord(word: String): WordFrequencyEntity?
    
    // Improved query with better matching - try both exact match and normalized forms
    @androidx.room.Query("SELECT * FROM word_frequencies WHERE dictionaryId = :dictionaryId AND (LOWER(word) = LOWER(:word) OR word = :word) LIMIT 1")
    suspend fun getFrequencyForWordInDictionary(word: String, dictionaryId: Long): WordFrequencyEntity?
    
    // Try more variations including trimming whitespace
    @androidx.room.Query("SELECT * FROM word_frequencies WHERE dictionaryId = :dictionaryId AND (LOWER(TRIM(word)) = LOWER(TRIM(:word))) LIMIT 1")
    suspend fun getFrequencyForWordTrimmed(word: String, dictionaryId: Long): WordFrequencyEntity?
    
    @androidx.room.Query("DELETE FROM word_frequencies WHERE dictionaryId = :dictionaryId")
    suspend fun deleteAllForDictionary(dictionaryId: Long)
    
    @androidx.room.Query("SELECT COUNT(*) FROM word_frequencies WHERE dictionaryId = :dictionaryId")
    suspend fun getCountForDictionary(dictionaryId: Long): Int
    
    @androidx.room.Query("SELECT COUNT(*) FROM word_frequencies")
    suspend fun getTotalCount(): Int
}
