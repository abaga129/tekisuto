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

    /**
     * Loads frequency data from a term_meta_bank file
     * This parses files in the format: [["term","freq",12345], ...] or [["term","freq",{"value":30,"displayValue":"30㋕"}], ...]
     */
    private fun loadFrequencyData(metaBankFile: File, frequencyMap: MutableMap<String, Int>) {
        try {
            // Read the file content
            val content = metaBankFile.readText()
            
            // Parse the JSON array
            val jsonArray = org.json.JSONArray(content)
            
            // Process each entry
            for (i in 0 until jsonArray.length()) {
                try {
                    val entry = jsonArray.getJSONArray(i)
                    
                    // Check if this is a frequency entry
                    if (entry.length() >= 3 && entry.getString(1) == "freq") {
                        val term = entry.getString(0)
                        
                        // Handle both direct integer and JSON object frequency formats
                        val freqData = entry.get(2)
                        val frequency = when {
                            freqData is Int -> freqData
                            freqData.toString().toIntOrNull() != null -> freqData.toString().toInt()
                            else -> {
                                try {
                                    // Try to parse as JSON object
                                    val jsonObj = entry.getJSONObject(2)
                                    if (jsonObj.has("value")) {
                                        jsonObj.getInt("value")
                                    } else {
                                        Log.w(TAG, "Frequency JSON object doesn't have 'value' property: $jsonObj")
                                        continue
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Unexpected frequency data type: ${e.message}")
                                    continue
                                }
                            }
                        }
                        
                        // Store in the map
                        frequencyMap[term] = frequency
                        
                        // Log a few examples for debugging
                        if (i < 5 || i % 10000 == 0) {
                            Log.d(TAG, "Parsed frequency $frequency for term: $term")
                        }
                    }
                } catch (e: Exception) {
                    // Log error but continue processing other entries
                    if (i < 10) {  // Only log errors for the first few entries to avoid log spam
                        Log.e(TAG, "Error parsing frequency entry at index $i: ${e.message}")
                    }
                }
            }
            
            Log.d(TAG, "Processed ${frequencyMap.size} frequency entries from ${metaBankFile.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading frequency data: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "ParserViewModel"
        private const val BATCH_SIZE = 500 // Process 500 entries at a time (increased from 100)
        private const val STREAM_CHUNK_SIZE = 1000 // Number of entries to read from file at once (increased from 200)

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
            
            // Find all term bank files and term meta bank files
            val termBankFiles = YomitanDictionaryExtractor.findTermBankFiles(extractedDir)
            val termMetaBankFiles = YomitanDictionaryExtractor.findTermMetaBankFiles(extractedDir)
            
            // Check if this is a frequency-only dictionary (no term banks, only meta banks)
            var isFrequencyOnlyDict = termBankFiles.isEmpty() && termMetaBankFiles.isNotEmpty()
            
            if (termBankFiles.isEmpty() && termMetaBankFiles.isEmpty()) {
                Log.e(TAG, "Neither term bank files nor term meta bank files found")
                YomitanDictionaryExtractor.cleanup(extractedDir)
                return Pair(false, -1L)
            }
            
            if (isFrequencyOnlyDict) {
                Log.d(TAG, "Detected frequency-only dictionary with ${termMetaBankFiles.size} meta bank files")
            }
            
            // Setup Moshi for JSON parsing
            val moshi = Moshi.Builder().build()
            val listOfAnyType = Types.newParameterizedType(List::class.java, Any::class.java)
            val listOfListOfAnyType = Types.newParameterizedType(List::class.java, listOfAnyType)
            val jsonAdapter: JsonAdapter<List<List<Any?>>> = moshi.adapter(listOfListOfAnyType)
            
            // Initialize counters
            var totalEntryCount = 0
            var successfulEntryCount = 0
            
            // Process term bank files (skip if this is a frequency-only dictionary)
            if (!isFrequencyOnlyDict) {
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
            } else {
                Log.d(TAG, "Skipping term bank processing for frequency-only dictionary")
                _importProgress.postValue(50) // Set progress to 50% before processing frequency data
            }
            
            // Initialize frequency count
            var frequencyCount = 0
            var parseFailures = 0
            
            // Process term meta banks for frequency information
            if (termMetaBankFiles.isNotEmpty()) {
                Log.d(TAG, "Found ${termMetaBankFiles.size} term meta bank files")
                
                // Set progress start point based on whether we processed term banks
                val progressStart = if (isFrequencyOnlyDict) 50 else 80
                
                // Process each term meta bank file
                for ((index, metaBankFile) in termMetaBankFiles.withIndex()) {
                    val progress = progressStart + ((index.toFloat() / termMetaBankFiles.size) * (100 - progressStart)).toInt()
                    _importProgress.postValue(progress)
                    
                    try {
                        // First try to process using the standard approach
                        var processedEntries = false
                        var frequencyEntriesFromStandardMethod = 0
                        
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
                                        frequencyEntriesFromStandardMethod += importedCount
                                        processedEntries = true
                                    }
                                }
                            }
                            
                            Log.d(TAG, "Processed ${frequencyEntriesFromStandardMethod} frequency entries from ${metaBankFile.name} using standard method")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing term meta file using standard method: ${e.message}")
                            parseFailures++
                        }
                        
                        // If standard processing failed or no entries were processed, try alternative method
                        if (!processedEntries) {
                            Log.d(TAG, "Standard processing failed or found no entries, trying alternative method for ${metaBankFile.name}")
                            
                            // Use the fallback method for the specific frequency format [["term","freq",12345], ...]
                            val frequencyMap = mutableMapOf<String, Int>()
                            loadFrequencyData(metaBankFile, frequencyMap)
                            
                            if (frequencyMap.isNotEmpty()) {
                                // Convert the map to WordFrequencyEntity objects
                                val frequencyEntities = frequencyMap.map { (term, freq) ->
                                    WordFrequencyEntity(
                                        dictionaryId = dictionaryId,
                                        word = term,
                                        frequency = freq
                                    )
                                }
                                
                                // Process in batches
                                val batches = frequencyEntities.chunked(BATCH_SIZE)
                                for (batch in batches) {
                                    if (batch.isNotEmpty()) {
                                        val importedCount = dictionaryRepository.importWordFrequencies(batch)
                                        frequencyCount += importedCount
                                    }
                                }
                                
                                Log.d(TAG, "Processed ${frequencyMap.size} frequency entries from ${metaBankFile.name} using alternative method")
                            } else {
                                Log.e(TAG, "No frequency data found in ${metaBankFile.name} using alternative method")
                            }
                        }
                        
                        Log.d(TAG, "Completed processing term meta bank file: ${metaBankFile.name}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing term meta bank file ${metaBankFile.name}: ${e.message}")
                    }
                }
                
                if (parseFailures > 0) {
                    Log.w(TAG, "Encountered $parseFailures parsing failures during frequency data processing")
                }
                
                Log.d(TAG, "Imported $frequencyCount frequency entries in total")
            } else {
                Log.d(TAG, "No term meta bank files found for frequency information")
            }

            
            // Update the entry count in dictionary metadata - force a proper update
            // For frequency-only dictionaries, set entry count to frequency count to ensure they appear in the UI
            isFrequencyOnlyDict = successfulEntryCount == 0 && frequencyCount > 0
            val finalEntryCount = if (isFrequencyOnlyDict) frequencyCount else successfulEntryCount
            
            val updatedMetadata = metadataEntity.copy(
                id = dictionaryId,
                entryCount = finalEntryCount,
                description = if (isFrequencyOnlyDict) {
                    "${metadataEntity.description} [Frequency data only: ${frequencyCount} entries]"
                } else {
                    metadataEntity.description
                }
            )
            dictionaryRepository.saveDictionaryMetadata(updatedMetadata)
            
            if (isFrequencyOnlyDict) {
                Log.d(TAG, "Saved frequency-only dictionary with ${frequencyCount} frequency entries")
            }
            
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