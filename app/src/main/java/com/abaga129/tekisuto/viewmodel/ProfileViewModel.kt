package com.abaga129.tekisuto.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.abaga129.tekisuto.database.AppDatabase
import com.abaga129.tekisuto.database.ProfileDao
import com.abaga129.tekisuto.database.ProfileEntity
import com.abaga129.tekisuto.util.ProfileSettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    
    private val TAG = "ProfileViewModel"
    
    private val database: AppDatabase = AppDatabase.createDatabase(application)
    
    private val profileDao: ProfileDao = database.profileDao()
    private val settingsManager = ProfileSettingsManager(application)
    
    private val _profiles = MutableLiveData<List<ProfileEntity>>()
    val profiles: LiveData<List<ProfileEntity>> = _profiles
    
    private val _currentProfile = MutableLiveData<ProfileEntity>()
    val currentProfile: LiveData<ProfileEntity> = _currentProfile
    
    fun loadProfiles() {
        viewModelScope.launch {
            try {
                val allProfiles = withContext(Dispatchers.IO) {
                    profileDao.getAllProfiles()
                }
                
                _profiles.value = allProfiles
                
                // If there are no profiles, create a default one
                if (allProfiles.isEmpty()) {
                    createDefaultProfile()
                } else {
                    // Load the default/current profile
                    loadCurrentProfile()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading profiles", e)
            }
        }
    }
    
    private suspend fun createDefaultProfile() {
        try {
            val defaultProfile = ProfileEntity(
                name = "Default",
                isDefault = true
            )
            
            withContext(Dispatchers.IO) {
                profileDao.insert(defaultProfile)
            }
            
            loadProfiles() // Reload the profiles
        } catch (e: Exception) {
            Log.e(TAG, "Error creating default profile", e)
        }
    }
    
    fun loadCurrentProfile() {
        viewModelScope.launch {
            try {
                // First, check if we have a current profile ID saved
                val currentId = settingsManager.getCurrentProfileId()
                var currentProfile: ProfileEntity? = null
                
                if (currentId != -1L) {
                    // Try to load the profile by ID
                    currentProfile = withContext(Dispatchers.IO) {
                        profileDao.getProfileById(currentId)
                    }
                }
                
                // If no current profile or not found, get default
                if (currentProfile == null) {
                    currentProfile = withContext(Dispatchers.IO) {
                        profileDao.getDefaultProfile()
                    }
                }
                
                if (currentProfile != null) {
                    // Apply profile settings
                    settingsManager.loadProfileSettings(currentProfile)
                    _currentProfile.value = currentProfile
                } else if (_profiles.value?.isNotEmpty() == true) {
                    // If no default profile, set the first one as default
                    val firstProfile = _profiles.value?.first()
                    if (firstProfile != null) {
                        setAsDefault(firstProfile)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading current profile", e)
            }
        }
    }
    
    fun createProfile(profile: ProfileEntity) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // If this is set as default, clear other defaults first
                    if (profile.isDefault) {
                        profileDao.clearDefaultStatus()
                    }
                    
                    profileDao.insert(profile)
                }
                
                loadProfiles() // Reload the profiles
            } catch (e: Exception) {
                Log.e(TAG, "Error creating profile", e)
            }
        }
    }
    
    fun updateProfile(profile: ProfileEntity) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Update the entire profile
                    profileDao.updateProfile(profile)
                    
                    // Handle default status separately
                    if (profile.isDefault) {
                        profileDao.clearDefaultStatus()
                        profileDao.setAsDefault(profile.id)
                    }
                }
                
                loadProfiles() // Reload the profiles
                
                // If this was the current profile, reload settings
                if (_currentProfile.value?.id == profile.id) {
                    _currentProfile.value = profile
                    settingsManager.loadProfileSettings(profile)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating profile", e)
            }
        }
    }
    
    fun deleteProfile(profile: ProfileEntity) {
        viewModelScope.launch {
            try {
                val profileCount = withContext(Dispatchers.IO) {
                    profileDao.getProfileCount()
                }
                
                // Don't allow deleting the last profile
                if (profileCount <= 1) {
                    Log.w(TAG, "Cannot delete the last profile")
                    return@launch
                }
                
                withContext(Dispatchers.IO) {
                    profileDao.deleteProfile(profile.id)
                    
                    // If we deleted the default profile, set a new default
                    if (profile.isDefault) {
                        val remainingProfiles = profileDao.getAllProfiles()
                        if (remainingProfiles.isNotEmpty()) {
                            profileDao.setAsDefault(remainingProfiles.first().id)
                        }
                    }
                }
                
                loadProfiles() // Reload the profiles
                
                // If the current profile was deleted, switch to default
                if (_currentProfile.value?.id == profile.id) {
                    loadCurrentProfile()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting profile", e)
            }
        }
    }
    
    fun setAsDefault(profile: ProfileEntity) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Clear default status on all profiles
                    profileDao.clearDefaultStatus()
                    
                    // Set this profile as default
                    profileDao.setAsDefault(profile.id)
                    
                    // Update last used date
                    profileDao.updateLastUsedDate(profile.id, Date())
                }
                
                // Set as current profile and load its settings
                settingsManager.loadProfileSettings(profile)
                _currentProfile.value = profile
                
                loadProfiles() // Reload the profiles
            } catch (e: Exception) {
                Log.e(TAG, "Error setting profile as default", e)
            }
        }
    }
    
    /**
     * Save current settings to the active profile
     */
    fun saveCurrentSettings() {
        viewModelScope.launch {
            try {
                val currentProfile = _currentProfile.value ?: return@launch
                
                // Extract settings from SharedPreferences into the profile
                val updatedProfile = settingsManager.extractProfileFromSettings(currentProfile)
                
                withContext(Dispatchers.IO) {
                    // Update the profile in the database
                    profileDao.updateProfile(updatedProfile)
                }
                
                // Update cached current profile
                _currentProfile.value = updatedProfile
                
                // Reload profiles to update the list
                val allProfiles = withContext(Dispatchers.IO) {
                    profileDao.getAllProfiles()
                }
                _profiles.value = allProfiles
                
                Log.d(TAG, "Current settings saved to profile: ${updatedProfile.name} (ID: ${updatedProfile.id})")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving current settings to profile", e)
            }
        }
    }
}