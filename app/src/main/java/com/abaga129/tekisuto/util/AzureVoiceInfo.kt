package com.abaga129.tekisuto.util

/**
 * Data class representing an Azure Text-to-Speech voice
 */
data class AzureVoiceInfo(
    val name: String,               // Voice ID used with the Azure service, e.g. "en-US-JennyNeural"
    val displayName: String,        // Human-readable name, e.g. "Jenny"
    val localName: String,          // Native language name
    val shortName: String,          // Language code, e.g. "en-US"
    val gender: String,             // "Female" or "Male"
    val locale: String,             // Language region, e.g. "en-US"
    val voiceType: String,          // "Neural"
    val status: String,             // "GA" (Generally Available) or "Preview"
    val wordsPerMinute: Int? = null // Speech rate, words per minute
) {
    /**
     * Returns a user-friendly display string for the voice
     */
    fun getDisplayString(): String {
        return "$displayName ($shortName, $gender)"
    }
    
    /**
     * Returns a grouping key for organizing voices by language/locale
     */
    fun getLanguageGroupKey(): String {
        return shortName.split("-").firstOrNull() ?: shortName
    }
    
    /**
     * Returns a display name for the language group
     */
    fun getLanguageGroupName(): String {
        return when(getLanguageGroupKey()) {
            "en" -> "English"
            "ja" -> "Japanese"
            "zh" -> "Chinese"
            "ko" -> "Korean"
            "es" -> "Spanish"
            "fr" -> "French"
            "de" -> "German"
            "it" -> "Italian"
            "ru" -> "Russian"
            "pt" -> "Portuguese"
            "nl" -> "Dutch"
            "ar" -> "Arabic"
            "hi" -> "Hindi"
            else -> locale
        }
    }
}