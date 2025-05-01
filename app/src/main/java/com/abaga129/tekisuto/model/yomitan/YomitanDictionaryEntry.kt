package com.abaga129.tekisuto.model.yomitan

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Types
import com.abaga129.tekisuto.database.DictionaryEntryEntity
import com.abaga129.tekisuto.util.StructuredContentHtmlConverter
import com.abaga129.tekisuto.model.yomitan.YomitanContentParser

/**
 * Represents a dictionary entry in a Yomitan dictionary term bank.
 * 
 * In Yomitan JSON, dictionary entries are stored as JSON arrays rather than objects.
 * Each array element has a specific meaning based on its position:
 * 
 * [0] - Term (string)
 * [1] - Reading (string or null)
 * [2] - Tags (array of strings)
 * [3] - Rules (obsolete, array of strings)
 * [4] - Number (numeric value, often 0)
 * [5] - Definitions (array of objects or strings)
 * [6] - Number (numeric value, often 0)
 * [7] - Reading Tags (array of strings)
 */
class YomitanDictionaryEntry {
    companion object {
        /**
         * Parses a JSON array into a YomitanDictionaryEntry
         * 
         * @param jsonArray The JSON array representing the entry
         * @param dictionaryId The ID of the dictionary this entry belongs to
         * @return A DictionaryEntryEntity created from the JSON data
         */
        fun fromJsonArray(jsonArray: List<Any?>, dictionaryId: Long): DictionaryEntryEntity {
            // Extract values from the array
            val term = jsonArray.getOrNull(0) as? String ?: ""
            val reading = jsonArray.getOrNull(1) as? String ?: ""
            
            // Handle tags (element 2 and possibly 6, 7)
            val entryTags = (jsonArray.getOrNull(2) as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val termTags = (jsonArray.getOrNull(6) as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val readingTags = (jsonArray.getOrNull(7) as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            
            // Combine all tags into one list
            val allTags = (entryTags + termTags + readingTags).distinct()
            
            // Extract definitions using the dedicated parser
            val definitionsRaw = jsonArray.getOrNull(5)  // Updated index from 4 to 5
            val (definition, isHtmlContent) = YomitanContentParser.parseDefinitions(definitionsRaw)
            
            // Extract part of speech from tags if available
            val partOfSpeech = extractPartOfSpeech(allTags)
            
            return DictionaryEntryEntity(
                dictionaryId = dictionaryId,
                term = term,
                reading = reading,
                definition = definition,
                partOfSpeech = partOfSpeech,
                tags = allTags,
                isHtmlContent = isHtmlContent
            )
        }
        
        /**
         * Extract part of speech information from tags
         */
        private fun extractPartOfSpeech(tags: List<String>): String {
            // Common part of speech tags
            val posMarkers = listOf(
                "v1", "v5", "vk", "vs", "adj-i", "adj-na", "n", "adv", "prt", 
                "conj", "pn", "aux", "exp", "int", "prefix", "suffix"
            )
            
            // Filter tags that are likely parts of speech
            val posTags = tags.filter { tag ->
                posMarkers.any { marker -> 
                    tag.equals(marker, ignoreCase = true) || 
                    tag.startsWith("$marker-", ignoreCase = true) ||
                    tag.contains("pos:") ||
                    tag.contains("part-of-speech")
                }
            }
            
            return posTags.joinToString(", ")
        }
    }
}