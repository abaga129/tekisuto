package com.abaga129.tekisuto.util

import android.util.Log
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import kotlinx.coroutines.tasks.await

/**
 * Utility class for language detection using ML Kit
 */
class LanguageDetector {
    
    private val languageIdentifier = LanguageIdentification.getClient(
        LanguageIdentificationOptions.Builder()
            .setConfidenceThreshold(0.5f)
            .build()
    )
    
    companion object {
        private const val TAG = "LanguageDetector"
        
        // Singleton instance
        @Volatile
        private var instance: LanguageDetector? = null
        
        fun getInstance(): LanguageDetector {
            return instance ?: synchronized(this) {
                instance ?: LanguageDetector().also { instance = it }
            }
        }
        
        // Map of ML Kit language codes to ISO codes for convenience
        val LANGUAGE_MAP = mapOf(
            // East Asian languages
            "ja" to "ja", // Japanese
            "zh" to "zh", // Chinese
            "ko" to "ko", // Korean
            
            // Latin-script languages
            "en" to "en", // English
            "es" to "es", // Spanish
            "fr" to "fr", // French
            "de" to "de", // German
            "it" to "it", // Italian
            "pt" to "pt", // Portuguese
            
            // Default for undetermined
            "und" to "en"  // Default to English if undetermined
        )
        
        /**
         * Quick content-based detection for CJK languages
         * This is faster than ML Kit for obvious cases
         */
        fun detectCJKLanguage(text: String): String? {
            // Japanese (Hiragana, Katakana, Kanji)
            if (text.matches(Regex(".*[\u3040-\u309F\u30A0-\u30FF]+.*"))) {
                return "ja"
            }
            
            // Chinese (Han characters without Japanese kana)
            if (text.matches(Regex(".*[\u4E00-\u9FFF]+.*")) && 
                !text.contains(Regex("[\u3040-\u309F\u30A0-\u30FF]"))) {
                return "zh"
            }
            
            // Korean (Hangul)
            if (text.matches(Regex(".*[\uAC00-\uD7A3]+.*"))) {
                return "ko"
            }
            
            return null // Not a CJK language or couldn't determine
        }
    }
    
    /**
     * Detect language synchronously
     * This is a simpler method that returns immediately with best-effort detection
     * Uses character-based detection for CJK languages as a fast path
     */
    fun detectLanguageSync(text: String): String {
        // Check for empty text
        if (text.isBlank()) {
            return "en" // Default to English for empty text
        }
        
        try {
            // Fast path: Try to detect CJK languages based on character sets
            // This is much faster than ML Kit for obvious cases
            val cjkLanguage = detectCJKLanguage(text)
            if (cjkLanguage != null) {
                Log.d(TAG, "CJK language detected quickly: $cjkLanguage")
                return cjkLanguage
            }
            
            // For non-CJK, do a quick check for common European language characters
            // These are faster heuristics than ML Kit
            if (text.length > 10) {
                // Spanish - check for distinctive Spanish characters
                if (text.contains('ñ') || text.contains('¿') || text.contains('¡')) {
                    return "es"
                }
                
                // French - check for distinctive French characters
                if (text.contains('ç') || text.contains('œ') || 
                    (text.contains('é') && (text.contains('è') || text.contains('ê')))) {
                    return "fr"
                }
                
                // German - check for distinctive German characters
                if (text.contains('ß') || text.contains('ü') && text.contains('ä')) {
                    return "de"
                }
                
                // Italian - some distinctive patterns
                if (text.contains('ì') || text.contains('ò') || text.endsWith("zione")) {
                    return "it"
                }
                
                // Russian - check for Cyrillic
                if (text.contains(Regex(".*[А-Яа-я]+.*"))) {
                    return "ru"
                }
            }
            
            // Default to English for non-specific Latin script
            return "en"
        } catch (e: Exception) {
            Log.e(TAG, "Error in language detection", e)
            return "en" // Default to English on error
        }
    }
    
    /**
     * Detect language asynchronously using ML Kit
     * More accurate but requires a callback
     */
    suspend fun detectLanguageAsync(text: String): String {
        // Empty check
        if (text.isBlank()) {
            return "en"
        }
        
        try {
            // For very short text, just use the sync method
            if (text.length < 10) {
                return detectLanguageSync(text)
            }
            
            // Take a sample of the text for faster detection
            val sample = text.take(200) // Take first 200 chars for efficiency
            
            // Detect language using ML Kit
            val detectedLanguage = languageIdentifier.identifyLanguage(sample).await()
            
            // Log result for debugging
            Log.d(TAG, "ML Kit detected language: $detectedLanguage")
            
            // Map to our supported languages or default to English
            return LANGUAGE_MAP[detectedLanguage] ?: "en"
        } catch (e: Exception) {
            Log.e(TAG, "Error in ML Kit language detection", e)
            return detectLanguageSync(text) // Fallback to sync method
        }
    }
    
    /**
     * Clean up resources
     */
    fun close() {
        languageIdentifier.close()
        instance = null
    }
}