package com.abaga129.tekisuto.util

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.preference.PreferenceManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

private const val TAG = "OcrHelper"

/**
 * Helper class to handle OCR operations using ML Kit
 */
class OcrHelper(private val context: Context) {

    private var textRecognizer: TextRecognizer
    
    init {
        // Get the preferred language from SharedPreferences
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val language = sharedPreferences.getString("ocr_language", "latin") ?: "latin"
        
        // Create the appropriate text recognizer based on the language preference
        textRecognizer = when (language) {
            "chinese" -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            "devanagari" -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
            "japanese" -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            "korean" -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            else -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        }
        
        Log.d(TAG, "Initialized OCR with language: $language")
    }

    /**
     * Recognize text from a bitmap image
     *
     * @param bitmap The bitmap image to analyze
     * @param callback Callback function with the recognized text
     */
    fun recognizeText(bitmap: Bitmap, callback: (String) -> Unit) {
        // Check if the language preference has changed and update recognizer if needed
        refreshRecognizerIfNeeded()
        
        val image = InputImage.fromBitmap(bitmap, 0)

        textRecognizer.process(image)
            .addOnSuccessListener { text ->
                val recognizedText = buildRecognizedText(text)
                Log.d(TAG, "Text recognition successful: ${text.textBlocks.size} blocks found")
                callback(recognizedText)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error during text recognition", e)
                callback("Error recognizing text: ${e.message}")
            }
    }
    
    /**
     * Refresh the text recognizer if the language preference has changed
     */
    private fun refreshRecognizerIfNeeded() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val currentLanguage = sharedPreferences.getString("ocr_language", "latin") ?: "latin"
        
        // Create a new recognizer if needed
        val newRecognizer = when (currentLanguage) {
            "chinese" -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            "devanagari" -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
            "japanese" -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            "korean" -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            else -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        }
        
        textRecognizer = newRecognizer
        Log.d(TAG, "Using OCR with language: $currentLanguage")
    }

    /**
     * Build formatted text from ML Kit's Text object
     */
    private fun buildRecognizedText(text: Text): String {
        val stringBuilder = StringBuilder()

        for (textBlock in text.textBlocks) {
            for (line in textBlock.lines) {
                stringBuilder.append(line.text).append("\n")
                
                // Log elements for debugging
                for (element in line.elements) {
                    Log.d(TAG, "Element text: ${element.text}")
                }
            }
            stringBuilder.append("\n")
        }

        return stringBuilder.toString().trim()
    }
    
    /**
     * Extract individual words from the text for dictionary matching
     * 
     * @param text The OCR text result
     * @return List of individual words
     */
    fun extractWords(text: String): List<String> {
        return text.split(Regex("[\\s,.。、!?]+"))
            .filter { it.isNotEmpty() }
            .distinct()
    }
}