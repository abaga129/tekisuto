package com.abaga129.tekisuto.ui.settings

import android.os.Bundle
import android.util.Log
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import com.abaga129.tekisuto.R

private const val TAG = "SettingsFragment"

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        
        findPreference<ListPreference>("ocr_language")?.let { languagePref ->
            Log.d(TAG, "Current OCR language: ${languagePref.value}")
            
            languagePref.setOnPreferenceChangeListener { _, newValue ->
                Log.d(TAG, "OCR language changed to: $newValue")
                true
            }
        }
    }
}