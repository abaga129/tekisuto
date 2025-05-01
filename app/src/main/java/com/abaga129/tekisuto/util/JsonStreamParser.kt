package com.abaga129.tekisuto.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader

/**
 * Utility for parsing large JSON files incrementally without loading the entire file
 * into memory at once. Designed to handle large dictionary files efficiently.
 */
class JsonStreamParser {
    companion object {
        private const val TAG = "JsonStreamParser"
        private const val READ_BUFFER_SIZE = 8192 // 8KB buffer for reading
        private const val CHUNK_FILE_SIZE = 1024 * 1024 // 1MB chunks for file reading
        
        /**
         * Process a JSON array file incrementally, providing chunks of entries to the processor function
         * Uses a true streaming approach that never loads the entire file into memory
         * 
         * @param file The JSON file containing a top-level array
         * @param chunkSize The number of array elements to process at a time
         * @param processor A suspend function that processes each chunk of entries
         * @return The total number of entries processed
         */
        suspend fun processJsonArrayFile(
            file: File, 
            chunkSize: Int,
            processor: suspend (List<List<Any?>>) -> Unit
        ): Int = withContext(Dispatchers.IO) {
            if (!file.exists()) {
                Log.e(TAG, "File does not exist: ${file.path}")
                return@withContext 0
            }
            
            try {
                // First, try direct incremental parsing
                val fileSize = file.length()
                Log.d(TAG, "Processing ${file.name}: file size: ${fileSize/1024}KB")
                
                return@withContext processFileIncrementally(file, chunkSize, processor)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing file: ${e.message}")
                return@withContext 0
            }
        }
        
        /**
         * Process the file in an incremental manner to minimize memory usage
         */
        private suspend fun processFileIncrementally(
            file: File,
            chunkSize: Int,
            processor: suspend (List<List<Any?>>) -> Unit
        ): Int {
            var totalEntries = 0
            
            try {
                FileInputStream(file).use { fileIs ->
                    BufferedReader(InputStreamReader(fileIs, "UTF-8"), READ_BUFFER_SIZE).use { reader ->
                        // Process JSON array character by character
                        var inArray = false
                        var inEntry = false
                        var inString = false
                        var escape = false
                        var bracketDepth = 0
                        var entryBuffer = StringBuilder()
                        var entryList = mutableListOf<List<Any?>>()
                        
                        var c: Int
                        while (reader.read().also { c = it } != -1) {
                            val char = c.toChar()
                            
                            // Handle string literals with escape sequences
                            if (inString) {
                                entryBuffer.append(char)
                                if (escape) {
                                    escape = false
                                } else if (char == '\\') {
                                    escape = true
                                } else if (char == '"') {
                                    inString = false
                                }
                                continue
                            }
                            
                            // Process based on character type
                            when (char) {
                                '[' -> {
                                    bracketDepth++
                                    if (bracketDepth == 1) {
                                        // Start of the top-level JSON array
                                        inArray = true
                                    } else if (bracketDepth == 2 && inArray) {
                                        // Start of an entry
                                        inEntry = true
                                        entryBuffer.append(char)
                                    } else {
                                        // Inside a nested structure
                                        entryBuffer.append(char)
                                    }
                                }
                                ']' -> {
                                    bracketDepth--
                                    if (bracketDepth == 1 && inEntry) {
                                        // End of an entry
                                        entryBuffer.append(char)
                                        
                                        // Process the entry
                                        try {
                                            val jsonArray = JSONArray(entryBuffer.toString())
                                            val entryData = convertJsonArrayToList(jsonArray)
                                            entryList.add(entryData)
                                            
                                            // Process in chunks for memory efficiency
                                            if (entryList.size >= chunkSize) {
                                                processor(entryList)
                                                totalEntries += entryList.size
                                                entryList = mutableListOf()
                                                
                                                // Log progress periodically
                                                if (totalEntries % 5000 == 0) {
                                                    Log.d(TAG, "Processed $totalEntries entries from ${file.name}")
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error parsing entry: ${e.message}")
                                        }
                                        
                                        inEntry = false
                                        entryBuffer = StringBuilder()
                                    } else if (bracketDepth == 0) {
                                        // End of the top-level array
                                        inArray = false
                                    } else {
                                        // Inside a nested structure
                                        entryBuffer.append(char)
                                    }
                                }
                                ',' -> {
                                    if (bracketDepth == 1) {
                                        // Comma between top-level entries, ignore
                                    } else {
                                        // Comma inside an entry
                                        entryBuffer.append(char)
                                    }
                                }
                                '"' -> {
                                    inString = true
                                    entryBuffer.append(char)
                                }
                                else -> {
                                    if (inEntry) {
                                        entryBuffer.append(char)
                                    }
                                }
                            }
                        }
                        
                        // Process any remaining entries
                        if (entryList.isNotEmpty()) {
                            processor(entryList)
                            totalEntries += entryList.size
                        }
                    }
                }
                
                Log.d(TAG, "Completed processing ${file.name}: $totalEntries entries")
                return totalEntries
            } catch (e: Exception) {
                Log.e(TAG, "Error during incremental processing: ${e.message}")
                
                // If nothing was processed, try the chunked approach
                if (totalEntries == 0) {
                    Log.w(TAG, "Attempting chunked approach for ${file.name}")
                    return processFileInChunks(file, chunkSize, processor)
                }
                
                return totalEntries
            }
        }
        
        /**
         * Process file in chunks to conserve memory
         * This is a fallback method used when incremental parsing fails
         */
        private suspend fun processFileInChunks(
            file: File,
            chunkSize: Int,
            processor: suspend (List<List<Any?>>) -> Unit
        ): Int {
            var totalEntries = 0
            
            try {
                val fileSize = file.length()
                
                // Use a smaller chunk size for large files
                val actualChunkSize = if (fileSize > 50 * 1024 * 1024) {
                    // For files > 50MB, use a smaller chunk size
                    chunkSize / 4
                } else if (fileSize > 10 * 1024 * 1024) {
                    // For files > 10MB, use a slightly smaller chunk size
                    chunkSize / 2
                } else {
                    chunkSize
                }
                
                Log.d(TAG, "Processing ${file.name} in chunks using chunk size: $actualChunkSize")
                
                // First, find out if the file starts with an array
                var isArrayFile = false
                var firstChar = ' '
                
                FileInputStream(file).use { fileIs ->
                    // Skip whitespace and find first non-whitespace character
                    var c: Int
                    while (fileIs.read().also { c = it } != -1) {
                        val char = c.toChar()
                        if (!char.isWhitespace()) {
                            firstChar = char
                            break
                        }
                    }
                    
                    isArrayFile = (firstChar == '[')
                }
                
                if (!isArrayFile) {
                    Log.e(TAG, "File does not start with a JSON array")
                    return 0
                }
                
                // Process file using JSON array parser
                FileInputStream(file).use { fileIs ->
                    BufferedReader(InputStreamReader(fileIs, "UTF-8"), READ_BUFFER_SIZE).use { reader ->
                        // Skip to the first [
                        var c: Int
                        do {
                            c = reader.read()
                        } while (c != -1 && c != '['.code)
                        
                        if (c == -1) {
                            Log.e(TAG, "File format error - no opening bracket found")
                            return 0
                        }
                        
                        // Now parse entries one by one
                        var currentEntry = StringBuilder()
                        var bracketDepth = 1  // We've already read the opening bracket
                        var inString = false
                        var escape = false
                        var entriesList = mutableListOf<List<Any?>>()
                        
                        while (reader.read().also { c = it } != -1) {
                            val char = c.toChar()
                            
                            // Handle string literals with escaping
                            if (inString) {
                                currentEntry.append(char)
                                if (escape) {
                                    escape = false
                                } else if (char == '\\') {
                                    escape = true
                                } else if (char == '"') {
                                    inString = false
                                }
                                continue
                            }
                            
                            // Process based on character
                            when (char) {
                                '[' -> {
                                    bracketDepth++
                                    currentEntry.append(char)
                                }
                                ']' -> {
                                    bracketDepth--
                                    if (bracketDepth == 0) {
                                        // End of array, we're done
                                        break
                                    } else if (bracketDepth == 1) {
                                        // End of entry
                                        currentEntry.append(char)
                                        
                                        // Process the entry
                                        try {
                                            val jsonArray = JSONArray(currentEntry.toString())
                                            val entry = convertJsonArrayToList(jsonArray)
                                            entriesList.add(entry)
                                            
                                            // Process batch if needed
                                            if (entriesList.size >= actualChunkSize) {
                                                processor(entriesList)
                                                totalEntries += entriesList.size
                                                entriesList = mutableListOf()
                                                
                                                // Force GC occasionally to free memory
                                                if (totalEntries % 10000 == 0) {
                                                    System.gc()
                                                }
                                                
                                                // Log progress
                                                if (totalEntries % 5000 == 0) {
                                                    Log.d(TAG, "Processed $totalEntries entries from ${file.name}")
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error parsing entry: ${e.message}")
                                        }
                                        
                                        // Reset for next entry
                                        currentEntry = StringBuilder()
                                    } else {
                                        // Inside a nested array
                                        currentEntry.append(char)
                                    }
                                }
                                ',' -> {
                                    if (bracketDepth == 1) {
                                        // We're at the top level - process the entry
                                        try {
                                            val jsonArray = JSONArray(currentEntry.toString())
                                            val entry = convertJsonArrayToList(jsonArray)
                                            entriesList.add(entry)
                                            
                                            // Process batch if needed
                                            if (entriesList.size >= actualChunkSize) {
                                                processor(entriesList)
                                                totalEntries += entriesList.size
                                                entriesList = mutableListOf()
                                                
                                                // Force GC occasionally to free memory
                                                if (totalEntries % 10000 == 0) {
                                                    System.gc()
                                                }
                                                
                                                // Log progress
                                                if (totalEntries % 5000 == 0) {
                                                    Log.d(TAG, "Processed $totalEntries entries from ${file.name}")
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error parsing entry: ${e.message}")
                                        }
                                        
                                        // Reset for next entry
                                        currentEntry = StringBuilder()
                                    } else {
                                        // Inside a nested structure
                                        currentEntry.append(char)
                                    }
                                }
                                '"' -> {
                                    inString = true
                                    currentEntry.append(char)
                                }
                                else -> {
                                    // Only append if not whitespace or if we're inside an entry structure
                                    if (!char.isWhitespace() || bracketDepth > 1) {
                                        currentEntry.append(char)
                                    }
                                }
                            }
                        }
                        
                        // Process any remaining entries
                        if (entriesList.isNotEmpty()) {
                            processor(entriesList)
                            totalEntries += entriesList.size
                        }
                    }
                }
                
                Log.d(TAG, "Completed chunked processing of ${file.name}: $totalEntries entries")
                return totalEntries
            } catch (e: Exception) {
                Log.e(TAG, "Chunked processing failed: ${e.message}")
                return totalEntries
            }
        }
        
        /**
         * Converts a JSONArray to a List of Any
         */
        private fun convertJsonArrayToList(jsonArray: JSONArray): List<Any?> {
            val result = mutableListOf<Any?>()
            
            for (i in 0 until jsonArray.length()) {
                when {
                    jsonArray.isNull(i) -> result.add(null)
                    else -> {
                        try {
                            // Try to get as JSONArray (for nested arrays)
                            val nestedArray = jsonArray.getJSONArray(i)
                            result.add(convertJsonArrayToList(nestedArray))
                        } catch (e: JSONException) {
                            try {
                                // Try to get as JSONObject (for nested objects)
                                val obj = jsonArray.getJSONObject(i)
                                result.add(convertJsonObjectToMap(obj))
                            } catch (e: JSONException) {
                                // Must be a primitive type
                                result.add(jsonArray.get(i))
                            }
                        }
                    }
                }
            }
            
            return result
        }
        
        /**
         * Converts a JSONObject to a Map
         */
        private fun convertJsonObjectToMap(jsonObject: JSONObject): Map<String, Any?> {
            val result = mutableMapOf<String, Any?>()
            
            jsonObject.keys().forEach { key ->
                val value = jsonObject.get(key)
                
                when (value) {
                    is JSONObject -> result[key] = convertJsonObjectToMap(value)
                    is JSONArray -> result[key] = convertJsonArrayToList(value)
                    JSONObject.NULL -> result[key] = null
                    else -> result[key] = value
                }
            }
            
            return result
        }
    }
}