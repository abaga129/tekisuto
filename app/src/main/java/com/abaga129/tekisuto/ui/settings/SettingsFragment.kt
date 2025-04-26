package com.abaga129.tekisuto.ui.settings

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import androidx.preference.SeekBarPreference
import com.abaga129.tekisuto.R
import com.abaga129.tekisuto.database.ProfileEntity
import com.abaga129.tekisuto.ui.dialog.VoiceSelectionDialog
import com.abaga129.tekisuto.ui.profile.ProfileManagerActivity
import com.abaga129.tekisuto.util.ProfileSettingsManager
import com.abaga129.tekisuto.util.SpeechService
import com.abaga129.tekisuto.viewmodel.ProfileViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

private const val TAG = "SettingsFragment"

class SettingsFragment : PreferenceFragmentCompat(), CoroutineScope, 
                        SharedPreferences.OnSharedPreferenceChangeListener {
    
    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job
        
    private lateinit var speechService: SpeechService
    private lateinit var currentLanguage: String
    private lateinit var profileViewModel: ProfileViewModel
    private var isInitialSetup = true

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        
        // Initialize speech service
        speechService = SpeechService(requireContext())
        
        // Initialize ViewModel
        profileViewModel = ViewModelProvider(requireActivity()).get(ProfileViewModel::class.java)
        
        // OCR service preference
        findPreference<ListPreference>("ocr_service")?.let { servicePref ->
            Log.d(TAG, "Current OCR service: ${servicePref.value}")
            
            // Initialize Cloud OCR API key visibility based on current service
            updateCloudOcrApiKeyVisibility(servicePref.value)
            
            servicePref.setOnPreferenceChangeListener { _, newValue ->
                val serviceType = newValue as String
                Log.d(TAG, "OCR service changed to: $serviceType")
                
                // Update Cloud OCR API key visibility
                updateCloudOcrApiKeyVisibility(serviceType)
                
                // Save the changed OCR service to the current profile immediately
                if (!isInitialSetup) {
                    Log.d(TAG, "Saving OCR service change immediately: $serviceType")
                    
                    // Get the current profile
                    val currentProfile = profileViewModel.currentProfile.value
                    if (currentProfile != null) {
                        try {
                            // Get a reference to the settings manager
                            val settingsManager = ProfileSettingsManager(requireContext())
                            
                            // Save the OCR service change in SharedPreferences
                            settingsManager.saveOcrServiceChange(serviceType, currentProfile.id)
                            
                            // Update the profile in database through ViewModel
                            val updatedProfile = currentProfile.copy(ocrService = serviceType)
                            profileViewModel.updateProfileWithService(updatedProfile, serviceType)
                            
                            Log.d(TAG, "Updated profile with service: $serviceType")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error saving OCR service change", e)
                        }
                    }
                }
                
                true
            }
        }
        
        // Cloud OCR API key preference removed
        
        // OCR language preference
        findPreference<ListPreference>("ocr_language")?.let { languagePref ->
            Log.d(TAG, "Current OCR language: ${languagePref.value}")
            
            // Store current language
            currentLanguage = convertOcrLanguageToSpeechLanguage(languagePref.value)
            
            // Update voice selection summary
            updateVoiceSelectionSummary(currentLanguage)
            
            languagePref.setOnPreferenceChangeListener { _, newValue ->
                val language = newValue as String
                Log.d(TAG, "OCR language changed to: $language")
                
                // Update current language
                currentLanguage = convertOcrLanguageToSpeechLanguage(language)
                
                // Update voice selection summary
                updateVoiceSelectionSummary(currentLanguage)
                
                // Save the changed OCR language to the current profile immediately
                if (!isInitialSetup) {
                    Log.d(TAG, "Saving OCR language change immediately: $language")
                    
                    // Get the current profile
                    val currentProfile = profileViewModel.currentProfile.value
                    if (currentProfile != null) {
                        try {
                            // Get a reference to the settings manager
                            val settingsManager = ProfileSettingsManager(requireContext())
                            
                            // Save the OCR language change in SharedPreferences
                            settingsManager.saveOcrLanguageChange(language, currentProfile.id)
                            
                            // Update the profile in database through ViewModel
                            val updatedProfile = currentProfile.copy(ocrLanguage = language)
                            profileViewModel.updateProfileWithLanguage(updatedProfile, language)
                            
                            // Force save all settings to ensure the language change is persisted
                            profileViewModel.saveCurrentSettings()
                            
                            Log.d(TAG, "Updated profile with language: $language")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error saving OCR language change", e)
                        }
                    }
                }
                
                true
            }
        }
        
        // Long press settings
        findPreference<SwitchPreferenceCompat>("enable_long_press_capture")?.let { longPressEnablePref ->
            longPressEnablePref.setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                Log.d(TAG, "Long press capture enabled: $enabled")
                true
            }
        }
        
        findPreference<SeekBarPreference>("long_press_duration")?.let { durationPref ->
            durationPref.setOnPreferenceChangeListener { _, newValue ->
                val duration = newValue as Int
                Log.d(TAG, "Long press duration set to: $duration ms")
                true
            }
        }
        
        // Azure Speech API settings
        findPreference<EditTextPreference>("azure_speech_key")?.let { apiKeyPref ->
            // Hide API key in summary
            apiKeyPref.summaryProvider = Preference.SummaryProvider<EditTextPreference> { preference ->
                val text = preference.text
                if (text.isNullOrEmpty()) {
                    getString(R.string.azure_speech_key_summary)
                } else {
                    // Show asterisks instead of the actual key
                    "********" + text.takeLast(4)
                }
            }
            
            // Validate on change
            apiKeyPref.setOnPreferenceChangeListener { _, newValue ->
                val key = newValue as String
                if (key.isEmpty()) {
                    Toast.makeText(
                        context,
                        "Warning: Audio generation requires an Azure Speech API key",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        "Azure Speech API key updated",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                true
            }
        }
        
        // Handle audio enable/disable
        findPreference<SwitchPreferenceCompat>("enable_audio")?.let { audioEnablePref ->
            audioEnablePref.setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                if (!enabled) {
                    Toast.makeText(
                        context,
                        "Audio generation disabled",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    // Check if API key is configured
                    val apiKey = preferenceManager.sharedPreferences?.getString("azure_speech_key", "") ?: ""
                    if (apiKey.isEmpty()) {
                        Toast.makeText(
                            context,
                            "Please configure an Azure Speech API key",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                true
            }
        }
        
        // Voice selection preference
        findPreference<Preference>("select_voice")?.let { voicePref ->
            // Set click listener
            voicePref.setOnPreferenceClickListener {
                openVoiceSelectionDialog()
                true
            }
        }
        
        // Add a save to profile preference
        findPreference<Preference>("save_to_profile")?.let { savePref ->
            savePref.setOnPreferenceClickListener {
                // Save current settings to profile
                saveCurrentSettings()
                true
            }
        }
        
        // Handle Manage Profiles preference programmatically
        findPreference<Preference>("manage_profiles")?.let { manageProfilesPref ->
            // Override the intent with our own click listener
            manageProfilesPref.setOnPreferenceClickListener {
                try {
                    // Launch the ProfileManagerActivity directly
                    val intent = Intent(requireContext(), ProfileManagerActivity::class.java)
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Error launching ProfileManagerActivity", e)
                    Toast.makeText(
                        context,
                        "Error launching Profile Manager: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
                true
            }
        }
        
        // App Whitelist Management
        findPreference<SwitchPreferenceCompat>("enable_app_whitelist")?.let { whitelistEnablePref ->
            whitelistEnablePref.setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                Log.d(TAG, "App whitelist enabled: $enabled")
                // If disabled, make sure the service is notified to show the button for all apps
                if (!enabled) {
                    refreshFloatingButtonVisibility()
                }
                true
            }
        }
        
        // App Whitelist Selection
        findPreference<Preference>("app_whitelist")?.let { whitelistPref ->
            // Update summary to show count of whitelisted apps
            updateWhitelistSummary(whitelistPref)
            
            // Set click listener to open app selection activity
            whitelistPref.setOnPreferenceClickListener {
                try {
                    // Launch the AppWhitelistActivity
                    val intent = Intent(requireContext(), com.abaga129.tekisuto.ui.whitelist.AppWhitelistActivity::class.java)
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Error launching AppWhitelistActivity", e)
                    Toast.makeText(
                        context,
                        "Error launching App Whitelist: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
                true
            }
        }
    }
    
    /**
     * Save current settings to the active profile
     */
    private fun saveCurrentSettings() {
        profileViewModel.saveCurrentSettings()
        Toast.makeText(
            context,
            "Settings saved to current profile",
            Toast.LENGTH_SHORT
        ).show()
    }
    
    override fun onResume() {
        super.onResume()
        
        // Register for preference change listener
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        
        // Initial setup flag to prevent auto-saving during initial load
        isInitialSetup = true
        
        // Make sure we get the latest OCR service and language values from the current profile
        profileViewModel.currentProfile.value?.let { profile ->
            Log.d(TAG, "onResume: Getting OCR settings from current profile: " + 
                   "Service=${profile.ocrService}, Language=${profile.ocrLanguage}")
            
            // Update the OCR service preference directly from the profile value
            findPreference<ListPreference>("ocr_service")?.let { servicePref ->
                if (servicePref.value != profile.ocrService) {
                    Log.d(TAG, "onResume: Updating OCR service value from ${servicePref.value} to ${profile.ocrService}")
                    servicePref.value = profile.ocrService
                } else {
                    Log.d(TAG, "onResume: OCR service value already matches profile: ${servicePref.value}")
                }
            }
            
            // Update the OCR language preference directly from the profile value
            findPreference<ListPreference>("ocr_language")?.let { languagePref ->
                if (languagePref.value != profile.ocrLanguage) {
                    Log.d(TAG, "onResume: Updating OCR language value from ${languagePref.value} to ${profile.ocrLanguage}")
                    languagePref.value = profile.ocrLanguage
                    
                    // Also update the current language variable for voice selection
                    currentLanguage = convertOcrLanguageToSpeechLanguage(profile.ocrLanguage)
                    updateVoiceSelectionSummary(currentLanguage)
                } else {
                    Log.d(TAG, "onResume: OCR language value already matches profile: ${languagePref.value}")
                }
            }
        }
        
        // Debug current OCR settings values
        findPreference<ListPreference>("ocr_service")?.let { servicePref ->
            Log.d(TAG, "onResume: Current OCR service value after update: ${servicePref.value}")
        }
        
        findPreference<ListPreference>("ocr_language")?.let { languagePref ->
            Log.d(TAG, "onResume: Current OCR language value after update: ${languagePref.value}")
        }
        
        // Update profile display
        updateCurrentProfileInfo()
        
        // Update app whitelist summary
        findPreference<Preference>("app_whitelist")?.let { whitelistPref ->
            updateWhitelistSummary(whitelistPref)
        }
        
        // Reset initial setup flag after a longer delay to ensure all UI operations are complete
        Handler(Looper.getMainLooper()).postDelayed({
            isInitialSetup = false
            Log.d(TAG, "Initial setup complete, settings changes will now be saved automatically")
        }, 1000)
    }
    
    override fun onPause() {
        super.onPause()
        
        // Get the current OCR service and language values before unregistering the listener
        val currentOcrService = preferenceManager.sharedPreferences?.getString("ocr_service", "mlkit")
        val currentOcrLanguage = preferenceManager.sharedPreferences?.getString("ocr_language", "latin")
        
        // Unregister listener
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        
        // Auto-save settings when leaving
        if (!isInitialSetup) {
            // Save current OCR service and language directly (as an extra safeguard)
            val currentProfile = profileViewModel.currentProfile.value
            if (currentProfile != null) {
                try {
                    // Get a reference to the settings manager
                    val settingsManager = ProfileSettingsManager(requireContext())
                    
                    // Always save OCR service from SharedPreferences to the profile
                    if (currentOcrService != null) {
                        Log.d(TAG, "onPause: Saving OCR service: $currentOcrService")
                        // Save the OCR service change to SharedPreferences (redundant but safe)
                        settingsManager.saveOcrServiceChange(currentOcrService, currentProfile.id)
                        
                        // Also update the profile directly via the ViewModel
                        profileViewModel.updateProfileWithService(currentProfile.copy(ocrService = currentOcrService), currentOcrService)
                    }
                    
                    // Always save OCR language from SharedPreferences to the profile
                    if (currentOcrLanguage != null) {
                        Log.d(TAG, "onPause: Saving OCR language: $currentOcrLanguage")
                        // Save the OCR language change to SharedPreferences (redundant but safe)
                        settingsManager.saveOcrLanguageChange(currentOcrLanguage, currentProfile.id)
                        
                        // Also update the profile directly via the ViewModel
                        profileViewModel.updateProfileWithLanguage(currentProfile.copy(ocrLanguage = currentOcrLanguage), currentOcrLanguage)
                        
                        // Log this update for debugging
                        Log.d(TAG, "onPause: Updated profile with language: $currentOcrLanguage")
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving OCR settings in onPause", e)
                }
            }
            
            // Force a save of all settings to profile
            try {
                Log.d(TAG, "onPause: Force saving all settings to profile")
                saveCurrentSettings()
            } catch (e: Exception) {
                Log.e(TAG, "Error force saving settings in onPause", e)
            }
        } else {
            Log.d(TAG, "onPause: Skipping settings save as initial setup is still in progress")
        }
    }
    
    /**
     * Update the current profile information display
     */
    private fun updateCurrentProfileInfo() {
        findPreference<Preference>("current_profile_info")?.let { profilePref ->
            // Observe the current profile
            profileViewModel.currentProfile.observe(this) { profile ->
                if (profile != null) {
                    profilePref.summary = "Current Profile: ${profile.name}"
                }
            }
        }
    }
    
    /**
     * Handle preference changes
     */
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        // Skip if it's the initial setup or key is null
        if (isInitialSetup || key == null) return
        
        Log.d(TAG, "Preference changed: $key")
        
        try {
            // Get the current profile
            val currentProfile = profileViewModel.currentProfile.value
            
            if (currentProfile != null) {
                // We have a profile, handle the change with it
                when (key) {
                    "ocr_service" -> {
                        // Get the new OCR service value
                        val serviceType = sharedPreferences?.getString(key, "mlkit") ?: "mlkit"
                        Log.d(TAG, "SharedPreference changed: OCR service to $serviceType")
                        
                        // Update the profile with the new service
                        profileViewModel.updateProfileWithService(currentProfile, serviceType)
                    }
                    "ocr_language" -> {
                        // Get the new OCR language value
                        val language = sharedPreferences?.getString(key, "latin") ?: "latin"
                        Log.d(TAG, "SharedPreference changed: OCR language to $language")
                        
                        // Update the profile with the new language
                        profileViewModel.updateProfileWithLanguage(currentProfile, language)
                    }
                    else -> {
                        // For all other settings, save to the profile
                        Log.d(TAG, "Auto-saving setting change for: $key")
                        profileViewModel.saveCurrentSettings()
                    }
                }
            } else {
                // No current profile, try to get one via sync methods
                val profileId = preferenceManager.sharedPreferences?.getLong("current_profile_id", -1L) ?: -1L
                
                if (profileId != -1L) {
                    // Try to get profile by ID
                    val profileById = profileViewModel.getProfileByIdSync(profileId)
                    
                    if (profileById != null) {
                        // We found a profile, handle the change with it
                        handlePreferenceChangeWithProfile(key, sharedPreferences, profileById)
                    } else {
                        // Try default profile
                        val defaultProfile = profileViewModel.getDefaultProfileSync()
                        
                        if (defaultProfile != null) {
                            // We found the default profile, handle the change with it
                            handlePreferenceChangeWithProfile(key, sharedPreferences, defaultProfile)
                        } else {
                            // Last resort - force save
                            Log.d(TAG, "No profile found, forcing save of current settings")
                            profileViewModel.saveCurrentSettings()
                        }
                    }
                } else {
                    // No profile ID, try to use saveCurrentSettings which has its own fallbacks
                    Log.d(TAG, "No profile ID available, forcing save of current settings")
                    profileViewModel.saveCurrentSettings()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling preference change: ${e.message}")
            // Last resort - just try to save
            try {
                profileViewModel.saveCurrentSettings()
            } catch (innerEx: Exception) {
                Log.e(TAG, "Final attempt to save settings failed: ${innerEx.message}")
            }
        }
    }
    
    /**
     * Helper method to handle preference changes with a specific profile
     */
    private fun handlePreferenceChangeWithProfile(
        key: String, 
        sharedPreferences: SharedPreferences?, 
        profile: ProfileEntity
    ) {
        when (key) {
            "ocr_service" -> {
                val serviceType = sharedPreferences?.getString(key, "mlkit") ?: "mlkit"
                Log.d(TAG, "Handling OCR service change to $serviceType with profile ${profile.id}")
                profileViewModel.updateProfileWithService(profile, serviceType)
            }
            "ocr_language" -> {
                val language = sharedPreferences?.getString(key, "latin") ?: "latin"
                Log.d(TAG, "Handling OCR language change to $language with profile ${profile.id}")
                profileViewModel.updateProfileWithLanguage(profile, language)
            }
            else -> {
                // Save current settings which will use the profile
                profileViewModel.saveCurrentSettings()
            }
        }
    }
    
    /**
     * Open the voice selection dialog
     */
    private fun openVoiceSelectionDialog() {
        // Check if API key is configured
        val apiKey = preferenceManager.sharedPreferences?.getString("azure_speech_key", "") ?: ""
        if (apiKey.isEmpty()) {
            Toast.makeText(
                context,
                getString(R.string.cannot_get_voices),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        
        // Get current voice
        val currentVoice = speechService.getSelectedVoice(currentLanguage) ?: ""
        
        // Show dialog
        context?.let { ctx ->
            val dialog = VoiceSelectionDialog(
                ctx,
                currentVoice,
                currentLanguage
            ) { selectedVoice ->
                // Save the selected voice
                speechService.saveVoiceSelection(currentLanguage, selectedVoice)
                
                // Update summary
                updateVoiceSelectionSummary(currentLanguage)
                
                Toast.makeText(context, "Voice updated", Toast.LENGTH_SHORT).show()
            }
            dialog.show()
        }
    }
    
    /**
     * Update the summary of the voice selection preference
     */
    private fun updateVoiceSelectionSummary(language: String) {
        findPreference<Preference>("select_voice")?.let { voicePref ->
            val voice = speechService.getSelectedVoice(language)
            if (voice != null) {
                voicePref.summary = "${getString(R.string.voice_name_label)} $voice"
            } else {
                voicePref.summary = getString(R.string.voice_name_label)
            }
        }
    }
    
    /**
     * Convert OCR language code to speech language code
     * For Latin script languages, use specific language codes instead of empty string
     */
    private fun convertOcrLanguageToSpeechLanguage(ocrLanguage: String): String {
        return when (ocrLanguage) {
            "japanese" -> "ja"
            "chinese" -> "zh"
            "korean" -> "ko"
            "devanagari" -> "hi" // Hindi
            "spanish" -> "es"  // Spanish
            "french" -> "fr"   // French
            "german" -> "de"   // German
            "italian" -> "it"  // Italian
            "portuguese" -> "pt" // Portuguese
            "russian" -> "ru"  // Russian
            "arabic" -> "ar"   // Arabic
            "latin" -> ""      // Empty string will show all voices for generic Latin script
            else -> "en"       // Default to English for other scripts
        }
    }
    
    /**
     * Update the App Whitelist preference summary to show the count of whitelisted apps
     */
    private fun updateWhitelistSummary(preference: Preference) {
        launch {
            val whitelistManager = com.abaga129.tekisuto.util.AppWhitelistManager(requireContext())
            val whitelistedApps = whitelistManager.getWhitelistedApps()
            val count = whitelistedApps.size
            
            if (count == 0) {
                preference.summary = getString(R.string.manage_app_whitelist_summary)
            } else {
                preference.summary = getString(R.string.apps_whitelisted_count, count)
            }
        }
    }
    
    /**
     * Refresh the floating button visibility based on current settings
     * Called when whitelist is disabled to ensure button shows for all apps
     */
    private fun refreshFloatingButtonVisibility() {
        // Get the accessibility service instance
        val accessibilityService = com.abaga129.tekisuto.service.AccessibilityOcrService.getInstance()
        
        // If the service is running, tell it to re-evaluate the button visibility
        accessibilityService?.refreshFloatingButtonVisibility()
    }
    
    /**
     * Update the visibility of service-specific preferences based on the selected service
     * Note: Cloud OCR API key handling has been removed
     */
    private fun updateCloudOcrApiKeyVisibility(serviceType: String) {
        // Cloud OCR API key preference handling removed
        // This function is kept for backwards compatibility but doesn't do anything now
    }
    
    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}