package com.abaga129.tekisuto.model.yomitan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class YomitanDictionaryEntryTest {
    
    @Test
    fun testFromJsonArrayBasic() {
        // Create a simple JSON array representation of a dictionary entry
        val jsonArray = listOf(
            "単語",           // term [0]
            "たんご",         // reading [1]
            listOf("n"),    // tags [2]
            emptyList<String>(),  // rules [3]
            listOf("word"), // definitions [4]
            1,              // sequence [5]
            emptyList<String>(),  // term tags [6]
            emptyList<String>()   // reading tags [7]
        )
        
        val dictionaryId = 1L
        val entry = YomitanDictionaryEntry.fromJsonArray(jsonArray, dictionaryId)
        
        assertEquals("単語", entry.term)
        assertEquals("たんご", entry.reading)
        assertEquals("word", entry.definition)
        assertEquals("n", entry.partOfSpeech)
        assertFalse(entry.isHtmlContent)
        assertEquals(dictionaryId, entry.dictionaryId)
    }
    
    @Test
    fun testFromJsonArrayWithMultipleTags() {
        // Create a JSON array with multiple tags
        val jsonArray = listOf(
            "勉強",           // term
            "べんきょう",      // reading
            listOf("n", "vs"),  // tags
            emptyList<String>(),  // rules
            listOf("study"), // definitions
            1,              // sequence
            listOf("common"),  // term tags
            listOf("kana")   // reading tags
        )
        
        val dictionaryId = 1L
        val entry = YomitanDictionaryEntry.fromJsonArray(jsonArray, dictionaryId)
        
        assertEquals("勉強", entry.term)
        assertEquals("べんきょう", entry.reading)
        assertEquals("study", entry.definition)
        assertEquals("n, vs", entry.partOfSpeech)
        
        // Check that tags are combined correctly
        val expectedTags = listOf("n", "vs", "common", "kana")
        assertEquals(expectedTags, entry.tags)
    }
    
    @Test
    fun testFromJsonArrayWithMultipleDefinitions() {
        // Create a JSON array with multiple string definitions
        val jsonArray = listOf(
            "食べる",         // term
            "たべる",         // reading
            listOf("v1"),   // tags
            emptyList<String>(),  // rules
            listOf("to eat", "to consume"), // multiple definitions
            1,              // sequence
            emptyList<String>(),  // term tags
            emptyList<String>()   // reading tags
        )
        
        val dictionaryId = 1L
        val entry = YomitanDictionaryEntry.fromJsonArray(jsonArray, dictionaryId)
        
        assertEquals("食べる", entry.term)
        assertEquals("たべる", entry.reading)
        assertEquals("to eat\nto consume", entry.definition) // Definitions joined with newline
        assertEquals("v1", entry.partOfSpeech)
    }
    
    @Test
    fun testFromJsonArrayWithStructuredContent() {
        // Create a JSON array with structured content definition
        val structuredDefinition = mapOf(
            "content" to listOf(
                "This is ",
                mapOf(
                    "type" to "italic",
                    "content" to listOf("formatted")
                ),
                " text."
            )
        )
        
        val jsonArray = listOf(
            "例",             // term
            "れい",           // reading
            listOf("n"),     // tags
            emptyList<String>(),  // rules
            listOf(structuredDefinition), // structured definition
            1,              // sequence
            emptyList<String>(),  // term tags
            emptyList<String>()   // reading tags
        )
        
        val dictionaryId = 1L
        val entry = YomitanDictionaryEntry.fromJsonArray(jsonArray, dictionaryId)
        
        assertEquals("例", entry.term)
        assertEquals("れい", entry.reading)
        // The HTML converter is mocked in the test, so we're just checking if it processes the content
        assertTrue(entry.isHtmlContent)
    }
    
    @Test
    fun testFromJsonArrayWithMissingFields() {
        // Create a JSON array with missing fields
        val jsonArray = listOf(
            "言葉",           // term [0]
            null,            // missing reading [1]
            null,            // missing tags [2]
            null,            // missing rules [3]
            listOf("word")   // definitions [4]
            // missing sequence [5]
            // missing term tags [6]
            // missing reading tags [7]
        )
        
        val dictionaryId = 1L
        val entry = YomitanDictionaryEntry.fromJsonArray(jsonArray, dictionaryId)
        
        assertEquals("言葉", entry.term)
        assertEquals("", entry.reading) // Default to empty string
        assertEquals("word", entry.definition)
        assertEquals("", entry.partOfSpeech) // Default to empty string
        assertTrue(entry.tags.isEmpty()) // Default to empty list
    }
    
    @Test
    fun testParsingFromActualJson() {
        val json = """
            [
                ["単語", "たんご", ["n"], [], ["word"], 1, [], []],
                ["例文", "れいぶん", ["n"], [], ["example sentence"], 2, ["common"], []]
            ]
        """.trimIndent()
        
        // Parse the JSON to get a List<List<Any?>>
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
            
        val listOfAnyType = Types.newParameterizedType(List::class.java, Any::class.java)
        val listOfListOfAnyType = Types.newParameterizedType(List::class.java, listOfAnyType)
        val jsonAdapter: JsonAdapter<List<List<Any?>>> = moshi.adapter(listOfListOfAnyType)
        
        val entries = jsonAdapter.fromJson(json)
        
        // Convert the parsed JSON to DictionaryEntryEntity objects
        val dictionaryId = 1L
        val dictionaryEntries = entries?.map { entryArray ->
            YomitanDictionaryEntry.fromJsonArray(entryArray, dictionaryId)
        }
        
        // Verify the results
        assertEquals(2, dictionaryEntries?.size)
        
        val firstEntry = dictionaryEntries?.get(0)
        assertEquals("単語", firstEntry?.term)
        assertEquals("たんご", firstEntry?.reading)
        assertEquals("word", firstEntry?.definition)
        assertEquals("n", firstEntry?.partOfSpeech)
        
        val secondEntry = dictionaryEntries?.get(1)
        assertEquals("例文", secondEntry?.term)
        assertEquals("れいぶん", secondEntry?.reading)
        assertEquals("example sentence", secondEntry?.definition)
        assertEquals("n", secondEntry?.partOfSpeech)
        
        // Check that term tags are processed correctly
        val secondEntryTags = secondEntry?.tags
        assertTrue(secondEntryTags?.contains("common") == true)
    }
    
    @Test
    fun testExtractPartOfSpeechFromTags() {
        // Test various part of speech tags
        val jsonArray = listOf(
            "テスト",          // term
            "てすと",          // reading
            listOf("adj-i", "v5r", "exp", "n-adv"),  // tags with multiple POS
            emptyList<String>(),  // rules
            listOf("test"),  // definitions
            1                // sequence
        )
        
        val dictionaryId = 1L
        val entry = YomitanDictionaryEntry.fromJsonArray(jsonArray, dictionaryId)
        
        // Should extract all POS tags
        assertEquals("adj-i, v5r, exp, n-adv", entry.partOfSpeech)
    }
    
    @Test
    fun testWithPosTagsInMultipleFields() {
        // Test with POS tags in different tag fields
        val jsonArray = listOf(
            "特別",           // term
            "とくべつ",        // reading
            listOf("adj-na"),  // entry tags
            emptyList<String>(),  // rules
            listOf("special"),  // definitions
            1,              // sequence
            listOf("n"),    // term tags
            listOf("pos:adverbial")   // reading tags with explicit POS marker
        )
        
        val dictionaryId = 1L
        val entry = YomitanDictionaryEntry.fromJsonArray(jsonArray, dictionaryId)
        
        // Should extract and combine all POS tags
        assertEquals("adj-na, n, pos:adverbial", entry.partOfSpeech)
    }
}