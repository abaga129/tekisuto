package com.abaga129.tekisuto.database

import android.content.Context
import android.util.Log
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import org.json.JSONArray
import org.json.JSONException

private const val TAG = "DictionaryRepository"

/**
 * Dictionary repository to handle database operations
 */
class DictionaryRepository(context: Context) {
    private val database: AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "tekisuto_dictionary.db"
    )
        .fallbackToDestructiveMigration() // For simplicity in development
        .build()

    private val dictionaryDao = database.dictionaryDao()

    /**
     * Imports dictionary entries into the database
     */
    suspend fun importDictionaryEntries(entries: List<DictionaryEntryEntity>) {
        try {
            // Insert into database
            dictionaryDao.insertAll(entries)
            Log.d(TAG, "Imported ${entries.size} entries to database")
        } catch (e: Exception) {
            Log.e(TAG, "Error importing entries to database", e)
            throw e
        }
    }

    /**
     * Searches for entries matching the given query
     */
    suspend fun searchDictionary(query: String): List<DictionaryEntryEntity> {
        return try {
            val searchQuery = "%$query%"
            dictionaryDao.searchEntries(searchQuery)
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
     * Gets total count of dictionary entries
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
     * Clears all dictionary entries
     */
    suspend fun clearDictionary() {
        try {
            dictionaryDao.deleteAll()
            Log.d(TAG, "Dictionary cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing dictionary", e)
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
 * Database entity for dictionary entries
 */
@Entity(tableName = "dictionary_entries")
data class DictionaryEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val term: String,
    val reading: String,
    val definition: String,
    val partOfSpeech: String,
    val tags: List<String> = emptyList()
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
}

/**
 * DAO for dictionary operations
 */
@Dao
interface DictionaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<DictionaryEntryEntity>)

    @Query("SELECT * FROM dictionary_entries WHERE LOWER(term) LIKE LOWER(:query) OR LOWER(reading) LIKE LOWER(:query) OR LOWER(definition) LIKE LOWER(:query)")
    suspend fun searchEntries(query: String): List<DictionaryEntryEntity>

    @Query("SELECT * FROM dictionary_entries WHERE LOWER(term) = LOWER(:term) LIMIT 1")
    suspend fun getEntryByTerm(term: String): DictionaryEntryEntity?

    @Query("SELECT COUNT(*) FROM dictionary_entries")
    suspend fun getCount(): Int

    @Query("DELETE FROM dictionary_entries")
    suspend fun deleteAll()
    
    @Query("SELECT * FROM dictionary_entries ORDER BY id DESC LIMIT :limit")
    suspend fun getRecentEntries(limit: Int): List<DictionaryEntryEntity>
}

/**
 * Room database
 */
@Database(entities = [DictionaryEntryEntity::class], version = 1)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao
}