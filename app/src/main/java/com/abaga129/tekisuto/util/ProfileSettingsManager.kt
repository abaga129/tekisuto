package com.abaga129.tekisuto.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import com.abaga129.tekisuto.database.ProfileEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * Manager class for profile-specific settings
 * This class syncs settings between SharedPreferences and the active Profile.
 */
class ProfileSettingsManager(private val context: Context) {
    
    private val TAG = "ProfileSettingsManager"
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    
    // Profile settings keys
    companion object {
        // OCR Settings
        const val OCR_LANGUAGE = "ocr_language"
        const val TRANSLATE_OCR_TEXT = "translate_ocr_text"
        const val TRANSLATE_TARGET_LANGUAGE = "translate_target_language"
        
        // Screenshot Settings
        const val ENABLE_LONG_PRESS_CAPTURE = "enable_long_press_capture"
        const val LONG_PRESS_DURATION = "long_press_duration"
        
        // Audio Settings
        const val ENABLE_AUDIO = "enable_audio"
        const val AZURE_SPEECH_KEY = "azure_speech_key"
        const val AZURE_SPEECH_REGION = "azure_speech_region"
        const val VOICE_SELECTION_PREFIX = "voice_selection_"  // Will be appended with language code
        
        // Current profile ID - used to track which profile is active
        const val CURRENT_PROFILE_ID = "current_profile_id"
    }
    
    /**
     * Load settings from the given profile to SharedPreferences
     */
    fun loadProfileSettings(profile: ProfileEntity) {
        Log.d(TAG, "Loading settings from profile: ${profile.name} (ID: ${profile.id})")
        
        val editor = prefs.edit()
        
        // Store current profile ID
        editor.putLong(CURRENT_PROFILE_ID, profile.id)
        
        // OCR Settings
        editor.putString(OCR_LANGUAGE, profile.ocrLanguage)
        editor.putBoolean(TRANSLATE_OCR_TEXT, profile.translateOcrText)
        editor.putString(TRANSLATE_TARGET_LANGUAGE, profile.translateTargetLanguage)
        
        // Screenshot Settings
        editor.putBoolean(ENABLE_LONG_PRESS_CAPTURE, profile.enableLongPressCapture)
        editor.putInt(LONG_PRESS_DURATION, profile.longPressDuration)
        
        // Audio Settings
        editor.putBoolean(ENABLE_AUDIO, profile.enableAudio)
        editor.putString(AZURE_SPEECH_KEY, profile.azureSpeechKey)
        editor.putString(AZURE_SPEECH_REGION, profile.azureSpeechRegion)
        
        // Save voice selection if any
        if (profile.voiceSelection.isNotEmpty()) {
            val parts = profile.voiceSelection.split(":")
            if (parts.size == 2) {
                val langCode = parts[0]
                val voiceId = parts[1]
                editor.putString(VOICE_SELECTION_PREFIX + langCode, voiceId)
            }
        }
        
        // AnkiDroid settings
        // Save to AnkiDroid's preferences
        val ankiPrefs = context.getSharedPreferences(AnkiDroidHelper.ANKI_PREFS, Context.MODE_PRIVATE)
        ankiPrefs.edit().apply {
            putLong(AnkiDroidHelper.PREF_DECK_ID, profile.ankiDeckId)
            putLong(AnkiDroidHelper.PREF_MODEL_ID, profile.ankiModelId)
            putInt(AnkiDroidHelper.PREF_FIELD_WORD, profile.ankiFieldWord)
            putInt(AnkiDroidHelper.PREF_FIELD_READING, profile.ankiFieldReading)
            putInt(AnkiDroidHelper.PREF_FIELD_DEFINITION, profile.ankiFieldDefinition)
            putInt(AnkiDroidHelper.PREF_FIELD_SCREENSHOT, profile.ankiFieldScreenshot)
            putInt(AnkiDroidHelper.PREF_FIELD_CONTEXT, profile.ankiFieldContext)
            putInt(AnkiDroidHelper.PREF_FIELD_PART_OF_SPEECH, profile.ankiFieldPartOfSpeech)
            putInt(AnkiDroidHelper.PREF_FIELD_TRANSLATION, profile.ankiFieldTranslation)
            putInt(AnkiDroidHelper.PREF_FIELD_AUDIO, profile.ankiFieldAudio)
            apply()
        }
        
        editor.apply()
        
        Log.d(TAG, "Profile settings loaded: OCR Language=${profile.ocrLanguage}, " +
                "Translate=${profile.translateOcrText}, Target=${profile.translateTargetLanguage}, " +
                "AnkiDroid Deck=${profile.ankiDeckId}, Model=${profile.ankiModelId}")
    }
    
