package com.abaga129.tekisuto.util

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import androidx.preference.PreferenceManager
import com.microsoft.cognitiveservices.speech.SpeechConfig
import com.microsoft.cognitiveservices.speech.SpeechSynthesizer
import com.microsoft.cognitiveservices.speech.audio.AudioConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.coroutines.resumeWithException
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

/**
 * Service for generating speech audio using Azure AI Services
 */
class SpeechService(private val context: Context) {
    companion object {
        private const val TAG = "SpeechService"
        
        // Default Azure keys for development/testing
        // In a production app, these would be stored more securely
        private const val DEFAULT_REGION = "eastus"
        
        // Cache directory for storing audio files
        private const val AUDIO_CACHE_DIR = "audio_cache"
        
        // Mapping of language codes to voice names
        private val VOICE_MAPPING = mapOf(
            "ja" to "ja-JP-NanamiNeural", // Japanese
            "zh" to "zh-CN-XiaoxiaoNeural", // Chinese (Simplified)
            "ko" to "ko-KR-SunHiNeural",  // Korean
            "en" to "en-US-JennyNeural",  // English
            "es" to "es-ES-ElviraNeural", // Spanish
            "fr" to "fr-FR-DeniseNeural", // French
            "de" to "de-DE-KatjaNeural",  // German
            "it" to "it-IT-ElsaNeural",   // Italian
            "ru" to "ru-RU-SvetlanaNeural" // Russian
        )
    }
    
    // Cache to prevent regenerating the same audio multiple times
    private val audioCache = mutableMapOf<String, File>()
    private var mediaPlayer: MediaPlayer? = null
    
