package com.abaga129.tekisuto.ui.ocr

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.preference.PreferenceManager
import com.abaga129.tekisuto.R
import com.abaga129.tekisuto.database.DictionaryEntryEntity
import com.abaga129.tekisuto.ui.settings.SettingsActivity
import com.abaga129.tekisuto.util.SpeechService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages audio generation and playback for OCR result words
 */
class AudioManager(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val speechService: SpeechService
) {
    private val TAG = "AudioManager"
    
    // Audio file cache for exports
    private val audioCache = mutableMapOf<String, File>()
    
    /**
     * Generate and play audio for a term
     * 
     * @param term The word to generate audio for
     * @param language The language code (e.g., "ja", "zh", "en")
     */
    fun playAudio(term: String, language: String) {
        // Create a single, unique ID for this audio request to track in logs
        val requestId = System.currentTimeMillis()

        // Check if audio is enabled in settings
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        val audioEnabled = sharedPrefs.getBoolean("enable_audio", true)

        if (!audioEnabled) {
            Toast.makeText(context, R.string.audio_unavailable, Toast.LENGTH_SHORT).show()
            return
        }

        // Check for Azure Speech API key
        val apiKey = sharedPrefs.getString("azure_speech_key", "") ?: ""
        val region = sharedPrefs.getString("azure_speech_region", "eastus") ?: "eastus"

        // Add detailed logging for debugging
        Log.e(TAG, "🔈 [REQ-$requestId] Audio request - Term: '$term', Language: '$language'")
        Log.e(TAG, "🔑 [REQ-$requestId] API config - Key present: ${apiKey.isNotEmpty()}, Region: $region")

        if (apiKey.isEmpty()) {
            Log.e(TAG, "❌ [REQ-$requestId] Missing API key")
            Toast.makeText(context, R.string.audio_generation_missing_api_key, Toast.LENGTH_LONG).show()
            // Open settings activity
            val intent = Intent(context, SettingsActivity::class.java)
            context.startActivity(intent)
            return
        }

        // Generate and play audio
        lifecycleScope.launch {
            try {
                // Show progress toast
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.generating_audio, Toast.LENGTH_SHORT).show()
                }

                Log.e(TAG, "⏳ [REQ-$requestId] Starting speech generation")

                // Create a simple test file to verify storage permissions
                try {
                    val testFile = File(context.cacheDir, "test_audio_${requestId}.tmp")
                    testFile.writeText("Test file for audio generation")
                    Log.e(TAG, "✓ [REQ-$requestId] Storage test successful: ${testFile.absolutePath}")
                    testFile.delete()
                } catch (e: Exception) {
                    Log.e(TAG, "❌ [REQ-$requestId] Storage test failed", e)
                }

                // Generate audio for the term
                val audioFile = speechService.generateSpeech(term, language)

                if (audioFile != null && audioFile.exists() && audioFile.length() > 100) {
                    Log.e(TAG, "✓ [REQ-$requestId] Speech generation successful")
                    Log.e(TAG, "📄 [REQ-$requestId] File: ${audioFile.absolutePath}, Size: ${audioFile.length()} bytes")

                    // Play the audio on the main thread
                    withContext(Dispatchers.Main) {
                        try {
                            // Show toast for playing audio
                            Toast.makeText(context, R.string.audio_playing, Toast.LENGTH_SHORT).show()

                            // Try to play the audio file
                            Log.e(TAG, "🔊 [REQ-$requestId] Playing audio")
                            speechService.playAudio(audioFile)

                            // Cache the audio file for AnkiDroid export
                            val cacheKey = "${language}_${term}"
                            audioCache[cacheKey] = audioFile

                            // Set up a backup way to play if MediaPlayer fails
                            Handler(Looper.getMainLooper()).postDelayed({
                                try {
                                    // Check if we need a backup playback attempt
                                    val player = MediaPlayer.create(context, Uri.fromFile(audioFile))
                                    Log.e(TAG, "🔄 [REQ-$requestId] Backup playback check")
                                    if (player != null) {
                                        player.setOnCompletionListener { it.release() }
                                        player.start()
                                        Log.e(TAG, "✓ [REQ-$requestId] Backup playback started")
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "❌ [REQ-$requestId] Backup playback failed", e)
                                }
                            }, 2000) // Give the first attempt 2 seconds
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ [REQ-$requestId] Error during audio playback", e)
                            Toast.makeText(
                                context,
                                "Error playing audio: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } else {
                    val fileStatus = if (audioFile == null) "null file"
                    else "exists: ${audioFile.exists()}, size: ${audioFile?.length() ?: 0} bytes"
                    Log.e(TAG, "❌ [REQ-$requestId] Speech generation failed - $fileStatus")

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "Failed to generate audio. Check Azure API key in settings.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ [REQ-$requestId] Critical error in audio process", e)
                Log.e(TAG, "🔍 [REQ-$requestId] Stack trace: ${e.stackTraceToString()}")

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    /**
     * Get an audio file for a term, either from cache or generate a new one
     * 
     * @param term The word to get audio for
     * @param language The language code
     * @return The audio file or null if generation failed
     */
    suspend fun getAudioFile(term: String, language: String): File? {
        // Check if we already have audio for this term
        val cacheKey = "${language}_${term}"
        var audioFile = audioCache[cacheKey]

        // If not in cache, generate it now
        if (audioFile == null) {
            audioFile = speechService.generateSpeech(term, language)
            if (audioFile != null) {
                audioCache[cacheKey] = audioFile
            }
        }
        
        return audioFile
    }
    
    /**
     * Determine the language of an entry for audio generation
     */
    fun determineLanguage(entry: DictionaryEntryEntity, ocrLanguage: String?): String {
        // First check the OCR language setting
        ocrLanguage?.let {
            return when (it) {
                "japanese" -> "ja"
                "chinese" -> "zh"
                "korean" -> "ko"
                else -> "en"
            }
        }

        // If OCR language is not set, guess from content
        val text = entry.term + " " + entry.reading

        return when {
            // Check for Japanese (Hiragana, Katakana, Kanji)
            text.contains(Regex("[\u3040-\u309F\u30A0-\u30FF\u4E00-\u9FFF]")) -> "ja"

            // Check for Chinese (mainly Han characters without Japanese-specific characters)
            text.contains(Regex("[\u4E00-\u9FFF]")) &&
                    !text.contains(Regex("[\u3040-\u309F\u30A0-\u30FF]")) -> "zh"

            // Check for Korean (Hangul)
            text.contains(Regex("[\uAC00-\uD7A3]")) -> "ko"

            // Default to English
            else -> "en"
        }
    }
    
    /**
     * Stop any currently playing audio
     */
    fun stopAudio() {
        speechService.stopAudio()
    }
}