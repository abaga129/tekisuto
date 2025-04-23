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
import com.abaga129.tekisuto.ui.dialog.VoiceSelectionDialog
import com.abaga129.tekisuto.ui.profile.ProfileManagerActivity
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
        
        // OCR language preference
        findPreference<ListPreference>("ocr_language")?.let { languagePref ->
            Log.d(TAG, "Current OCR language: ${languagePref.value}")
            
            // Store current language
            currentLanguage = convertOcrLanguageToSpeechLanguage(languagePref.value)
            
            // Update voice selection summary
            updateVoiceSelectionSummary(currentLanguage)
            
            languagePref.setOnPreferenceChangeListener { _, newValue ->
                Log.d(TAG, "OCR language changed to: $newValue")
                // Update current language
                currentLanguage = convertOcrLanguageToSpeechLanguage(newValue as String)
                
                // Update voice selection summary
                updateVoiceSelectionSummary(currentLanguage)
                
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
        
        // Update profile display
        updateCurrentProfileInfo()
        
        // Update app whitelist summary
        findPreference<Preference>("app_whitelist")?.let { whitelistPref ->
            updateWhitelistSummary(whitelistPref)
        }
        
        // Reset initial setup flag after a short delay
        Handler(Looper.getMainLooper()).postDelayed({
            isInitialSetup = false
        }, 500)
    }
    
    override fun onPause() {
        super.onPause()
        
        // Unregister listener
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        
        // Auto-save settings when leaving
        if (!isInitialSetup) {
            saveCurrentSettings()
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
        
        // Optional: automatically save to profile on certain changes
        /*
        when (key) {
            "ocr_language", "translate_ocr_text", "translate_target_language" -> {
                // Auto-save after OCR settings change
                saveCurrentSettings()
            }
        }
        */
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
     * For Latin script, return an empty string to show all voices
     */
    private fun convertOcrLanguageToSpeechLanguage(ocrLanguage: String): String {
        return when (ocrLanguage) {
            "japanese" -> "ja"
            "chinese" -> "zh"
            "korean" -> "ko"
            "devanagari" -> "hi" // Hindi
            "latin" -> "" // Empty string will show all voices for Latin script
            else -> "en" // Default to English for other scripts
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
    
    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}