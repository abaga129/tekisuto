package com.abaga129.tekisuto.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentifier
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await
import java.util.Locale

/**
 * Helper class for text translation
 */
class TranslationHelper(val context: Context) {
    private val prefs: SharedPreferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
    private var translator: Translator? = null
    private var languageIdentifier: LanguageIdentifier = LanguageIdentification.getClient()
    private var currentSourceLanguage: String? = null
    private var currentTargetLanguage: String? = null
    
    companion object {
        private const val TAG = "TranslationHelper"
        const val PREF_TRANSLATE_ENABLED = "translate_ocr_text"
        const val PREF_TARGET_LANGUAGE = "translate_target_language"
        
        // Default target language is English
        const val DEFAULT_TARGET_LANGUAGE = "en" // Maps to TranslateLanguage.ENGLISH
        
        // Map language codes to ML Kit translate languages
        private val languageCodeMapping = mapOf(
            "en" to TranslateLanguage.ENGLISH,
            "es" to TranslateLanguage.SPANISH,
            "fr" to TranslateLanguage.FRENCH,
            "de" to TranslateLanguage.GERMAN,
            "it" to TranslateLanguage.ITALIAN,
            "pt" to TranslateLanguage.PORTUGUESE,
            "ru" to TranslateLanguage.RUSSIAN,
            "zh" to TranslateLanguage.CHINESE,
            "ja" to TranslateLanguage.JAPANESE,
            "ko" to TranslateLanguage.KOREAN
        )
    }
    
    /**
     * Check if translation is enabled in preferences
     */
    fun isTranslationEnabled(): Boolean {
        return prefs.getBoolean(PREF_TRANSLATE_ENABLED, true)
    }
    
    /**
     * Get target language code from preferences
     */
    fun getTargetLanguageCode(): String {
        return prefs.getString(PREF_TARGET_LANGUAGE, DEFAULT_TARGET_LANGUAGE) ?: DEFAULT_TARGET_LANGUAGE
    }
    
    /**
     * Get ML Kit target language for translation
     */
    fun getTargetLanguage(): String {
        val code = getTargetLanguageCode()
        return languageCodeMapping[code] ?: TranslateLanguage.ENGLISH
    }
    
    /**
     * Translate text to target language
     * @param text Text to translate
     * @param sourceLanguage Optional source language code (from OCR settings)
     * @return Translated text or empty string if translation failed
     */
    suspend fun translateText(text: String, sourceLanguage: String? = null): String {
        if (!isTranslationEnabled() || text.isBlank()) {
            return ""
        }
        
        try {
            // Apply OCR corrections (like 0 to O)
            val correctedText = correctOcrErrors(text, sourceLanguage)
            
            val target = getTargetLanguage()
            Log.d(TAG, "Target language: $target")
            
            // For Latin script OCR, we'll detect the actual language instead of assuming English
            val source = if (sourceLanguage == "latin") {
                // Detect language for Latin script
                detectLanguage(correctedText)
            } else {
                // For other scripts, use the mapping
                mapOcrLanguageToTranslateLanguage(sourceLanguage)
            }
            
            Log.d(TAG, "Source language: $source")
            
            // Skip translation if source and target are likely the same
            if (source == target) {
                Log.d(TAG, "Target language matches source. Exiting translation")
                return correctedText
            }
            
            // Reset translator if source or target language changed
            if (currentSourceLanguage != source || currentTargetLanguage != target) {
                resetTranslator()
            }
            
            // Create or get translator
            val translatorInstance = getTranslator(source, target)
            
            // Download translation model if needed
            if (!isModelDownloaded(translatorInstance)) {
                downloadTranslationModel(translatorInstance)
            }
            
            // Perform translation
            return translatorInstance.translate(correctedText).await()
        } catch (e: Exception) {
            Log.e(TAG, "Translation error", e)
            return ""
        }
    }
    
