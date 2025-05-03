package com.abaga129.tekisuto.model.yomitan

import android.util.Log
import com.abaga129.tekisuto.database.WordFrequencyEntity

/**
 * Represents a term meta entry in a Yomitan dictionary.
 * These entries typically contain frequency information.
 * 
 * In Yomitan JSON, term meta entries are stored as JSON arrays rather than objects.
 * There are several possible formats:
 * 
 * Format 1 (traditional):
 * [0] - Term (string)
 * [1] - Reading (string or null)
 * [2] - Tags (array of strings, usually containing frequency info)
 * [3] - Rules (obsolete, array of strings)
 * [4] - Number (numeric value, often 0)
 * [5] - Score/Frequency (number)
 * [6] - Sequence number (number or null)
 *
 * Format 2 (frequency-specific):
 * [0] - Term (string)
 * [1] - Type (string, usually "freq" for frequency)
 * [2] - Frequency value (either number or object with "value" property)
 */
class YomitanTermMetaEntry {
    companion object {
        private const val TAG = "YomitanTermMetaEntry"
        
        /**
         * Parses a JSON array into a WordFrequencyEntity
         * 
         * @param jsonArray The JSON array representing the term meta entry
         * @param dictionaryId The ID of the dictionary this entry belongs to
         * @return A WordFrequencyEntity created from the JSON data, or null if frequency info not found
         */
        fun fromJsonArray(jsonArray: List<Any?>, dictionaryId: Long): WordFrequencyEntity? {
            // Extract term (should be at position 0 in all formats)
            val term = jsonArray.getOrNull(0) as? String ?: return null
            
            // Check which format we're dealing with
            val isFrequencyFormat = jsonArray.size >= 3 && jsonArray.getOrNull(1) == "freq"
            
            var frequency: Int? = null
            
            if (isFrequencyFormat) {
                // Handle Format 2: [term, "freq", frequencyValue]
                val freqData = jsonArray.getOrNull(2)
                frequency = when(freqData) {
                    is Int -> freqData
                    is Number -> freqData.toInt()
                    is String -> freqData.toIntOrNull()
                    is Map<*, *> -> {
                        // Handle case where frequency is a JSON object with a "value" property
                        val value = freqData["value"]
                        when(value) {
                            is Int -> value
                            is Number -> value.toInt()
                            is String -> value.toIntOrNull()
                            else -> null
                        }
                    }
                    else -> null
                }
                
                // Log a sample of the parsed frequency data for debugging
                if (frequency != null) {
                    Log.d(TAG, "Parsed frequency $frequency for term: $term (Format 2)")
                }
            } else {
                // Handle Format 1 (traditional format)
                // Try to get frequency from position 5 (direct frequency value)
                val scoreFrequency = jsonArray.getOrNull(5)
                if (scoreFrequency is Number) {
                    frequency = scoreFrequency.toInt()
                } else if (scoreFrequency is String) {
                    // Try to parse string to int
                    frequency = scoreFrequency.toIntOrNull()
                }
                
                // If frequency not found at position 5, try to extract from tags at position 2
                if (frequency == null) {
                    val tags = (jsonArray.getOrNull(2) as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    frequency = extractFrequencyFromTags(tags)
                }
                
                // Log a sample of the parsed frequency data for debugging
                if (frequency != null) {
                    Log.d(TAG, "Parsed frequency $frequency for term: $term (Format 1)")
                }
            }
            
            // If we still don't have a frequency value, return null
            if (frequency == null) {
                return null
            }
            
            return WordFrequencyEntity(
                dictionaryId = dictionaryId,
                word = term,
                frequency = frequency
            )
        }
        
        /**
         * Extract frequency information from tags
         */
        private fun extractFrequencyFromTags(tags: List<String>): Int? {
            // Common patterns for frequency in tags
            val frequencyPatterns = listOf(
                Regex("^freq:([0-9]+)$"),
                Regex("^frequency:([0-9]+)$"),
                Regex("^rank:([0-9]+)$"),
                Regex("^([0-9]+)$")
            )
            
            for (tag in tags) {
                for (pattern in frequencyPatterns) {
                    val matchResult = pattern.find(tag)
                    if (matchResult != null) {
                        val frequencyStr = matchResult.groupValues[1]
                        return frequencyStr.toIntOrNull()
                    }
                }
            }
            
            return null
        }
    }
}