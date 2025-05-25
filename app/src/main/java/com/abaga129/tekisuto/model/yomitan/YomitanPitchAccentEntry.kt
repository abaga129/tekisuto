package com.abaga129.tekisuto.model.yomitan

import android.util.Log
import com.abaga129.tekisuto.database.WordPitchAccentEntity

/**
 * Represents a pitch accent entry in a Yomitan dictionary.
 * 
 * In Yomitan JSON, pitch accent entries are typically stored as term meta entries 
 * with a "pitch" type identifier.
 * 
 * Common formats:
 * 
 * Format 1 (basic):
 * [0] - Term (string)
 * [1] - Type identifier = "pitch" (string)
 * [2] - Pitch accent pattern (either a number, or a formatted string)
 *
 * Format 2 (detailed):
 * [0] - Term (string)
 * [1] - Type identifier = "pitch" (string)
 * [2] - Reading (string)
 * [3] - Pitch accent pattern (string or number)
 * 
 * Format 3 (detailed with tags):
 * [0] - Term (string)
 * [1] - Type identifier = "pitch" (string)
 * [2] - Reading (string)
 * [3] - Pitch accent pattern (string or number)
 * [4] - Tags (array of strings, might contain additional information)
 */
class YomitanPitchAccentEntry {
    companion object {
        private const val TAG = "YomitanPitchAccentEntry"
        
        /**
         * Parses a JSON array into a WordPitchAccentEntity
         * 
         * @param jsonArray The JSON array representing the pitch accent entry
         * @param dictionaryId The ID of the dictionary this entry belongs to
         * @return A WordPitchAccentEntity created from the JSON data, or null if parsing fails
         */
        fun fromJsonArray(jsonArray: List<Any?>, dictionaryId: Long): WordPitchAccentEntity? {
            // Extract term (should be at position 0)
            val term = jsonArray.getOrNull(0) as? String ?: return null
            
            // Check if this is a pitch accent entry (position 1 should be "pitch")
            val typeId = jsonArray.getOrNull(1) as? String ?: return null
            if (typeId != "pitch") {
                return null
            }
            
            // Determine format based on array length and content types
            val format = when {
                jsonArray.size == 3 -> 1 // Basic format: [term, "pitch", pattern]
                jsonArray.size >= 4 && jsonArray.getOrNull(2) is String -> 2 // Detailed format with reading
                else -> 0 // Unknown format
            }
            
            if (format == 0) {
                Log.w(TAG, "Unknown pitch accent format: $jsonArray")
                return null
            }
            
            var reading = ""
            var pitchAccent = ""
            
            when (format) {
                1 -> {
                    // Basic format: [term, "pitch", pattern]
                    reading = term
                    pitchAccent = jsonArray.getOrNull(2)?.toString() ?: ""
                }
                2 -> {
                    // Detailed format: [term, "pitch", reading, pattern, ...]
                    reading = jsonArray.getOrNull(2) as? String ?: term
                    pitchAccent = jsonArray.getOrNull(3)?.toString() ?: ""
                }
            }
            
            // Log a sample of the parsed pitch accent data for debugging
            Log.d(TAG, "Parsed pitch accent '$pitchAccent' for term: $term, reading: $reading (Format $format)")
            
            // If we don't have a valid pitch accent pattern, return null
            if (pitchAccent.isEmpty()) {
                return null
            }
            
            return WordPitchAccentEntity(
                dictionaryId = dictionaryId,
                word = term,
                reading = reading,
                pitchAccent = pitchAccent
            )
        }
    }
}