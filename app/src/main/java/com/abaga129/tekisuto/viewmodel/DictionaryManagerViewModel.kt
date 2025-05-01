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

    // ParserViewModel instance to handle the import
    private val parserViewModel = ParserViewModel.getInstance()

    init {
        parserViewModel.initRepository(repository)
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
                // Log dictionary entry counts before loading (debugging)
                val dictionariesBeforeLoading = repository.getAllDictionaries()
                for (dict in dictionariesBeforeLoading) {
                    val entryCount = repository.getDictionaryEntryCount(dict.id)
                    Log.d(TAG, "DEBUG - Before loading: Dictionary ${dict.id} (${dict.title}) has $entryCount entries")
                }
                
                // Load all dictionaries
                var allDictionaryList = repository.getAllDictionaries()
                
                // Ensure all dictionaries have sequential priorities
                allDictionaryList = ensureSequentialPriorities(allDictionaryList)
                _allDictionaries.value = allDictionaryList
                
                // If we have an active profile, load dictionaries for that profile
                if (activeProfileId > 0) {
                    var profileDictionaryList = repository.getDictionariesForProfile(activeProfileId)
                    
                    // Ensure profile dictionaries have sequential priorities
                    profileDictionaryList = ensureSequentialPriorities(profileDictionaryList)
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
                
                // Log dictionary entry counts after loading (debugging)
                for (dict in allDictionaryList) {
                    val entryCount = repository.getDictionaryEntryCount(dict.id)
                    Log.d(TAG, "DEBUG - After loading: Dictionary ${dict.id} (${dict.title}) has $entryCount entries")
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
                // Forward the import progress from ParserViewModel
                parserViewModel.importProgress.observeForever { progress ->
                    _importProgress.value = progress
                }

                val (success, newDictionaryId) = parserViewModel.importYomitanDictionary(context, uri)
                if (success && newDictionaryId > 0) {
                    // Get all dictionaries to determine the highest priority
                    val allDictionaries = repository.getAllDictionaries()
                    
                    // Verify entry count after getAllDictionaries call
                    val verifyEntryCount = repository.getDictionaryEntryCount(newDictionaryId)
                    Log.d(TAG, "VERIFY: Dictionary $newDictionaryId has $verifyEntryCount entries after getAllDictionaries")
                    
                    // Set highest priority for new dictionary (it should appear at the top)
                    val highestPriority = allDictionaries.size
                    repository.updateDictionaryPriority(newDictionaryId, highestPriority)
                    Log.d(TAG, "Set new dictionary priority to $highestPriority for dictionary $newDictionaryId")
                    
                    // If we have an active profile, associate the dictionary with it
                    if (activeProfileId > 0) {
                        repository.addDictionaryToProfile(activeProfileId, newDictionaryId)
                        Log.d(TAG, "Added dictionary $newDictionaryId to profile $activeProfileId")
                    }
                    
                    // Load dictionaries to ensure proper sequential priority
                    loadDictionaries()
                    
                    // Verify entry count after loadDictionaries call
                    val verifyEntryCountAfterLoad = repository.getDictionaryEntryCount(newDictionaryId)
                    Log.d(TAG, "VERIFY: Dictionary $newDictionaryId has $verifyEntryCountAfterLoad entries after loadDictionaries")
                    
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

    /**
     * When loading dictionaries, ensure they have sequential priorities
     */
    private suspend fun ensureSequentialPriorities(dictionaries: List<DictionaryMetadataEntity>): List<DictionaryMetadataEntity> {
        // If fewer than 2 dictionaries, no need to reorder
        if (dictionaries.size < 2) return dictionaries
        
        var needsReordering = false
        
        // Check if priorities are not sequential or if multiple dictionaries have the same priority
        val prioritiesSet = dictionaries.map { it.priority }.toSet()
        if (prioritiesSet.size != dictionaries.size || 
            dictionaries.any { it.priority < 0 }) {
            needsReordering = true
        }
        
        if (needsReordering) {
            Log.d(TAG, "Priorities need reordering. Current priorities: ${dictionaries.map { it.priority }}")
            
            // Assign new sequential priorities starting from highest (list size - 1) to lowest (0)
            // This maintains the current order but ensures priorities are sequential
            val size = dictionaries.size
            dictionaries.forEachIndexed { index, dict ->
                val newPriority = size - 1 - index
                if (dict.priority != newPriority) {
                    // Verify entry count before updateDictionaryPriority
                    val beforeEntryCount = repository.getDictionaryEntryCount(dict.id)
                    Log.d(TAG, "VERIFY: Dictionary ${dict.id} has $beforeEntryCount entries before updateDictionaryPriority")
                    
                    repository.updateDictionaryPriority(dict.id, newPriority)
                    
                    // Verify entry count after updateDictionaryPriority
                    val afterEntryCount = repository.getDictionaryEntryCount(dict.id)
                    Log.d(TAG, "VERIFY: Dictionary ${dict.id} has $afterEntryCount entries after updateDictionaryPriority")
                    
                    Log.d(TAG, "Updated dictionary ${dict.title} priority from ${dict.priority} to $newPriority")
                }
            }
            
            // Return the updated list
            val updatedDictionaries = repository.getAllDictionaries()
            
            // Make sure we didn't lose any dictionaries during reordering
            if (updatedDictionaries.size != dictionaries.size) {
                Log.e(TAG, "WARNING: Dictionary count changed during priority reordering! Original: ${dictionaries.size}, Updated: ${updatedDictionaries.size}")
            }
            
            // Verify entry count after getAllDictionaries
            for (dict in updatedDictionaries) {
                val entryCount = repository.getDictionaryEntryCount(dict.id)
                Log.d(TAG, "VERIFY: Dictionary ${dict.id} has $entryCount entries after reordering priorities")
            }
            
            return updatedDictionaries
        }
        
        return dictionaries
    }

    fun moveDictionaryUp(position: Int) {
        val currentList = _dictionaries.value ?: return
        if (position <= 0 || position >= currentList.size) return

        // Simply swap positions in the list and update priorities accordingly
        viewModelScope.launch {
            try {
                // Get the current dictionaries that need to be swapped
                val movingUp = currentList[position]
                val movingDown = currentList[position - 1]
                
                // Simply swap their priority values
                val upPriority = movingUp.priority
                val downPriority = movingDown.priority
                
                repository.updateDictionaryPriority(movingUp.id, downPriority)
                repository.updateDictionaryPriority(movingDown.id, upPriority)
                
                // Reload the dictionaries
                loadDictionaries()
                _error.value = null
            } catch (e: Exception) {
                Log.e(TAG, "Error moving dictionary up", e)
                _error.value = e.message ?: "Error moving dictionary up"
            }
        }
    }

    fun moveDictionaryDown(position: Int) {
        val currentList = _dictionaries.value ?: return
        if (position < 0 || position >= currentList.size - 1) return

        // Simply swap positions in the list and update priorities accordingly
        viewModelScope.launch {
            try {
                // Get the current dictionaries that need to be swapped
                val movingDown = currentList[position]
                val movingUp = currentList[position + 1]
                
                // Simply swap their priority values
                val downPriority = movingDown.priority
                val upPriority = movingUp.priority
                
                repository.updateDictionaryPriority(movingDown.id, upPriority)
                repository.updateDictionaryPriority(movingUp.id, downPriority)
                
                // Reload the dictionaries
                loadDictionaries()
                _error.value = null
            } catch (e: Exception) {
                Log.e(TAG, "Error moving dictionary down", e)
                _error.value = e.message ?: "Error moving dictionary down"
            }
        }
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