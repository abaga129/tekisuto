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
 *
 * Format 3 (enhanced frequency with reading):
 * [0] - Term (string)
 * [1] - Type (string, usually "freq" for frequency)
 * [2] - Object containing:
 *       - reading (string, e.g. "いちども")
 *       - frequency (object with "value" and optionally "displayValue")
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
            var reading: String? = null
            var displayValue: String? = null
            
            if (isFrequencyFormat) {
                // Handle Format 2 and 3: [term, "freq", frequencyData]
                val freqData = jsonArray.getOrNull(2)
                
                when (freqData) {
                    is Int -> {
                        // Simple integer frequency
                        frequency = freqData
                    }
                    is Number -> {
                        // Numeric frequency
                        frequency = freqData.toInt()
                    }
                    is String -> {
                        // String frequency
                        frequency = freqData.toIntOrNull()
                    }
                    is Map<*, *> -> {
                        // Handle enhanced frequency format with reading
                        // Check if this is Format 3 with reading
                        val readingValue = freqData["reading"] as? String
                        if (readingValue != null) {
                            reading = readingValue
                        }
                        
                        // Extract frequency information
                        val frequencyInfo = freqData["frequency"]
                        when (frequencyInfo) {
                            is Int -> {
                                frequency = frequencyInfo
                            }
                            is Number -> {
                                frequency = frequencyInfo.toInt()
                            }
                            is Map<*, *> -> {
                                // Complex frequency object with value and displayValue
                                val value = frequencyInfo["value"]
                                frequency = when (value) {
                                    is Int -> value
                                    is Number -> value.toInt()
                                    is String -> value.toIntOrNull()
                                    else -> null
                                }
                                
                                // Extract displayValue if available
                                val displayVal = frequencyInfo["displayValue"] as? String
                                if (displayVal != null) {
                                    displayValue = displayVal
                                }
                            }
                            else -> {
                                // Fallback: try to parse frequency directly from the map
                                val value = freqData["value"]
                                frequency = when (value) {
                                    is Int -> value
                                    is Number -> value.toInt()
                                    is String -> value.toIntOrNull()
                                    else -> null
                                }
                                
                                // Try to get displayValue directly from freqData
                                val displayVal = freqData["displayValue"] as? String
                                if (displayVal != null) {
                                    displayValue = displayVal
                                }
                            }
                        }
                    }
                    else -> null
                }
                
                // Log a sample of the parsed frequency data for debugging
                if (frequency != null) {
                    Log.d(TAG, "Parsed frequency $frequency for term: $term (Format 2/3), reading: $reading, displayValue: $displayValue")
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
                
                // For Format 1, reading might be at position 1
                val readingValue = jsonArray.getOrNull(1) as? String
                if (!readingValue.isNullOrBlank()) {
                    reading = readingValue
                }
                
                // Log a sample of the parsed frequency data for debugging
                if (frequency != null) {
                    Log.d(TAG, "Parsed frequency $frequency for term: $term (Format 1), reading: $reading")
                }
            }
            
            // If we still don't have a frequency value, return null
            if (frequency == null) {
                return null
            }
            
            return WordFrequencyEntity(
                dictionaryId = dictionaryId,
                word = term,
                reading = reading,
                frequency = frequency,
                displayValue = displayValue
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
