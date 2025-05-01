package com.abaga129.tekisuto.util

import org.junit.Assert.assertEquals
import org.junit.Test

class StructuredContentHtmlConverterTest {
    
    @Test
    fun testConvertPlainText() {
        val content = listOf("This is plain text.")
        val result = StructuredContentHtmlConverter.convertToHtml(content)
        
        assertEquals("This is plain text.", result)
    }
    
    @Test
    fun testConvertWithBasicFormatting() {
        val content = listOf(
            "This is ",
            mapOf(
                "type" to "bold",
                "content" to listOf("bold")
            ),
            " and ",
            mapOf(
                "type" to "italic",
                "content" to listOf("italic")
            ),
            " text."
        )
        
        val result = StructuredContentHtmlConverter.convertToHtml(content)
        val expected = "This is <b>bold</b> and <i>italic</i> text."
        
        assertEquals(expected, result)
    }
    
    @Test
    fun testConvertWithNestedFormatting() {
        val content = listOf(
            "This has ",
            mapOf(
                "type" to "bold",
                "content" to listOf(
                    "bold text with ",
                    mapOf(
                        "type" to "italic",
                        "content" to listOf("nested italic")
                    )
                )
            ),
            " formatting."
        )
        
        val result = StructuredContentHtmlConverter.convertToHtml(content)
        val expected = "This has <b>bold text with <i>nested italic</i></b> formatting."
        
        assertEquals(expected, result)
    }
    
    @Test
    fun testConvertWithKanjiAndKana() {
        val content = listOf(
            mapOf(
                "type" to "kanji",
                "text" to "漢字"
            ),
            " and ",
            mapOf(
                "type" to "kana",
                "text" to "かな"
            )
        )
        
        val result = StructuredContentHtmlConverter.convertToHtml(content)
        val expected = "<span class=\"kanji\">漢字</span> and <span class=\"kana\">かな</span>"
        
        assertEquals(expected, result)
    }
    
    @Test
    fun testConvertWithRuby() {
        val content = listOf(
            "Japanese uses ",
            mapOf(
                "type" to "ruby",
                "content" to listOf("漢字"),
                "ruby" to "かんじ"
            ),
            " for many words."
        )
        
        val result = StructuredContentHtmlConverter.convertToHtml(content)
        val expected = "Japanese uses <ruby>漢字<rt>かんじ</rt></ruby> for many words."
        
        assertEquals(expected, result)
    }
    
    @Test
    fun testConvertWithLink() {
        val content = listOf(
            "Check out this ",
            mapOf(
                "type" to "link",
                "content" to listOf("website"),
                "url" to "https://example.com"
            ),
            " for more information."
        )
        
        val result = StructuredContentHtmlConverter.convertToHtml(content)
        val expected = "Check out this <a href=\"https://example.com\">website</a> for more information."
        
        assertEquals(expected, result)
    }
    
    @Test
    fun testConvertWithDivAndSpan() {
        val content = listOf(
            mapOf(
                "type" to "div",
                "class" to "example",
                "content" to listOf(
                    "This is wrapped in a div with ",
                    mapOf(
                        "type" to "span",
                        "class" to "highlight",
                        "content" to listOf("highlighted")
                    ),
                    " text."
                )
            )
        )
        
        val result = StructuredContentHtmlConverter.convertToHtml(content)
        val expected = "<div class=\"example\">This is wrapped in a div with <span class=\"highlight\">highlighted</span> text.</div>"
        
        assertEquals(expected, result)
    }
    
    @Test
    fun testConvertWithLineBreakAndHorizontalRule() {
        val content = listOf(
            "First line",
            mapOf("type" to "br"),
            "Second line",
            mapOf("type" to "hr"),
            "Third line"
        )
        
        val result = StructuredContentHtmlConverter.convertToHtml(content)
        val expected = "First line<br/>Second line<hr/>Third line"
        
        assertEquals(expected, result)
    }
    
    @Test
    fun testConvertWithImage() {
        val content = listOf(
            "Here's an image: ",
            mapOf(
                "type" to "image",
                "url" to "https://example.com/image.jpg"
            )
        )
        
        val result = StructuredContentHtmlConverter.convertToHtml(content)
        val expected = "Here's an image: <img src=\"https://example.com/image.jpg\" alt=\"Image\" />"
        
        assertEquals(expected, result)
    }
    
    @Test
    fun testConvertWithHeading() {
        val content = listOf(
            mapOf(
                "type" to "heading",
                "content" to listOf("Section Title")
            ),
            " - This is a section."
        )
        
        val result = StructuredContentHtmlConverter.convertToHtml(content)
        val expected = "<h3>Section Title</h3> - This is a section."
        
        assertEquals(expected, result)
    }
    
    @Test
    fun testConvertWithHtmlEscaping() {
        val content = listOf(
            "This should be escaped: <script>alert('test')</script> & < > \" '"
        )
        
        val result = StructuredContentHtmlConverter.convertToHtml(content)
        val expected = "This should be escaped: &lt;script&gt;alert(&#39;test&#39;)&lt;/script&gt; &amp; &lt; &gt; &quot; &#39;"
        
        assertEquals(expected, result)
    }
    
    @Test
    fun testHandlingOfUnknownTypes() {
        val content = listOf(
            "This has an ",
            mapOf(
                "type" to "unknown",
                "content" to listOf("unknown element")
            ),
            " in it."
        )
        
        val result = StructuredContentHtmlConverter.convertToHtml(content)
        val expected = "This has an unknown element in it."
        
        assertEquals(expected, result)
    }
    
    @Test
    fun testHandlingErrorGracefully() {
        val content = listOf(
            "Normal text",
            null,  // This should be handled gracefully
            "More text"
        )
        
        val result = StructuredContentHtmlConverter.convertToHtml(content)
        // If it doesn't crash, the test passes
        assert(result.contains("Normal text"))
        assert(result.contains("More text"))
    }
}