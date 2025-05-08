package com.abaga129.tekisuto.database

import android.content.Context
import android.util.Log

/**
 * Repository for word frequency operations
 */
class WordFrequencyRepository(context: Context) : BaseRepository(context) {

    companion object {
        @Volatile
        private var INSTANCE: WordFrequencyRepository? = null

        fun getInstance(context: Context): WordFrequencyRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = WordFrequencyRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    private val wordFrequencyDao = database.wordFrequencyDao()

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
            }

            result
        } catch (e: Exception) {
            Log.e(TAG, "Error getting frequency for word $word in dictionary $dictionaryId", e)
            null
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
