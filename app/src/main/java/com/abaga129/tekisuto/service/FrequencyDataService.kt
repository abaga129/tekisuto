package com.abaga129.tekisuto.service

import android.util.Log
import com.abaga129.tekisuto.database.DictionaryEntryEntity
import com.abaga129.tekisuto.database.DictionaryRepository
import com.abaga129.tekisuto.database.WordFrequencyEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Service for handling dictionary frequency data operations
 */
class FrequencyDataService(private val repository: DictionaryRepository) {
    
    companion object {
        private const val TAG = "FrequencyDataService"
    }

    /**
     * Retrieve frequency data for a dictionary entry
     * 
     * @param entry The dictionary entry to retrieve frequency data for
     * @return The WordFrequencyEntity if found, null otherwise
     */
    suspend fun getFrequencyForEntry(entry: DictionaryEntryEntity): WordFrequencyEntity? = withContext(Dispatchers.IO) {
        try {
            // First try to find the frequency in any dictionary
            val frequencyData = repository.getFrequencyForWord(entry.term)
            
            // Log the frequency data for debugging
            if (frequencyData != null) {
                Log.d(TAG, "✓ Retrieved frequency data for '${entry.term}': #${frequencyData.frequency} from dictionary ${frequencyData.dictionaryId}")
            } else {
                Log.d(TAG, "✗ No frequency data found for '${entry.term}' in any dictionary")
                
                // Try alternative lookup approaches
                val alternativeData = tryAlternativeFrequencyLookup(entry)
                if (alternativeData != null) {
                    Log.d(TAG, "✓ Found frequency data using alternative lookup for '${entry.term}': #${alternativeData.frequency}")
                    return@withContext alternativeData
                }
                
                // Log dictionary frequency stats for debugging
                logDictionaryFrequencyStats(entry.dictionaryId)
            }
            
            return@withContext frequencyData
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving frequency data for '${entry.term}'", e)
            return@withContext null
        }
    }
    
    /**
     * Try alternative approaches to find frequency data for a word
     * This includes trying variations of the word, other dictionaries, etc.
     */
    private suspend fun tryAlternativeFrequencyLookup(entry: DictionaryEntryEntity): WordFrequencyEntity? {
        // Try with reading if available
        if (entry.reading.isNotBlank() && entry.reading != entry.term) {
            val readingFrequencyData = repository.getFrequencyForWord(entry.reading)
            if (readingFrequencyData != null) {
                Log.d(TAG, "Found frequency data using reading '${entry.reading}' instead of term '${entry.term}'")
                return readingFrequencyData
            }
        }
        
        // Try normalized variants of the term
        val normalizedTerm = normalizeJapaneseCharacters(entry.term)
        if (normalizedTerm != entry.term) {
            val normalizedFrequencyData = repository.getFrequencyForWord(normalizedTerm)
            if (normalizedFrequencyData != null) {
                Log.d(TAG, "Found frequency data using normalized term '${normalizedTerm}' instead of '${entry.term}'")
                return normalizedFrequencyData
            }
        }
        
        return null
    }
    
    /**
     * Log frequency statistics for a dictionary
     */
    private suspend fun logDictionaryFrequencyStats(dictionaryId: Long) {
        val count = repository.getWordFrequencyCount(dictionaryId)
        Log.d(TAG, "Dictionary $dictionaryId has $count frequency entries")
        
        // Get a few sample entries to verify the data format
        if (count > 0) {
            val samples = repository.getWordFrequencies(dictionaryId).take(3)
            samples.forEach { freq ->
                Log.d(TAG, "Sample frequency entry: word='${freq.word}', frequency=#${freq.frequency}")
            }
        }
    }
    
    /**
     * Bulk retrieve frequency data for multiple entries
     * 
     * @param entries The list of dictionary entries to retrieve frequency data for
     * @return A map of entry IDs to their frequency data (if found)
     */
    suspend fun getFrequenciesForEntries(entries: List<DictionaryEntryEntity>): Map<Long, WordFrequencyEntity> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<Long, WordFrequencyEntity>()
        
        try {
            // Get all unique terms from all entries
            val allTerms = entries.map { it.term }.distinct()
            
            // Log the lookup attempt
            Log.d(TAG, "Bulk frequency lookup for ${allTerms.size} unique terms")
            
            // Create a map of terms to their frequency data
            val termToFrequencyMap = mutableMapOf<String, WordFrequencyEntity>()
            
            // Look up each term in any dictionary
            allTerms.forEach { term ->
                val frequencyData = repository.getFrequencyForWord(term)
                if (frequencyData != null) {
                    termToFrequencyMap[term] = frequencyData
                }
            }
            
            // Map frequency data to entries by term
            entries.forEach { entry ->
                val frequencyData = termToFrequencyMap[entry.term]
                if (frequencyData != null) {
                    result[entry.id] = frequencyData
                }
            }
            
            // Log results
            Log.d(TAG, "Found frequency data for ${result.size}/${entries.size} entries")
            
            return@withContext result
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving bulk frequency data", e)
            return@withContext emptyMap<Long, WordFrequencyEntity>()
        }
    }
    
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
     * Check if a dictionary has frequency data
     * 
     * @param dictionaryId The dictionary ID to check
     * @return true if the dictionary has frequency data, false otherwise
     */
    suspend fun dictionaryHasFrequencyData(dictionaryId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val count = repository.getWordFrequencyCount(dictionaryId)
            return@withContext count > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if dictionary has frequency data", e)
            return@withContext false
        }
    }
    
    /**
     * Get total frequency count across all dictionaries
     * 
     * @return The total number of frequency entries in the database
     */
    suspend fun getTotalFrequencyCount(): Int = withContext(Dispatchers.IO) {
        try {
            return@withContext repository.getWordFrequencyCount()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting total frequency count", e)
            return@withContext 0
        }
    }
}