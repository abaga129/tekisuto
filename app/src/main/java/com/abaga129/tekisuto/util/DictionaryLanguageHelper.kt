package com.abaga129.tekisuto.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.abaga129.tekisuto.database.DictionaryMetadataEntity

/**
 * Helper class to store and retrieve dictionary language information
 * for text-to-speech generation
 */
class DictionaryLanguageHelper(private val context: Context) {
    companion object {
        private const val TAG = "DictionaryLangHelper"
        private const val PREFS_NAME = "dictionary_languages"
        
        // Key prefixes for SharedPreferences
        private const val KEY_SOURCE = "dictionary_%s_source"
        private const val KEY_TARGET = "dictionary_%s_target"
        
        // Azure language codes
        private val AZURE_LANGUAGE_CODES = setOf(
            "ar", "zh-CN", "zh-TW", "cs", "da", "nl", "en", 
            "fi", "fr", "de", "el", "he", "hi", "hu", "id", 
            "it", "ja", "ko", "nb", "pl", "pt", "pt-BR", 
            "ro", "ru", "sk", "sl", "es", "sv", "ta", "th", 
            "tr", "vi"
        )
        
        // Common language code mappings
        private val LANGUAGE_CODE_MAP = mapOf(
            // ISO 639-1 to Azure codes
            "zh" to "zh-CN",
            "pt" to "pt-BR",
            "nb" to "no",
            
            // Common names to Azure codes
            "chinese" to "zh-CN",
            "japanese" to "ja",
            "spanish" to "es",
            "english" to "en",
            "french" to "fr",
            "german" to "de",
            "italian" to "it",
            "russian" to "ru",
            "korean" to "ko",
            
            // ISO 639-2 to Azure codes
            "jpn" to "ja",
            "eng" to "en",
            "spa" to "es",
            "fra" to "fr",
            "deu" to "de",
            "ita" to "it",
            "rus" to "ru",
            "kor" to "ko",
            "cmn" to "zh-CN",
            "zho" to "zh-CN",
            
            // OCR languages to Azure codes
            "latin" to "en",
            "devanagari" to "hi"
        )
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Store language information for a dictionary
     */
    fun storeDictionaryLanguages(dictionary: DictionaryMetadataEntity) {
        val sourceKey = KEY_SOURCE.format(dictionary.id)
        val targetKey = KEY_TARGET.format(dictionary.id)
        
        val sourceLanguage = dictionary.sourceLanguage
        val targetLanguage = dictionary.targetLanguage
        
        Log.d(TAG, "Storing dictionary languages - ID: ${dictionary.id}, Source: $sourceLanguage, Target: $targetLanguage")
        
        prefs.edit()
            .putString(sourceKey, sourceLanguage)
            .putString(targetKey, targetLanguage)
            .apply()
    }
    
    /**
     * Get the source language for a dictionary
     * @return Azure-compatible language code
     */
    fun getSourceLanguage(dictionaryId: Long): String? {
        val sourceKey = KEY_SOURCE.format(dictionaryId)
        val language = prefs.getString(sourceKey, null)
        
        if (language != null) {
            return mapToAzureLanguageCode(language)
        }
        
        return null
    }
    
    /**
     * Get the target language for a dictionary
     * @return Azure-compatible language code
     */
    fun getTargetLanguage(dictionaryId: Long): String? {
        val targetKey = KEY_TARGET.format(dictionaryId)
        val language = prefs.getString(targetKey, null)
        
        if (language != null) {
            return mapToAzureLanguageCode(language)
        }
        
        return null
    }
    
    /**
     * Map a language code to Azure-compatible format
     */
    fun mapToAzureLanguageCode(code: String): String {
        // Handle null or empty
        if (code.isBlank()) return "en"
        
        // Normalize: lowercase and trim
        val normalizedCode = code.lowercase().trim()
        
        // If already a valid Azure code, return as is
        if (AZURE_LANGUAGE_CODES.contains(normalizedCode)) {
            return normalizedCode
        }
        
        // Check our mapping table
        val mappedCode = LANGUAGE_CODE_MAP[normalizedCode]
        if (mappedCode != null) {
            return mappedCode
        }
        
        // For 2-letter ISO codes not in our mapping but valid in Azure
        if (normalizedCode.length == 2 && AZURE_LANGUAGE_CODES.contains(normalizedCode)) {
            return normalizedCode
        }
        
        // For codes with subtypes (e.g., "en-US"), try the base code
        val baseLang = normalizedCode.split("-")[0]
        if (baseLang.length == 2 && AZURE_LANGUAGE_CODES.contains(baseLang)) {
            return baseLang
        }
        
        // Last resort: try to find a partial match
        for (azureCode in AZURE_LANGUAGE_CODES) {
            if (azureCode.startsWith(baseLang)) {
                return azureCode
            }
        }
        
        // Default to English if we can't determine the language
        Log.w(TAG, "Could not map language code: $code - defaulting to English")
        return "en"
    }
}