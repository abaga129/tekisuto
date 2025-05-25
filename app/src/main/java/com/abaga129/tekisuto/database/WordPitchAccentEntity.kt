package com.abaga129.tekisuto.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.abaga129.tekisuto.database.DictionaryMetadataEntity

/**
 * Entity for storing word pitch accent data separate from dictionary entries
 */
@Entity(
    tableName = "word_pitch_accents",
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
        Index("word", unique = false),
        Index("reading", unique = false)
    ]
)
data class WordPitchAccentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dictionaryId: Long,
    val word: String,
    val reading: String,
    val pitchAccent: String // Stores pitch accent pattern. Could be a number, a description, or a formatted pattern
)

/**
 * DAO for word pitch accent operations
 */
@androidx.room.Dao
interface WordPitchAccentDao {
    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insert(entity: WordPitchAccentEntity): Long
    
    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<WordPitchAccentEntity>): List<Long>
    
    @androidx.room.Query("SELECT * FROM word_pitch_accents WHERE dictionaryId = :dictionaryId")
    suspend fun getPitchAccentsForDictionary(dictionaryId: Long): List<WordPitchAccentEntity>
    
    @androidx.room.Query("SELECT * FROM word_pitch_accents WHERE word = :word LIMIT 1")
    suspend fun getPitchAccentForWord(word: String): WordPitchAccentEntity?
    
    // Improved query with better matching - try both exact match and normalized forms
    @androidx.room.Query("SELECT * FROM word_pitch_accents WHERE dictionaryId = :dictionaryId AND (LOWER(word) = LOWER(:word) OR word = :word) LIMIT 1")
    suspend fun getPitchAccentForWordInDictionary(word: String, dictionaryId: Long): WordPitchAccentEntity?

    // Query that tries to match the word with reading too
    @androidx.room.Query("SELECT * FROM word_pitch_accents WHERE (LOWER(word) = LOWER(:word) OR LOWER(reading) = LOWER(:reading)) ORDER BY dictionaryId LIMIT 1")
    suspend fun getPitchAccentByWordAndReading(word: String, reading: String): WordPitchAccentEntity?
    
    // Try more variations including trimming whitespace
    @androidx.room.Query("SELECT * FROM word_pitch_accents WHERE dictionaryId = :dictionaryId AND (LOWER(TRIM(word)) = LOWER(TRIM(:word))) LIMIT 1")
    suspend fun getPitchAccentForWordTrimmed(word: String, dictionaryId: Long): WordPitchAccentEntity?
    
    @androidx.room.Query("DELETE FROM word_pitch_accents WHERE dictionaryId = :dictionaryId")
    suspend fun deleteAllForDictionary(dictionaryId: Long)
    
    @androidx.room.Query("SELECT COUNT(*) FROM word_pitch_accents WHERE dictionaryId = :dictionaryId")
    suspend fun getCountForDictionary(dictionaryId: Long): Int
    
    @androidx.room.Query("SELECT COUNT(*) FROM word_pitch_accents")
    suspend fun getTotalCount(): Int
}