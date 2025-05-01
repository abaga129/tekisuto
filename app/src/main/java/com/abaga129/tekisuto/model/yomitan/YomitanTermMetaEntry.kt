package com.abaga129.tekisuto.model.yomitan

import com.abaga129.tekisuto.database.WordFrequencyEntity

/**
 * Represents a term meta entry in a Yomitan dictionary.
 * These entries typically contain frequency information.
 * 
 * In Yomitan JSON, term meta entries are stored as JSON arrays rather than objects.
 * Each array element has a specific meaning based on its position:
 * 
 * [0] - Term (string)
 * [1] - Reading (string or null)
 * [2] - Tags (array of strings, usually containing frequency info)
 * [3] - Rules (obsolete, array of strings)
 * [4] - Number (numeric value, often 0)
 * [5] - Score/Frequency (number)
 * [6] - Sequence number (number or null)
 */
class YomitanTermMetaEntry {
    companion object {
        /**
         * Parses a JSON array into a WordFrequencyEntity
         * 
         * @param jsonArray The JSON array representing the term meta entry
         * @param dictionaryId The ID of the dictionary this entry belongs to
         * @return A WordFrequencyEntity created from the JSON data, or null if frequency info not found
         */
        fun fromJsonArray(jsonArray: List<Any?>, dictionaryId: Long): WordFrequencyEntity? {
            // Extract values from the array
            val term = jsonArray.getOrNull(0) as? String ?: return null
            
            // Try to get frequency from position 5 (direct frequency value)
            var frequency: Int? = null
            
            val scoreFrequency = jsonArray.getOrNull(5)
            if (scoreFrequency is Number) {
                frequency = scoreFrequency.toInt()
            } else if (scoreFrequency is String) {
                // Try to parse string to int
                frequency = scoreFrequency.toIntOrNull()
            }
            
            // If frequency not found at position 4, try to extract from tags
            if (frequency == null) {
                val tags = (jsonArray.getOrNull(2) as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                frequency = extractFrequencyFromTags(tags)
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