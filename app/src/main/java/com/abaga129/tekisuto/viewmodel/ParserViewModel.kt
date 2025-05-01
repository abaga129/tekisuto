package com.abaga129.tekisuto.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.abaga129.tekisuto.database.DictionaryEntryEntity
import com.abaga129.tekisuto.database.DictionaryMetadataEntity
import com.abaga129.tekisuto.database.DictionaryRepository
import com.abaga129.tekisuto.database.WordFrequencyEntity
import com.abaga129.tekisuto.model.yomitan.YomitanDictionaryEntry
import com.abaga129.tekisuto.model.yomitan.YomitanIndexInfo
import com.abaga129.tekisuto.model.yomitan.YomitanTermMetaEntry
import com.abaga129.tekisuto.util.YomitanDictionaryExtractor
import com.abaga129.tekisuto.util.JsonStreamParser
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import org.json.JSONArray
import org.json.JSONException

/**
 * ViewModel for handling dictionary parsing and importing operations
 */
class ParserViewModel private constructor() {

    companion object {
        private const val TAG = "ParserViewModel"
        private const val BATCH_SIZE = 100 // Process 100 entries at a time to save memory
        private const val STREAM_CHUNK_SIZE = 200 // Number of entries to read from file at once

        // Singleton instance
        @Volatile
        private var instance: ParserViewModel? = null

        fun getInstance(): ParserViewModel {
            return instance ?: synchronized(this) {
                instance ?: ParserViewModel().also { instance = it }
            }
        }
    }

    // Import progress tracking
    private val _importProgress = MutableLiveData<Int>()
    val importProgress: LiveData<Int> = _importProgress

    // Dictionary info display
    private val _dictionaryInfo = MutableLiveData<DictionaryInfo?>()
    val dictionaryInfo: LiveData<DictionaryInfo?> = _dictionaryInfo

    // Repository for database operations
    private lateinit var dictionaryRepository: DictionaryRepository

    // Counter for imported entries - used to limit logging
    private var entryCount = 0

    /**
     * Initialize repository - must be called before using import functionality
     */
    fun initRepository(repository: DictionaryRepository) {
        dictionaryRepository = repository
    }

    /**
     * Dictionary information data class
     */
    data class DictionaryInfo(
        val title: String,
        val revision: String,
        val format: Int,
        val author: String,
        val description: String,
        val sourceLanguage: String,
        val targetLanguage: String
    )

