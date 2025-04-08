package com.abaga129.tekisuto.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abaga129.tekisuto.database.DictionaryEntryEntity
import com.abaga129.tekisuto.database.DictionaryRepository
import com.abaga129.tekisuto.util.TranslationHelper
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "OCRResultViewModel"

class OCRResultViewModel : ViewModel() {
    
    override fun onCleared() {
        super.onCleared()
        translationHelper?.close()
    }

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
    
    private val _translatedText = MutableLiveData<String>()
    val translatedText: LiveData<String> get() = _translatedText
    
    private val _isTranslating = MutableLiveData<Boolean>()
    val isTranslating: LiveData<Boolean> get() = _isTranslating
    
    private val _wordTranslation = MutableLiveData<Pair<String, String>>()
    val wordTranslation: LiveData<Pair<String, String>> get() = _wordTranslation
    
    private val _isTranslatingWord = MutableLiveData<Boolean>()
    val isTranslatingWord: LiveData<Boolean> get() = _isTranslatingWord
    
    private var dictionaryRepository: DictionaryRepository? = null
    private var translationHelper: TranslationHelper? = null

    fun setOcrText(text: String, ocrLanguage: String? = null) {
        _ocrText.value = text
        
        // Auto-translate if enabled
        translationHelper?.let { helper ->
            if (helper.isTranslationEnabled()) {
                translateOcrText(ocrLanguage)
            }
        }
    }

    fun setScreenshotPath(path: String) {
        _screenshotPath.value = path
    }
    
    fun initDictionaryRepository(context: Context) {
        if (dictionaryRepository == null) {
            dictionaryRepository = DictionaryRepository(context)
        }
    }
    
    fun initTranslationHelper(context: Context) {
        if (translationHelper == null) {
            translationHelper = TranslationHelper(context)
        }
    }
    
    /**
     * Translate the OCR text using ML Kit
     * @param sourceLanguage Optional source language code (OCR language setting)
     */
    fun translateOcrText(sourceLanguage: String? = null) {
        val text = _ocrText.value ?: return
        val helper = translationHelper ?: return
        
        if (!helper.isTranslationEnabled() || text.isBlank()) {
            _translatedText.value = ""
            return
        }
        
        _isTranslating.value = true
        
        // Get OCR language from preferences if not provided
        val ocrLanguage = sourceLanguage ?: getOcrLanguage()
        
        viewModelScope.launch {
            try {
                val translated = helper.translateText(text, ocrLanguage)
                _translatedText.postValue(translated)
                Log.d(TAG, "Text translated successfully from language: $ocrLanguage")
            } catch (e: Exception) {
                Log.e(TAG, "Error translating text", e)
                _translatedText.postValue("")
            } finally {
                _isTranslating.postValue(false)
            }
        }
    }
    
    /**
     * Translate a single word
     * @param word The word to translate
     * @param sourceLanguage Optional source language code
     */
    fun translateWord(word: String, sourceLanguage: String? = null) {
        val helper = translationHelper ?: return
        
        if (!helper.isTranslationEnabled() || word.isBlank()) {
            return
        }
        
        _isTranslatingWord.value = true
        
        viewModelScope.launch {
            try {
                // Get OCR language from preferences if not provided
                val ocrLanguage = sourceLanguage ?: getOcrLanguage()
                
                // Translate the word
                val translated = helper.translateText(word, ocrLanguage)
                
                // Only update if translation succeeded and is different
                if (translated.isNotBlank() && translated != word) {
                    _wordTranslation.postValue(Pair(word, translated))
                    Log.d(TAG, "Word translated: $word → $translated")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error translating word: $word", e)
            } finally {
                _isTranslatingWord.postValue(false)
            }
        }
    }
    
    /**
     * Get the OCR language from preferences
     */
    private fun getOcrLanguage(): String {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(
            translationHelper?.context ?: return "latin")
        return prefs.getString("ocr_language", "latin") ?: "latin"
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
                
                // Get basic word list (much faster extraction)
                val startTime = System.currentTimeMillis()
                val rawWords = text.split(Regex("[\\s,.。、!?：:;；\n\r\t]+"))
                    .filter { it.isNotEmpty() }
                    .map { it.trim() } // Trim any extra whitespace
                    .map { it.lowercase(Locale.ROOT) } // Convert to lowercase for matching
                    .distinct() // Remove duplicates
                    .take(50) // Limit to 50 words to avoid overloading
                
                Log.d(TAG, "Word extraction took ${System.currentTimeMillis() - startTime}ms for ${rawWords.size} words")
                
                // PHASE 1: Fast bulk exact match search
                val bulkSearchTime = System.currentTimeMillis()
                val exactMatches = repository.bulkSearchByExactTerms(rawWords)
                Log.d(TAG, "Bulk search took ${System.currentTimeMillis() - bulkSearchTime}ms, found ${exactMatches.size} matches")
                
                // If we found enough matches via exact search, use those
                if (exactMatches.size >= 3) {
                    val sortedMatches = exactMatches
                        .distinctBy { it.term.lowercase(Locale.ROOT) }
                        .sortedBy { it.term.length }
                        .take(10)
                    
                    _dictionaryMatches.postValue(sortedMatches)
                    Log.d(TAG, "Found ${sortedMatches.size} exact matches, skipping fuzzy search")
                    _isSearching.postValue(false)
                    return@launch
                }
                
                // PHASE 2: If exact search didn't find enough, do a limited fuzzy search
                val matches = mutableListOf<DictionaryEntryEntity>()
                matches.addAll(exactMatches)
                
                // Add processed terms to avoid duplicates
                val processedTerms = exactMatches.map { it.term.lowercase(Locale.ROOT) }.toMutableSet()
                
                // Only check a few words with fuzzy search to keep it fast
                val termsToFuzzySearch = rawWords
                    .filter { !processedTerms.contains(it) }
                    .take(5) // Limit fuzzy search to just a few terms
                
                Log.d(TAG, "Doing fuzzy search for ${termsToFuzzySearch.size} terms")
                
                // Do a single fast search for each remaining term
                val fuzzySearchStart = System.currentTimeMillis()
                for (word in termsToFuzzySearch) {
                    // Skip if already processed
                    if (processedTerms.contains(word)) continue
                    
                    // Use fast search to find matches quickly
                    val fuzzyResults = repository.searchDictionary("%$word%", fastSearch = true)
                    
                    // Take just the first match for each term
                    if (fuzzyResults.isNotEmpty()) {
                        val bestMatch = fuzzyResults.first()
                        if (!processedTerms.contains(bestMatch.term.lowercase(Locale.ROOT))) {
                            matches.add(bestMatch)
                            processedTerms.add(bestMatch.term.lowercase(Locale.ROOT))
                        }
                    }
                }
                Log.d(TAG, "Fuzzy search took ${System.currentTimeMillis() - fuzzySearchStart}ms")
                
                // Sort matches by term length (typically shorter terms are more common/basic)
                val sortedMatches = matches
                    .distinctBy { it.term.lowercase(Locale.ROOT) }
                    .sortedBy { it.term.length }
                    .take(10) // Limit to top 10 matches
                
                Log.d(TAG, "Total search took ${System.currentTimeMillis() - startTime}ms, found ${sortedMatches.size} matches")
                _dictionaryMatches.postValue(sortedMatches)
            } catch (e: Exception) {
                Log.e(TAG, "Error searching dictionary", e)
            } finally {
                _isSearching.postValue(false)
            }
        }
    }
    
    // We've inlined the word extraction for better performance

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