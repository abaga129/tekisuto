package com.abaga129.tekisuto.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.abaga129.tekisuto.database.DictionaryEntryEntity
import com.abaga129.tekisuto.database.DictionaryRepository
import com.abaga129.tekisuto.database.WordFrequencyEntity
import com.abaga129.tekisuto.service.FrequencyDataService
import com.abaga129.tekisuto.util.ProfileSettingsManager
import kotlinx.coroutines.launch

class DictionaryBrowserViewModel(application: Application) : AndroidViewModel(application) {
    
    private lateinit var repository: DictionaryRepository
    private lateinit var frequencyService: FrequencyDataService
    private val profileSettingsManager = ProfileSettingsManager(application)
    
    // Search query text
    private val _searchQuery = MutableLiveData<String>()
    val searchQuery: LiveData<String> = _searchQuery
    
    // Dictionary entries
    private val _entries = MutableLiveData<List<DictionaryEntryEntity>>()
    val entries: LiveData<List<DictionaryEntryEntity>> = _entries
    
    // Frequency data for dictionary entries
    private val _frequencyData = MutableLiveData<Map<Long, WordFrequencyEntity>>()
    val frequencyData: LiveData<Map<Long, WordFrequencyEntity>> = _frequencyData
    
    // Loading state
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    // Dictionary entry count for statistics
    private val _entryCount = MutableLiveData<Int>(0)
    val entryCount: LiveData<Int> = _entryCount
    
    // Total frequency count for statistics
    private val _frequencyCount = MutableLiveData<Int>(0)
    val frequencyCount: LiveData<Int> = _frequencyCount
    
    /**
     * Initialize repository
     */
    fun initRepository(repository: DictionaryRepository) {
        this.repository = repository
        this.frequencyService = FrequencyDataService(repository)
        loadDictionaryStats()
        checkFrequencyData()
    }
    
    /**
     * Load dictionary statistics
     */
    private fun loadDictionaryStats() {
        viewModelScope.launch {
            try {
                // Get the active profile ID
                val profileId = profileSettingsManager.getCurrentProfileId()
                
                // Get entry count for the active profile's dictionaries
                val count = if (profileId > 0) {
                    // Get count of entries from dictionaries assigned to the active profile
                    val dictionaries = repository.getDictionariesForProfile(profileId)
                    dictionaries.sumOf { repository.getDictionaryEntryCount(it.id) }
                } else {
                    // Fallback to all dictionaries if no profile selected
                    repository.getDictionaryEntryCount()
                }
                _entryCount.value = count
                
                // Get total frequency count
                val frequencyCount = frequencyService.getTotalFrequencyCount()
                _frequencyCount.value = frequencyCount
                android.util.Log.d("DictionaryViewModel", "Total frequency entries: $frequencyCount")
            } catch (e: Exception) {
                android.util.Log.e("DictionaryViewModel", "Error loading dictionary stats", e)
            }
        }
    }
    
