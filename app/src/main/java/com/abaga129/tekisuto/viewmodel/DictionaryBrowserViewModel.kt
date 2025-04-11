package com.abaga129.tekisuto.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.abaga129.tekisuto.database.DictionaryEntryEntity
import com.abaga129.tekisuto.database.DictionaryRepository
import kotlinx.coroutines.launch

class DictionaryBrowserViewModel(application: Application) : AndroidViewModel(application) {
    
    private lateinit var repository: DictionaryRepository
    
    // Search query text
    private val _searchQuery = MutableLiveData<String>()
    val searchQuery: LiveData<String> = _searchQuery
    
    // Dictionary entries
    private val _entries = MutableLiveData<List<DictionaryEntryEntity>>()
    val entries: LiveData<List<DictionaryEntryEntity>> = _entries
    
    // Loading state
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    // Dictionary entry count for statistics
    private val _entryCount = MutableLiveData<Int>(0)
    val entryCount: LiveData<Int> = _entryCount
    
    /**
     * Initialize repository
     */
    fun initRepository(repository: DictionaryRepository) {
        this.repository = repository
        loadDictionaryStats()
    }
    
    /**
     * Load dictionary statistics
     */
    private fun loadDictionaryStats() {
        viewModelScope.launch {
            try {
                val count = repository.getDictionaryEntryCount()
                _entryCount.value = count
            } catch (e: Exception) {
                // Log error but don't crash
            }
        }
    }
    
    /**
     * Clear all dictionary entries
     */
    fun clearDictionary() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.clearAllDictionaries()
                loadDictionaryStats()
                _entries.value = emptyList()
                android.util.Log.d("DictionaryViewModel", "Dictionary cleared successfully")
            } catch (e: Exception) {
                android.util.Log.e("DictionaryViewModel", "Error clearing dictionary", e)
                throw e
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Update search query and trigger search
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isNotEmpty()) {
            searchDictionary(query)
        } else {
            _entries.value = emptyList()
        }
    }
    
    /**
     * Search dictionary with current query
     */
    private fun searchDictionary(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                android.util.Log.d("DictionaryViewModel", "Searching for: '$query'")
                // Use fast search for interactive typing, switch to priority-based for the final result
                val results = if (query.length <= 3) {
                    // Use fast search for short queries during typing
                    repository.searchDictionary(query, fastSearch = true)
                } else {
                    // Use prioritized search for longer, likely final queries
                    repository.searchDictionary(query, fastSearch = false)
                }
                _entries.value = results
                android.util.Log.d("DictionaryViewModel", "Search returned ${results.size} results")
                
                // Log a few entries for debugging
                results.take(5).forEachIndexed { index, entry ->
                    android.util.Log.d("DictionaryViewModel", "Result $index: term=${entry.term}, " +
                            "reading=${entry.reading}, pos=${entry.partOfSpeech}, " +
                            "definition=${entry.definition.take(30)}..." +
                            (if (entry.term.equals(query, ignoreCase = true)) " [EXACT MATCH]" else ""))
                }
            } catch (e: Exception) {
                android.util.Log.e("DictionaryViewModel", "Error searching dictionary", e)
                _entries.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Load all entries (limited to a reasonable amount)
     */
    fun loadAllEntries(limit: Int = 100) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val results = repository.getRecentEntries(limit)
                _entries.value = results
                android.util.Log.d("DictionaryViewModel", "Loaded ${results.size} entries")
                
                // Log a few entries for debugging
                results.take(3).forEachIndexed { index, entry ->
                    android.util.Log.d("DictionaryViewModel", "Entry $index: term=${entry.term}, " +
                            "reading=${entry.reading}, pos=${entry.partOfSpeech}, " +
                            "definition=${entry.definition.take(30)}...")
                }
            } catch (e: Exception) {
                android.util.Log.e("DictionaryViewModel", "Error loading entries", e)
                _entries.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
}