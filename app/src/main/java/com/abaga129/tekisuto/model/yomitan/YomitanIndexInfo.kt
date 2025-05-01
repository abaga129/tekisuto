package com.abaga129.tekisuto.model.yomitan

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Represents the information from the index.json file in a Yomitan dictionary.
 * This class maps to the JSON structure found in the index.json file.
 */
@JsonClass(generateAdapter = true)
data class YomitanIndexInfo(
    // Required fields
    @Json(name = "title")
    val title: String = "",
    
    @Json(name = "format")
    val format: Int = 3,
    
    @Json(name = "revision")
    val revision: String = "",
    
    @Json(name = "sequenced")
    val sequenced: Boolean = false,
    
    // Optional fields
    @Json(name = "author")
    val author: String? = null,
    
    @Json(name = "url")
    val url: String? = null,
    
    @Json(name = "description")
    val description: String? = null,
    
    @Json(name = "attribution")
    val attribution: String? = null,
    
    @Json(name = "sourceLanguage")
    val sourceLanguage: String? = null,
    
    @Json(name = "targetLanguage")
    val targetLanguage: String? = null,
    
    @Json(name = "isUpdatable")
    val isUpdatable: Boolean? = null,
    
    @Json(name = "indexUrl")
    val indexUrl: String? = null,
    
    @Json(name = "downloadUrl")
    val downloadUrl: String? = null
) {
    /**
     * Converts this YomitanIndexInfo to a DictionaryMetadataEntity for database storage
     * @return DictionaryMetadataEntity representing this dictionary
     */
    fun toDictionaryMetadataEntity(): com.abaga129.tekisuto.database.DictionaryMetadataEntity {
        return com.abaga129.tekisuto.database.DictionaryMetadataEntity(
            title = title,
            author = author ?: "",
            description = description ?: attribution ?: "",
            sourceLanguage = sourceLanguage ?: "",
            targetLanguage = targetLanguage ?: "",
            entryCount = 0, // Will be updated during import
            priority = 0,   // Default priority
            importDate = java.util.Date()
        )
    }
}