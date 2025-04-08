package com.abaga129.tekisuto.database

import android.content.Context
import android.util.Log
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import org.json.JSONException
import java.util.Date

private const val TAG = "DictionaryRepository"

/**
 * Dictionary repository to handle database operations
 */
class DictionaryRepository(context: Context) {
    private val database: AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "tekisuto_dictionary${com.abaga129.tekisuto.BuildConfig.DB_NAME_SUFFIX}.db"
    )
        // Add migration for adding isHtmlContent column
        .addMigrations(MIGRATION_3_4)
        .fallbackToDestructiveMigration() // Fallback if we added more migrations in the future
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING) // Improve performance with WAL
        .build()
        
    init {
        // For debugging only
        Log.d("DictionaryRepository", "Database initialized with schema version ${database.openHelper.readableDatabase.version}")
    }

    init {
        // Check database can be accessed (will fail early if schema issues)
        try {
            // Just open DB to verify connection works
            database.openHelper.readableDatabase
        } catch (e: IllegalStateException) {
            if (e.message?.contains("Room cannot verify the data integrity") == true) {
                Log.e(TAG, "Database schema mismatch. Please uninstall and reinstall the app or clear app data.", e)
                // Display a Toast on the main thread
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(
                        context,
                        "Database error detected. Please uninstall and reinstall the app.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private val dictionaryDao = database.dictionaryDao()
    private val dictionaryMetadataDao = database.dictionaryMetadataDao()

    /**
     * Saves dictionary metadata and returns the ID
     */
    suspend fun saveDictionaryMetadata(metadata: DictionaryMetadataEntity): Long {
        return try {
            dictionaryMetadataDao.insert(metadata)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving dictionary metadata", e)
            -1
        }
    }

    /**
     * Gets all dictionary metadata
     */
    suspend fun getAllDictionaries(): List<DictionaryMetadataEntity> {
        return try {
            dictionaryMetadataDao.getAllDictionaries()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting dictionaries", e)
            emptyList()
        }
    }

    /**
     * Deletes a dictionary and all its entries
     */
    suspend fun deleteDictionary(dictionaryId: Long) {
        try {
            // Delete all entries for this dictionary
            dictionaryDao.deleteEntriesByDictionaryId(dictionaryId)
            // Delete the dictionary metadata
            dictionaryMetadataDao.deleteDictionary(dictionaryId)
            Log.d(TAG, "Dictionary with ID $dictionaryId deleted")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting dictionary", e)
            throw e
        }
    }

    /**
     * Updates the priority of a dictionary
     */
    suspend fun updateDictionaryPriority(dictionaryId: Long, newPriority: Int) {
        try {
            dictionaryMetadataDao.updatePriority(dictionaryId, newPriority)
            Log.d(TAG, "Updated priority of dictionary $dictionaryId to $newPriority")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating dictionary priority", e)
            throw e
        }
    }

    /**
     * Imports dictionary entries into the database
     * @return The number of entries successfully imported
     */
    suspend fun importDictionaryEntries(entries: List<DictionaryEntryEntity>): Int {
        try {
            if (entries.isEmpty()) {
                Log.w(TAG, "importDictionaryEntries called with empty list")
                return 0
            }

            // Check for invalid entries - log detailed debug info
            val invalidEntries = entries.filter { it.term.isBlank() }
            if (invalidEntries.isNotEmpty()) {
                Log.e(TAG, "Found ${invalidEntries.size} invalid entries with blank terms. Will skip these.")
                
                // Log the first few invalid entries for debugging
                invalidEntries.take(3).forEachIndexed { index, entry ->
                    Log.e(TAG, "Invalid entry #$index - " +
                          "term: '${entry.term}', reading: '${entry.reading}', " +
                          "definition length: ${entry.definition.length}")
                }
            }
            
            // Filter out invalid entries
            val validEntries = entries.filter { it.term.isNotBlank() }
            
            // Log sample of entries being imported
            if (validEntries.isNotEmpty()) {
                val sampleEntry = validEntries.first()
                Log.d(TAG, "Sample entry: term='${sampleEntry.term}', " +
                      "reading='${sampleEntry.reading}', " +
                      "isHtml=${sampleEntry.isHtmlContent}, " +
                      "definition length=${sampleEntry.definition.length}")
            }

            // Insert into database
            val result = dictionaryDao.insertAll(validEntries)
            
            Log.d(TAG, "Successfully imported ${validEntries.size} entries to database")
            return validEntries.size
        } catch (e: Exception) {
            Log.e(TAG, "Error importing entries to database: ${e.message}", e)
            
            // Try to provide more details about what might have gone wrong
            if (entries.isNotEmpty()) {
                try {
                    val firstEntry = entries.first()
                    Log.e(TAG, "First entry details - term: '${firstEntry.term}', " +
                          "def length: ${firstEntry.definition.length}")
                } catch (ex: Exception) {
                    Log.e(TAG, "Could not inspect first entry: ${ex.message}")
                }
            }
            
            throw e
        }
    }

    /**
     * Searches for entries matching the given query, ordered by dictionary priority
     * Use fastSearch=true for quick search without prioritization (better for interactive search)
     */
    suspend fun searchDictionary(query: String, fastSearch: Boolean = false): List<DictionaryEntryEntity> {
        return try {
            val searchQuery = "%$query%"
            if (fastSearch) {
                // Fast search without priority ordering
                dictionaryDao.fastSearchEntries(searchQuery)
            } else {
                // Full search with priority ordering
                dictionaryDao.searchEntriesOrderedByPriority(searchQuery)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching dictionary", e)
            emptyList()
        }
    }

    /**
     * Gets a dictionary entry by its term
     */
    suspend fun getEntryByTerm(term: String): DictionaryEntryEntity? {
        return try {
            dictionaryDao.getEntryByTerm(term)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting entry by term", e)
            null
        }
    }
    
    /**
     * Bulk search for entries by exact term match
     * Much faster than individual queries for multiple terms
     */
    suspend fun bulkSearchByExactTerms(terms: List<String>): List<DictionaryEntryEntity> {
        return try {
            if (terms.isEmpty()) {
                return emptyList()
            }
            // Limit to 100 terms to avoid SQL query size limits
            val searchTerms = terms.take(100)
            dictionaryDao.bulkSearchByExactTerms(searchTerms)
        } catch (e: Exception) {
            Log.e(TAG, "Error searching dictionary by exact terms", e)
            emptyList()
        }
    }

    /**
     * Gets total count of dictionary entries across all dictionaries
     */
    suspend fun getDictionaryEntryCount(): Int {
        return try {
            dictionaryDao.getCount()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting dictionary entry count", e)
            0
        }
    }

    /**
     * Gets entry count for a specific dictionary
     */
    suspend fun getDictionaryEntryCount(dictionaryId: Long): Int {
        return try {
            dictionaryDao.getCountByDictionaryId(dictionaryId)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting entry count for dictionary $dictionaryId", e)
            0
        }
    }

    /**
     * Clears all dictionary entries and metadata
     */
    suspend fun clearAllDictionaries() {
        try {
            dictionaryDao.deleteAll()
            dictionaryMetadataDao.deleteAll()
            Log.d(TAG, "All dictionaries cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing all dictionaries", e)
            throw e
        }
    }

    /**
     * Gets recent dictionary entries with limit
     */
    suspend fun getRecentEntries(limit: Int): List<DictionaryEntryEntity> {
        return try {
            dictionaryDao.getRecentEntries(limit)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting recent entries", e)
            emptyList()
        }
    }
}

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
 * Type converters for Room
 */
class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>): String {
        val jsonArray = JSONArray()
        value.forEach { jsonArray.put(it) }
        return jsonArray.toString()
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val list = mutableListOf<String>()
        try {
            val jsonArray = JSONArray(value)
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getString(i))
            }
        } catch (e: JSONException) {
            // Return empty list on error
            Log.e("Converters", "Error converting JSON to string list", e)
        }
        return list
    }

    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}

