package com.abaga129.tekisuto.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.abaga129.tekisuto.database.DictionaryEntryEntity
import com.abaga129.tekisuto.database.DictionaryRepository
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
        val enabledServicesSetting = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )

        val enabled = enabledServicesSetting?.contains(serviceClassName) == true
        _isAccessibilityServiceEnabled.value = enabled
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

            // Clear existing entries before import
            dictionaryRepository.clearDictionary()

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
                
                // Force garbage collection before processing each large file
                if (file.length() > 10 * 1024 * 1024) { // If file > 10MB
                    System.gc()
                }
                
                // Process this file
                try {
                    val entriesInThisFile = processTermBankFile(file)
                    totalEntriesProcessed += entriesInThisFile
                    
                    // Update progress
                    val progress = 10 + ((index + 1) * 80) / dictionaryFiles.size
                    _importProgress.postValue(progress.coerceAtMost(90))
                    
                    Log.d(TAG, "Completed processing ${file.name} with $entriesInThisFile entries")
                } catch (e: OutOfMemoryError) {
                    Log.e(TAG, "Out of memory error processing file ${file.name}. Skipping file.", e)
                    _importProgress.postValue(-2) // Special code for OOM but continuing
                    // Force garbage collection
                    System.gc()
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
     * @return The number of entries processed
     */
    private suspend fun processTermBankFile(termBankFile: File): Int {
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
            
            // Check the first few characters to determine the format
            val firstChars = CharArray(100)
            bufferedReader.mark(1000) // Mark with large readahead limit for big files
            bufferedReader.read(firstChars, 0, 100)
            bufferedReader.reset()
            
            val firstCharsString = String(firstChars)
            Log.d(TAG, "First chars of file: ${firstCharsString.take(20)}...")
            
            // Initialize batch processing
            val entries = mutableListOf<DictionaryEntryEntity>()
            
            // Different dictionaries use different formats
            entriesProcessed = when {
                firstCharsString.trimStart().startsWith("[") -> {
                    // Array format (includes both standard and Spanish array-in-array format)
                    Log.d(TAG, "Processing array-based dictionary format")
                    processArrayFormatDictionary(bufferedReader, fileSize, entries, entriesProcessed)
                }
                firstCharsString.trimStart().startsWith("{") || firstCharsString.contains("\"term\"") -> {
                    // Object-based format with explicit term/reading/definition fields
                    Log.d(TAG, "Processing object-based dictionary format")
                    processObjectFormatDictionary(bufferedReader, fileSize, entries, entriesProcessed)
                }
                else -> {
                    // Simple text format (term:reading:definition)
                    Log.d(TAG, "Processing simple text dictionary format")
                    processSimpleTextDictionary(bufferedReader, fileSize, entries, entriesProcessed)
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
        entriesProcessed: Int
    ): Int {
        var processedCount = entriesProcessed
        var bytesRead = 0L
        
        try {
            // Check if the first entry looks like part of a top-level array (Spanish dictionary format)
            val hasTopLevelArray = hasTopLevelArray(bufferedReader)
            
            if (hasTopLevelArray) {
                // Process the file as one giant line with array of arrays
                Log.d(TAG, "Processing array-in-array format dictionary")
                // We need a fresh reader since we can't reliably reset
                processedCount = processArrayInArrayFormat(bufferedReader, fileSize, entries, processedCount)
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
                                    val parsedEntry = parseTermBankEntry(jsonArray)
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
     * Spanish dictionaries have format: [[entry1], [entry2], ...]
     * Standard dictionaries have format: [entry1]
     */
    private fun hasTopLevelArray(reader: BufferedReader): Boolean {
        try {
            // Check if this is an array of arrays format - common in Spanish dictionaries
            reader.mark(1000) // Mark so we can reset
            
            // Read the first 100 characters to see if it's an array of arrays
            val firstChunk = CharArray(100)
            reader.read(firstChunk, 0, 100)
            val startStr = String(firstChunk)
            
            // Look for a pattern that suggests array of arrays - the key is nested brackets at start
            val hasTopLevelArray = startStr.trim().startsWith("[[")
            
            // Reset the reader to the beginning
            reader.reset()
            
            Log.d(TAG, "Dictionary format check: ${startStr.take(20)}... hasTopLevelArray=$hasTopLevelArray")
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
        entriesProcessed: Int
    ): Int {
        var processedCount = entriesProcessed
        var bytesRead = 0L
        var charRead: Int
        
        try {
            // Skip the initial opening bracket of the outer array
            reader.mark(1)
            if (reader.read().toChar() != '[') {
                Log.e(TAG, "Expected outer array start but didn't find it")
                return processedCount
            }
            
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
                            val jsonArray = JSONArray(entryBuilder.toString())
                            val parsedEntry = parseTermBankEntry(jsonArray)
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
        entriesProcessed: Int
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
                            val parsedEntry = parseObjectTermBankEntry(jsonObject)
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
        entriesProcessed: Int
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
                        val parsedEntry = parseSimpleTextEntry(currentLine)
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
    private fun parseTermBankEntry(termEntry: JSONArray): DictionaryEntryEntity? {
        return try {
            // Based on your sample, entries are arrays with specific positions
            // [0] = term, [1] = reading, [2] = tags?, [3] = part of speech, [5] = glosses/content
            val term = termEntry.getString(0)
            val reading = termEntry.optString(1, "")
            val partOfSpeech = termEntry.optString(3, "")

            // Extract glosses from the structured content or simple array
            var definition = ""
            
            // Format 3 dictionaries (Spanish) often have simple array of strings at position 5
            val contentObj = termEntry.opt(5)
            if (contentObj is JSONArray) {
                // Simple array of strings - common in Spanish dictionaries
                val defBuilder = StringBuilder()
                for (i in 0 until contentObj.length()) {
                    if (defBuilder.isNotEmpty()) defBuilder.append("\n")
                    defBuilder.append((i + 1).toString()).append(". ")
                    defBuilder.append(contentObj.getString(i))
                }
                definition = defBuilder.toString()
            } else if (contentObj is JSONObject) {
                // Structured content - more common in Japanese dictionaries
                definition = extractDefinitionFromStructuredContent(contentObj)
            } else if (contentObj is String) {
                // Simple string definition
                definition = contentObj
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

            // Create entry and log definition info
            val entry = DictionaryEntryEntity(
                term = term,
                reading = reading,
                definition = definition,
                partOfSpeech = partOfSpeech,
                tags = tags
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
    private fun parseObjectTermBankEntry(termEntry: JSONObject): DictionaryEntryEntity? {
        return try {
            // Get the fields
            val term = termEntry.getString("term")
            val reading = termEntry.optString("reading", "")
            val partOfSpeech = termEntry.optString("partOfSpeech", "")
            
            // Get the definition which might be in different formats
            var definition = ""
            if (termEntry.has("definition")) {
                // Direct definition field
                definition = termEntry.getString("definition")
            } else if (termEntry.has("definitions")) {
                // Array of definitions
                val definitions = termEntry.getJSONArray("definitions")
                val defBuilder = StringBuilder()
                
                for (i in 0 until definitions.length()) {
                    if (defBuilder.isNotEmpty()) defBuilder.append("\n")
                    defBuilder.append((i + 1).toString()).append(". ")
                    defBuilder.append(definitions.getString(i))
                }
                
                definition = defBuilder.toString()
            } else if (termEntry.has("meaning")) {
                // Single meaning field
                definition = termEntry.getString("meaning")
            } else if (termEntry.has("meanings")) {
                // Array of meanings
                val meanings = termEntry.getJSONArray("meanings")
                val defBuilder = StringBuilder()
                
                for (i in 0 until meanings.length()) {
                    if (defBuilder.isNotEmpty()) defBuilder.append("\n")
                    defBuilder.append((i + 1).toString()).append(". ")
                    defBuilder.append(meanings.getString(i))
                }
                
                definition = defBuilder.toString()
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
                term = term,
                reading = reading,
                definition = definition,
                partOfSpeech = partOfSpeech,
                tags = tags
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
    private fun parseSimpleTextEntry(line: String): DictionaryEntryEntity? {
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
                term = term,
                reading = reading,
                definition = definition,
                partOfSpeech = "",
                tags = emptyList()
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