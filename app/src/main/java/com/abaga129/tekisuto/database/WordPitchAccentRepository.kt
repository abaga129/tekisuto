package com.abaga129.tekisuto.database

import android.content.Context
import android.util.Log

/**
 * Repository for word pitch accent operations
 */
class WordPitchAccentRepository(context: Context) : BaseRepository(context) {

    companion object {
        private const val TAG = "WordPitchAccentRepo"
        
        @Volatile
        private var INSTANCE: WordPitchAccentRepository? = null

        fun getInstance(context: Context): WordPitchAccentRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = WordPitchAccentRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    private val wordPitchAccentDao = database.wordPitchAccentDao()

    /**
     * Gets pitch accent for a specific word and reading combination
     * @param word The word to get pitch accent for
     * @param reading The reading to match
     * @return WordPitchAccentEntity for the word, or null if not found
     */
    suspend fun getPitchAccentForWordAndReading(word: String, reading: String): WordPitchAccentEntity? {
        return try {
            wordPitchAccentDao.getPitchAccentByWordAndReading(word, reading)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting pitch accent for word $word with reading $reading", e)
            null
        }
    }

    /**
     * Imports pitch accent data during dictionary import
     * This method should be called after importing dictionary entries
     * @param entries List of word pitch accents to import
     * @return The number of entries imported
     */
    suspend fun importWordPitchAccents(entries: List<WordPitchAccentEntity>): Int {
        return try {
            if (entries.isEmpty()) {
                Log.w(TAG, "importWordPitchAccents called with empty list")
                return 0
            }

            // Log sample of entries being imported
            if (entries.isNotEmpty()) {
                val sampleEntry = entries.first()
                Log.d(TAG, "Sample pitch accent entry: word='${sampleEntry.word}', " +
                        "reading='${sampleEntry.reading}', " +
                        "dictionaryId=${sampleEntry.dictionaryId}, " +
                        "pitchAccent=${sampleEntry.pitchAccent}")
            }

            val result = wordPitchAccentDao.insertAll(entries)
            Log.d(TAG, "Successfully imported ${entries.size} word pitch accents to database")
            return entries.size
        } catch (e: Exception) {
            Log.e(TAG, "Error importing word pitch accents: ${e.message}", e)
            throw e
        }
    }
}