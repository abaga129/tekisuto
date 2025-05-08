package com.abaga129.tekisuto.database

import android.content.Context
import android.util.Log

/**
 * Repository for dictionary entry operations
 */
class DictionaryEntryRepository(context: Context) : BaseRepository(context) {

    companion object {
        @Volatile
        private var INSTANCE: DictionaryEntryRepository? = null

        fun getInstance(context: Context): DictionaryEntryRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = DictionaryEntryRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    private val dictionaryDao = database.dictionaryDao()

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
     * Clears all dictionary entries
     */
    suspend fun clearAllDictionaryEntries() {
        try {
            dictionaryDao.deleteAll()
            Log.d(TAG, "All dictionary entries cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing all dictionary entries", e)
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
}
