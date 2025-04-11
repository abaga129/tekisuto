package com.abaga129.tekisuto.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.abaga129.tekisuto.database.DictionaryEntryEntity
import com.abaga129.tekisuto.database.DictionaryMetadataEntity
import com.abaga129.tekisuto.database.DictionaryRepository
import com.abaga129.tekisuto.util.StructuredContentHtmlConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileReader
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

private const val TAG = "MainViewModel"
private const val BATCH_SIZE = 100 // Process 100 entries at a time - reduced to save memory

class MainViewModel : ViewModel() {

    private val _isAccessibilityServiceEnabled = MutableLiveData<Boolean>()
    val isAccessibilityServiceEnabled: LiveData<Boolean> = _isAccessibilityServiceEnabled

    // Add a mutable live data for import progress
    private val _importProgress = MutableLiveData<Int>()
    val importProgress: LiveData<Int> = _importProgress

    // Add a mutable live data for displaying the dictionaries
    private val _dictionaryInfo = MutableLiveData<DictionaryInfo?>()
    val dictionaryInfo: LiveData<DictionaryInfo?> = _dictionaryInfo

    // Add repository as a dependency
    private lateinit var dictionaryRepository: DictionaryRepository

    // Initialize repository
    fun initRepository(repository: DictionaryRepository) {
        dictionaryRepository = repository
    }

    fun checkAccessibilityServiceStatus(context: Context, serviceClass: Class<*>) {
        val serviceClassName = serviceClass.name
        val packageName = context.packageName
        val componentName = "$packageName/$serviceClassName"
        
        try {
            // Get the enabled accessibility services string from settings
            val enabledServicesStr = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            
            Log.d(TAG, "Current enabled services: $enabledServicesStr")
            Log.d(TAG, "Looking for service: $componentName")
            
            // The format in the settings string is "packagename/serviceclassname"
            // We need to check if our service is in this list
            val isEnabled = if (enabledServicesStr.isEmpty()) {
                false
            } else {
                // Split the string into individual service components
                val enabledServices = enabledServicesStr.split(":")
                
                // Check if our service is in the list
                enabledServices.any { service ->
                    service.equals(componentName, ignoreCase = true) ||
                    service.contains(serviceClassName) ||
                    (service.contains(packageName) && service.contains(".AccessibilityOcrService"))
                }
            }
            
            // Update the LiveData with our finding
            _isAccessibilityServiceEnabled.value = isEnabled
            
            Log.d(TAG, "Accessibility service is ${if (isEnabled) "ENABLED" else "DISABLED"}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking accessibility service status", e)
            _isAccessibilityServiceEnabled.value = false
        }
    }

