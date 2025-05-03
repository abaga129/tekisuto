package com.abaga129.tekisuto.model.yomitan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class YomitanTermMetaEntryTest {
    
    @Test
    fun testFromJsonArrayWithDirectFrequency() {
        // Create a JSON array with direct frequency value
        val jsonArray = listOf(
            "単語",           // term [0]
            "たんご",         // reading [1]
            emptyList<String>(),  // tags [2]
            emptyList<String>(),  // rules [3]
            500,             // frequency [4]
            1                // sequence [5]
        )
        
        val dictionaryId = 1L
        val frequencyEntity = YomitanTermMetaEntry.fromJsonArray(jsonArray, dictionaryId)
        
        assertNotNull(frequencyEntity)
        assertEquals("単語", frequencyEntity?.word)
        assertEquals(500, frequencyEntity?.frequency)
        assertEquals(dictionaryId, frequencyEntity?.dictionaryId)
    }
    
    @Test
    fun testFromJsonArrayWithFrequencyInTags() {
        // Create a JSON array with frequency in tags
        val jsonArray = listOf(
            "学校",           // term [0]
            "がっこう",        // reading [1]
            listOf("freq:250", "common"),  // tags with frequency [2]
            emptyList<String>(),  // rules [3]
            null,            // no direct frequency [4]
            2                // sequence [5]
        )
        
        val dictionaryId = 1L
        val frequencyEntity = YomitanTermMetaEntry.fromJsonArray(jsonArray, dictionaryId)
        
        assertNotNull(frequencyEntity)
        assertEquals("学校", frequencyEntity?.word)
        assertEquals(250, frequencyEntity?.frequency)
        assertEquals(dictionaryId, frequencyEntity?.dictionaryId)
    }
    
    @Test
    fun testFromJsonArrayWithFrequencyAsString() {
        // Create a JSON array with frequency as string
        val jsonArray = listOf(
            "車",             // term [0]
            "くるま",          // reading [1]
            emptyList<String>(),  // tags [2]
            emptyList<String>(),  // rules [3]
            "750",           // frequency as string [4]
            3                // sequence [5]
        )
        
        val dictionaryId = 1L
        val frequencyEntity = YomitanTermMetaEntry.fromJsonArray(jsonArray, dictionaryId)
        
        assertNotNull(frequencyEntity)
        assertEquals("車", frequencyEntity?.word)
        assertEquals(750, frequencyEntity?.frequency)
        assertEquals(dictionaryId, frequencyEntity?.dictionaryId)
    }
    
    @Test
    fun testFromJsonArrayWithMultipleFrequencyTags() {
        // Create a JSON array with multiple frequency-related tags
        val jsonArray = listOf(
            "時間",           // term [0]
            "じかん",          // reading [1]
            listOf("rank:100", "frequency:200", "freq:300"),  // multiple frequency tags [2]
            emptyList<String>(),  // rules [3]
            null,            // no direct frequency [4]
            4                // sequence [5]
        )
        
        val dictionaryId = 1L
        val frequencyEntity = YomitanTermMetaEntry.fromJsonArray(jsonArray, dictionaryId)
        
        assertNotNull(frequencyEntity)
        assertEquals("時間", frequencyEntity?.word)
        // Should use the first matching frequency found (100)
        assertEquals(100, frequencyEntity?.frequency)
    }
    
    @Test
    fun testFromJsonArrayWithNoFrequency() {
        // Create a JSON array with no frequency information
        val jsonArray = listOf(
            "猫",             // term [0]
            "ねこ",           // reading [1]
            listOf("common", "animal"),  // tags without frequency [2]
            emptyList<String>(),  // rules [3]
            null,            // no frequency [4]
            5                // sequence [5]
        )
        
        val dictionaryId = 1L
        val frequencyEntity = YomitanTermMetaEntry.fromJsonArray(jsonArray, dictionaryId)
        
        // Should return null since no frequency information is available
        assertNull(frequencyEntity)
    }
    
    @Test
    fun testParsingFromActualJson() {
        val json = """
            [
                ["単語", "たんご", ["freq:100"], [], 100, 1],
                ["例文", "れいぶん", ["common"], [], 200, 2],
                ["無意味", "むいみ", ["rare"], [], null, 3]
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
        
        // Convert the parsed JSON to WordFrequencyEntity objects
        val dictionaryId = 1L
        val frequencyEntities = entries?.mapNotNull { entryArray ->
            YomitanTermMetaEntry.fromJsonArray(entryArray, dictionaryId)
        }
        
        // Verify the results
        assertEquals(2, frequencyEntities?.size)  // Only 2 entries have frequency data
        
        val firstEntity = frequencyEntities?.get(0)
        assertEquals("単語", firstEntity?.word)
        assertEquals(100, firstEntity?.frequency)
        
        val secondEntity = frequencyEntities?.get(1)
        assertEquals("例文", secondEntity?.word)
        assertEquals(200, secondEntity?.frequency)
    }
    
    @Test
    fun testFromJsonArrayWithMissingTerm() {
        // Create a JSON array with missing term
        val jsonArray = listOf(
            null,            // missing term [0]
            "よみ",           // reading [1]
            emptyList<String>(),  // tags [2]
            emptyList<String>(),  // rules [3]
            500,             // frequency [4]
            6                // sequence [5]
        )
        
        val dictionaryId = 1L
        val frequencyEntity = YomitanTermMetaEntry.fromJsonArray(jsonArray, dictionaryId)
        
        // Should return null since term is missing
        assertNull(frequencyEntity)
    }
    
    // Tests for the new frequency format
    
    @Test
    fun testFrequencyFormatWithDirectValue() {
        // Format: ["term", "freq", numericValue]
        val jsonArray = listOf(
            "水",             // term [0]
            "freq",          // type [1]
            123              // frequency value [2]
        )
        
        val dictionaryId = 1L
        val frequencyEntity = YomitanTermMetaEntry.fromJsonArray(jsonArray, dictionaryId)
        
        assertNotNull(frequencyEntity)
        assertEquals("水", frequencyEntity?.word)
        assertEquals(123, frequencyEntity?.frequency)
        assertEquals(dictionaryId, frequencyEntity?.dictionaryId)
    }
    
    @Test
    fun testFrequencyFormatWithStringValue() {
        // Format: ["term", "freq", "456"]
        val jsonArray = listOf(
            "空",             // term [0]
            "freq",          // type [1]
            "456"            // frequency value as string [2]
        )
        
        val dictionaryId = 1L
        val frequencyEntity = YomitanTermMetaEntry.fromJsonArray(jsonArray, dictionaryId)
        
        assertNotNull(frequencyEntity)
        assertEquals("空", frequencyEntity?.word)
        assertEquals(456, frequencyEntity?.frequency)
        assertEquals(dictionaryId, frequencyEntity?.dictionaryId)
    }
    
    @Test
    fun testFrequencyFormatWithObjectValue() {
        // Format: ["term", "freq", {"value": 789, "displayValue": "789㋕"}]
        val jsonArray = listOf(
            "山",             // term [0]
            "freq",          // type [1]
            mapOf(            // frequency as an object
                "value" to 789,
                "displayValue" to "789㋕"
            )
        )
        
        val dictionaryId = 1L
        val frequencyEntity = YomitanTermMetaEntry.fromJsonArray(jsonArray, dictionaryId)
        
        assertNotNull(frequencyEntity)
        assertEquals("山", frequencyEntity?.word)
        assertEquals(789, frequencyEntity?.frequency)
        assertEquals(dictionaryId, frequencyEntity?.dictionaryId)
    }
    
    @Test
    fun testFrequencyFormatWithObjectStringValue() {
        // Format: ["term", "freq", {"value": "1024", "displayValue": "1024㋕"}]
        val jsonArray = listOf(
            "川",             // term [0]
            "freq",          // type [1]
            mapOf(            // frequency as an object with string value
                "value" to "1024",
                "displayValue" to "1024㋕"
            )
        )
        
        val dictionaryId = 1L
        val frequencyEntity = YomitanTermMetaEntry.fromJsonArray(jsonArray, dictionaryId)
        
        assertNotNull(frequencyEntity)
        assertEquals("川", frequencyEntity?.word)
        assertEquals(1024, frequencyEntity?.frequency)
        assertEquals(dictionaryId, frequencyEntity?.dictionaryId)
    }
    
    @Test
    fun testParsingFrequencyFormatFromActualJson() {
        val json = """
            [
                ["山", "freq", 100],
                ["川", "freq", {"value": 200, "displayValue": "200㋕"}],
                ["空", "freq", "300"],
                ["火", "other", 400],
                ["水", "freq", {"otherProperty": 500}]
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
        
        // Convert the parsed JSON to WordFrequencyEntity objects
        val dictionaryId = 1L
        val frequencyEntities = entries?.mapNotNull { entryArray ->
            YomitanTermMetaEntry.fromJsonArray(entryArray, dictionaryId)
        }
        
        // Verify the results
        assertEquals(3, frequencyEntities?.size)  // Only 3 valid frequency entries (the last two should be skipped)
        
        val firstEntity = frequencyEntities?.get(0)
        assertEquals("山", firstEntity?.word)
        assertEquals(100, firstEntity?.frequency)
        
        val secondEntity = frequencyEntities?.get(1)
        assertEquals("川", secondEntity?.word)
        assertEquals(200, secondEntity?.frequency)
        
        val thirdEntity = frequencyEntities?.get(2)
        assertEquals("空", thirdEntity?.word)
        assertEquals(300, thirdEntity?.frequency)
    }
}