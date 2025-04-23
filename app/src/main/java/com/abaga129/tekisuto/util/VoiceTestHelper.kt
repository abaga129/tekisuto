package com.abaga129.tekisuto.util

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Helper class for testing voices
 */
class VoiceTestHelper(private val context: Context) {
    
    private val speechService = SpeechService(context)
    
    /**
     * Test a voice by generating and playing a sample text
     * 
     * @param voiceName The name of the voice to test
     * @param languageCode The language code for the text
     * @return True if successful, false otherwise
     */
    suspend fun testVoice(voiceName: String, languageCode: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Save the voice temporarily for testing
                val originalVoice = speechService.getSelectedVoice(languageCode)
                speechService.saveVoiceSelection(languageCode, voiceName)
                
                // Sample text based on language
                val sampleText = getSampleText(languageCode)
                
                // Generate speech
                val audioFile = speechService.generateSpeech(sampleText, languageCode)
                
                // Restore original voice selection
                if (originalVoice != null) {
                    speechService.saveVoiceSelection(languageCode, originalVoice)
                }
                
                if (audioFile != null) {
                    // Play the audio
                    withContext(Dispatchers.Main) {
                        speechService.playAudio(audioFile)
                        Toast.makeText(context, "Playing test voice...", Toast.LENGTH_SHORT).show()
                    }
                    return@withContext true
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to generate test audio", Toast.LENGTH_SHORT).show()
                    }
                    return@withContext false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error testing voice: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                return@withContext false
            }
        }
    }
    
    /**
     * Get a sample text for testing based on language
     */
    private fun getSampleText(languageCode: String): String {
        return when (languageCode) {
            "ja" -> "こんにちは、これは音声テストです。" // Japanese
            "zh" -> "你好，这是语音测试。" // Chinese
            "ko" -> "안녕하세요, 이것은 음성 테스트입니다." // Korean
            "hi" -> "नमस्ते, यह एक वॉयस टेस्ट है।" // Hindi
            "es" -> "Hola, esta es una prueba de voz." // Spanish
            "fr" -> "Bonjour, ceci est un test vocal." // French
            "de" -> "Hallo, dies ist ein Sprachtest." // German
            "it" -> "Ciao, questo è un test vocale." // Italian
            "ru" -> "Привет, это тест голоса." // Russian
            else -> "Hello, this is a voice test." // English
        }
    }
}