package com.abaga129.tekisuto.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abaga129.tekisuto.database.DictionaryEntryEntity
import com.abaga129.tekisuto.database.DictionaryRepository
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "OCRResultViewModel"

class OCRResultViewModel : ViewModel() {

    private val _ocrText = MutableLiveData<String>()
    val ocrText: LiveData<String> get() = _ocrText

    private val _screenshotPath = MutableLiveData<String>()
    val screenshotPath: LiveData<String> get() = _screenshotPath

    private val _saveResult = MutableLiveData<String>()
    val saveResult: LiveData<String> get() = _saveResult
    
    private val _dictionaryMatches = MutableLiveData<List<DictionaryEntryEntity>>()
    val dictionaryMatches: LiveData<List<DictionaryEntryEntity>> get() = _dictionaryMatches
    
    private val _isSearching = MutableLiveData<Boolean>()
    val isSearching: LiveData<Boolean> get() = _isSearching
    
    private var dictionaryRepository: DictionaryRepository? = null

    fun setOcrText(text: String) {
        _ocrText.value = text
    }

    fun setScreenshotPath(path: String) {
        _screenshotPath.value = path
    }
    
    fun initDictionaryRepository(context: Context) {
        if (dictionaryRepository == null) {
            dictionaryRepository = DictionaryRepository(context)
        }
    }
    
    /**
     * Search for dictionary matches in the OCR text
     * @param selectedText Optional text to search for. If null, uses the full OCR text.
     */
    fun findDictionaryMatches(selectedText: String? = null) {
        val text = selectedText ?: _ocrText.value ?: return
        val repository = dictionaryRepository ?: return
        
        _isSearching.value = true
        _dictionaryMatches.value = emptyList()
        
        viewModelScope.launch {
            try {
                // Log the original text for debugging
                Log.d(TAG, "OCR Text for dictionary search: $text")
                
                // Split text into words
                val words = extractWordsFromText(text)
                Log.d(TAG, "Extracted ${words.size} words for dictionary lookup")
                
                val matches = mutableListOf<DictionaryEntryEntity>()
                val processedTerms = mutableSetOf<String>() // Track terms we've already processed
                
                // Check each word against the dictionary
                for (word in words) {
                    // Skip empty words or already processed terms
                    if (word.isBlank() || processedTerms.contains(word.lowercase(Locale.ROOT))) {
                        continue
                    }
                    
                    // Try exact match first (which is now case-insensitive in the repository)
                    var entry = repository.getEntryByTerm(word)
                    
                    if (entry != null) {
                        matches.add(entry)
                        processedTerms.add(word.lowercase(Locale.ROOT))
                        Log.d(TAG, "Found exact match for '$word': ${entry.term}")
                    } else {
                        // If no exact match, try a broader search
                        val searchResults = repository.searchDictionary(word)
                        if (searchResults.isNotEmpty()) {
                            // Only add the first few results to avoid overwhelming the user
                            searchResults.take(2).forEach { result ->
                                if (!processedTerms.contains(result.term.lowercase(Locale.ROOT))) {
                                    matches.add(result)
                                    processedTerms.add(result.term.lowercase(Locale.ROOT))
                                    Log.d(TAG, "Found partial match for '$word': ${result.term}")
                                }
                            }
                        }
                    }
                }
                
                // Sort matches by term length (typically shorter terms are more common/basic)
                val sortedMatches = matches.distinctBy { it.term.lowercase(Locale.ROOT) }
                    .sortedBy { it.term.length }
                
                Log.d(TAG, "Found ${sortedMatches.size} dictionary matches")
                _dictionaryMatches.postValue(sortedMatches)
            } catch (e: Exception) {
                Log.e(TAG, "Error searching dictionary", e)
            } finally {
                _isSearching.postValue(false)
            }
        }
    }
    
    /**
     * Extract words from OCR text for dictionary lookup
     */
    private fun extractWordsFromText(text: String): List<String> {
        // Split by common delimiters
        val rawWords = text.split(Regex("[\\s,.。、!?：:;；\n\r\t]+"))
            .filter { it.isNotEmpty() }
            .map { it.trim() } // Trim any extra whitespace
        
        Log.d(TAG, "Raw words extracted: ${rawWords.joinToString(", ")}")
        
        // Process words to handle common OCR issues
        val processedWords = mutableListOf<String>()
        
        // Add individual words (no need to lowercase here since our SQL queries are now case-insensitive)
        processedWords.addAll(rawWords)
        
        // Also add lowercase versions to improve matching
        processedWords.addAll(rawWords.map { it.lowercase(Locale.ROOT) })
        
        // For words with mixed case, try a standardized version
        processedWords.addAll(rawWords
            .filter { it.any { char -> char.isUpperCase() } && it.any { char -> char.isLowerCase() } }
            .map { it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString() } }
        )
        
        // Add combined word pairs for languages like Japanese that might not have spaces
        if (rawWords.size >= 2) {
            for (i in 0 until rawWords.size - 1) {
                processedWords.add(rawWords[i] + rawWords[i + 1])
            }
        }
        
        // Return distinct words to avoid duplicate lookups
        return processedWords.distinct()
    }

    /**
     * Save OCR text to a file in external storage
     */
    fun saveOcrTextToFile(context: Context) {
        val text = _ocrText.value ?: return

        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "OCR_Text_$timeStamp.txt"
            val storageDir = context.getExternalFilesDir(null)
            val file = File(storageDir, fileName)

            FileWriter(file).use { writer ->
                writer.write(text)
            }

            _saveResult.value = "Text saved to ${file.absolutePath}"
        } catch (e: IOException) {
            e.printStackTrace()
            _saveResult.value = "Failed to save text: ${e.message}"
        }
    }
}