    /**
     * Imports a Yomitan dictionary from the given URI
     * @param context Android context
     * @param uri URI of the dictionary file (zip)
     * @return true if import successful, false otherwise
     */
    suspend fun importYomitanDictionary(context: Context, uri: Uri): Boolean {
        return try {
            // Reset the entry counter
            entryCount = 0
            
            Log.d(TAG, "Starting dictionary import from $uri")
            _importProgress.postValue(0)

            // Extract the zip file to a temporary directory
            val extractedDir = extractDictionaryZip(context, uri)
            if (extractedDir == null) {
                Log.e(TAG, "Failed to extract dictionary zip")
                _importProgress.postValue(-1)
                return false
            }

            // Read index.json for dictionary metadata
            val indexFile = File(extractedDir, "index.json")
            if (!indexFile.exists()) {
                Log.e(TAG, "index.json not found in dictionary")
                _importProgress.postValue(-1)
                extractedDir.deleteRecursively()
                return false
            }

            val indexJson = indexFile.readText()
            val dictionaryInfo = parseDictionaryInfo(indexJson)

            _importProgress.postValue(10)

            // Create dictionary metadata entry
            val dictionaryMetadata = DictionaryMetadataEntity(
                title = dictionaryInfo.title,
                author = dictionaryInfo.author,
                description = dictionaryInfo.description,
                sourceLanguage = dictionaryInfo.sourceLanguage,
                targetLanguage = dictionaryInfo.targetLanguage,
                priority = 0 // Default priority
            )
            
            // Save the metadata and get the dictionary ID
            val dictionaryId = dictionaryRepository.saveDictionaryMetadata(dictionaryMetadata)
            
            // Store language information for TTS
            try {
                // Update metadata with the assigned ID
                val savedMetadata = dictionaryMetadata.copy(id = dictionaryId)
                val languageHelper = com.abaga129.tekisuto.util.DictionaryLanguageHelper(context)
                languageHelper.storeDictionaryLanguages(savedMetadata)
                Log.d(TAG, "Stored dictionary languages - Source: ${savedMetadata.sourceLanguage}, Target: ${savedMetadata.targetLanguage}")
            } catch (e: Exception) {
                Log.e(TAG, "Error storing dictionary languages", e)
                // Continue even if storing languages fails
            }
            
            if (dictionaryId <= 0) {
                Log.e(TAG, "Failed to save dictionary metadata")
                _importProgress.postValue(-1)
                extractedDir.deleteRecursively()
                return false
            }

            // Process dictionary files in different formats
            var totalEntriesProcessed = 0
            val dictionaryFiles = mutableListOf<File>()
            
            // First, try the standard Yomitan term_bank files
            var termBankIndex = 1
            while (true) {
                val termBankFile = File(extractedDir, "term_bank_${termBankIndex}.json")
                if (termBankFile.exists()) {
                    dictionaryFiles.add(termBankFile)
                    termBankIndex++
                } else {
                    break
                }
            }
            
            // If no term_bank files, look for any JSON files that might contain dictionary data
            if (dictionaryFiles.isEmpty()) {
                extractedDir.listFiles()?.forEach { file ->
                    if (file.isFile && (file.name.endsWith(".json") || file.name.endsWith(".txt"))) {
                        // Skip index.json as it's metadata
                        if (file.name != "index.json" && file.name != "tag_bank_1.json" && 
                            file.name != "term_meta_bank_1.json") {
                            dictionaryFiles.add(file)
                        }
                    }
                }
            }
            
            // Estimate memory usage and handle large dictionaries carefully
            val totalSize = dictionaryFiles.sumOf { it.length() }
            Log.d(TAG, "Total dictionary size: ${totalSize / 1024 / 1024}MB with ${dictionaryFiles.size} files")
            
            // Determine if we need special processing for this dictionary format
            val isSpanishDictionary = dictionaryInfo.format >= 3 || 
                                     dictionaryInfo.title.contains("Spanish", ignoreCase = true)
            
            if (isSpanishDictionary) {
                Log.d(TAG, "Processing dictionary in Spanish dictionary format (format: ${dictionaryInfo.format})")
            }
            
            // Sort files by size - process smaller files first to show progress quickly
            dictionaryFiles.sortBy { it.length() }
            
            // Process all the dictionary files we found
            dictionaryFiles.forEachIndexed { index, file ->
                Log.d(TAG, "Processing dictionary file (${file.length() / 1024}KB): ${file.name}")
                
                // Special handling for array-es dictionary format
                if (file.name == "term_bank_1.json" && file.length() < 1 * 1024 * 1024) { // Small file < 1MB
                    // Try direct JSON parsing first
                    try {
                        Log.d(TAG, "Attempting special array-es dictionary processing")
                        val fileContent = file.readText()
                        
                        if (fileContent.startsWith("[") && fileContent.contains("\"abada\"")) {
                            // This is likely the array-es dictionary 
                            Log.d(TAG, "Detected array-es dictionary format - using direct JSON parsing")
                            
                            val entriesArray = JSONArray(fileContent) 
                            Log.d(TAG, "Parsed array with ${entriesArray.length()} entries")
                            
                            var entriesImported = 0
                            val batchEntries = mutableListOf<DictionaryEntryEntity>()
                            
                            // Process each entry in the array
                            for (i in 0 until entriesArray.length()) {
                                try {
                                    val entryArray = entriesArray.getJSONArray(i)
                                    
                                    // Extract entry fields
                                    val term = entryArray.getString(0)
                                    val reading = entryArray.optString(1, "")
                                    val partOfSpeech = entryArray.optString(2, "")
                                    
                                    // Get definition array at position 5
                                    var definition = ""
                                    if (entryArray.length() > 5 && entryArray.opt(5) is JSONArray) {
                                        val defArray = entryArray.getJSONArray(5)
                                        val defBuilder = StringBuilder()
                                        
                                        for (j in 0 until defArray.length()) {
                                            if (defBuilder.isNotEmpty()) defBuilder.append("\n")
                                            defBuilder.append(defArray.getString(j))
                                        }
                                        
                                        definition = defBuilder.toString()
                                    }
                                    
                                    // Create entity
                                    val entry = DictionaryEntryEntity(
                                        dictionaryId = dictionaryId,
                                        term = term,
                                        reading = reading,
                                        definition = definition,
                                        partOfSpeech = partOfSpeech,
                                        tags = emptyList(),
                                        isHtmlContent = false
                                    )
                                    
                                    // Add to batch
                                    batchEntries.add(entry)
                                    entriesImported++
                                    
                                    // Save batch if needed
                                    if (batchEntries.size >= BATCH_SIZE) {
                                        val savedCount = dictionaryRepository.importDictionaryEntries(batchEntries)
                                        Log.d(TAG, "Saved batch of ${batchEntries.size} entries, total processed: $entriesImported")
                                        batchEntries.clear()
                                    }
                                    
                                    // Log progress
                                    if (entriesImported <= 5 || entriesImported % 100 == 0) {
                                        Log.d(TAG, "Processed entry #$entriesImported: term='$term', reading='$reading'")
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error processing array entry at index $i: ${e.message}")
                                }
                            }
                            
                            // Save any remaining entries
                            if (batchEntries.isNotEmpty()) {
                                dictionaryRepository.importDictionaryEntries(batchEntries)
                                Log.d(TAG, "Saved final batch of ${batchEntries.size} entries")
                            }
                            
                            totalEntriesProcessed += entriesImported
                            
                            // Update progress
                            val progress = 10 + ((index + 1) * 80) / dictionaryFiles.size
                            _importProgress.postValue(progress.coerceAtMost(90))
                            
                            Log.d(TAG, "Completed special processing of ${file.name} with $entriesImported entries")
                            
                            // Skip regular processing for this file
                            return@forEachIndexed
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in special array-es processing, falling back to standard processing: ${e.message}")
                    }
                }
                
                // For the first few files, check if it has a Yomitan structure
                if (index < 3) {
                    var fileReader: BufferedReader? = null
                    try {
                        // Check first few characters of the file
                        fileReader = BufferedReader(FileReader(file))
                        val firstChars = CharArray(100)
                        fileReader.read(firstChars, 0, 100)
                        
                        val fileStart = String(firstChars).trim()
                        Log.d(TAG, "File starts with: ${fileStart.take(50)}...")
                        
                        // Try to identify dictionary type
                        val isStructuredContent = fileStart.contains("\"structured-content\"")
                        val isArrayFormat = fileStart.startsWith("[")
                        val isObjectFormat = fileStart.startsWith("{")
                        
                        Log.d(TAG, "Dictionary file analysis: " +
                                   "isStructuredContent=$isStructuredContent, " +
                                   "isArrayFormat=$isArrayFormat, " +
                                   "isObjectFormat=$isObjectFormat")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error analyzing dictionary file: ${e.message}")
                    } finally {
                        try {
                            fileReader?.close()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error closing file reader: ${e.message}")
                        }
                    }
                }
                
                // Force garbage collection before processing each large file
                if (file.length() > 10 * 1024 * 1024) { // If file > 10MB
                    System.gc()
                }
                
                // Process this file
                try {
                    // Reset entry count for diagnostic logs
                    entryCount = 0
                    
                    // Process file and count entries
                    val entriesInThisFile = processTermBankFile(file, dictionaryId)
                    totalEntriesProcessed += entriesInThisFile
                    
                    // Update progress
                    val progress = 10 + ((index + 1) * 80) / dictionaryFiles.size
                    _importProgress.postValue(progress.coerceAtMost(90))
                    
                    Log.d(TAG, "Completed processing ${file.name} with $entriesInThisFile entries")
                    
                    // If no entries were processed, log a warning
                    if (entriesInThisFile == 0) {
                        Log.w(TAG, "No entries were processed from file: ${file.name}. " +
                                   "This might indicate a format problem.")
                    }
                } catch (e: OutOfMemoryError) {
                    Log.e(TAG, "Out of memory error processing file ${file.name}. Skipping file.", e)
                    _importProgress.postValue(-2) // Special code for OOM but continuing
                    // Force garbage collection
                    System.gc()
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing file ${file.name}: ${e.message}", e)
                }
            }

            Log.d(TAG, "Processed $totalEntriesProcessed entries from ${dictionaryFiles.size} dictionary files")

            // Check if anything was actually imported
            if (totalEntriesProcessed == 0) {
                Log.e(TAG, "No entries were processed during import!")
            }

            // Update dictionary info
            _dictionaryInfo.postValue(dictionaryInfo)

            // Clean up temporary files
            extractedDir.deleteRecursively()
            
            // Force final garbage collection
            System.gc()

            // Complete progress
            _importProgress.postValue(100)

            Log.d(TAG, "Dictionary import completed successfully with $totalEntriesProcessed entries")
            true
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Out of memory error importing dictionary", e)
            _importProgress.postValue(-1)
            // Force garbage collection
            System.gc()
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error importing dictionary", e)
            _importProgress.postValue(-1)
            false
        }
    }

    /**
     * Process a term bank file using streaming to avoid OOM errors
     * @param termBankFile The file to process
     * @param dictionaryId The ID of the dictionary this file belongs to
     * @return The number of entries processed
     */
    private suspend fun processTermBankFile(termBankFile: File, dictionaryId: Long): Int {
        var entriesProcessed = 0
        var fileStream: FileInputStream? = null
        var bufferedReader: BufferedReader? = null

        try {
            // Check if file is too large (>50MB) to handle memory constraints
            val fileSize = termBankFile.length()
            if (fileSize > 50 * 1024 * 1024) {
                Log.w(TAG, "Dictionary file is very large (${fileSize/1024/1024}MB). Processing with memory optimizations.")
            }

            // Create a BufferedReader with a large buffer for better performance
            fileStream = FileInputStream(termBankFile)
            bufferedReader = BufferedReader(InputStreamReader(fileStream), 16384)
            
            // Read more characters to better determine the format
            val firstChars = CharArray(500)
            bufferedReader.mark(2000) // Mark with large readahead limit for big files
            bufferedReader.read(firstChars, 0, 500)
            bufferedReader.reset()
            
            val firstCharsString = String(firstChars)
            Log.d(TAG, "First chars of file: ${firstCharsString.take(100)}...")
            
            // Initialize batch processing
            val entries = mutableListOf<DictionaryEntryEntity>()
            
            // Look for common patterns in dictionary formats
            val isArrayFormat = firstCharsString.trimStart().startsWith("[")
            val isObjectFormat = firstCharsString.trimStart().startsWith("{")
            val containsTermField = firstCharsString.contains("\"term\"")
            
            // Look for clues of array-of-arrays format
            val containsArraySeparators = firstCharsString.contains("],[") || 
                                         firstCharsString.contains("], [")
            val containsPositionMarkers = firstCharsString.contains(",0,") || 
                                         firstCharsString.contains(",1,")
            
            // Additional array format checks
            val isComplexArrayFormat = isArrayFormat && containsArraySeparators && containsPositionMarkers
            
            Log.d(TAG, "Dictionary format analysis: " +
                      "isArrayFormat=$isArrayFormat, " +
                      "isObjectFormat=$isObjectFormat, " +
                      "containsTermField=$containsTermField, " +
                      "containsArraySeparators=$containsArraySeparators, " +
                      "isComplexArrayFormat=$isComplexArrayFormat")
            
            // Try special processing for array dictionaries first
            if (isArrayFormat && (containsArraySeparators || firstCharsString.contains("\"abada\"") || 
                                 firstCharsString.contains("\"abrogable\""))) {
                // Direct array file processing - this is a special case for the array-es dictionary
                Log.d(TAG, "Detected array-es dictionary format - using direct JSON parsing")
                
                try {
                    // Close and reopen the file to ensure we start from the beginning
                    bufferedReader.close()
                    fileStream.close()
                    
                    // Read the entire file as a JSON string
                    val jsonContent = termBankFile.readText()
                    
                    // Parse the top-level array
                    val topArray = JSONArray(jsonContent)
                    Log.d(TAG, "Successfully parsed top-level array with ${topArray.length()} entries")
                    
                    // Process each entry in the array
                    for (i in 0 until topArray.length()) {
                        try {
                            val entryArray = topArray.getJSONArray(i)
                            val parsedEntry = parseTermBankEntry(entryArray, dictionaryId)
                            
                            if (parsedEntry != null) {
                                entries.add(parsedEntry)
                                entriesProcessed++
                                
                                // Save batch when limit reached
                                if (entries.size >= BATCH_SIZE) {
                                    dictionaryRepository.importDictionaryEntries(entries)
                                    Log.d(TAG, "Saved batch of ${entries.size} entries, total processed: $entriesProcessed")
                                    entries.clear()
                                }
                                
                                // Log progress
                                if (entriesProcessed <= 10 || entriesProcessed % 100 == 0) {
                                    Log.d(TAG, "Processed entry #$entriesProcessed: term='${parsedEntry.term}'")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing array entry at index $i: ${e.message}")
                        }
                    }
                    
                    // Return early
                    return entriesProcessed
                } catch (e: Exception) {
                    Log.e(TAG, "Error in direct array processing, falling back to standard processing: ${e.message}")
                    
                    // Reopen the file for standard processing
                    fileStream = FileInputStream(termBankFile)
                    bufferedReader = BufferedReader(InputStreamReader(fileStream), 16384)
                }
            }
            
            // Standard processing for other dictionary formats
            entriesProcessed = when {
                isComplexArrayFormat -> {
                    // Complex array format with nested entries
                    Log.d(TAG, "Processing complex array dictionary format with nested entries")
                    processArrayInArrayFormat(bufferedReader, fileSize, entries, entriesProcessed, dictionaryId)
                }
                isArrayFormat -> {
                    // Array format (includes both standard and array-in-array format)
                    Log.d(TAG, "Processing array-based dictionary format")
                    processArrayFormatDictionary(bufferedReader, fileSize, entries, entriesProcessed, dictionaryId)
                }
                isObjectFormat || containsTermField -> {
                    // Object-based format with explicit term/reading/definition fields
                    Log.d(TAG, "Processing object-based dictionary format")
                    processObjectFormatDictionary(bufferedReader, fileSize, entries, entriesProcessed, dictionaryId)
                }
                else -> {
                    // Simple text format (term:reading:definition)
                    Log.d(TAG, "Processing simple text dictionary format")
                    processSimpleTextDictionary(bufferedReader, fileSize, entries, entriesProcessed, dictionaryId)
                }
            }
            
            // Save any remaining entries
            if (entries.isNotEmpty()) {
                dictionaryRepository.importDictionaryEntries(entries)
                Log.d(TAG, "Saved final batch of ${entries.size} entries")
            }
            
            // Force garbage collection after processing large files
            System.gc()
            
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Out of memory error processing dictionary file. Try using smaller dictionary files.", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing term bank file: ${e.message}", e)
        } finally {
            // Close resources
            try {
                bufferedReader?.close()
                fileStream?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing streams", e)
            }
        }

        return entriesProcessed
    }
    
    /**
     * Process dictionary in standard Yomitan/Yomichan array format
     */
    private suspend fun processArrayFormatDictionary(
        bufferedReader: BufferedReader,
        fileSize: Long,
        entries: MutableList<DictionaryEntryEntity>,
        entriesProcessed: Int,
        dictionaryId: Long
    ): Int {
        var processedCount = entriesProcessed
        var bytesRead = 0L
        
        try {
            // Check if the first entry looks like part of a top-level array format
            val hasTopLevelArray = hasTopLevelArray(bufferedReader)
            
            // Special handling for array format detection - try to validate actual content
            try {
                bufferedReader.mark(5000)
                val testChars = CharArray(1000)
                bufferedReader.read(testChars, 0, 1000)
                val contentSample = String(testChars)
                
                // Log first 100 chars to diagnose format issues
                Log.d(TAG, "Dictionary content sample: ${contentSample.take(100)}...")
                
                // Look for specific array patterns that indicate this is an array of entries
                val containsMultipleArrays = contentSample.contains("],[") || contentSample.contains("], [")
                val containsNumericPosition = contentSample.contains(",0,") // Common in array format dictionaries
                
                if (containsMultipleArrays && containsNumericPosition && !hasTopLevelArray) {
                    Log.d(TAG, "Content suggests this is a top-level array format despite initial detection. Forcing array processing.")
                    bufferedReader.reset()
                    processedCount = processArrayInArrayFormat(bufferedReader, fileSize, entries, processedCount, dictionaryId)
                    return processedCount
                }
                
                // Reset to the beginning
                bufferedReader.reset()
            } catch (e: Exception) {
                Log.e(TAG, "Error during additional format detection: ${e.message}")
                try {
                    bufferedReader.reset()
                } catch (ex: Exception) {
                    Log.e(TAG, "Failed to reset reader: ${ex.message}")
                }
            }
            
            if (hasTopLevelArray) {
                // Process the file as one giant line with array of arrays
                Log.d(TAG, "Processing array-in-array format dictionary")
                // We need a fresh reader since we can't reliably reset
                processedCount = processArrayInArrayFormat(bufferedReader, fileSize, entries, processedCount, dictionaryId)
            } else {
                // Process line by line (standard format)
                Log.d(TAG, "Processing multi-line array format dictionary")
                // Use a line-by-line approach instead of accumulating an entire entry
                val sb = StringBuilder(512) // Start with a modest buffer size
                var bracketCount = 0
                var inEntry = false
                var line: String? = null
                
                while (bufferedReader.readLine().also { line = it } != null) {
                    if (line == null) break
                    bytesRead += line!!.length + 1 // +1 for newline
                    
                    // Update progress occasionally
                    if (processedCount % 100 == 0) {
                        val fileProgress = (bytesRead.toFloat() / fileSize * 100).toInt()
                        Log.d(TAG, "Term bank processing: $fileProgress% complete, $processedCount entries")
                    }
                    
                    if (!inEntry && line!!.trimStart().startsWith("[")) {
                        // Start of a new entry
                        inEntry = true
                        bracketCount = 1
                        sb.clear()
                        sb.append("[")
                    } else if (inEntry) {
                        // Count opening and closing brackets
                        for (char in line!!) {
                            if (char == '[') bracketCount++
                            else if (char == ']') bracketCount--
                        }
                        
                        // Handle potential OOM by appending until we know it's a complete entry
                        try {
                            sb.append(line)
                            
                            // If brackets are balanced, we have a complete entry
                            if (bracketCount == 0) {
                                try {
                                    // Parse the entry
                                    val jsonArray = JSONArray(sb.toString())
                                    val parsedEntry = parseTermBankEntry(jsonArray, dictionaryId)
                                    if (parsedEntry != null) {
                                        entries.add(parsedEntry)
                                        processedCount++
                                        
                                        // If we've reached the batch size, save to database
                                        if (entries.size >= BATCH_SIZE) {
                                            dictionaryRepository.importDictionaryEntries(entries)
                                            entries.clear()
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error parsing array-format entry: ${e.message}")
                                }
                                
                                // Reset for next entry
                                inEntry = false
                                sb.clear()
                            }
                        } catch (e: OutOfMemoryError) {
                            // Handle OOM by skipping this entry
                            Log.e(TAG, "OOM error processing dictionary entry - skipping", e)
                            inEntry = false
                            sb.clear()
                            bracketCount = 0
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in processArrayFormatDictionary", e)
        }
        
        return processedCount
    }
    
    /**
     * Check if this dictionary has a top-level array format
     * Various dictionary formats include:
     * 1. [[entry1], [entry2], ...] - Array of arrays (most common in Yomitan)
     * 2. [{"type":"structured-content",...}, {...}] - Array of objects with structured content
     * 3. [{term:"word", ...}, {...}] - Array of objects with direct term fields
     * 4. [["word", "reading", ...], ["word2", ...]] - Simple arrays of strings
     * We need to detect all these formats and process them as a top-level array.
     */
    private fun hasTopLevelArray(reader: BufferedReader): Boolean {
        try {
            // Mark so we can reset after checking
            reader.mark(2000) // Increased mark limit to handle larger headers
            
            // Read more characters to better detect the format
            val firstChunk = CharArray(500) // Increased sample size
            reader.read(firstChunk, 0, 500)
            val startStr = String(firstChunk)
            
            // Reset the reader to the beginning
            reader.reset()
            
            // Trim leading whitespace
            val trimmedStart = startStr.trimStart()
            
            // The file must start with an opening bracket to be a JSON array
            if (!trimmedStart.startsWith("[")) {
                Log.d(TAG, "Not a JSON array format")
                return false
            }
            
            // Now check various array formats:
            
            // 1. Array of arrays: [[...], [...]]
            val isArrayOfArrays = trimmedStart.startsWith("[[") && 
                                 (trimmedStart.contains("],[") || 
                                  trimmedStart.contains("], ["))
            
            // 2. Array of objects: [{...}, {...}]
            val isArrayOfObjects = trimmedStart.startsWith("[{") && 
                                  (trimmedStart.contains("},{") || 
                                   trimmedStart.contains("}, {"))
            
            // 3. Check for simple array containing strings (like ["word", "reading", ...])
            val containsQuotedStrings = trimmedStart.contains("\",\"") || 
                                       trimmedStart.contains("\", \"")
                                        
            // 4. Check if it has structured content objects
            val hasStructuredContent = trimmedStart.contains("\"type\"") && 
                                      trimmedStart.contains("\"structured-content\"")
            
            // Special detection for array formats with string arrays at position 5 (definitions)
            val hasFifthPositionArray = trimmedStart.contains("\",\"\",\"\",\"\",0,\\[") || 
                                       trimmedStart.contains("\",\"\",\"\",\"\",0, [")
            
            // Combine all checks
            val hasTopLevelArray = isArrayOfArrays || isArrayOfObjects || 
                                  hasStructuredContent || hasFifthPositionArray
            
            Log.d(TAG, "Dictionary format analysis: " +
                        "isArrayOfArrays=$isArrayOfArrays, " +
                        "isArrayOfObjects=$isArrayOfObjects, " +
                        "hasQuotedStrings=$containsQuotedStrings, " +
                        "hasFifthPositionArray=$hasFifthPositionArray, " +
                        "hasStructuredContent=$hasStructuredContent, " + 
                        "hasTopLevelArray=$hasTopLevelArray")
            
            return hasTopLevelArray
        } catch (e: Exception) {
            Log.e(TAG, "Error checking array format", e)
            return false // Default to standard format
        }
    }
    
    /**
     * Processes a dictionary with array-in-array format, commonly used in Spanish dictionaries
     * Where format is [[entry1], [entry2], ...] instead of separate lines for each entry
     * Uses a character-by-character approach to avoid loading the whole file into memory
     */
    private suspend fun processArrayInArrayFormat(
        reader: BufferedReader, 
        fileSize: Long,
        entries: MutableList<DictionaryEntryEntity>,
        entriesProcessed: Int,
        dictionaryId: Long
    ): Int {
        var processedCount = entriesProcessed
        var bytesRead = 0L
        var charRead: Int
        
        try {
            // Skip the initial opening bracket of the outer array and any whitespace
            var c: Char
            do {
                reader.mark(1)
                c = reader.read().toChar()
            } while (c.isWhitespace())
            
            if (c != '[') {
                Log.e(TAG, "Expected outer array start '[' but found '$c'")
                return processedCount
            }
            
            // Log the start of processing
            Log.d(TAG, "Starting to process array-in-array formatted dictionary")
            
            // Now parse each entry in the array manually
            var depth = 1  // We're inside the outer array
            var inEntry = false
            val entryBuilder = StringBuilder(1024)
            
            while (reader.read().also { charRead = it } != -1) {
                bytesRead++
                val char = charRead.toChar()
                
                // Show progress periodically
                if (bytesRead % 100000 == 0L) {
                    val progress = (bytesRead * 100 / fileSize).toInt()
                    Log.d(TAG, "Processing Spanish dictionary: $progress%, $processedCount entries")
                }
                
                // Handle brackets to track the entry boundaries
                if (char == '[') {
                    depth++
                    if (depth == 2) {
                        // Start of a new array entry
                        inEntry = true
                        entryBuilder.clear()
                    }
                    if (inEntry) {
                        entryBuilder.append(char)
                    }
                } else if (char == ']') {
                    depth--
                    if (inEntry) {
                        entryBuilder.append(char)
                    }
                    
                    if (depth == 1 && inEntry) {
                        // End of entry, process it
                        inEntry = false
                        try {
                            // Get the entry string
                            val entryStr = entryBuilder.toString()
                            
                            // Detailed logging for the first few entries or on errors
                            if (processedCount < 5) {
                                Log.d(TAG, "Processing entry #$processedCount: ${entryStr.take(100)}...")
                            }
                            
                            try {
                                val jsonArray = JSONArray(entryStr)
                                val startWithBracket = entryStr.trim().startsWith("[[")
                                
                                // Check if this is potentially a top-level array of entries
                                if (startWithBracket && jsonArray.length() > 0 && 
                                    jsonArray.opt(0) is JSONArray && entryStr.contains("],[")) {
                                        
                                    // This is a nested array with multiple entries - process each one
                                    Log.d(TAG, "Found nested array with ${jsonArray.length()} entries")
                                    
                                    for (i in 0 until jsonArray.length()) {
                                        try {
                                            val nestedEntry = jsonArray.optJSONArray(i)
                                            if (nestedEntry != null) {
                                                // Process each nested entry
                                                val parsedEntry = parseTermBankEntry(nestedEntry, dictionaryId)
                                                if (parsedEntry != null) {
                                                    entries.add(parsedEntry)
                                                    processedCount++
                                                    entryCount++
                                                    
                                                    // Log entries
                                                    if (processedCount <= 10 || processedCount % 100 == 0) {
                                                        Log.d(TAG, "Processed nested entry #$processedCount: " +
                                                              "term='${parsedEntry.term}', reading='${parsedEntry.reading}'")
                                                    }
                                                    
                                                    // Save batch if needed
                                                    if (entries.size >= BATCH_SIZE) {
                                                        val savedCount = dictionaryRepository.importDictionaryEntries(entries)
                                                        Log.d(TAG, "Saved batch of ${entries.size} entries")
                                                        entries.clear()
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error processing nested entry at index $i: ${e.message}")
                                        }
                                    }
                                } else {
                                    // Regular single entry - process normally
                                    val parsedEntry = parseTermBankEntry(jsonArray, dictionaryId)
                                    
                                    if (parsedEntry != null) {
                                        // Add to the current batch
                                        entries.add(parsedEntry)
                                        processedCount++
                                        entryCount++ // For logging consistency
                                        
                                        // Log successful entries periodically
                                        if (processedCount <= 10 || processedCount % 100 == 0) {
                                            Log.d(TAG, "Successfully processed entry #$processedCount: " +
                                                  "term='${parsedEntry.term}', reading='${parsedEntry.reading}', " +
                                                  "definition length=${parsedEntry.definition.length}")
                                        }
                                        
                                        // If we've reached the batch size, save to database
                                        if (entries.size >= BATCH_SIZE) {
                                            try {
                                                val savedCount = dictionaryRepository.importDictionaryEntries(entries)
                                                Log.d(TAG, "Saved batch of ${entries.size} entries, DB returned $savedCount")
                                                entries.clear()
                                                
                                                // Log progress after each batch
                                                val progress = (bytesRead * 100 / fileSize).toInt()
                                                Log.d(TAG, "Processed $processedCount entries so far ($progress% of file)")
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Error saving entries to database: ${e.message}", e)
                                            }
                                        }
                                    } else if (processedCount < 10) {
                                        // Log entry parsing failures
                                        Log.w(TAG, "Failed to parse entry: ${entryStr.take(150)}...")
                                    }
                                }
                            } catch (e: Exception) {
                                // Log parsing errors
                                Log.e(TAG, "Error parsing entry JSON: ${e.message}, Entry start: ${entryStr.take(50)}...")
                            }
                        } catch (e: Exception) {
                            // Skip errors in individual entries
                            Log.e(TAG, "Error parsing Spanish dictionary entry: ${e.message}")
                        }
                    } else if (depth == 0) {
                        // End of the entire array
                        break
                    }
                } else if (inEntry) {
                    // Add any other character to the current entry
                    entryBuilder.append(char)
                }
            }
            
            Log.d(TAG, "Completed Spanish dictionary processing with $processedCount entries")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing Spanish dictionary format", e)
        }
        
        return processedCount
    }
    
    /**
     * Process dictionary in object format with explicit term/reading/definition fields
     */
    private suspend fun processObjectFormatDictionary(
        bufferedReader: BufferedReader,
        fileSize: Long,
        entries: MutableList<DictionaryEntryEntity>,
        entriesProcessed: Int,
        dictionaryId: Long
    ): Int {
        var processedCount = entriesProcessed
        var bytesRead = 0L
        
        // Use a char-by-char approach instead of accumulating an entire entry
        val sb = StringBuilder(512) // Start with a modest buffer size
        var braceCount = 0
        var inEntry = false
        var line: String? = null
        
        while (bufferedReader.readLine().also { line = it } != null) {
            if (line == null) break
            bytesRead += line!!.length + 1 // +1 for newline
            
            // Update progress occasionally
            if (processedCount % 100 == 0) {
                val fileProgress = (bytesRead.toFloat() / fileSize * 100).toInt()
                Log.d(TAG, "Term bank processing: $fileProgress% complete, $processedCount entries")
            }
            
            if (!inEntry && line!!.trimStart().startsWith("{")) {
                // Start of a new entry
                inEntry = true
                braceCount = 1
                sb.clear()
                sb.append("{")
            } else if (inEntry) {
                // Count opening and closing braces
                for (char in line!!) {
                    if (char == '{') braceCount++
                    else if (char == '}') braceCount--
                }
                
                // Handle potential OOM by checking before appending
                try {
                    sb.append(line)
                    
                    // If braces are balanced, we have a complete entry
                    if (braceCount == 0) {
                        try {
                            // Parse the entry
                            val jsonObject = JSONObject(sb.toString())
                            val parsedEntry = parseObjectTermBankEntry(jsonObject, dictionaryId)
                            if (parsedEntry != null) {
                                entries.add(parsedEntry)
                                processedCount++
                                
                                // If we've reached the batch size, save to database
                                if (entries.size >= BATCH_SIZE) {
                                    dictionaryRepository.importDictionaryEntries(entries)
                                    entries.clear()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing object-format entry: ${e.message}")
                        }
                        
                        // Reset for next entry
                        inEntry = false
                        sb.clear()
                    }
                } catch (e: OutOfMemoryError) {
                    // Handle OOM by skipping this entry
                    Log.e(TAG, "OOM error processing dictionary entry - skipping", e)
                    inEntry = false
                    sb.clear()
                    braceCount = 0
                }
            }
        }
        
        return processedCount
    }
    
    /**
     * Process dictionary in simple text format (word:reading:definition)
     */
    private suspend fun processSimpleTextDictionary(
        bufferedReader: BufferedReader,
        fileSize: Long,
        entries: MutableList<DictionaryEntryEntity>,
        entriesProcessed: Int,
        dictionaryId: Long
    ): Int {
        var processedCount = entriesProcessed
        var bytesRead = 0L
        
        // Process the file line by line
        bufferedReader.useLines { lines ->
            lines.forEach { currentLine ->
                bytesRead += currentLine.length + 1 // +1 for newline

                // Skip empty lines
                if (currentLine.isNotBlank()) {
                    try {
                        // Update progress occasionally
                        if (processedCount % 100 == 0) {
                            val fileProgress = (bytesRead.toFloat() / fileSize * 100).toInt()
                            Log.d(TAG, "Term bank processing: $fileProgress% complete, $processedCount entries")
                        }
                        
                        // Parse the line (we support various simple formats)
                        val parsedEntry = parseSimpleTextEntry(currentLine, dictionaryId)
                        if (parsedEntry != null) {
                            entries.add(parsedEntry)
                            processedCount++

                            // If we've reached the batch size, save to database
                            if (entries.size >= BATCH_SIZE) {
                                dictionaryRepository.importDictionaryEntries(entries)
                                entries.clear()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing simple text entry: ${e.message}")
                    }
                }
            }
        }
        
        return processedCount
    }

    /**
     * Parse a single term bank entry in array format
     */
    private fun parseTermBankEntry(termEntry: JSONArray, dictionaryId: Long): DictionaryEntryEntity? {
        return try {
            // Validate entry structure
            if (termEntry.length() < 1) {
                Log.e(TAG, "Invalid term entry: array too short, need at least term at position 0")
                return null
            }
            
            // Debug log the entire entry structure for the first few entries
            if (entryCount < 5) {
                Log.d(TAG, "Processing entry structure: length=${termEntry.length()}, content=${termEntry.toString().take(100)}...")
                
                // Log the first few positions to better understand the structure
                for (i in 0 until Math.min(termEntry.length(), 6)) {
                    val item = termEntry.opt(i)
                    val preview = when (item) {
                        is String -> "\"${item.take(20)}${if (item.length > 20) "..." else ""}\""
                        is JSONArray -> "JSONArray(${item.length()})"
                        is JSONObject -> "JSONObject keys: ${if (item.keys().hasNext()) item.keys().asSequence().take(3).joinToString() else "empty"}"
                        null -> "null"
                        else -> item.toString().take(20)
                    }
                    Log.d(TAG, "  Position $i: $preview")
                }
            }
            
            // Based on Yomitan dictionary format:
            // [0] = term, [1] = reading, [2] = tags, [3] = part of speech/rules, [4] = score, [5] = glosses/content
            
            // Check if termEntry might be a whole file parsed as a single entry
            // This is a special case for array-es dictionaries
            if (termEntry.length() > 0) {
                val firstItem = termEntry.opt(0)
                if (firstItem is JSONArray) {
                    // This appears to be a nested array structure like [["term", ...], ["term2", ...]]
                    // Process the first entry and return it
                    Log.d(TAG, "Found nested array structure - extracting first entry")
                    
                    try {
                        // Get the first entry from the array
                        val nestedEntry = termEntry.getJSONArray(0)
                        
                        // Make a recursive call to properly process the nested entry
                        return parseTermBankEntry(nestedEntry, dictionaryId)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error extracting nested entry: ${e.message}")
                    }
                }
            }
            
            // Get the term (required)
            // Check for string that looks like [["abada", ... - this is misformatted JSON
            val termString = termEntry.getString(0)
            if (termString.startsWith("[[\"") || termString.contains("],[")) {
                // This is a string representation of a JSON array - needs special handling
                Log.w(TAG, "Found string containing JSON array: ${termString.take(50)}...")
                
                try {
                    // Try to parse the string as a JSON array
                    val parsedArray = JSONArray(termString)
                    if (parsedArray.length() > 0) {
                        // Extract the first entry from the array
                        val firstEntry = parsedArray.getJSONArray(0)
                        
                        // Create a dictionary entry from this first item
                        return DictionaryEntryEntity(
                            dictionaryId = dictionaryId,
                            term = firstEntry.getString(0),
                            reading = if (firstEntry.length() > 1) firstEntry.getString(1) else "",
                            definition = if (firstEntry.length() > 5) {
                                val defArray = firstEntry.getJSONArray(5)
                                val defBuilder = StringBuilder()
                                for (i in 0 until defArray.length()) {
                                    if (defBuilder.isNotEmpty()) defBuilder.append("\n")
                                    defBuilder.append(defArray.getString(i))
                                }
                                defBuilder.toString()
                            } else "",
                            partOfSpeech = if (firstEntry.length() > 2) firstEntry.getString(2) else "",
                            tags = emptyList(),
                            isHtmlContent = false
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing JSON string: ${e.message}")
                }
            }
            
            // Normal processing if the above special case handling didn't return
            val term = termString // Assign to variable term for consistency with rest of method
            if (term.isEmpty()) {
                Log.e(TAG, "Empty term in entry, skipping: ${termEntry.toString().take(50)}...")
                return null
            }
            
            // Get the reading (optional)
            val reading = termEntry.optString(1, "")
            
            // Get the part of speech (optional)
            val partOfSpeech = termEntry.optString(3, "")

            // Extract glosses from the structured content or simple array
            var definition = ""
            
            // Format 3 dictionaries (Spanish) often have simple array of strings at position 5
            val contentObj = termEntry.opt(5)
            
            // Flag to track if the content is HTML
            var isHtml = false
            
            if (contentObj is JSONArray) {
                // Check if any item is a structured content object
                var hasStructuredContent = false
                for (i in 0 until contentObj.length()) {
                    val item = contentObj.opt(i)
                    if (item is JSONObject && item.has("type") && item.getString("type") == "structured-content") {
                        hasStructuredContent = true
                        break
                    }
                }
                
                if (hasStructuredContent) {
                    // Process as structured content
                    val htmlBuilder = StringBuilder()
                    for (i in 0 until contentObj.length()) {
                        val item = contentObj.opt(i)
                        if (item is JSONObject && item.has("type") && item.getString("type") == "structured-content") {
                            // Convert structured content to HTML
                            htmlBuilder.append(StructuredContentHtmlConverter.convertToHtml(item))
                        } else if (item is String) {
                            // Wrap plain strings in paragraph tags
                            htmlBuilder.append("<p>").append(item).append("</p>")
                        }
                    }
                    definition = htmlBuilder.toString()
                    isHtml = true
                } else {
                    // Simple array of strings - convert to HTML ordered list
                    definition = StructuredContentHtmlConverter.convertStringArrayToHtml(contentObj)
                    isHtml = true
                }
            } else if (contentObj is JSONObject) {
                // Check if this is structured content
                if (contentObj.has("type") && contentObj.getString("type") == "structured-content") {
                    // Debug the structured content object
                    if (entryCount < 5) {
                        Log.d(TAG, "Found structured content at position 5: ${contentObj.toString().take(150)}...")
                    }
                    
                    // Convert structured content to HTML
                    definition = StructuredContentHtmlConverter.convertToHtml(contentObj)
                    
                    // Debug the generated HTML
                    if (entryCount < 5) {
                        Log.d(TAG, "Generated HTML: ${definition.take(150)}...")
                    }
                    
                    isHtml = true
                } else {
                    // Process as regular JSON object
                    definition = extractDefinitionFromStructuredContent(contentObj)
                }
            } else if (contentObj is String) {
                // Simple string definition - wrap in paragraph tag
                definition = "<p>$contentObj</p>"
                isHtml = true
            }

            // Get the tags
            val tags = if (termEntry.opt(2) is JSONArray) {
                // Handle tags as JSON array
                val tagsArray = termEntry.getJSONArray(2)
                val tagsList = mutableListOf<String>()
                for (i in 0 until tagsArray.length()) {
                    tagsList.add(tagsArray.getString(i))
                }
                tagsList
            } else {
                // Handle tags as string
                extractTags(termEntry.optString(2, ""))
            }

            // Check if definition looks like raw HTML/JSON
            if (definition.contains("<tag>") || definition.contains("\"content\":") || 
                definition.contains("\"tag\":")) {
                // Found raw JSON or invalid HTML - turn off HTML flag
                Log.w(TAG, "Found raw JSON/HTML in definition - turning off HTML flag")
                isHtml = false
            }
            
            // Create entry and log definition info
            val entry = DictionaryEntryEntity(
                dictionaryId = dictionaryId,
                term = term,
                reading = reading,
                definition = definition,
                partOfSpeech = partOfSpeech,
                tags = tags,
                isHtmlContent = isHtml
            )
            
            // Log some details about the entry for debugging - limit frequency for performance
            if (entryCount % 1000 == 0) {
                if (definition.isNotBlank()) {
                    Log.d(TAG, "Created array entry with definition: ${definition.take(50)}${if (definition.length > 50) "..." else ""}")
                } else {
                    Log.d(TAG, "Created array entry with EMPTY definition for term: $term")
                }
            }
            
            entryCount++
            entry
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing array term bank entry", e)
            null
        }
    }
    
    // Counter for imported entries - used to limit logging
    private var entryCount = 0
    
    /**
     * Parse a term bank entry in object format
     */
    private fun parseObjectTermBankEntry(termEntry: JSONObject, dictionaryId: Long): DictionaryEntryEntity? {
        return try {
            // Get the fields
            val term = termEntry.getString("term")
            val reading = termEntry.optString("reading", "")
            val partOfSpeech = termEntry.optString("partOfSpeech", "")
            
            // Get the definition which might be in different formats
            var definition = ""
            var isHtml = false

            if (termEntry.has("definition")) {
                // Direct definition field - check if it's a structured content object
                val defValue = termEntry.get("definition")
                if (defValue is JSONObject && defValue.has("type") && 
                    defValue.getString("type") == "structured-content") {
                    // Convert structured content to HTML
                    definition = com.abaga129.tekisuto.util.StructuredContentHtmlConverter.convertToHtml(defValue)
                    isHtml = true
                } else if (defValue is String) {
                    // Simple string definition - wrap in HTML paragraph
                    definition = "<p>$defValue</p>"
                    isHtml = true
                }
            } else if (termEntry.has("definitions")) {
                // Array of definitions - check if any are structured content
                val definitions = termEntry.getJSONArray("definitions")
                
                // Convert to HTML list
                definition = com.abaga129.tekisuto.util.StructuredContentHtmlConverter.convertStringArrayToHtml(definitions)
                isHtml = true
            } else if (termEntry.has("meaning")) {
                // Single meaning field
                val meaning = termEntry.get("meaning")
                if (meaning is JSONObject && meaning.has("type") && 
                    meaning.getString("type") == "structured-content") {
                    // Convert structured content to HTML
                    definition = com.abaga129.tekisuto.util.StructuredContentHtmlConverter.convertToHtml(meaning)
                    isHtml = true
                } else if (meaning is String) {
                    // Simple string meaning - wrap in HTML paragraph
                    definition = "<p>$meaning</p>"
                    isHtml = true
                }
            } else if (termEntry.has("meanings")) {
                // Array of meanings - convert to HTML list
                val meanings = termEntry.getJSONArray("meanings")
                definition = com.abaga129.tekisuto.util.StructuredContentHtmlConverter.convertStringArrayToHtml(meanings)
                isHtml = true
            }
            
            // Get the tags
            val tags = if (termEntry.has("tags")) {
                val tagsArray = termEntry.getJSONArray("tags")
                val tagsList = mutableListOf<String>()
                
                for (i in 0 until tagsArray.length()) {
                    tagsList.add(tagsArray.getString(i))
                }
                
                tagsList
            } else {
                emptyList()
            }
            
            // Create entry
            val entry = DictionaryEntryEntity(
                dictionaryId = dictionaryId,
                term = term,
                reading = reading,
                definition = definition,
                partOfSpeech = partOfSpeech,
                tags = tags,
                isHtmlContent = isHtml
            )
            
            // Log some details about the entry for debugging
            if (definition.isNotBlank()) {
                Log.d(TAG, "Created object entry with definition: ${definition.take(50)}${if (definition.length > 50) "..." else ""}")
            } else {
                Log.d(TAG, "Created object entry with EMPTY definition for term: $term")
            }
            
            entry
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing object term bank entry", e)
            null
        }
    }
    
    /**
     * Parse a term from a simple text format
     * Supports various formats like "term:reading:definition" or "term<tab>reading<tab>definition"
     */
    private fun parseSimpleTextEntry(line: String, dictionaryId: Long): DictionaryEntryEntity? {
        return try {
            // Try to split the line in various ways (tab, colon, etc.)
            val parts = when {
                line.contains("\t") -> line.split("\t")
                line.contains("::") -> line.split("::")
                line.contains(":") -> line.split(":")
                line.contains("=") -> line.split("=")
                else -> {
                    // If there are no separators, assume it's just a term
                    listOf(line)
                }
            }
            
            if (parts.isEmpty()) return null
            
            // Extract term (required)
            val term = parts[0].trim()
            if (term.isEmpty()) return null
            
            // Extract reading (optional)
            val reading = if (parts.size > 1) parts[1].trim() else ""
            
            // Extract definition (optional)
            val definition = if (parts.size > 2) parts.subList(2, parts.size).joinToString("; ") else ""
            
            // Create entry
            val entry = DictionaryEntryEntity(
                dictionaryId = dictionaryId,
                term = term,
                reading = reading,
                definition = definition,
                partOfSpeech = "",
                tags = emptyList(),
                isHtmlContent = false // Explicitly set to false for simple text entries
            )
            
            // Log some details about the entry for debugging
            if (definition.isNotBlank()) {
                Log.d(TAG, "Created simple text entry with definition: ${definition.take(50)}${if (definition.length > 50) "..." else ""}")
            } else {
                Log.d(TAG, "Created simple text entry with EMPTY definition for term: $term")
            }
            
            entry
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing simple text entry", e)
            null
        }
    }

    /**
     * Extracts the dictionary zip file to a temporary directory
     */
    private suspend fun extractDictionaryZip(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val zipInputStream = ZipInputStream(BufferedInputStream(inputStream))

            // Create a temporary directory
            val tempDir = File(context.cacheDir, "dictionary_import_${System.currentTimeMillis()}")
            if (!tempDir.exists()) {
                tempDir.mkdirs()
            }

            // Extract all files
            var entry = zipInputStream.nextEntry
            val buffer = ByteArray(8192)

            while (entry != null) {
                val entryFile = File(tempDir, entry.name)

                if (entry.isDirectory) {
                    entryFile.mkdirs()
                } else {
                    val parent = entryFile.parentFile
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs()
                    }

                    val outputStream = FileOutputStream(entryFile)
                    var len: Int

                    while (zipInputStream.read(buffer).also { len = it } > 0) {
                        outputStream.write(buffer, 0, len)
                    }

                    outputStream.close()
                }

                zipInputStream.closeEntry()
                entry = zipInputStream.nextEntry
            }

            zipInputStream.close()
            inputStream.close()

            tempDir
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting dictionary zip", e)
            null
        }
    }

    /**
     * Parses dictionary info from index.json
     */
    private fun parseDictionaryInfo(indexJson: String): DictionaryInfo {
        return try {
            val json = JSONObject(indexJson)
            Log.d(TAG, "Dictionary index.json: $indexJson")
            
            // Check for different dictionary info formats
            val title = when {
                json.has("title") -> json.optString("title")
                json.has("name") -> json.optString("name")
                json.has("dict_title") -> json.optString("dict_title")
                json.has("dictionary_title") -> json.optString("dictionary_title")
                else -> "Unknown Dictionary"
            }
            
            val revision = when {
                json.has("revision") -> json.optString("revision")
                json.has("version") -> json.optString("version")
                else -> "Unknown"
            }
            
            val format = when {
                json.has("format") -> json.optInt("format", 0)
                json.has("version") -> json.optInt("version", 0)
                else -> 0
            }
            
            val sequenced = json.optBoolean("sequenced", false)
            
            val author = when {
                json.has("author") -> json.optString("author")
                json.has("creator") -> json.optString("creator")
                else -> "Unknown"
            }
            
            val description = when {
                json.has("description") -> json.optString("description")
                json.has("about") -> json.optString("about")
                json.has("info") -> json.optString("info")
                else -> ""
            }
            
            val sourceLanguage = when {
                json.has("sourceLanguage") -> json.optString("sourceLanguage")
                json.has("source_language") -> json.optString("source_language")
                json.has("src_language") -> json.optString("src_language")
                json.has("from") -> json.optString("from")
                else -> "es" // Default to Spanish if importing a Spanish dictionary
            }
            
            val targetLanguage = when {
                json.has("targetLanguage") -> json.optString("targetLanguage")
                json.has("target_language") -> json.optString("target_language")
                json.has("tgt_language") -> json.optString("tgt_language")
                json.has("to") -> json.optString("to")
                else -> "en" // Default to English
            }
            
            Log.d(TAG, "Dictionary info parsed: $title (format: $format, sequenced: $sequenced)")
            
            DictionaryInfo(
                title = title,
                revision = revision,
                format = format,
                author = author,
                description = description,
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing dictionary info", e)
            DictionaryInfo(
                title = "Unknown Dictionary",
                revision = "Unknown",
                format = 0,
                author = "Unknown",
                description = "",
                sourceLanguage = "es", // Default to Spanish
                targetLanguage = "en" // Default to English
            )
        }
    }

    /**
     * Extracts a human-readable definition from structured content
     */
    private fun extractDefinitionFromStructuredContent(structuredContent: JSONObject): String {
        val sb = StringBuilder()

        try {
            if (structuredContent.has("type") && structuredContent.getString("type") == "structured-content") {
                val content = structuredContent.getJSONArray("content")

                // Find the glosses section
                for (i in 0 until content.length()) {
                    val item = content.getJSONObject(i)
                    if (item.has("tag") && item.getString("tag") == "ol") {
                        if (item.has("data") && item.getJSONObject("data").has("content") &&
                            item.getJSONObject("data").getString("content") == "glosses") {

                            // Process glosses
                            val glosses = item.getJSONArray("content")
                            for (j in 0 until glosses.length()) {
                                val gloss = glosses.getJSONObject(j)
                                if (gloss.has("tag") && gloss.getString("tag") == "li") {
                                    // Extract the text content
                                    val glossContent = gloss.getJSONArray("content")
                                    for (k in 0 until glossContent.length()) {
                                        val textContainer = glossContent.getJSONObject(k)
                                        if (textContainer.has("tag") && textContainer.getString("tag") == "div") {
                                            val text = extractTextFromContent(textContainer.getJSONArray("content"))
                                            if (text.isNotEmpty()) {
                                                if (sb.isNotEmpty()) sb.append("\n")
                                                sb.append((j + 1).toString()).append(". ").append(text)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting definition from structured content", e)
        }

        return sb.toString()
    }

    /**
     * Extracts text from content array, which may be simple strings or complex objects
     */
    private fun extractTextFromContent(content: JSONArray): String {
        val sb = StringBuilder()

        for (i in 0 until content.length()) {
            try {
                val item = content.get(i)

                if (item is String) {
                    sb.append(item)
                } else if (item is JSONObject && item.has("tag")) {
                    // If it's a nested tag, recursively extract text
                    if (item.has("content")) {
                        if (item.get("content") is JSONArray) {
                            sb.append(extractTextFromContent(item.getJSONArray("content")))
                        } else if (item.get("content") is String) {
                            sb.append(item.getString("content"))
                        }
                    }
                }
            } catch (e: Exception) {
                // Skip problematic items
            }
        }

        return sb.toString()
    }

    /**
     * Extract tags from tag string
     */
    private fun extractTags(tagString: String): List<String> {
        return if (tagString.isBlank()) {
            emptyList()
        } else {
            tagString.split(",").map { it.trim() }
        }
    }
}

/**
 * Dictionary information
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