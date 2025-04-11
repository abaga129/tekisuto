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
        // Check if the text is likely vertical Japanese
        val isVerticalJapanese = isLikelyVerticalJapanese(text)
        
        if (isVerticalJapanese) {
            Log.d(TAG, "Detected vertical Japanese text - applying special processing")
            return processVerticalJapaneseText(text)
        }
        
        // Standard horizontal text processing
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

        val rawText = stringBuilder.toString().trim()
        return correctOcrErrors(rawText)
    }
    
    /**
     * Detects if the recognized text is likely vertical Japanese
     * based on the arrangement and content of text blocks and characters
     */
    private fun isLikelyVerticalJapanese(text: Text): Boolean {
        // If not Japanese language, return false immediately
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val language = sharedPreferences.getString("ocr_language", "latin") ?: "latin"
        if (language != "japanese") {
            return false
        }
        
        // Check if there are multiple text blocks arranged vertically
        val blocks = text.textBlocks
        if (blocks.size < 2) return false
        
        // Count vertical vs horizontal arrangements
        var verticalCount = 0
        var horizontalCount = 0
        
        // Check if the blocks are arranged in vertical columns (right to left)
        val sortedBlocksX = blocks.sortedBy { it.boundingBox?.centerX() ?: 0 }
        
        // Check text content - Japanese text will have hiragana/katakana/kanji
        var hasJapaneseChars = false
        val japanesePattern = Regex("[\u3040-\u309F\u30A0-\u30FF\u4E00-\u9FFF]+")
        
        for (block in blocks) {
            // Check for Japanese characters
            if (japanesePattern.containsMatchIn(block.text)) {
                hasJapaneseChars = true
            }
            
            // Check if lines are stacked vertically with minimal horizontal overlap
            if (block.lines.size > 1) {
                val lines = block.lines
                // Check if the average width of characters is close to their height
                // (Japanese characters in vertical text are often square-ish)
                val charWidth = block.boundingBox?.width()?.toFloat() ?: 0f
                val charHeight = block.boundingBox?.height()?.toFloat() ?: 0f
                val charRatio = if (charHeight > 0) charWidth / charHeight else 0f
                
                // Vertical text blocks tend to be taller than wide
                if (charRatio < 0.7) {
                    verticalCount++
                } else {
                    horizontalCount++
                }
            }
        }
        
        // If no Japanese characters, not likely vertical Japanese
        if (!hasJapaneseChars) return false
        
        // Decision based on various factors
        return verticalCount > horizontalCount || 
              (sortedBlocksX.size >= 2 && hasJapaneseChars)
    }
    
    /**
     * Process vertical Japanese text to reorder it correctly
     * In vertical Japanese, text flows top to bottom, right to left
     */
    private fun processVerticalJapaneseText(text: Text): String {
        // First, sort blocks from right to left (vertical Japanese columns)
        val blocksRightToLeft = text.textBlocks.sortedByDescending { it.boundingBox?.centerX() ?: 0 }
        
        val stringBuilder = StringBuilder()
        
        // Process each block (column) from right to left
        for (block in blocksRightToLeft) {
            // For each block, process lines from top to bottom
            val linesTopToBottom = block.lines.sortedBy { it.boundingBox?.centerY() ?: 0 }
            
            for (line in linesTopToBottom) {
                stringBuilder.append(line.text)
                // Don't add line break between lines in the same vertical column
                // Just concatenate the characters in the proper order
            }
            // Add a line break between columns
            stringBuilder.append("\n")
        }
        
        val processedText = stringBuilder.toString().trim()
        Log.d(TAG, "Processed vertical Japanese text: $processedText")
        
        return correctOcrErrors(processedText)
    }
    
    /**
     * Apply corrections to common OCR errors
     */
    private fun correctOcrErrors(text: String): String {
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
            Log.d(TAG, "OCR text corrected: 0→O and 1→I replacements applied")
        }
        
        return corrected
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