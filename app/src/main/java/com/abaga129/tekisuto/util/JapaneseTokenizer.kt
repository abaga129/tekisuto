package com.abaga129.tekisuto.util

import android.util.Log
import com.atilika.kuromoji.ipadic.Tokenizer
import java.util.concurrent.ConcurrentHashMap

/**
 * Utility class for Japanese text tokenization using Kuromoji
 */
class JapaneseTokenizer {
    companion object {
        private const val TAG = "JapaneseTokenizer"
        
        // Cache the tokenizer instance (it's expensive to create)
        private val tokenizer by lazy {
            try {
                Tokenizer()
            } catch (e: Exception) {
                Log.e(TAG, "Error creating Kuromoji tokenizer", e)
                null
            }
        }
        
        // Cache tokenization results to improve performance
        private val tokenCache = ConcurrentHashMap<String, List<String>>()
        
        /**
         * Tokenize Japanese text into words
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
                
                // Use Kuromoji tokenizer
                val tokens = tokenizerInstance.tokenize(text)
                
                // Log basic info for debugging
                Log.d(TAG, "Tokenized ${tokens.size} tokens from: $text")
                
                // Extract meaningful words
                val words = mutableListOf<String>()
                for (token in tokens) {
                    val surface = token.surface
                    val pos = token.partOfSpeechLevel1
                    
                    if (isDEBUG) {
                        Log.d(TAG, "Token: $surface, POS: $pos")
                    }
                    
                    // Only include meaningful words
                    if (isImportantWord(token)) {
                        words.add(surface)
                    }
                }
                
                // Cache the result
                if (words.isNotEmpty()) {
                    tokenCache[text] = words
                }
                
                Log.d(TAG, "Meaningful words: ${words.joinToString(", ")}")
                words
            } catch (e: Exception) {
                Log.e(TAG, "Error tokenizing Japanese text", e)
                emptyList()
            }
        }
        
        /**
         * Determines if a token represents an important word we want to include
         */
        private fun isImportantWord(token: com.atilika.kuromoji.ipadic.Token): Boolean {
            val surface = token.surface
            
            // Skip empty tokens
            if (surface.isEmpty()) return false
            
            // Get part of speech information
            val pos = token.partOfSpeechLevel1
            
            // Skip common particles, auxiliary verbs, conjunctions, and punctuation
            when (pos) {
                "助詞" -> return false // Particles like は, が, に, etc.
                "助動詞" -> return false // Auxiliary verbs
                "記号" -> return false // Symbols and punctuation
                "接続詞" -> return false // Conjunctions
                "BOS/EOS" -> return false // Beginning/End of sentence markers
            }
            
            // Skip single hiragana characters (often not meaningful on their own)
            if (surface.length == 1 && isHiragana(surface[0])) {
                return false
            }
            
            return true
        }
        
        /**
         * Check if a character is hiragana
         */
        private fun isHiragana(char: Char): Boolean {
            return char in '\u3040'..'\u309F'
        }
        
        /**
         * Check if text is likely Japanese
         * This is a simple heuristic and not 100% accurate
         * 
         * @param text The text to check
         * @return True if the text is likely Japanese
         */
        fun isLikelyJapanese(text: String): Boolean {
            // Check for Japanese characters (Hiragana, Katakana, Kanji)
            val japanesePattern = Regex("[\u3040-\u309F\u30A0-\u30FF\u4E00-\u9FFF]+")
            
            // Calculate percentage of Japanese characters
            val japaneseMatches = japanesePattern.findAll(text)
            val japaneseChars = japaneseMatches.sumOf { it.value.length }
            
            // If more than 15% of text is Japanese, consider it Japanese
            val ratio = japaneseChars.toFloat() / text.length
            val isJapanese = ratio > 0.15
            
            Log.d(TAG, "Japanese detection: $text -> $isJapanese (ratio: $ratio)")
            return isJapanese
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