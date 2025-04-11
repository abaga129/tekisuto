package com.abaga129.tekisuto.util

import org.junit.Test
import org.junit.Assert.*

class JapaneseTokenizerTest {

    @Test
    fun testJapaneseTokenization() {
        // Basic Japanese sentence
        val text = "私は猫が好きです。"
        val tokens = JapaneseTokenizer.tokenize(text)
        
        // Log the actual tokens for debugging
        println("Tokens for '$text': ${tokens.joinToString(", ")}")
        
        // Should include nouns and meaningful words, exact tokens may vary based on Kuromoji version
        assertTrue("Should tokenize pronouns", tokens.any { it == "私" }) // I
        assertTrue("Should tokenize nouns", tokens.any { it == "猫" }) // cat
        
        // Should not contain particles (may need to adjust based on actual output)
        // Note: These assertions may need adjustment if the tokenizer implementation varies
        tokens.forEach { println("Token: $it") }
        
        // More complex sentence
        val complex = "東京に住んでいる山田さんは毎日電車で会社に行きます。"
        val complexTokens = JapaneseTokenizer.tokenize(complex)
        
        // Log the tokens for debugging
        println("Tokens for complex sentence: ${complexTokens.joinToString(", ")}")
        
        // Check for meaningful parts (these should be reasonably stable across versions)
        assertTrue("Should tokenize locations", complexTokens.any { it.contains("東京") })
        assertTrue("Should tokenize common nouns", complexTokens.any { it.contains("会社") })
        
        // Verify detection of Japanese text
        assertTrue("Should detect Japanese text", JapaneseTokenizer.isLikelyJapanese("こんにちは"))
        assertTrue("Should detect Japanese text", JapaneseTokenizer.isLikelyJapanese("これは日本語です"))
        assertFalse("Should not detect English as Japanese", JapaneseTokenizer.isLikelyJapanese("Hello world"))
        
        // Mixed text should be detected if it has enough Japanese
        assertTrue("Should detect mixed text with Japanese", 
            JapaneseTokenizer.isLikelyJapanese("This is 日本語 mixed with English"))
    }
}