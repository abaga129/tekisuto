package com.abaga129.tekisuto.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.abaga129.tekisuto.database.DictionaryMetadataEntity
import com.abaga129.tekisuto.database.DictionaryRepository
import kotlinx.coroutines.launch

private const val TAG = "DictionaryManagerVM"

class DictionaryManagerViewModel(private val repository: DictionaryRepository) : ViewModel() {

    private val _dictionaries = MutableLiveData<List<DictionaryMetadataEntity>>()
    val dictionaries: LiveData<List<DictionaryMetadataEntity>> = _dictionaries

    private val _allDictionaries = MutableLiveData<List<DictionaryMetadataEntity>>()
    val allDictionaries: LiveData<List<DictionaryMetadataEntity>> = _allDictionaries

    private val _importProgress = MutableLiveData<Int>()
    val importProgress: LiveData<Int> = _importProgress

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // Active profile ID
    private var activeProfileId: Long = -1

    // MainViewModel instance to handle the import
    private val mainViewModel = MainViewModel()

    init {
        mainViewModel.initRepository(repository)
    }
    
    /**
     * Sets the active profile ID and loads dictionaries for that profile
     */
    fun setActiveProfile(profileId: Long) {
        this.activeProfileId = profileId
        loadDictionaries()
    }

    /**
     * Loads dictionaries - if activeProfileId is set, loads only dictionaries for that profile
     */
    fun loadDictionaries() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Load all dictionaries
                val allDictionaryList = repository.getAllDictionaries()
                _allDictionaries.value = allDictionaryList
                
                // If we have an active profile, load dictionaries for that profile
                if (activeProfileId > 0) {
                    val profileDictionaryList = repository.getDictionariesForProfile(activeProfileId)
                    _dictionaries.value = profileDictionaryList
                    Log.d(TAG, "Loaded ${profileDictionaryList.size} dictionaries for profile $activeProfileId")
                    
                    // Log detailed information for debugging
                    if (profileDictionaryList.isEmpty()) {
                        Log.d(TAG, "No dictionaries found for profile $activeProfileId")
                    } else {
                        Log.d(TAG, "Dictionaries for profile $activeProfileId: " + 
                              profileDictionaryList.joinToString { it.title })
                    }
                } else {
                    // If no active profile, show all dictionaries
                    _dictionaries.value = allDictionaryList
                    Log.d(TAG, "No active profile, showing all ${allDictionaryList.size} dictionaries")
                }
                
                // Make sure values are never null
                if (_allDictionaries.value == null) {
                    _allDictionaries.value = emptyList()
                }
                if (_dictionaries.value == null) {
                    _dictionaries.value = emptyList()
                }
                
                _error.value = null
            } catch (e: Exception) {
                Log.e(TAG, "Error loading dictionaries", e)
                _error.value = e.message ?: "Error loading dictionaries"
                
                // Set empty lists on error to avoid null values
                _allDictionaries.value = emptyList()
                _dictionaries.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun importDictionary(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Forward the import progress from MainViewModel
                mainViewModel.importProgress.observeForever { progress ->
                    _importProgress.value = progress
                }

                val success = mainViewModel.importYomitanDictionary(context, uri)
                if (success) {
                    // Get the last imported dictionary
                    val allDictionaries = repository.getAllDictionaries()
                    val lastImportedDictionary = allDictionaries.firstOrNull()
                    
                    // If we have an active profile and a newly imported dictionary,
                    // associate the dictionary with the profile
                    if (activeProfileId > 0 && lastImportedDictionary != null) {
                        repository.addDictionaryToProfile(activeProfileId, lastImportedDictionary.id)
                        Log.d(TAG, "Added dictionary ${lastImportedDictionary.id} to profile $activeProfileId")
                    }
                    
                    loadDictionaries()
                    _error.value = null
                } else {
                    _error.value = "Failed to import dictionary"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error importing dictionary", e)
                _error.value = e.message ?: "Error importing dictionary"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteDictionary(dictionaryId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.deleteDictionary(dictionaryId)
                loadDictionaries()
                _error.value = null
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting dictionary", e)
                _error.value = e.message ?: "Error deleting dictionary"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateDictionaryPriority(dictionaryId: Long, newPriority: Int) {
        viewModelScope.launch {
            try {
                repository.updateDictionaryPriority(dictionaryId, newPriority)
                loadDictionaries()
                _error.value = null
            } catch (e: Exception) {
                Log.e(TAG, "Error updating dictionary priority", e)
                _error.value = e.message ?: "Error updating dictionary priority"
            }
        }
    }

    fun moveDictionaryUp(position: Int) {
        val currentList = _dictionaries.value ?: return
        if (position <= 0 || position >= currentList.size) return

        val dictionary = currentList[position]
        val higherPriorityDict = currentList[position - 1]

        // Swap priorities
        updateDictionaryPriority(dictionary.id, higherPriorityDict.priority + 1)
    }

    fun moveDictionaryDown(position: Int) {
        val currentList = _dictionaries.value ?: return
        if (position < 0 || position >= currentList.size - 1) return

        val dictionary = currentList[position]
        val lowerPriorityDict = currentList[position + 1]

        // Swap priorities
        updateDictionaryPriority(dictionary.id, lowerPriorityDict.priority - 1)
    }
    
    /**
     * Adds a dictionary to the active profile
     */
    fun addDictionaryToProfile(dictionaryId: Long) {
        if (activeProfileId <= 0) {
            Log.w(TAG, "Cannot add dictionary to profile: no active profile")
            return
        }
        
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.addDictionaryToProfile(activeProfileId, dictionaryId)
                loadDictionaries()
                _error.value = null
            } catch (e: Exception) {
                Log.e(TAG, "Error adding dictionary to profile", e)
                _error.value = e.message ?: "Error adding dictionary to profile"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Removes a dictionary from the active profile
     */
    fun removeDictionaryFromProfile(dictionaryId: Long) {
        if (activeProfileId <= 0) {
            Log.w(TAG, "Cannot remove dictionary from profile: no active profile")
            return
        }
        
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.removeDictionaryFromProfile(activeProfileId, dictionaryId)
                loadDictionaries()
                _error.value = null
            } catch (e: Exception) {
                Log.e(TAG, "Error removing dictionary from profile", e)
                _error.value = e.message ?: "Error removing dictionary from profile"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Checks if a dictionary is in the active profile
     */
    suspend fun isDictionaryInProfile(dictionaryId: Long): Boolean {
        return if (activeProfileId <= 0) {
            false
        } else {
            repository.isDictionaryInProfile(activeProfileId, dictionaryId)
        }
    }

    /**
     * Factory for creating DictionaryManagerViewModel with dependencies
     */
    class Factory(private val repository: DictionaryRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DictionaryManagerViewModel::class.java)) {
                return DictionaryManagerViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}