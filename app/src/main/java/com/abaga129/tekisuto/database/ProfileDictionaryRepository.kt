package com.abaga129.tekisuto.database

import android.content.Context
import android.util.Log

/**
 * Repository for profile-dictionary associations
 */
class ProfileDictionaryRepository(context: Context) : BaseRepository(context) {

    companion object {
        @Volatile
        private var INSTANCE: ProfileDictionaryRepository? = null

        fun getInstance(context: Context): ProfileDictionaryRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = ProfileDictionaryRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    private val profileDictionaryDao = database.profileDictionaryDao()

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
            
            // If there's a database error, it might be related to a column issue
            // Try to explicitly handle the case where ankiFieldPitchAccent might be missing
            if (e.message?.contains("ankiFieldPitchAccent") == true) {
                Log.d(TAG, "Attempting to recover from ankiFieldPitchAccent column error")
                try {
                    // Execute the SQL to add the column directly 
                    database.openHelper.writableDatabase.execSQL("""
                        ALTER TABLE profiles ADD COLUMN IF NOT EXISTS ankiFieldPitchAccent INTEGER NOT NULL DEFAULT -1
                    """)
                    
                    // Try the query again
                    return profileDictionaryDao.getDictionaryMetadataForProfile(profileId)
                } catch (e2: Exception) {
                    Log.e(TAG, "Recovery attempt failed", e2)
                }
            }
            
            // Return empty list as fallback
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
}