/**
 * DAO for dictionary metadata operations
 */
@Dao
interface DictionaryMetadataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(metadata: DictionaryMetadataEntity): Long

    @Query("SELECT * FROM dictionary_metadata ORDER BY priority DESC")
    suspend fun getAllDictionaries(): List<DictionaryMetadataEntity>

    @Query("SELECT * FROM dictionary_metadata WHERE id = :id")
    suspend fun getDictionaryById(id: Long): DictionaryMetadataEntity?

    @Query("DELETE FROM dictionary_metadata WHERE id = :id")
    suspend fun deleteDictionary(id: Long)

    @Query("DELETE FROM dictionary_metadata")
    suspend fun deleteAll()

    @Query("UPDATE dictionary_metadata SET priority = :priority WHERE id = :id")
    suspend fun updatePriority(id: Long, priority: Int)

    @Query("UPDATE dictionary_metadata SET entryCount = :count WHERE id = :id")
    suspend fun updateEntryCount(id: Long, count: Int)
}

/**
 * DAO for dictionary operations
 */
@Dao
interface DictionaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<DictionaryEntryEntity>): List<Long>

    @Query("SELECT e.* FROM dictionary_entries e " +
           "JOIN dictionary_metadata m ON e.dictionaryId = m.id " +
           "WHERE LOWER(e.term) LIKE LOWER(:query) OR LOWER(e.reading) LIKE LOWER(:query) OR LOWER(e.definition) LIKE LOWER(:query) " +
           "ORDER BY m.priority DESC, e.id DESC")
    suspend fun searchEntriesOrderedByPriority(query: String): List<DictionaryEntryEntity>

    @Query("SELECT * FROM dictionary_entries " +
           "WHERE term LIKE :query OR reading LIKE :query " +  /* Using index without LOWER for better performance */
           "ORDER BY id DESC LIMIT 50")
    suspend fun fastSearchEntries(query: String): List<DictionaryEntryEntity>
    
    /* Bulk search for multiple terms at once */
    @Query("SELECT * FROM dictionary_entries WHERE term IN (:terms) LIMIT 100")
    suspend fun bulkSearchByExactTerms(terms: List<String>): List<DictionaryEntryEntity>

    @Query("SELECT * FROM dictionary_entries WHERE LOWER(term) = LOWER(:term) " +
           "ORDER BY id DESC LIMIT 1")
    suspend fun getEntryByTerm(term: String): DictionaryEntryEntity?

    @Query("SELECT COUNT(*) FROM dictionary_entries")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM dictionary_entries WHERE dictionaryId = :dictionaryId")
    suspend fun getCountByDictionaryId(dictionaryId: Long): Int

    @Query("DELETE FROM dictionary_entries")
    suspend fun deleteAll()

    @Query("DELETE FROM dictionary_entries WHERE dictionaryId = :dictionaryId")
    suspend fun deleteEntriesByDictionaryId(dictionaryId: Long)

    @Query("SELECT * FROM dictionary_entries ORDER BY id DESC LIMIT :limit")
    suspend fun getRecentEntries(limit: Int): List<DictionaryEntryEntity>
}

/**
 * Room database
 */
@Database(entities = [DictionaryEntryEntity::class, DictionaryMetadataEntity::class], version = 4)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao
    abstract fun dictionaryMetadataDao(): DictionaryMetadataDao
}

/**
 * Migration from version 3 to 4 - adds isHtmlContent column to dictionary_entries table
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add isHtmlContent column with default value false
        database.execSQL("ALTER TABLE dictionary_entries ADD COLUMN isHtmlContent INTEGER NOT NULL DEFAULT 0")
    }
}