    /**
     * Generate speech for the given text and language
     * 
     * @param text Text to convert to speech
     * @param language Language code (e.g., "ja" for Japanese)
     * @return File containing the audio, or null if generation failed
     */
    suspend fun generateSpeech(text: String, language: String): File? {
        return withContext(Dispatchers.IO) {
            try {
                // Log start of speech generation
                Log.e(TAG, "🔄 STARTING SPEECH GENERATION - Text: '$text', Language: '$language'")
                
                // Check cache first
                val cacheKey = "${language}_${text}"
                audioCache[cacheKey]?.let { cachedFile ->
                    if (cachedFile.exists() && cachedFile.length() > 0) {
                        Log.e(TAG, "🔄 Using cached audio from ${cachedFile.absolutePath}, size: ${cachedFile.length()} bytes")
                        return@withContext cachedFile
                    } else {
                        Log.e(TAG, "❌ Cached file invalid or deleted, regenerating")
                        // Cached file no longer valid, remove from cache
                        audioCache.remove(cacheKey)
                    }
                }
                
                // Get API key and region from preferences
                val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
                val apiKey = sharedPreferences.getString("azure_speech_key", "") ?: ""
                val region = sharedPreferences.getString("azure_speech_region", DEFAULT_REGION) ?: DEFAULT_REGION
                
                Log.e(TAG, "🔐 Azure configuration - Region: $region, API Key present: ${apiKey.isNotEmpty()}")
                
                if (apiKey.isEmpty()) {
                    Log.e(TAG, "❌ Azure Speech API key not configured")
                    return@withContext null
                }
                
                // Determine voice based on language
                val voiceName = VOICE_MAPPING[language] ?: VOICE_MAPPING["en"] ?: "en-US-JennyNeural"
                Log.e(TAG, "🎤 Using voice: $voiceName for language: $language")
                
                // Create file for saving audio
                val audioFile = createAudioFile()
                Log.e(TAG, "📄 Created audio file at: ${audioFile.absolutePath}")
                
                // Generate speech using Azure
                Log.e(TAG, "🚀 Calling Azure Speech service...")
                
                // Validate the text isn't empty
                if (text.trim().isEmpty()) {
                    Log.e(TAG, "❌ Cannot generate speech for empty text")
                    return@withContext null
                }
                
                // Try to generate speech
                val result = try {
                    generateAzureSpeech(apiKey, region, text, voiceName, audioFile)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ CRITICAL ERROR during Azure speech generation", e)
                    Log.e(TAG, "❌ Stack trace: ${e.stackTraceToString()}")
                    false
                }
                
                if (result) {
                    // Verify file was created correctly
                    if (!audioFile.exists()) {
                        Log.e(TAG, "❌ Audio file doesn't exist after successful generation")
                        return@withContext null
                    }
                    
                    if (audioFile.length() < 100) { // Suspicious if less than 100 bytes
                        Log.e(TAG, "❌ Audio file suspiciously small (${audioFile.length()} bytes)")
                        return@withContext null
                    }
                    
                    Log.e(TAG, "✅ Successfully generated audio, file size: ${audioFile.length()} bytes")
                    // Cache the result
                    audioCache[cacheKey] = audioFile
                    return@withContext audioFile
                } else {
                    Log.e(TAG, "❌ Azure speech generation returned false or failed")
                    return@withContext null
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ CRITICAL ERROR in generateSpeech method", e)
                Log.e(TAG, "❌ Stack trace: ${e.stackTraceToString()}")
                return@withContext null
            }
        }
    }
    
    /**
     * Generate speech using Azure Speech Service
     * 
     * This is a low-level function that directly interacts with the Azure Speech SDK
     */
    private suspend fun generateAzureSpeech(
        apiKey: String, 
        region: String,
        text: String,
        voiceName: String,
        outputFile: File
    ): Boolean = suspendCancellableCoroutine { continuation ->
        try {
            Log.d(TAG, "Initializing Azure Speech Service with region: $region and voice: $voiceName")
            
            // Initialize speech config with API key and region
            val speechConfig = SpeechConfig.fromSubscription(apiKey, region)
            speechConfig.speechSynthesisVoiceName = voiceName
            
            // Configure audio output to file
            val audioConfig = AudioConfig.fromWavFileOutput(outputFile.absolutePath)
            Log.d(TAG, "Audio output configured to file: ${outputFile.absolutePath}")
            
            // Create synthesizer
            val synthesizer = SpeechSynthesizer(speechConfig, audioConfig)
            Log.d(TAG, "SpeechSynthesizer created, calling SpeakTextAsync for text: ${text.take(20)}${if (text.length > 20) "..." else ""}")
            
            try {
                // Start speech synthesis
                val result = synthesizer.SpeakTextAsync(text).get()
                
                // Check result
                Log.d(TAG, "Speech synthesis completed with reason: ${result.reason}")
                
                if (result.reason.toString() == "SynthesizingAudioCompleted") {
                    Log.d(TAG, "Speech synthesis successful, saved to ${outputFile.absolutePath}")
                    
                    // Verify file was created and has data
                    if (outputFile.exists() && outputFile.length() > 0) {
                        Log.d(TAG, "Output file exists and has size: ${outputFile.length()} bytes")
                        continuation.resume(true) { }
                    } else {
                        Log.e(TAG, "Output file does not exist or is empty. Exists: ${outputFile.exists()}, Size: ${if (outputFile.exists()) outputFile.length() else 0} bytes")
                        continuation.resume(false) { }
                    }
                } else {
                    Log.e(TAG, "Speech synthesis failed with reason: ${result.reason}")
                    continuation.resume(false) { }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error calling SpeakTextAsync", e)
                continuation.resume(false) { }
            } finally {
                // Close resources
                try {
                    Log.d(TAG, "Closing Azure Speech resources")
                    synthesizer.close()
                    speechConfig.close()
                    audioConfig.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing Azure Speech resources", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in Azure speech generation", e)
            continuation.resumeWithException(e)
        }
    }
    
    /**
     * Play audio from the given file
     */
    fun playAudio(audioFile: File) {
        try {
            // Log critical information at the beginning
            Log.e(TAG, "▶️ AUDIO PLAYBACK ATTEMPT - file: ${audioFile.absolutePath}")
            
            // Check if file exists and is readable
            if (!audioFile.exists()) {
                Log.e(TAG, "❌ CRITICAL: Audio file does not exist: ${audioFile.absolutePath}")
                return
            }
            
            if (!audioFile.canRead()) {
                Log.e(TAG, "❌ CRITICAL: Cannot read audio file: ${audioFile.absolutePath}")
                return
            }
            
            // Debug file properties
            val fileSize = audioFile.length()
            Log.e(TAG, "📋 File diagnostics: size=${fileSize} bytes, lastModified=${audioFile.lastModified()}, canWrite=${audioFile.canWrite()}")
            
            // Check if file is empty or suspiciously small
            if (fileSize < 100) { // Too small to be valid audio
                Log.e(TAG, "❌ CRITICAL: Audio file appears to be empty or corrupted (size: $fileSize bytes)")
                // Try to read first few bytes to debug content
                try {
                    val bytes = audioFile.readBytes().take(20).joinToString(", ") { it.toString() }
                    Log.e(TAG, "📋 File first bytes: $bytes")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to read file bytes", e)
                }
                return
            }
            
            // Stop any currently playing audio
            stopAudio()
            
            // Initialize and start new player with more verbose logging
            Log.e(TAG, "🔊 Creating MediaPlayer instance and setting data source")
            mediaPlayer = MediaPlayer().apply {
                setOnPreparedListener {
                    Log.e(TAG, "✅ MediaPlayer prepared successfully, starting playback")
                    it.start()
                }
                
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "❌ MediaPlayer ERROR: what=$what, extra=$extra")
                    // Common error codes
                    val errorMsg = when(what) {
                        MediaPlayer.MEDIA_ERROR_UNKNOWN -> "Unknown error"
                        MediaPlayer.MEDIA_ERROR_SERVER_DIED -> "Server died"
                        else -> "Other error"
                    }
                    val extraMsg = when(extra) {
                        MediaPlayer.MEDIA_ERROR_IO -> "IO error"
                        MediaPlayer.MEDIA_ERROR_MALFORMED -> "Malformed"
                        MediaPlayer.MEDIA_ERROR_UNSUPPORTED -> "Unsupported format"
                        MediaPlayer.MEDIA_ERROR_TIMED_OUT -> "Timed out"
                        else -> "Other extra error"
                    }
                    Log.e(TAG, "❌ Detailed error: $errorMsg, $extraMsg")
                    false
                }
                
                setOnCompletionListener {
                    Log.e(TAG, "✅ MediaPlayer playback completed successfully")
                }
                
                try {
                    Log.e(TAG, "🔄 Setting data source: ${audioFile.absolutePath}")
                    setDataSource(audioFile.absolutePath)
                    Log.e(TAG, "🔄 Calling prepareAsync()")
                    // Prepare asynchronously to avoid blocking the UI thread
                    prepareAsync()
                } catch (e: Exception) {
                    Log.e(TAG, "❌ CRITICAL ERROR setting data source or preparing", e)
                }
            }
            Log.e(TAG, "🔄 MediaPlayer setup complete, waiting for preparation")
        } catch (e: Exception) {
            Log.e(TAG, "❌ CRITICAL ERROR in playAudio method", e)
        }
    }
    
    /**
     * Stop any currently playing audio
     */
    fun stopAudio() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null
    }
    