    /**
     * Save current SharedPreferences settings to the profile
     */
    fun extractProfileFromSettings(profile: ProfileEntity): ProfileEntity {
        Log.d(TAG, "Extracting settings for profile: ${profile.name} (ID: ${profile.id})")
        
        // Get current settings
        val ocrLanguage = prefs.getString(OCR_LANGUAGE, "latin") ?: "latin"
        val translateOcrText = prefs.getBoolean(TRANSLATE_OCR_TEXT, true)
        val translateTargetLanguage = prefs.getString(TRANSLATE_TARGET_LANGUAGE, "en") ?: "en"
        val enableLongPressCapture = prefs.getBoolean(ENABLE_LONG_PRESS_CAPTURE, true)
        val longPressDuration = prefs.getInt(LONG_PRESS_DURATION, 500)
        val enableAudio = prefs.getBoolean(ENABLE_AUDIO, true)
        val azureSpeechKey = prefs.getString(AZURE_SPEECH_KEY, "") ?: ""
        val azureSpeechRegion = prefs.getString(AZURE_SPEECH_REGION, "eastus") ?: "eastus"
        
        // Get voice selection - needs to be determined from current language
        val speechLanguage = convertOcrLanguageToSpeechLanguage(ocrLanguage)
        val voiceId = prefs.getString(VOICE_SELECTION_PREFIX + speechLanguage, "") ?: ""
        val voiceSelection = if (speechLanguage.isNotEmpty() && voiceId.isNotEmpty()) {
            "$speechLanguage:$voiceId"
        } else ""
        
        // Get AnkiDroid settings
        val ankiPrefs = context.getSharedPreferences(AnkiDroidHelper.ANKI_PREFS, Context.MODE_PRIVATE)
        val ankiDeckId = ankiPrefs.getLong(AnkiDroidHelper.PREF_DECK_ID, 0)
        val ankiModelId = ankiPrefs.getLong(AnkiDroidHelper.PREF_MODEL_ID, 0)
        val ankiFieldWord = ankiPrefs.getInt(AnkiDroidHelper.PREF_FIELD_WORD, -1)
        val ankiFieldReading = ankiPrefs.getInt(AnkiDroidHelper.PREF_FIELD_READING, -1)
        val ankiFieldDefinition = ankiPrefs.getInt(AnkiDroidHelper.PREF_FIELD_DEFINITION, -1)
        val ankiFieldScreenshot = ankiPrefs.getInt(AnkiDroidHelper.PREF_FIELD_SCREENSHOT, -1)
        val ankiFieldContext = ankiPrefs.getInt(AnkiDroidHelper.PREF_FIELD_CONTEXT, -1)
        val ankiFieldPartOfSpeech = ankiPrefs.getInt(AnkiDroidHelper.PREF_FIELD_PART_OF_SPEECH, -1)
        val ankiFieldTranslation = ankiPrefs.getInt(AnkiDroidHelper.PREF_FIELD_TRANSLATION, -1)
        val ankiFieldAudio = ankiPrefs.getInt(AnkiDroidHelper.PREF_FIELD_AUDIO, -1)
        
        // Create updated profile
        return profile.copy(
            ocrLanguage = ocrLanguage,
            translateOcrText = translateOcrText,
            translateTargetLanguage = translateTargetLanguage,
            enableLongPressCapture = enableLongPressCapture,
            longPressDuration = longPressDuration,
            enableAudio = enableAudio,
            azureSpeechKey = azureSpeechKey,
            azureSpeechRegion = azureSpeechRegion,
            voiceSelection = voiceSelection,
            ankiDeckId = ankiDeckId,
            ankiModelId = ankiModelId,
            ankiFieldWord = ankiFieldWord,
            ankiFieldReading = ankiFieldReading,
            ankiFieldDefinition = ankiFieldDefinition,
            ankiFieldScreenshot = ankiFieldScreenshot,
            ankiFieldContext = ankiFieldContext,
            ankiFieldPartOfSpeech = ankiFieldPartOfSpeech,
            ankiFieldTranslation = ankiFieldTranslation,
            ankiFieldAudio = ankiFieldAudio,
            // Update last used date
            lastUsedDate = Date()
        )
    }
    
    /**
     * Get the current profile ID from preferences
     */
    fun getCurrentProfileId(): Long {
        return prefs.getLong(CURRENT_PROFILE_ID, -1L)
    }
    
    /**
     * Convert OCR language code to speech language code
     * For Latin script, return an empty string to show all voices
     */
    private fun convertOcrLanguageToSpeechLanguage(ocrLanguage: String): String {
        return when (ocrLanguage) {
            "japanese" -> "ja"
            "chinese" -> "zh"
            "korean" -> "ko"
            "devanagari" -> "hi" // Hindi
            "latin" -> "" // Empty string will show all voices for Latin script
            else -> "en" // Default to English for other scripts
        }
    }
}