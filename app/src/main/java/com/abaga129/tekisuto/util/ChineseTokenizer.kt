package com.abaga129.tekisuto.util

import android.util.Log
import com.huaban.analysis.jieba.JiebaSegmenter
import com.huaban.analysis.jieba.SegToken
import java.util.concurrent.ConcurrentHashMap

/**
 * Utility class for Chinese text tokenization using Jieba
 */
class ChineseTokenizer {
    companion object {
        private const val TAG = "ChineseTokenizer"
        
        // Cache the tokenizer instance (it's relatively expensive to create)
        private val tokenizer by lazy {
            try {
                JiebaSegmenter()
            } catch (e: Exception) {
                Log.e(TAG, "Error creating Jieba tokenizer", e)
                null
            }
        }
        
        // Cache tokenization results to improve performance
        private val tokenCache = ConcurrentHashMap<String, List<String>>()
        
        /**
         * Tokenize Chinese text into words
         * 
         * @param text The text to tokenize
         * @return List of words
         */
        fun tokenize(text: String): List<String> {
            // Check cache first
            tokenCache[text]?.let { return it }
            
            return try {
                // Get tokenizer instance
                val tokenizerInstance = tokenizer ?: return emptyList()
                
                // Use Jieba tokenizer in search mode for better segmentation
                // of unknown words and names
                val tokens = tokenizerInstance.process(text, JiebaSegmenter.SegMode.SEARCH)
                
                // Log basic info for debugging
                Log.d(TAG, "Tokenized ${tokens.size} tokens from: $text")
                
                // Extract meaningful words
                val words = mutableListOf<String>()
                for (token in tokens) {
                    val word = token.word
                    
                    // Only include meaningful words
                    if (isImportantWord(token)) {
                        words.add(word)
                        
                        if (isDEBUG) {
                            Log.d(TAG, "Token: $word, Start: ${token.startOffset}, End: ${token.endOffset}")
                        }
                    }
                }
                
                // Cache the result
                if (words.isNotEmpty()) {
                    tokenCache[text] = words
                }
                
                Log.d(TAG, "Meaningful words: ${words.joinToString(", ")}")
                words
            } catch (e: Exception) {
                Log.e(TAG, "Error tokenizing Chinese text", e)
                emptyList()
            }
        }
        
        /**
         * Determines if a token represents an important word we want to include
         */
        private fun isImportantWord(token: SegToken): Boolean {
            val word = token.word
            
            // Skip empty tokens
            if (word.isEmpty()) return false
            
            // Skip single character tokens (often not meaningful on their own)
            // except when they're numerals or specific characters with meaning
            if (word.length == 1) {
                val firstChar = word[0]
                
                // Digits should be kept
                if (firstChar.isDigit()) return true
                
                // Chinese punctuation should be skipped
                // Common punctuation in Chinese
                val punctuation = setOf('。', '，', '、', '：', '；', '！', '？', 
                                        '"', '"', '\'', '\'', '（', '）', '《', '》')
                if (punctuation.contains(firstChar)) return false
                
                // Single-character words that are common conjunctions, particles, etc.
                val skipChars = setOf('的', '地', '得', '了', '着', '过', '和', '与', 
                                      '而', '或', '且', '但', '却', '所', '以', '之')
                if (skipChars.contains(firstChar)) return false
            }
            
            return true
        }
        
        /**
         * Check if text is likely Chinese
         * This is a simple heuristic and not 100% accurate
         * 
         * @param text The text to check
         * @return True if the text is likely Chinese
         */
        fun isLikelyChinese(text: String): Boolean {
            // Check for Chinese characters (mainly Hanzi range)
            // CJK Unified Ideographs (common Chinese characters)
            val chinesePattern = Regex("[\\u4E00-\\u9FFF]+")
            
            // Calculate percentage of Chinese characters
            val chineseMatches = chinesePattern.findAll(text)
            val chineseChars = chineseMatches.sumOf { it.value.length }
            
            // If the text is empty, return false
            if (text.isEmpty()) return false
            
            // If more than 15% of text is Chinese, consider it Chinese
            val ratio = chineseChars.toFloat() / text.length
            val isChinese = ratio > 0.15
            
            Log.d(TAG, "Chinese detection: $text -> $isChinese (ratio: $ratio)")
            return isChinese
        }
        
        /**
         * Process text that might contain vertical Chinese
         * Vertical Chinese reads top to bottom, right to left
         */
        fun processVerticalChineseText(text: String): String {
            val lines = text.split("\n")
            if (lines.size <= 1) return text
            
            val sb = StringBuilder()
            val chinesePattern = Regex("[\\u4E00-\\u9FFF]")
            var currentColumn = StringBuilder()
            
            // Process similar to vertical Japanese text
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
                
                // If line is very short and has Chinese characters, it's likely part of a vertical column
                if (line.length <= 4 && chinesePattern.containsMatchIn(line)) {
                    // Add to current column without spaces (Chinese doesn't use spaces)
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
            Log.d(TAG, "Processed vertical Chinese text: Original length: ${text.length}, Processed length: ${result.length}")
            
            return result
        }
        
        /**
         * Clear the token cache to free memory
         */
        fun clearCache() {
            tokenCache.clear()
        }
        
        // Enable more detailed logging in debug builds
        private const val isDEBUG = false
    }
}