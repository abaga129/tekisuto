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
                        if (file.name != "index.json") {
                            dictionaryFiles.add(file)
                        }
                    }
                }
            }
            
            // Estimate memory usage and handle large dictionaries carefully
            val totalSize = dictionaryFiles.sumOf { it.length() }
            Log.d(TAG, "Total dictionary size: ${totalSize / 1024 / 1024}MB")
            
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
                } catch (e: OutOfMemoryError) {
                    Log.e(TAG, "Out of memory error processing file ${file.name}. Skipping file.", e)
                    _importProgress.postValue(-2) // Special code for OOM but continuing
                    // Force garbage collection
                    System.gc()
                }
            }

            Log.d(TAG, "Processed $totalEntriesProcessed entries from ${dictionaryFiles.size} dictionary files")

            // Update dictionary info
            _dictionaryInfo.postValue(dictionaryInfo)

            // Clean up temporary files
            extractedDir.deleteRecursively()
            
            // Force final garbage collection
            System.gc()

            // Complete progress
            _importProgress.postValue(100)

            Log.d(TAG, "Dictionary import completed successfully")
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

        try {
            // Check if file is too large (>50MB) to handle memory constraints
            val fileSize = termBankFile.length()
            if (fileSize > 50 * 1024 * 1024) {
                Log.w(TAG, "Dictionary file is very large (${fileSize/1024/1024}MB). Processing with memory optimizations.")
            }

            // Use a streaming JSON parser with larger buffer size for efficiency
            val fileStream = FileInputStream(termBankFile)
            val bufferedReader = BufferedReader(InputStreamReader(fileStream), 8192)

            // Check the file format by reading the first line
            var line = bufferedReader.readLine()
            if (line == null) {
                Log.e(TAG, "Term bank file is empty")
                bufferedReader.close()
                return 0
            }
            
            // Reset the file stream to start from the beginning
            bufferedReader.close()
            fileStream.close()
            
            val newFileStream = FileInputStream(termBankFile)
            val newBufferedReader = BufferedReader(InputStreamReader(newFileStream), 8192)
            
            // Initialize batch processing
            val entries = mutableListOf<DictionaryEntryEntity>()
            
            // Different dictionaries use different formats
            entriesProcessed = when {
                line.trimStart().startsWith("[") -> {
                    // Yomitan/Yomichan format with arrays
                    Log.d(TAG, "Processing array-based dictionary format")
                    processArrayFormatDictionary(newBufferedReader, fileSize, entries, entriesProcessed)
                }
                line.trimStart().startsWith("{") || line.contains("\"term\"") -> {
                    // Object-based format with explicit term/reading/definition fields
                    Log.d(TAG, "Processing object-based dictionary format")
                    processObjectFormatDictionary(newBufferedReader, fileSize, entries, entriesProcessed)
                }
                else -> {
                    // Simple text format (term:reading:definition)
                    Log.d(TAG, "Processing simple text dictionary format")
                    processSimpleTextDictionary(newBufferedReader, fileSize, entries, entriesProcessed)
                }
            }
            
            // Save any remaining entries
            if (entries.isNotEmpty()) {
                dictionaryRepository.importDictionaryEntries(entries)
            }
            
            newBufferedReader.close()
            newFileStream.close()
            
            // Force garbage collection after processing large files
            System.gc()
            
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Out of memory error processing dictionary file. Try using smaller dictionary files.", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing term bank file", e)
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
        
        // Use a char-by-char approach instead of accumulating an entire entry
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

            // Extract glosses from the structured content
            var definition = ""
            val contentObj = termEntry.optJSONArray(5)
            if (contentObj != null && contentObj.length() > 0) {
                definition = extractDefinitionFromStructuredContent(contentObj.getJSONObject(0))
            }

            // Get the tags
            val tags = extractTags(termEntry.optString(2, ""))

            // Create entry and log definition info
            val entry = DictionaryEntryEntity(
                term = term,
                reading = reading,
                definition = definition,
                partOfSpeech = partOfSpeech,
                tags = tags
            )
            
            // Log some details about the entry for debugging
            if (definition.isNotBlank()) {
                Log.d(TAG, "Created array entry with definition: ${definition.take(50)}${if (definition.length > 50) "..." else ""}")
            } else {
                Log.d(TAG, "Created array entry with EMPTY definition for term: $term")
            }
            
            entry
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing array term bank entry", e)
            null
        }
    }
    
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
                else -> ""
            }
            
            val targetLanguage = when {
                json.has("targetLanguage") -> json.optString("targetLanguage")
                json.has("target_language") -> json.optString("target_language")
                json.has("tgt_language") -> json.optString("tgt_language")
                json.has("to") -> json.optString("to")
                else -> ""
            }
            
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
                sourceLanguage = "",
                targetLanguage = ""
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