package com.abaga129.tekisuto.ui.settings

import android.os.Bundle
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import com.abaga129.tekisuto.R
import com.abaga129.tekisuto.ui.BaseEdgeToEdgeActivity
import com.abaga129.tekisuto.viewmodel.ProfileViewModel

private const val TAG = "SettingsActivity"

class SettingsActivity : BaseEdgeToEdgeActivity() {

    private lateinit var profileViewModel: ProfileViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        // Apply insets to the root view
        applyInsetsToView(android.R.id.content)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.ocr_settings_title)
        
        // Initialize the ProfileViewModel
        profileViewModel = ViewModelProvider(this).get(ProfileViewModel::class.java)
        
        // Load profiles and ensure a current profile is selected
        Log.d(TAG, "Loading profiles in SettingsActivity")
        profileViewModel.loadProfiles()
        
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // Ensure we have a current profile loaded
        if (profileViewModel.currentProfile.value == null) {
            Log.d(TAG, "onResume: No current profile, loading one")
            profileViewModel.loadCurrentProfile()
        } else {
            Log.d(TAG, "onResume: Current profile is ${profileViewModel.currentProfile.value?.name}")
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}