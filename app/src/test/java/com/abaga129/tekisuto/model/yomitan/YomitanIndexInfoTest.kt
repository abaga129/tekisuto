package com.abaga129.tekisuto.model.yomitan

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YomitanIndexInfoTest {
    
    @Test
    fun testParseBasicIndexInfo() {
        val json = """
            {"title":"JMdict","format":3,"revision":"JMdict1","sequenced":true}
        """.trimIndent()
        
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(YomitanIndexInfo::class.java)
        
        val indexInfo = adapter.fromJson(json)
        
        assertNotNull(indexInfo)
        assertEquals("JMdict", indexInfo?.title)
        assertEquals(3, indexInfo?.format)
        assertEquals("JMdict1", indexInfo?.revision)
        assertTrue(indexInfo?.sequenced == true)
    }
    
    @Test
    fun testParseFullIndexInfo() {
        val json = """
            {
                "title": "Jitendex [2023-12-12]",
                "author": "stephenmk",
                "sequenced": true,
                "format": 3,
                "url": "jitendex.org",
                "revision": "3.1",
                "attribution": "© CC BY-SA 4.0 Stephen Kraus 2023",
                "sourceLanguage": "ja",
                "targetLanguage": "en",
                "isUpdatable": true,
                "indexUrl": "https://example.com/dictionary/index.json",
                "downloadUrl": "https://example.com/dictionary/download.zip"
            }
        """.trimIndent()
        
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(YomitanIndexInfo::class.java)
        
        val indexInfo = adapter.fromJson(json)
        
        assertNotNull(indexInfo)
        assertEquals("Jitendex [2023-12-12]", indexInfo?.title)
        assertEquals("stephenmk", indexInfo?.author)
        assertEquals(3, indexInfo?.format)
        assertEquals("3.1", indexInfo?.revision)
        assertEquals("jitendex.org", indexInfo?.url)
        assertEquals("© CC BY-SA 4.0 Stephen Kraus 2023", indexInfo?.attribution)
        assertEquals("ja", indexInfo?.sourceLanguage)
        assertEquals("en", indexInfo?.targetLanguage)
        assertTrue(indexInfo?.isUpdatable == true)
        assertEquals("https://example.com/dictionary/index.json", indexInfo?.indexUrl)
        assertEquals("https://example.com/dictionary/download.zip", indexInfo?.downloadUrl)
    }
    
    @Test
    fun testConvertToDictionaryMetadataEntity() {
        val indexInfo = YomitanIndexInfo(
            title = "Test Dictionary",
            format = 3,
            revision = "1.0",
            sequenced = true,
            author = "Test Author",
            description = "Test Description",
            sourceLanguage = "ja",
            targetLanguage = "en"
        )
        
        val entity = indexInfo.toDictionaryMetadataEntity()
        
        assertEquals("Test Dictionary", entity.title)
        assertEquals("Test Author", entity.author)
        assertEquals("Test Description", entity.description)
        assertEquals("ja", entity.sourceLanguage)
        assertEquals("en", entity.targetLanguage)
        assertEquals(0, entity.entryCount) // Default value
        assertEquals(0, entity.priority)   // Default value
    }
    
    @Test
    fun testParseWithMissingOptionalFields() {
        val json = """
            {"title":"Minimal Dictionary","format":3,"revision":"1.0","sequenced":false}
        """.trimIndent()
        
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(YomitanIndexInfo::class.java)
        
        val indexInfo = adapter.fromJson(json)
        
        assertNotNull(indexInfo)
        assertEquals("Minimal Dictionary", indexInfo?.title)
        assertEquals(null, indexInfo?.author)
        assertEquals(null, indexInfo?.description)
        assertEquals(null, indexInfo?.sourceLanguage)
        assertEquals(null, indexInfo?.targetLanguage)
        
        // Test conversion with minimal data
        val entity = indexInfo?.toDictionaryMetadataEntity()
        
        assertNotNull(entity)
        assertEquals("Minimal Dictionary", entity?.title)
        assertEquals("", entity?.author) // Default to empty string
        assertEquals("", entity?.description) // Default to empty string
        assertEquals("", entity?.sourceLanguage) // Default to empty string
        assertEquals("", entity?.targetLanguage) // Default to empty string
    }
}