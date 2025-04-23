package com.abaga129.tekisuto.ui.ocr

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import com.abaga129.tekisuto.util.WordTokenizerFlow

/**
 * Handles text processing operations for OCR results including 
 * text formatting, tokenization, and clickable text generation.
 */
class OCRTextProcessor(private val context: Context) {
    
    private val TAG = "OCRTextProcessor"
    
    /**
     * Creates clickable word buttons from OCR text
     *
     * @param container ViewGroup to add the clickable words to
     * @param text OCR text to process
     * @param ocrLanguage The detected language of the OCR text
     * @param onWordClick Callback for when a word is clicked
     * @param updateSearchField Function to update the search field with the selected word
     */
    fun setupClickableText(
        container: ViewGroup,
        text: String,
        ocrLanguage: String?,
        onWordClick: (String) -> Unit,
        updateSearchField: (String) -> Unit
    ) {
        // Handle vertical CJK text if detected
        val processedText = when {
            // For vertical Japanese text
            ocrLanguage == "japanese" && isVerticalJapaneseText(text) -> {
                Log.d(TAG, "Detected vertical Japanese text - processing for display")
                processVerticalJapaneseText(text)
            }
            // For vertical Chinese text
            ocrLanguage == "chinese" && isVerticalChineseText(text) -> {
                Log.d(TAG, "Detected vertical Chinese text - processing for display")
                com.abaga129.tekisuto.util.ChineseTokenizer.processVerticalChineseText(text)
            }
            // Default: use original text
            else -> text
        }

        // Create flow layout with word buttons
        WordTokenizerFlow.createClickableWordsFlow(
            context = context,
            parentViewGroup = container,
            text = processedText, // Use the processed text
            onWordClick = { word ->
                // Handle word click - lookup in dictionary
                onWordClick(word)
                // Populate the search field with the clicked word (lowercase)
                updateSearchField(word.lowercase())
                // Show a brief message
                Toast.makeText(context, "Looking up: $word", Toast.LENGTH_SHORT).show()
            },
            onWordLongClick = { word ->
                // Long click can still do dictionary lookup for consistency
                onWordClick(word)
                // Populate the search field with the clicked word (lowercase)
                updateSearchField(word.lowercase())
                true // Consume the event
            }
        )
    }

    /**
     * Detect if the text is likely vertical Japanese text
     * based on patterns common in vertical OCR output
     */
    fun isVerticalJapaneseText(text: String): Boolean {
        if (text.isEmpty()) return false

        // Japanese character pattern
        val japanesePattern = Regex("[\u3040-\u309F\u30A0-\u30FF\u4E00-\u9FFF]")

        // Check if text has Japanese characters
        if (!japanesePattern.containsMatchIn(text)) return false

        // If the text has a lot of short lines with Japanese characters,
        // it's likely vertical Japanese text
        val lines = text.split("\n")

        // If most lines are short (1-4 characters), it's likely vertical text
        val shortLines = lines.count { line ->
            line.length in 1..4 && japanesePattern.containsMatchIn(line)
        }

        val shortLineRatio = if (lines.isNotEmpty()) {
            shortLines.toFloat() / lines.size
        } else 0f

        Log.d(TAG, "Vertical Japanese detection: $shortLineRatio of lines are short")

        // If more than 50% of the lines are short Japanese lines, it's vertical
        return shortLineRatio > 0.5
    }

    /**
     * Detect if the text is likely vertical Chinese text
     * based on patterns common in vertical OCR output
     */
    fun isVerticalChineseText(text: String): Boolean {
        if (text.isEmpty()) return false

        // Chinese character pattern (mainly Hanzi)
        val chinesePattern = Regex("[\u4E00-\u9FFF]")

        // Check if text has Chinese characters
        if (!chinesePattern.containsMatchIn(text)) return false

        // If the text has a lot of short lines with Chinese characters,
        // it's likely vertical Chinese text
        val lines = text.split("\n")

        // If most lines are short (1-4 characters), it's likely vertical text
        val shortLines = lines.count { line ->
            line.length in 1..4 && chinesePattern.containsMatchIn(line)
        }

        val shortLineRatio = if (lines.isNotEmpty()) {
            shortLines.toFloat() / lines.size
        } else 0f

        Log.d(TAG, "Vertical Chinese detection: $shortLineRatio of lines are short")

        // If more than 50% of the lines are short Chinese lines, it's vertical
        return shortLineRatio > 0.5
    }

    /**
     * Process vertical Japanese text to make it more readable
     * Recombines columns of text into proper paragraphs
     */
    fun processVerticalJapaneseText(text: String): String {
        val lines = text.split("\n")
        if (lines.size <= 1) return text

        val sb = StringBuilder()
        val japanesePattern = Regex("[\u3040-\u309F\u30A0-\u30FF\u4E00-\u9FFF]")
        var currentColumn = StringBuilder()

        // Group characters from vertically oriented text
        for (i in lines.indices) {
            val line = lines[i].trim()

            // Skip empty lines
            if (line.isEmpty()) {
                // If we have content in the current column, add it
                if (currentColumn.isNotEmpty()) {
                    sb.append(currentColumn.toString())
                    sb.append("\n")
                    currentColumn.clear()
                }
                continue
            }

            // If line is very short and has Japanese characters, it's likely part of a vertical column
            if (line.length <= 4 && japanesePattern.containsMatchIn(line)) {
                // Add to current column without spaces (Japanese doesn't use spaces)
                currentColumn.append(line)
            } else {
                // This is a normal horizontal line or a line break
                // Add any accumulated column content first
                if (currentColumn.isNotEmpty()) {
                    sb.append(currentColumn.toString())
                    sb.append("\n")
                    currentColumn.clear()
                }

                // Then add this line
                sb.append(line)
                sb.append("\n")
            }
        }

        // Add any remaining column content
        if (currentColumn.isNotEmpty()) {
            sb.append(currentColumn.toString())
        }

        val result = sb.toString().trim()
        Log.d(TAG, "Processed vertical Japanese text: \nOriginal: $text\nProcessed: $result")

        return result
    }
    
    /**
     * Highlight words in the tokenizer flow that match dictionary entries
     */
    fun highlightMatchedWords(matches: List<com.abaga129.tekisuto.database.DictionaryEntryEntity>) {
        // Clear all highlights first
        WordTokenizerFlow.clearHighlights()

        // Highlight matched words
        matches.forEach { entry ->
            WordTokenizerFlow.highlightWord(entry.term)
        }
    }
}