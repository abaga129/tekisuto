package com.abaga129.tekisuto.ui.settings

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import androidx.preference.SeekBarPreference
import com.abaga129.tekisuto.R

private const val TAG = "SettingsFragment"

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        
        // OCR language preference
        findPreference<ListPreference>("ocr_language")?.let { languagePref ->
            Log.d(TAG, "Current OCR language: ${languagePref.value}")
            
            languagePref.setOnPreferenceChangeListener { _, newValue ->
                Log.d(TAG, "OCR language changed to: $newValue")
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
    }
}