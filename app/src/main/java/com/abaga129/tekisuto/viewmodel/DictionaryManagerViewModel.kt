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

    private val _importProgress = MutableLiveData<Int>()
    val importProgress: LiveData<Int> = _importProgress

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // MainViewModel instance to handle the import
    private val mainViewModel = MainViewModel()

    init {
        mainViewModel.initRepository(repository)
        loadDictionaries()
    }

    fun loadDictionaries() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val dictionaryList = repository.getAllDictionaries()
                _dictionaries.value = dictionaryList
                _error.value = null
            } catch (e: Exception) {
                Log.e(TAG, "Error loading dictionaries", e)
                _error.value = e.message ?: "Error loading dictionaries"
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