    /**
     * Create a temporary file for storing audio
     */
    private fun createAudioFile(): File {
        try {
            // Create cache directory if it doesn't exist
            val cacheDir = File(context.cacheDir, AUDIO_CACHE_DIR)
            if (!cacheDir.exists()) {
                val created = cacheDir.mkdirs()
                Log.e(TAG, "Cache directory creation result: $created - path: ${cacheDir.absolutePath}")
            }
            
            // Verify the directory exists and is writable
            if (!cacheDir.exists()) {
                Log.e(TAG, "❌ CRITICAL: Cache directory doesn't exist after creation attempt")
                // Try to create in the main cache directory instead
                val fileName = "speech_${UUID.randomUUID()}.wav"
                return File(context.cacheDir, fileName)
            }
            
            if (!cacheDir.canWrite()) {
                Log.e(TAG, "❌ CRITICAL: Cache directory is not writable")
                // Try to create in the main cache directory instead
                val fileName = "speech_${UUID.randomUUID()}.wav"
                return File(context.cacheDir, fileName)
            }
            
            // Create a unique file name with a timestamp prefix for better sorting
            val fileName = "speech_${System.currentTimeMillis()}_${UUID.randomUUID()}.wav"
            val file = File(cacheDir, fileName)
            
            // Test if file is accessible
            try {
                // Touch the file to make sure we can write to it
                file.createNewFile()
                Log.e(TAG, "✓ Audio file created successfully: ${file.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to create audio file in cache directory", e)
                // Fall back to direct cache file if subdirectory fails
                return File(context.cacheDir, fileName)
            }
            
            return file
        } catch (e: Exception) {
            Log.e(TAG, "❌ CRITICAL ERROR creating audio file", e)
            // Last resort - use a fixed filename in main cache
            return File(context.cacheDir, "emergency_audio_${System.currentTimeMillis()}.wav")
        }
    }
    
    /**
     * Save audio file to a permanent location for AnkiDroid export
     * 
     * @param sourceFile Temporary audio file
     * @param word Word that the audio corresponds to (used in file name)
     * @return Permanent audio file
     */
    suspend fun saveAudioForExport(sourceFile: File, word: String, language: String): File? {
        return withContext(Dispatchers.IO) {
            try {
                // Create directory for permanent storage
                val storageDir = File(context.filesDir, "anki_audio")
                if (!storageDir.exists()) {
                    storageDir.mkdirs()
                }
                
                // Sanitize word for file name
                val sanitizedWord = word.replace("[^a-zA-Z0-9]".toRegex(), "_")
                val fileName = "${language}_${sanitizedWord}_${System.currentTimeMillis()}.wav"
                val destFile = File(storageDir, fileName)
                
                // Copy file
                sourceFile.inputStream().use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                
                Log.d(TAG, "Saved audio for export: ${destFile.absolutePath}")
                return@withContext destFile
            } catch (e: Exception) {
                Log.e(TAG, "Error saving audio for export", e)
                return@withContext null
            }
        }
    }
    
    /**
     * Clear all cached audio files
     */
    fun clearCache() {
        val cacheDir = File(context.cacheDir, AUDIO_CACHE_DIR)
        cacheDir.listFiles()?.forEach { it.delete() }
        audioCache.clear()
    }
}