package com.abaga129.tekisuto.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.abaga129.tekisuto.database.DictionaryMetadataEntity
import java.util.Date

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

/**
 * Database entity for dictionary entries
 */
@Entity(
    tableName = "dictionary_entries",
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
        Index("term"),
        Index("reading")
    ]
)
data class DictionaryEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dictionaryId: Long = 0,
    val term: String,
    val reading: String,
    val definition: String,
    val partOfSpeech: String,
    val tags: List<String> = emptyList(),
    val isHtmlContent: Boolean = false // Flag to indicate if definition contains HTML content
)

/**
 * Database entity for dictionary metadata
 */
@Entity(tableName = "dictionary_metadata")
data class DictionaryMetadataEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val author: String,
    val description: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val entryCount: Int = 0,
    val priority: Int = 0,
    val importDate: Date = Date()
)

/**
 * Entity for tracking words that have been exported to AnkiDroid
 */
@Entity(tableName = "exported_words",
    indices = [Index(value = ["word"], unique = false)])
data class ExportedWordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val word: String,                  // The word that was exported
    val dictionaryId: Long,            // Which dictionary it came from
    val ankiDeckId: Long,              // AnkiDroid deck ID
    val ankiNoteId: Long? = null,      // AnkiDroid note ID if available
    val dateAdded: Date = Date()       // When it was exported
)