    /**
     * Correct common OCR errors in the text
     * @param text Original OCR text
     * @param sourceLanguage Language of the text (to apply language-specific corrections)
     * @return Corrected text
     */
    private fun correctOcrErrors(text: String, sourceLanguage: String?): String {
        // Skip correction for non-Latin scripts
        if (sourceLanguage != null && sourceLanguage != "latin") {
            return text
        }
        
        var corrected = text
        
        // Replace zeros inside words with the letter 'o'
        // This pattern finds zeros that are surrounded by letters
        val zeroPattern = Regex("(?<=[a-zA-Z])0(?=[a-zA-Z])")
        corrected = corrected.replace(zeroPattern, "o")
        
        // Replace standalone zeros that are likely uppercase O
        // This is more aggressive - finds zeros that are:
        // - at the start of a word followed by lowercase letters
        // - after uppercase letters
        val zeroAtStartPattern = Regex("\\b0(?=[a-z])")
        corrected = corrected.replace(zeroAtStartPattern, "O")
        
        val zeroAfterUppercasePattern = Regex("(?<=[A-Z])0")
        corrected = corrected.replace(zeroAfterUppercasePattern, "O")
        
        // Fix common OCR confusions:
        // 1 (one) vs l (lowercase L) vs I (uppercase i)
        // Only apply to specific patterns to avoid false positives
        
        // Correct '1' at the beginning of words that are likely capital I
        val oneAtStartPattern = Regex("\\b1(?=[a-z])")
        corrected = corrected.replace(oneAtStartPattern, "I")
        
        // Log corrections if text has changed
        if (corrected != text) {
            Log.d(TAG, "OCR text corrected: 0→O replacements applied")
        }
        
        return corrected
    }
    
    /**
     * Map OCR language to ML Kit translation language
     */
    private fun mapOcrLanguageToTranslateLanguage(ocrLanguage: String?): String {
        return when (ocrLanguage) {
            "japanese" -> TranslateLanguage.JAPANESE
            "chinese" -> TranslateLanguage.CHINESE
            "korean" -> TranslateLanguage.KOREAN
            "devanagari" -> TranslateLanguage.HINDI
            "latin", null -> TranslateLanguage.ENGLISH // For Latin, we'll detect later
            else -> TranslateLanguage.ENGLISH
        }
    }
    
    /**
     * Detect the language of the text
     * @param text Text to analyze
     * @return ML Kit TranslateLanguage identifier
     */
    private suspend fun detectLanguage(text: String): String {
        return try {
            // Take a sample of the text (first few sentences) for faster detection
            val sample = text.split(Regex("[.!?\\n]"))
                .filter { it.isNotBlank() }
                .take(3)
                .joinToString(". ")
                .trim()
                .take(500) // Limit to 500 chars for faster processing
            
            val languageCode = languageIdentifier.identifyLanguage(sample).await()
            Log.d(TAG, "Detected language: $languageCode")
            
            // Map ISO code to TranslateLanguage if supported
            when (languageCode) {
                "en" -> TranslateLanguage.ENGLISH
                "es" -> TranslateLanguage.SPANISH
                "fr" -> TranslateLanguage.FRENCH
                "de" -> TranslateLanguage.GERMAN
                "it" -> TranslateLanguage.ITALIAN
                "pt" -> TranslateLanguage.PORTUGUESE
                "ru" -> TranslateLanguage.RUSSIAN
                "zh" -> TranslateLanguage.CHINESE
                "ja" -> TranslateLanguage.JAPANESE
                "ko" -> TranslateLanguage.KOREAN
                "hi" -> TranslateLanguage.HINDI
                "und" -> TranslateLanguage.ENGLISH // Undetermined, fallback to English
                else -> if (languageCode in TranslateLanguage.getAllLanguages()) {
                    languageCode // If ML Kit can translate it directly
                } else {
                    TranslateLanguage.ENGLISH // Default fallback
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting language", e)
            TranslateLanguage.ENGLISH // Default fallback
        }
    }
    
    /**
     * Reset the translator instance when language changes
     */
    private fun resetTranslator() {
        translator?.close()
        translator = null
        currentSourceLanguage = null
        currentTargetLanguage = null
    }
    
    /**
     * Get translator for the specified language pair
     */
    private fun getTranslator(source: String, target: String): Translator {
        if (translator == null || currentSourceLanguage != source || currentTargetLanguage != target) {
            // Close previous translator instance if it exists
            translator?.close()
            
            // Create new translator with the requested languages
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(source)
                .setTargetLanguage(target)
                .build()
            translator = Translation.getClient(options)
            
            // Remember current language pair
            currentSourceLanguage = source
            currentTargetLanguage = target
        }
        return translator!!
    }
    
    /**
     * Check if translation model is downloaded
     */
    private suspend fun isModelDownloaded(translator: Translator): Boolean {
        return try {
            translator.downloadModelIfNeeded().await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking model download status", e)
            false
        }
    }
    
    /**
     * Download translation model
     */
    private suspend fun downloadTranslationModel(translator: Translator) {
        try {
            translator.downloadModelIfNeeded().await()
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading translation model", e)
        }
    }
    
    /**
     * Close translator to free resources
     */
    fun close() {
        translator?.close()
        translator = null
        languageIdentifier.close()
    }
}