    /**
     * Check frequency data for diagnostics
     */
    private fun checkFrequencyData() {
        viewModelScope.launch {
            try {
                // Get all dictionaries
                val dictionaries = repository.getAllDictionaries()
                android.util.Log.d("DictionaryViewModel", "Total dictionaries: ${dictionaries.size}")
                
                // Check each dictionary for frequency data
                dictionaries.forEach { dict ->
                    val frequencyCount = repository.getWordFrequencyCount(dict.id)
                    android.util.Log.d("DictionaryViewModel", "Dictionary ${dict.id} (${dict.title}): $frequencyCount frequency entries")
                    
                    // If it has frequency data, check a few samples
                    if (frequencyCount > 0) {
                        val samples = repository.getWordFrequencies(dict.id).take(3)
                        samples.forEach { freq ->
                            android.util.Log.d("DictionaryViewModel", "Sample: word='${freq.word}', frequency=#${freq.frequency}")
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("DictionaryViewModel", "Error checking frequency data", e)
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
                _frequencyData.value = emptyMap()
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
            _frequencyData.value = emptyMap()
        }
    }
    
    /**
     * Search dictionary with current query
     */
    private fun searchDictionary(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Get the active profile ID
                val profileId = profileSettingsManager.getCurrentProfileId()
                
                android.util.Log.d("DictionaryViewModel", "Searching for: '$query' with profileId: $profileId")
                
                // Use fast search for interactive typing, switch to priority-based for the final result
                val results = if (query.length <= 3) {
                    // Use fast search for short queries during typing
                    repository.searchDictionary(query, profileId, fastSearch = true)
                } else {
                    // Use prioritized search for longer, likely final queries
                    repository.searchDictionary(query, profileId, fastSearch = false)
                }
                _entries.value = results
                android.util.Log.d("DictionaryViewModel", "Search returned ${results.size} results")
                
                // Get frequency data for all results
                val frequencies = frequencyService.getFrequenciesForEntries(results)
                _frequencyData.value = frequencies
                android.util.Log.d("DictionaryViewModel", "Found frequency data for ${frequencies.size}/${results.size} entries")
                
                // Log a few entries for debugging
                results.take(3).forEachIndexed { index, entry ->
                    android.util.Log.d("DictionaryViewModel", "Result $index: term=${entry.term}, " +
                            "reading=${entry.reading}, pos=${entry.partOfSpeech}, " +
                            "definition=${entry.definition.take(30)}..." +
                            (if (entry.term.equals(query, ignoreCase = true)) " [EXACT MATCH]" else ""))
                    
                    // Log frequency data if available
                    val frequency = frequencies[entry.id]
                    if (frequency != null) {
                        android.util.Log.d("DictionaryViewModel", "✓ Frequency data for '${entry.term}': #${frequency.frequency}")
                    } else {
                        android.util.Log.d("DictionaryViewModel", "✗ No frequency data for '${entry.term}'")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("DictionaryViewModel", "Error searching dictionary", e)
                _entries.value = emptyList()
                _frequencyData.value = emptyMap()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Get frequency data for a specific entry
     */
    fun getFrequencyForEntry(entry: DictionaryEntryEntity): WordFrequencyEntity? {
        return _frequencyData.value?.get(entry.id)
    }
    
    /**
     * Manually fetch frequency data for an entry (useful for debugging)
     */
    fun fetchFrequencyForEntry(entry: DictionaryEntryEntity) {
        viewModelScope.launch {
            try {
                val frequencyData = frequencyService.getFrequencyForEntry(entry)
                if (frequencyData != null) {
                    // Update the frequency data map
                    val currentMap = _frequencyData.value?.toMutableMap() ?: mutableMapOf()
                    currentMap[entry.id] = frequencyData
                    _frequencyData.value = currentMap
                    
                    android.util.Log.d("DictionaryViewModel", "Manually fetched frequency data for '${entry.term}': #${frequencyData.frequency}")
                } else {
                    android.util.Log.d("DictionaryViewModel", "No frequency data found for '${entry.term}' when manually fetching")
                }
            } catch (e: Exception) {
                android.util.Log.e("DictionaryViewModel", "Error manually fetching frequency data", e)
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
                // Get the active profile ID
                val profileId = profileSettingsManager.getCurrentProfileId()
                
                // Only show entries from dictionaries assigned to the active profile
                val results = if (profileId > 0) {
                    // Get entries from dictionaries in the active profile
                    val profileDictionaries = repository.getDictionariesForProfile(profileId)
                    if (profileDictionaries.isNotEmpty()) {
                        val dictionaryIds = profileDictionaries.map { it.id }
                        repository.getRecentEntriesFromDictionaries(limit, dictionaryIds)
                    } else {
                        emptyList()
                    }
                } else {
                    // Fallback to all dictionaries if no profile selected
                    repository.getRecentEntries(limit)
                }
                
                _entries.value = results
                android.util.Log.d("DictionaryViewModel", "Loaded ${results.size} entries for profileId: $profileId")
                
                // Get frequency data for all results
                val frequencies = frequencyService.getFrequenciesForEntries(results)
                _frequencyData.value = frequencies
                
                // Log a few entries for debugging
                results.take(3).forEachIndexed { index, entry ->
                    android.util.Log.d("DictionaryViewModel", "Entry $index: term=${entry.term}, " +
                            "reading=${entry.reading}, pos=${entry.partOfSpeech}, " +
                            "definition=${entry.definition.take(30)}...")
                    
                    // Log frequency data if available
                    val frequency = frequencies[entry.id]
                    if (frequency != null) {
                        android.util.Log.d("DictionaryViewModel", "✓ Frequency data for '${entry.term}': #${frequency.frequency}")
                    } else {
                        android.util.Log.d("DictionaryViewModel", "✗ No frequency data for '${entry.term}'")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("DictionaryViewModel", "Error loading entries", e)
                _entries.value = emptyList()
                _frequencyData.value = emptyMap()
            } finally {
                _isLoading.value = false
            }
        }
    }
}