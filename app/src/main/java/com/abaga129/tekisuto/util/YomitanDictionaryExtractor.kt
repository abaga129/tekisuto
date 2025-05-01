package com.abaga129.tekisuto.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.abaga129.tekisuto.model.yomitan.YomitanIndexInfo
import com.squareup.moshi.Moshi
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * Utility class to extract Yomitan dictionary files from a zip archive
 */
class YomitanDictionaryExtractor {
    companion object {
        private const val TAG = "YomitanDictExtractor"
        
        /**
         * Extracts a Yomitan dictionary archive to a temporary directory
         * @param context Android context
         * @param uri URI of the Yomitan dictionary zip file
         * @return The directory containing the extracted files, or null if extraction failed
         */
        fun extractDictionary(context: Context, uri: Uri): File? {
            val tempDir = createTempDirectory(context)
            Log.d(TAG, "Extracting dictionary to: ${tempDir.absolutePath}")
            
            return try {
                // Open the zip file from the URI
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedInputStream(inputStream).use { bufferedInputStream ->
                        ZipInputStream(bufferedInputStream).use { zipInputStream ->
                            var entry = zipInputStream.nextEntry
                            while (entry != null) {
                                val fileName = entry.name
                                
                                // Skip directories
                                if (!entry.isDirectory) {
                                    val outputFile = File(tempDir, fileName)
                                    
                                    // Create parent directories if needed
                                    outputFile.parentFile?.mkdirs()
                                    
                                    // Write file
                                    FileOutputStream(outputFile).use { output ->
                                        val buffer = ByteArray(4096)
                                        var count = zipInputStream.read(buffer)
                                        while (count != -1) {
                                            output.write(buffer, 0, count)
                                            count = zipInputStream.read(buffer)
                                        }
                                    }
                                    
                                    Log.d(TAG, "Extracted: $fileName")
                                }
                                
                                zipInputStream.closeEntry()
                                entry = zipInputStream.nextEntry
                            }
                        }
                    }
                }
                
                // Verify the extracted files
                if (verifyExtractedFiles(tempDir)) {
                    tempDir
                } else {
                    Log.e(TAG, "Verification of extracted files failed")
                    tempDir.deleteRecursively()
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting dictionary", e)
                tempDir.deleteRecursively()
                null
            }
        }
        
        /**
         * Creates a temporary directory for dictionary extraction
         */
        private fun createTempDirectory(context: Context): File {
            val tempDirName = "yomitan_dict_" + System.currentTimeMillis()
            val tempDir = File(context.cacheDir, tempDirName)
            tempDir.mkdirs()
            return tempDir
        }
        
        /**
         * Verifies that the extracted files contain the necessary files
         * @param directory The directory containing the extracted files
         * @return true if verification succeeds, false otherwise
         */
        private fun verifyExtractedFiles(directory: File): Boolean {
            // Check for index.json
            val indexFile = File(directory, "index.json")
            if (!indexFile.exists()) {
                Log.e(TAG, "index.json not found in extracted files")
                return false
            }
            
            // Try to parse index.json
            try {
                val indexInfo = parseIndexInfo(indexFile)
                if (indexInfo == null) {
                    Log.e(TAG, "Failed to parse index.json")
                    return false
                }
                
                Log.d(TAG, "Successfully parsed index.json for dictionary: ${indexInfo.title}")
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing index.json", e)
                return false
            }
            
            // Check for at least one term bank file
            val termBankFiles = directory.listFiles { file ->
                file.name.matches(Regex("term_bank_\\d+\\.json"))
            }
            
            if (termBankFiles.isNullOrEmpty()) {
                Log.e(TAG, "No term bank files found in extracted files")
                return false
            }
            
            Log.d(TAG, "Found ${termBankFiles.size} term bank files")
            return true
        }
        
        /**
         * Parses the index.json file to get dictionary metadata
         */
        fun parseIndexInfo(indexFile: File): YomitanIndexInfo? {
            return try {
                val jsonString = indexFile.readText()
                val moshi = Moshi.Builder().build()
                val adapter = moshi.adapter(YomitanIndexInfo::class.java)
                adapter.fromJson(jsonString)
            } catch (e: IOException) {
                Log.e(TAG, "Error reading or parsing index.json", e)
                null
            }
        }
        
        /**
         * Finds all term bank files in the dictionary directory
         */
        fun findTermBankFiles(directory: File): List<File> {
            return directory.listFiles { file ->
                file.name.matches(Regex("term_bank_\\d+\\.json"))
            }?.sortedBy { file ->
                // Extract the number part for proper sorting
                val numMatch = Regex("term_bank_(\\d+)\\.json").find(file.name)
                numMatch?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE
            } ?: emptyList()
        }
        
        /**
         * Finds all term meta bank files in the dictionary directory
         */
        fun findTermMetaBankFiles(directory: File): List<File> {
            return directory.listFiles { file ->
                file.name.matches(Regex("term_meta_bank_\\d+\\.json"))
            }?.sortedBy { file ->
                // Extract the number part for proper sorting
                val numMatch = Regex("term_meta_bank_(\\d+)\\.json").find(file.name)
                numMatch?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE
            } ?: emptyList()
        }
        
        /**
         * Cleans up temporary files
         */
        fun cleanup(directory: File) {
            if (directory.exists()) {
                directory.deleteRecursively()
                Log.d(TAG, "Cleaned up temporary files in: ${directory.absolutePath}")
            }
        }
    }
}