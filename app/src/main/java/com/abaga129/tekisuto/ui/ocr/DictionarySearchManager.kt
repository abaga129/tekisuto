package com.abaga129.tekisuto.ui.ocr

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.abaga129.tekisuto.database.DictionaryEntryEntity
import com.abaga129.tekisuto.database.DictionaryRepository
import com.abaga129.tekisuto.ui.adapter.DictionaryMatchAdapter
import com.abaga129.tekisuto.util.SpeechService
import com.abaga129.tekisuto.viewmodel.OCRResultViewModel

/**
 * Manages dictionary lookup and search functionality
 */
class DictionarySearchManager(
    private val context: Context,
    private val viewModel: OCRResultViewModel,
    private val dictionarySearchEditText: EditText,
    private val lifecycleOwner: LifecycleOwner,
    private val dictionaryRepository: DictionaryRepository,
    private val speechService: SpeechService,
    private val textProcessor: OCRTextProcessor
) {
    
    // Dictionary match adapter
    private lateinit var dictionaryMatchAdapter: DictionaryMatchAdapter
    
    /**
     * Initialize the dictionary search components
     */
    fun initialize() {
        // Setup the dictionary match adapter
        dictionaryMatchAdapter = DictionaryMatchAdapter()
        
        // Set services and listeners
        dictionaryMatchAdapter.setDictionaryRepository(dictionaryRepository)
        dictionaryMatchAdapter.setSpeechService(speechService)
        
        // Set up a text watcher for the dictionary search EditText to convert to lowercase
        setupSearchTextWatcher()
        
        // Observe dictionary search results
        setupObservers()
    }
    
    /**
     * Set up text watcher for the search field to convert input to lowercase
     */
    private fun setupSearchTextWatcher() {
        dictionarySearchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                s?.let { editable ->
                    val text = editable.toString()
                    // If text is not already lowercase, convert it
                    if (text != text.lowercase()) {
                        // Remove the text watcher temporarily to avoid infinite loop
                        dictionarySearchEditText.removeTextChangedListener(this)
                        // Update the text to lowercase
                        dictionarySearchEditText.setText(text.lowercase())
                        // Move cursor to the end
                        dictionarySearchEditText.setSelection(dictionarySearchEditText.text.length)
                        // Add the text watcher back
                        dictionarySearchEditText.addTextChangedListener(this)
                    }
                }
            }
        })
    }
    
    /**
     * Set up observers for dictionary search results
     */
    private fun setupObservers() {
        // Observe dictionary matches
        viewModel.dictionaryMatches.observe(lifecycleOwner, Observer { matches ->
            processDictionaryMatches(matches)
        })
    }
    
    /**
     * Process dictionary match results
     */
    private fun processDictionaryMatches(matches: List<DictionaryEntryEntity>) {
        // Highlight matched words in the OCR text
        textProcessor.highlightMatchedWords(matches)
        
        // Update the adapter with the matches
        dictionaryMatchAdapter.submitList(matches)
    }
    
    /**
     * Set an audio play listener on the adapter
     */
    fun setOnAudioPlayListener(listener: DictionaryMatchAdapter.OnAudioPlayListener) {
        dictionaryMatchAdapter.setOnAudioPlayListener(listener)
    }
    
    /**
     * Set an Anki export listener on the adapter
     */
    fun setOnAnkiExportListener(listener: DictionaryMatchAdapter.OnAnkiExportListener) {
        dictionaryMatchAdapter.setOnAnkiExportListener(listener)
    }
    
    /**
     * Set lifecycle scope on the adapter
     */
    fun setLifecycleScope(lifecycleScope: androidx.lifecycle.LifecycleCoroutineScope) {
        dictionaryMatchAdapter.setLifecycleScope(lifecycleScope)
    }
    
    /**
     * Get the dictionary match adapter
     */
    fun getAdapter(): DictionaryMatchAdapter {
        return dictionaryMatchAdapter
    }
    
    /**
     * Set OCR text in the adapter
     */
    fun setOcrText(text: String) {
        dictionaryMatchAdapter.setOcrText(text)
    }
    
    /**
     * Set screenshot path in the adapter
     */
    fun setScreenshotPath(path: String) {
        dictionaryMatchAdapter.setScreenshotPath(path)
    }
    
    /**
     * Search for a specific term in the dictionary
     */
    fun searchTerm(term: String?) {
        // Store the search term in the EditText to make it visible to the user
        if (!term.isNullOrBlank()) {
            updateSearchField(term)
        }
        
        // Perform the search
        viewModel.findDictionaryMatches(term)
    }
    
    /**
     * Update the search field with text
     */
    fun updateSearchField(text: String) {
        dictionarySearchEditText.setText(text)
    }
}