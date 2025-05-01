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
import com.abaga129.tekisuto.database.MIGRATION_8_9
import org.json.JSONArray
import org.json.JSONException
import java.util.Date
import kotlin.random.Random

private const val TAG = "DictionaryRepository"

/**
 * Dictionary repository to handle database operations
 */
class DictionaryRepository(context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: DictionaryRepository? = null
        
        fun getInstance(context: Context): DictionaryRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = DictionaryRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
    private val database: AppDatabase = AppDatabase.createDatabase(context)
        
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
    private val exportedWordsDao = database.exportedWordsDao()
    private val profileDictionaryDao = database.profileDictionaryDao()
    private val wordFrequencyDao = database.wordFrequencyDao()
    
    /**
     * Normalize Japanese characters for better matching
     * - Convert full-width to half-width where appropriate
     * - Normalize variations of similar characters
     */
    private fun normalizeJapaneseCharacters(input: String): String {
        var result = input
        
        // Replace full-width alphabetic characters with half-width
        val fullWidthAlphabeticStart = 0xFF21 // Ａ
        val fullWidthAlphabeticEnd = 0xFF5A // ｚ
        val asciiOffset = 0xFF21 - 'A'.code
        
        val sb = StringBuilder()
        for (c in result) {
            val code = c.code
            if (code in fullWidthAlphabeticStart..fullWidthAlphabeticEnd) {
                sb.append((code - asciiOffset).toChar())
            } else {
                sb.append(c)
            }
        }
        result = sb.toString()
        
        // Other specific normalizations
        val replacements = mapOf(
            '〜' to '～',
            'ー' to '－',
            '－' to '-',
            '，' to ',',
            '　' to ' '
        )
        
        for ((fullWidth, halfWidth) in replacements) {
            result = result.replace(fullWidth, halfWidth)
        }
        
        return result
    }

    /**
     * Saves dictionary metadata and returns the ID
     * Uses a safe approach that won't delete dictionary entries when updating
     */
    suspend fun saveDictionaryMetadata(metadata: DictionaryMetadataEntity): Long {
        return try {
            if (metadata.id > 0) {
                // This is an update to an existing dictionary - use UPDATE query instead of REPLACE
                val entryCountBefore = getDictionaryEntryCount(metadata.id)
                Log.d(TAG, "Updating existing dictionary ${metadata.id} which has $entryCountBefore entries")
                
                // Use direct update query instead of insert with REPLACE strategy
                val updateCount = dictionaryMetadataDao.updateMetadata(
                    id = metadata.id,
                    title = metadata.title,
                    author = metadata.author,
                    description = metadata.description,
                    sourceLanguage = metadata.sourceLanguage,
                    targetLanguage = metadata.targetLanguage,
                    entryCount = metadata.entryCount,
                    priority = metadata.priority,
                    importDate = metadata.importDate.time
                )
                
                Log.d(TAG, "Updated dictionary metadata with ID ${metadata.id}, rows affected: $updateCount")
                
                // Check if entry count is still correct after update
                val entryCountAfter = getDictionaryEntryCount(metadata.id)
                Log.d(TAG, "After update, dictionary ${metadata.id} has $entryCountAfter entries")
                
                if (entryCountAfter != entryCountBefore) {
                    Log.e(TAG, "WARNING: Dictionary entries changed from $entryCountBefore to $entryCountAfter during metadata update!")
                }
                
                metadata.id
            } else {
                // New dictionary, use normal insert
                val id = dictionaryMetadataDao.insert(metadata)
                Log.d(TAG, "Inserted new dictionary metadata with ID $id")
                id
            }
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
            // Log dictionary entry counts before querying metadata
            Log.d(TAG, "getAllDictionaries called - Checking dictionary entry counts before query")
            val dictionaryIds = dictionaryMetadataDao.getAllDictionaryIds()
            for (id in dictionaryIds) {
                val entryCount = getDictionaryEntryCount(id)
                Log.d(TAG, "VERIFY: Before getAllDictionaries returns - Dictionary $id has $entryCount entries")
            }
            
            val result = dictionaryMetadataDao.getAllDictionaries()
            
            // Log dictionary entry counts after querying metadata
            Log.d(TAG, "getAllDictionaries returning ${result.size} dictionaries - Verifying entry counts")
            for (dict in result) {
                val entryCount = getDictionaryEntryCount(dict.id)
                Log.d(TAG, "VERIFY: After getAllDictionaries returns - Dictionary ${dict.id} has $entryCount entries")
            }
            
            result
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
            Log.d(TAG, "Before updatePriority: Dictionary $dictionaryId - Getting entry count")
            val entryCountBefore = getDictionaryEntryCount(dictionaryId)
            Log.d(TAG, "Before updatePriority: Dictionary $dictionaryId has $entryCountBefore entries")
            
            dictionaryMetadataDao.updatePriority(dictionaryId, newPriority)
            Log.d(TAG, "Updated priority of dictionary $dictionaryId to $newPriority")
            
            // Verify entries weren't affected
            val entryCountAfter = getDictionaryEntryCount(dictionaryId)
            Log.d(TAG, "After updatePriority: Dictionary $dictionaryId has $entryCountAfter entries")
            
            if (entryCountBefore != entryCountAfter) {
                Log.e(TAG, "WARNING: Entry count changed from $entryCountBefore to $entryCountAfter during priority update!")
            }
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
     * Results are prioritized with exact matches at the top
     * 
     * @param query The search query
     * @param profileId The profile ID to search dictionaries for (or null to search all dictionaries)
     * @param fastSearch Whether to use fast search (without full prioritization)
     * @return List of matching dictionary entries
     */
    suspend fun searchDictionary(
        query: String, 
        profileId: Long? = null, 
        fastSearch: Boolean = false
    ): List<DictionaryEntryEntity> {
        return try {
            val searchQuery = "%$query%"
            val exactQuery = query.trim() // For exact match comparison
            
            if (profileId == null) {
                // Search all dictionaries
                if (fastSearch) {
                    // Fast search without priority ordering but with exact match prioritization
                    dictionaryDao.fastSearchEntries(searchQuery, exactQuery)
                } else {
                    // Full search with priority ordering and exact match prioritization
                    dictionaryDao.searchEntriesOrderedByPriority(searchQuery, exactQuery)
                }
            } else {
                // Search only dictionaries associated with this profile
                if (fastSearch) {
                    // Fast search for profile dictionaries
                    dictionaryDao.fastSearchEntriesForProfile(searchQuery, exactQuery, profileId)
                } else {
                    // Full search with priority for profile dictionaries
                    dictionaryDao.searchEntriesForProfile(searchQuery, exactQuery, profileId)
                }
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
     * 
     * @param terms List of terms to search for
     * @param profileId Optional profile ID to limit search to dictionaries in that profile
     * @return List of matching dictionary entries
     */
    suspend fun bulkSearchByExactTerms(terms: List<String>, profileId: Long? = null): List<DictionaryEntryEntity> {
        return try {
            if (terms.isEmpty()) {
                return emptyList()
            }
            // Limit to 100 terms to avoid SQL query size limits
            val searchTerms = terms.take(100)
            
            if (profileId == null || profileId <= 0) {
                // Search all dictionaries
                dictionaryDao.bulkSearchByExactTerms(searchTerms)
            } else {
                // Search only dictionaries for this profile
                dictionaryDao.bulkSearchByExactTermsForProfile(searchTerms, profileId)
            }
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
    
    /**
     * Gets recent dictionary entries from specific dictionaries
     * @param limit Maximum number of entries to return
     * @param dictionaryIds List of dictionary IDs to include
     * @return List of dictionary entries from the specified dictionaries
     */
    suspend fun getRecentEntriesFromDictionaries(limit: Int, dictionaryIds: List<Long>): List<DictionaryEntryEntity> {
        return try {
            if (dictionaryIds.isEmpty()) {
                Log.d(TAG, "No dictionary IDs provided, returning empty list")
                return emptyList()
            }
            dictionaryDao.getRecentEntriesFromDictionaries(limit, dictionaryIds)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting recent entries from dictionaries: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Adds a word to the exported words table
     */
    suspend fun addExportedWord(word: String, dictionaryId: Long, ankiDeckId: Long, ankiNoteId: Long? = null) {
        try {
            val exportedWord = ExportedWordEntity(
                word = word.trim().lowercase(),
                dictionaryId = dictionaryId,
                ankiDeckId = ankiDeckId,
                ankiNoteId = ankiNoteId
            )
            exportedWordsDao.insert(exportedWord)
            Log.d(TAG, "Added exported word: $word")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding exported word", e)
            throw e
        }
    }
    
    /**
     * Checks if a word has been exported to AnkiDroid
     */
    suspend fun isWordExported(word: String): Boolean {
        return try {
            exportedWordsDao.isWordExported(word.trim().lowercase())
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if word is exported", e)
            false
        }
    }
    
    /**
     * Gets all exported words
     */
    suspend fun getAllExportedWords(): List<ExportedWordEntity> {
        return try {
            exportedWordsDao.getAllExportedWords()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all exported words", e)
            emptyList()
        }
    }
    
    /**
     * Gets the count of exported words
     */
    suspend fun getExportedWordCount(): Int {
        return try {
            exportedWordsDao.getExportedWordCount()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting exported word count", e)
            0
        }
    }
    
    /**
     * Imports a list of words as exported words (e.g., from Anki)
     * 
     * @param words List of words to import
     * @param dictionaryId Dictionary ID (use 0 for "any dictionary")
     * @param ankiDeckId Anki deck ID (use -1 for words imported from .apkg)
     * @return Number of words imported
     */
    suspend fun importExportedWords(words: List<String>, dictionaryId: Long, ankiDeckId: Long): Int {
        try {
            if (words.isEmpty()) {
                Log.w(TAG, "importExportedWords called with empty list")
                return 0
            }
            
            // Create ExportedWordEntity objects for each word
            val exportedWords = words.map { word ->
                ExportedWordEntity(
                    word = word.trim().lowercase(),
                    dictionaryId = dictionaryId,
                    ankiDeckId = ankiDeckId
                )
            }
            
            // Insert into database
            val result = exportedWordsDao.insertAll(exportedWords)
            
            Log.d(TAG, "Successfully imported ${exportedWords.size} exported words")
            return exportedWords.size
        } catch (e: Exception) {
            Log.e(TAG, "Error importing exported words: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Gets all dictionaries for a specific profile
     * @param profileId The profile ID to get dictionaries for
     * @return List of DictionaryMetadataEntity for the profile
     */
    suspend fun getDictionariesForProfile(profileId: Long): List<DictionaryMetadataEntity> {
        return try {
            profileDictionaryDao.getDictionaryMetadataForProfile(profileId)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting dictionaries for profile $profileId", e)
            emptyList()
        }
    }

    /**
     * Adds a dictionary to a profile
     * @param profileId The profile ID
     * @param dictionaryId The dictionary ID to add
     */
    suspend fun addDictionaryToProfile(profileId: Long, dictionaryId: Long) {
        try {
            val entity = ProfileDictionaryEntity(profileId, dictionaryId)
            profileDictionaryDao.insert(entity)
            Log.d(TAG, "Added dictionary $dictionaryId to profile $profileId")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding dictionary to profile", e)
            throw e
        }
    }

    /**
     * Removes a dictionary from a profile
     * @param profileId The profile ID
     * @param dictionaryId The dictionary ID to remove
     */
    suspend fun removeDictionaryFromProfile(profileId: Long, dictionaryId: Long) {
        try {
            profileDictionaryDao.remove(profileId, dictionaryId)
            Log.d(TAG, "Removed dictionary $dictionaryId from profile $profileId")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing dictionary from profile", e)
            throw e
        }
    }

    /**
     * Check if a dictionary is in a profile
     * @param profileId The profile ID
     * @param dictionaryId The dictionary ID to check
     * @return true if the dictionary is in the profile, false otherwise
     */
    suspend fun isDictionaryInProfile(profileId: Long, dictionaryId: Long): Boolean {
        return try {
            profileDictionaryDao.isDictionaryInProfile(profileId, dictionaryId)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if dictionary is in profile", e)
            false
        }
    }
    
    /**
     * Removes all dictionaries from a profile
     * @param profileId The profile ID
     */
    suspend fun removeAllDictionariesFromProfile(profileId: Long) {
        try {
            profileDictionaryDao.removeAllForProfile(profileId)
            Log.d(TAG, "Removed all dictionaries from profile $profileId")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing all dictionaries from profile", e)
            throw e
        }
    }
    
    /**
     * Adds multiple dictionaries to a profile at once
     * @param profileId The profile ID
     * @param dictionaryIds List of dictionary IDs to add
     */
    suspend fun addDictionariesToProfile(profileId: Long, dictionaryIds: List<Long>) {
        try {
            val entities = dictionaryIds.map { ProfileDictionaryEntity(profileId, it) }
            profileDictionaryDao.insertAll(entities)
            Log.d(TAG, "Added ${dictionaryIds.size} dictionaries to profile $profileId")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding dictionaries to profile", e)
            throw e
        }
    }
    
    /**
     * Gets word frequency data for a specific dictionary
     * @param dictionaryId The dictionary ID to get frequency data for
     * @return List of WordFrequencyEntity for the dictionary
     */
    suspend fun getWordFrequencies(dictionaryId: Long): List<WordFrequencyEntity> {
        return try {
            wordFrequencyDao.getFrequenciesForDictionary(dictionaryId)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting word frequencies for dictionary $dictionaryId", e)
            emptyList()
        }
    }
    
    /**
     * Gets frequency for a specific word
     * @param word The word to get frequency for
     * @return WordFrequencyEntity for the word, or null if not found
     */
    suspend fun getFrequencyForWord(word: String): WordFrequencyEntity? {
        return try {
            wordFrequencyDao.getFrequencyForWord(word)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting frequency for word $word", e)
            null
        }
    }
    
    /**
     * Gets frequency for a specific word in a specific dictionary
     * @param word The word to get frequency for
     * @param dictionaryId The dictionary ID to get frequency from
     * @return WordFrequencyEntity for the word, or null if not found
     */
    suspend fun getFrequencyForWordInDictionary(word: String, dictionaryId: Long): WordFrequencyEntity? {
        return try {
            // Add debug logging
            Log.d(TAG, "Looking up frequency for word='$word' in dictionary=$dictionaryId")
            
            // Get total count of frequencies for this dictionary (debug only)
            val totalCount = wordFrequencyDao.getCountForDictionary(dictionaryId)
            Log.d(TAG, "Dictionary $dictionaryId has $totalCount frequency entries total")
            
            var result: WordFrequencyEntity? = null
            
            // First try with the improved combined query
            result = wordFrequencyDao.getFrequencyForWordInDictionary(word, dictionaryId)
            
            // If that doesn't work, try with trimmed whitespace
            if (result == null) {
                Log.d(TAG, "Standard lookup failed, trying with trimmed whitespace")
                result = wordFrequencyDao.getFrequencyForWordTrimmed(word, dictionaryId)
            }
            
            // If still no results, try a few more variations
            if (result == null) {
                // Try with common character normalizations
                val normalizedWord = normalizeJapaneseCharacters(word)
                if (normalizedWord != word) {
                    Log.d(TAG, "Trying with normalized characters: '$word' -> '$normalizedWord'")
                    result = wordFrequencyDao.getFrequencyForWordInDictionary(normalizedWord, dictionaryId)
                }
            }
            
            // Log results
            if (result != null) {
                Log.d(TAG, "Found frequency for '$word': #${result.frequency}")
            } else {
                Log.d(TAG, "No frequency data found for '$word' in dictionary $dictionaryId")
                
                // Extra debugging - dump a few frequency entries from this dictionary to check format
                val sampleEntries = wordFrequencyDao.getFrequenciesForDictionary(dictionaryId).take(3)
                if (sampleEntries.isNotEmpty()) {
                    Log.d(TAG, "Sample frequency entries from dictionary $dictionaryId:")
                    sampleEntries.forEach { 
                        Log.d(TAG, "- Word: '${it.word}', Frequency: ${it.frequency}")
                    }
                }
                
                // For debugging, check if we can find frequencies for this word in ANY dictionary
                val anyDictResult = wordFrequencyDao.getFrequencyForWord(word)
                if (anyDictResult != null) {
                    Log.d(TAG, "However, found frequency in dictionary ${anyDictResult.dictionaryId}: #${anyDictResult.frequency}")
                }
                
                // Return the actual result from the database
                // No synthetic test data
            }
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error getting frequency for word $word in dictionary $dictionaryId", e)
            null
        }
    }
    
    /**
     * Gets dictionary metadata for a specific dictionary
     * @param dictionaryId The dictionary ID to get metadata for
     * @return DictionaryMetadataEntity for the dictionary, or null if not found
     */
    suspend fun getDictionaryMetadata(dictionaryId: Long): DictionaryMetadataEntity? {
        return try {
            dictionaryMetadataDao.getDictionaryById(dictionaryId)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting dictionary metadata for dictionary $dictionaryId", e)
            null
        }
    }
    
    /**
     * Saves word frequency data
     * @param entity The WordFrequencyEntity to save
     * @return The ID of the saved entity
     */
    suspend fun saveWordFrequency(entity: WordFrequencyEntity): Long {
        return try {
            wordFrequencyDao.insert(entity)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving word frequency", e)
            -1
        }
    }
    
    /**
     * Saves multiple word frequency entities at once
     * @param entities List of WordFrequencyEntity to save
     * @return List of IDs for the saved entities
     */
    suspend fun saveWordFrequencies(entities: List<WordFrequencyEntity>): List<Long> {
        return try {
            wordFrequencyDao.insertAll(entities)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving word frequencies", e)
            emptyList()
        }
    }
    
    /**
     * Gets the total count of word frequency entries
     * @return The count of word frequency entries
     */
    suspend fun getWordFrequencyCount(): Int {
        return try {
            wordFrequencyDao.getTotalCount()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting word frequency count", e)
            0
        }
    }
    
    /**
     * Gets the count of word frequency entries for a specific dictionary
     * @param dictionaryId The dictionary ID to get count for
     * @return The count of word frequency entries for the dictionary
     */
    suspend fun getWordFrequencyCount(dictionaryId: Long): Int {
        return try {
            wordFrequencyDao.getCountForDictionary(dictionaryId)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting word frequency count for dictionary $dictionaryId", e)
            0
        }
    }
    
    /**
     * Imports frequency data during dictionary import
     * This method should be called after importing dictionary entries
     * @param entries List of word frequencies to import
     * @return The number of entries imported
     */
    suspend fun importWordFrequencies(entries: List<WordFrequencyEntity>): Int {
        return try {
            if (entries.isEmpty()) {
                Log.w(TAG, "importWordFrequencies called with empty list")
                return 0
            }
            
            // Log sample of entries being imported
            if (entries.isNotEmpty()) {
                val sampleEntry = entries.first()
                Log.d(TAG, "Sample frequency entry: word='${sampleEntry.word}', " +
                      "dictionaryId=${sampleEntry.dictionaryId}, " +
                      "frequency=${sampleEntry.frequency}")
            }
            
            val result = wordFrequencyDao.insertAll(entries)
            Log.d(TAG, "Successfully imported ${entries.size} word frequencies to database")
            return entries.size
        } catch (e: Exception) {
            Log.e(TAG, "Error importing word frequencies: ${e.message}", e)
            throw e
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

/**
 * DAO for dictionary operations
 */
@Dao
interface DictionaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<DictionaryEntryEntity>): List<Long>

    @Query("SELECT e.* FROM dictionary_entries e " +
           "JOIN dictionary_metadata m ON e.dictionaryId = m.id " +
           "LEFT JOIN word_frequencies wf ON e.term = wf.word AND e.dictionaryId = wf.dictionaryId " +
           "WHERE LOWER(e.term) LIKE LOWER(:query) OR LOWER(e.reading) LIKE LOWER(:query) OR LOWER(e.definition) LIKE LOWER(:query) " +
           "ORDER BY " +
           "CASE " +
           "  WHEN LOWER(e.term) = LOWER(:exactQuery) THEN 0 " + // Exact term match gets highest priority
           "  WHEN LOWER(e.term) LIKE LOWER(:exactQuery) || '%' THEN 1 " + // Term starts with query
           "  WHEN LOWER(e.reading) = LOWER(:exactQuery) THEN 2 " + // Exact reading match
           "  WHEN LOWER(e.reading) LIKE LOWER(:exactQuery) || '%' THEN 3 " + // Reading starts with query
           "  ELSE 4 " + // Everything else (matches in definition, etc.)
           "END, " +
           "CASE WHEN wf.frequency IS NOT NULL THEN 0 ELSE 1 END, " + // Prioritize entries with frequency data
           "CASE WHEN wf.frequency IS NOT NULL THEN wf.frequency ELSE 999999 END, " + // Sort by frequency (lower numbers first)
           "m.priority DESC, e.id DESC")
    suspend fun searchEntriesOrderedByPriority(query: String, exactQuery: String = query): List<DictionaryEntryEntity>

    @Query("SELECT e.* FROM dictionary_entries e " +
           "JOIN dictionary_metadata m ON e.dictionaryId = m.id " +
           "JOIN profile_dictionaries pd ON e.dictionaryId = pd.dictionaryId " +
           "LEFT JOIN word_frequencies wf ON e.term = wf.word AND e.dictionaryId = wf.dictionaryId " +
           "WHERE (LOWER(e.term) LIKE LOWER(:query) OR LOWER(e.reading) LIKE LOWER(:query) OR LOWER(e.definition) LIKE LOWER(:query)) " +
           "AND pd.profileId = :profileId " +
           "ORDER BY " +
           "CASE " +
           "  WHEN LOWER(e.term) = LOWER(:exactQuery) THEN 0 " + // Exact term match gets highest priority
           "  WHEN LOWER(e.term) LIKE LOWER(:exactQuery) || '%' THEN 1 " + // Term starts with query
           "  WHEN LOWER(e.reading) = LOWER(:exactQuery) THEN 2 " + // Exact reading match
           "  WHEN LOWER(e.reading) LIKE LOWER(:exactQuery) || '%' THEN 3 " + // Reading starts with query
           "  ELSE 4 " + // Everything else (matches in definition, etc.)
           "END, " +
           "CASE WHEN wf.frequency IS NOT NULL THEN 0 ELSE 1 END, " + // Prioritize entries with frequency data
           "CASE WHEN wf.frequency IS NOT NULL THEN wf.frequency ELSE 999999 END, " + // Sort by frequency (lower numbers first)
           "m.priority DESC, e.id DESC")
    suspend fun searchEntriesForProfile(query: String, exactQuery: String = query, profileId: Long): List<DictionaryEntryEntity>

    @Query("SELECT e.* FROM dictionary_entries e " +
"JOIN dictionary_metadata m ON e.dictionaryId = m.id " +
"LEFT JOIN word_frequencies wf ON e.term = wf.word AND e.dictionaryId = wf.dictionaryId " +
"WHERE e.term LIKE :query OR e.reading LIKE :query " +  /* Using index without LOWER for better performance */
"ORDER BY " +
"CASE " +
"  WHEN e.term = :exactQuery THEN 0 " + // Exact term match gets highest priority
"  WHEN e.term LIKE :exactQuery || '%' THEN 1 " + // Term starts with query
"  WHEN e.reading = :exactQuery THEN 2 " + // Exact reading match
"  WHEN e.reading LIKE :exactQuery || '%' THEN 3 " + // Reading starts with query
"  ELSE 4 " + // Everything else
"END, " +
"CASE WHEN wf.frequency IS NOT NULL THEN 0 ELSE 1 END, " + // Prioritize entries with frequency data
"CASE WHEN wf.frequency IS NOT NULL THEN wf.frequency ELSE 999999 END, " + // Sort by frequency (lower is better)
"m.priority DESC, e.id DESC LIMIT 50")
    suspend fun fastSearchEntries(query: String, exactQuery: String = query): List<DictionaryEntryEntity>
    
    @Query("SELECT e.* FROM dictionary_entries e " +
"JOIN profile_dictionaries pd ON e.dictionaryId = pd.dictionaryId " +
"JOIN dictionary_metadata m ON e.dictionaryId = m.id " +
"LEFT JOIN word_frequencies wf ON e.term = wf.word AND e.dictionaryId = wf.dictionaryId " +
"WHERE (e.term LIKE :query OR e.reading LIKE :query) " +  /* Using index without LOWER for better performance */
"AND pd.profileId = :profileId " +
"ORDER BY " +
"CASE " +
"  WHEN e.term = :exactQuery THEN 0 " + // Exact term match gets highest priority
"  WHEN e.term LIKE :exactQuery || '%' THEN 1 " + // Term starts with query
"  WHEN e.reading = :exactQuery THEN 2 " + // Exact reading match
"  WHEN e.reading LIKE :exactQuery || '%' THEN 3 " + // Reading starts with query
"  ELSE 4 " + // Everything else
"END, " +
"CASE WHEN wf.frequency IS NOT NULL THEN 0 ELSE 1 END, " + // Prioritize entries with frequency data
"CASE WHEN wf.frequency IS NOT NULL THEN wf.frequency ELSE 999999 END, " + // Sort by frequency (lower is better)
"m.priority DESC, e.id DESC LIMIT 50")
    suspend fun fastSearchEntriesForProfile(query: String, exactQuery: String = query, profileId: Long): List<DictionaryEntryEntity>
    
    /* Bulk search for multiple terms at once */
    @Query("SELECT * FROM dictionary_entries WHERE term IN (:terms) LIMIT 100")
    suspend fun bulkSearchByExactTerms(terms: List<String>): List<DictionaryEntryEntity>
    
    /* Bulk search for multiple terms at once, limited to a specific profile */
    @Query("SELECT e.* FROM dictionary_entries e " +
           "JOIN profile_dictionaries pd ON e.dictionaryId = pd.dictionaryId " +
           "WHERE e.term IN (:terms) AND pd.profileId = :profileId LIMIT 100")
    suspend fun bulkSearchByExactTermsForProfile(terms: List<String>, profileId: Long): List<DictionaryEntryEntity>

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
    
    @Query("SELECT * FROM dictionary_entries WHERE dictionaryId IN (:dictionaryIds) ORDER BY id DESC LIMIT :limit")
    suspend fun getRecentEntriesFromDictionaries(limit: Int, dictionaryIds: List<Long>): List<DictionaryEntryEntity>
}

/**
 * Room database
 */
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



/**
 * Migration from version 3 to 4 - adds isHtmlContent column to dictionary_entries table
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add isHtmlContent column with default value false
        database.execSQL("ALTER TABLE dictionary_entries ADD COLUMN isHtmlContent INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Migration from version 4 to 5 - adds exported_words table
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create exported_words table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS exported_words (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                word TEXT NOT NULL,
                dictionaryId INTEGER NOT NULL,
                ankiDeckId INTEGER NOT NULL,
                ankiNoteId INTEGER,
                dateAdded INTEGER NOT NULL
            )
        """)
        
        // Create index on word column
        database.execSQL("CREATE INDEX IF NOT EXISTS index_exported_words_word ON exported_words (word)")
    }
}

/**
 * Migration from version 5 to 6 - adds frequency column to dictionary_entries table
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add frequency column to dictionary_entries table (nullable INTEGER)
        database.execSQL("ALTER TABLE dictionary_entries ADD COLUMN frequency INTEGER")
    }
}