    /**
     * Imports a Yomitan dictionary from the given URI
     * @param context Android context
     * @param uri URI of the dictionary file (zip)
     * @return Pair<Boolean, Long> where first is success status and second is dictionaryId if successful
     */
    suspend fun importYomitanDictionary(context: Context, uri: Uri): Pair<Boolean, Long> {
        if (!::dictionaryRepository.isInitialized) {
            Log.e(TAG, "Repository not initialized. Call initRepository() first")
            return Pair(false, -1L)
        }

        try {
            _importProgress.postValue(0)
            
            // Extract the dictionary
            val extractedDir = withContext(Dispatchers.IO) {
                YomitanDictionaryExtractor.extractDictionary(context, uri)
            }
            
            if (extractedDir == null) {
                Log.e(TAG, "Failed to extract dictionary")
                return Pair(false, -1L)
            }
            
            // Parse the index.json file
            val indexFile = File(extractedDir, "index.json")
            val indexInfo = withContext(Dispatchers.IO) {
                YomitanDictionaryExtractor.parseIndexInfo(indexFile)
            }
            
            if (indexInfo == null) {
                Log.e(TAG, "Failed to parse index.json")
                YomitanDictionaryExtractor.cleanup(extractedDir)
                return Pair(false, -1L)
            }
            
            // Update dictionary info LiveData
            val dictInfo = DictionaryInfo(
                title = indexInfo.title,
                revision = indexInfo.revision,
                format = indexInfo.format,
                author = indexInfo.author ?: "",
                description = indexInfo.description ?: "",
                sourceLanguage = indexInfo.sourceLanguage ?: "",
                targetLanguage = indexInfo.targetLanguage ?: ""
            )
            _dictionaryInfo.postValue(dictInfo)
            
            // Save dictionary metadata to database
            val metadataEntity = indexInfo.toDictionaryMetadataEntity()
            val dictionaryId = dictionaryRepository.saveDictionaryMetadata(metadataEntity)
            
            if (dictionaryId <= 0) {
                Log.e(TAG, "Failed to save dictionary metadata")
                YomitanDictionaryExtractor.cleanup(extractedDir)
                return Pair(false, -1L)
            }
            
            // Find all term bank files and parse them
            val termBankFiles = YomitanDictionaryExtractor.findTermBankFiles(extractedDir)
            if (termBankFiles.isEmpty()) {
                Log.e(TAG, "No term bank files found")
                YomitanDictionaryExtractor.cleanup(extractedDir)
                return Pair(false, -1L)
            }
            
            // Setup Moshi for JSON parsing
            val moshi = Moshi.Builder().build()
            val listOfAnyType = Types.newParameterizedType(List::class.java, Any::class.java)
            val listOfListOfAnyType = Types.newParameterizedType(List::class.java, listOfAnyType)
            val jsonAdapter: JsonAdapter<List<List<Any?>>> = moshi.adapter(listOfListOfAnyType)
            
            // Initialize counters
            var totalEntryCount = 0
            var successfulEntryCount = 0
            
            // Process each term bank file
            for ((index, termBankFile) in termBankFiles.withIndex()) {
                val progress = ((index.toFloat() / termBankFiles.size) * 100).toInt()
                _importProgress.postValue(progress)
                
                try {
                    // Use JsonStreamParser to process the file incrementally
                    var fileEntryCount = 0
                    
                    fileEntryCount = JsonStreamParser.processJsonArrayFile(
                        file = termBankFile,
                        chunkSize = STREAM_CHUNK_SIZE
                    ) { chunk ->
                        // Process this chunk of entries
                        // Convert JSON entries to DictionaryEntryEntity objects
                        val dictionaryEntries = chunk.mapNotNull { entryArray ->
                            try {
                                YomitanDictionaryEntry.fromJsonArray(entryArray, dictionaryId)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error converting entry: ${e.message}")
                                null
                            }
                        }
                        
                        // Process in smaller batches for DB operations
                        val batches = dictionaryEntries.chunked(BATCH_SIZE)
                        
                        for (batch in batches) {
                            // Import batch to database
                            if (batch.isNotEmpty()) {
                                val importedCount = dictionaryRepository.importDictionaryEntries(batch)
                                successfulEntryCount += importedCount
                                
                                // Log progress periodically
                                if (successfulEntryCount / 1000 > entryCount / 1000) {
                                    Log.d(TAG, "Imported $successfulEntryCount entries so far")
                                    entryCount = successfulEntryCount
                                    
                                    // Update progress during import
                                    val intraFileProgress = ((index.toFloat() + 0.5f) / termBankFiles.size) * 100
                                    _importProgress.postValue(intraFileProgress.toInt())
                                }
                            }
                        }
                    }
                    
                    totalEntryCount += fileEntryCount
                    Log.d(TAG, "Processed term bank file: ${termBankFile.name}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing term bank file ${termBankFile.name}: ${e.message}")
                }
            }
            
            // Process term meta banks for frequency information
            val termMetaBankFiles = YomitanDictionaryExtractor.findTermMetaBankFiles(extractedDir)
            
            if (termMetaBankFiles.isNotEmpty()) {
                Log.d(TAG, "Found ${termMetaBankFiles.size} term meta bank files")
                
                var frequencyCount = 0
                
                // Process each term meta bank file
                for ((index, metaBankFile) in termMetaBankFiles.withIndex()) {
                    val progress = 80 + ((index.toFloat() / termMetaBankFiles.size) * 20).toInt()
                    _importProgress.postValue(progress)
                    
                    try {
                        // Use JsonStreamParser to process the file incrementally
                        JsonStreamParser.processJsonArrayFile(
                            file = metaBankFile,
                            chunkSize = STREAM_CHUNK_SIZE
                        ) { chunk ->
                            // Convert JSON entries to WordFrequencyEntity objects
                            val frequencyEntries = chunk.mapNotNull { entryArray ->
                                try {
                                    YomitanTermMetaEntry.fromJsonArray(entryArray, dictionaryId)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error converting frequency entry: ${e.message}")
                                    null
                                }
                            }
                            
                            // Process in smaller batches for DB operations
                            val batches = frequencyEntries.chunked(BATCH_SIZE)
                            
                            for (batch in batches) {
                                // Import batch to database
                                if (batch.isNotEmpty()) {
                                    val importedCount = dictionaryRepository.importWordFrequencies(batch)
                                    frequencyCount += importedCount
                                }
                            }
                        }
                        
                        Log.d(TAG, "Processed term meta bank file: ${metaBankFile.name}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing term meta bank file ${metaBankFile.name}: ${e.message}")
                    }
                }
                
                Log.d(TAG, "Imported $frequencyCount frequency entries")
            } else {
                Log.d(TAG, "No term meta bank files found for frequency information")
            }
            
            // Update the entry count in dictionary metadata - force a proper update
            val updatedMetadata = metadataEntity.copy(
                id = dictionaryId,
                entryCount = successfulEntryCount
            )
            dictionaryRepository.saveDictionaryMetadata(updatedMetadata)
            
            // Verify the entry count after update
            val verifyEntryCount = dictionaryRepository.getDictionaryEntryCount(dictionaryId)
            Log.d(TAG, "VERIFICATION: After updating metadata, dictionary $dictionaryId should have $successfulEntryCount entries, actual count: $verifyEntryCount")
            
            // Clean up temporary files
            YomitanDictionaryExtractor.cleanup(extractedDir)
            
            // Set final progress
            _importProgress.postValue(100)
            
            // Before returning, do a quick verification of entry count
            val verifyEntryCount2 = dictionaryRepository.getDictionaryEntryCount(dictionaryId)
            Log.d(TAG, "VERIFY: Dictionary $dictionaryId has $verifyEntryCount2 entries before returning from importYomitanDictionary")
            
            Log.d(TAG, "Import complete. Successfully imported $successfulEntryCount out of $totalEntryCount entries")
            return Pair(successfulEntryCount > 0, dictionaryId)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error importing dictionary", e)
            return Pair(false, -1L)
        }
    }
}