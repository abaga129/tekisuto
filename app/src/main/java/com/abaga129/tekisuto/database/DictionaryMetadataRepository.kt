package com.abaga129.tekisuto.database

import android.content.Context
import android.util.Log

/**
 * Repository for dictionary metadata operations
 */
class DictionaryMetadataRepository(context: Context) : BaseRepository(context) {

    companion object {
        @Volatile
        private var INSTANCE: DictionaryMetadataRepository? = null

        fun getInstance(context: Context): DictionaryMetadataRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = DictionaryMetadataRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    private val dictionaryMetadataDao = database.dictionaryMetadataDao()
    private val dictionaryDao = database.dictionaryDao()

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
     * Gets dictionary entry count for a specific dictionary
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
     * Clears all dictionary metadata
     */
    suspend fun clearAllDictionaryMetadata() {
        try {
            dictionaryMetadataDao.deleteAll()
            Log.d(TAG, "All dictionary metadata cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing all dictionary metadata", e)
            throw e
        }
    }
}
