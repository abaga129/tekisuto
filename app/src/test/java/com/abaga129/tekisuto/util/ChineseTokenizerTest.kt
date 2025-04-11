package com.abaga129.tekisuto.util

import org.junit.Test
import org.junit.Assert.*

class ChineseTokenizerTest {

    @Test
    fun testChineseTokenization() {
        // Basic Chinese sentence
        val text = "我喜欢吃中国菜。"
        val tokens = ChineseTokenizer.tokenize(text)
        
        // Should tokenize to meaningful words: 我 (I), 喜欢 (like), 吃 (eat), 中国 (China), 菜 (food)
        println("Tokens for '$text': ${tokens.joinToString(", ")}")
        
        // Should include nouns and meaningful words
        assertTrue("Should tokenize pronouns", tokens.any { it == "我" }) // I
        assertTrue("Should tokenize verbs", tokens.any { it.contains("喜欢") }) // like
        assertTrue("Should tokenize country names", tokens.any { it.contains("中国") }) // China
        
        // More complex sentence
        val complex = "北京是中国的首都，有着悠久的历史。"
        val complexTokens = ChineseTokenizer.tokenize(complex)
        
        // Log tokens for debugging
        println("Tokens for complex sentence: ${complexTokens.joinToString(", ")}")
        
        // Check for meaningful words
        assertTrue("Should tokenize place names", complexTokens.any { it.contains("北京") }) // Beijing
        assertTrue("Should tokenize common nouns", complexTokens.any { it.contains("首都") }) // capital
        assertTrue("Should tokenize adjectives", complexTokens.any { it.contains("悠久") }) // long (time)
        
        // Verify detection of Chinese text
        assertTrue("Should detect Chinese text", ChineseTokenizer.isLikelyChinese("你好"))
        assertTrue("Should detect Chinese text", ChineseTokenizer.isLikelyChinese("这是中文"))
        assertFalse("Should not detect English as Chinese", ChineseTokenizer.isLikelyChinese("Hello world"))
        
        // Mixed text should be detected if it has enough Chinese
        assertTrue("Should detect mixed text with Chinese", 
            ChineseTokenizer.isLikelyChinese("This is 中文 mixed with English"))
    }
    
    @Test
    fun testVerticalChineseProcessing() {
        // Vertical Chinese text (simulated OCR output)
        val verticalText = """
            北
            京
            是
            中
            国
            的
            
            首
            都
        """.trimIndent()
        
        val processedText = ChineseTokenizer.processVerticalChineseText(verticalText)
        
        // Should combine into horizontal text
        assertTrue("Should combine vertical column", processedText.contains("北京是中国的"))
        assertTrue("Should handle multiple columns", processedText.contains("首都"))
        
        // More complex vertical text with multiple columns
        val complexVertical = """
            你
            好
            
            世
            界
        """.trimIndent()
        
        val processedComplex = ChineseTokenizer.processVerticalChineseText(complexVertical)
        
        // Should reconstruct proper phrases
        assertTrue("Should reconstruct proper phrases", 
            processedComplex.contains("你好") && processedComplex.contains("世界"))
        
        println("Original vertical: $complexVertical")
        println("Processed: $processedComplex")
    }
}