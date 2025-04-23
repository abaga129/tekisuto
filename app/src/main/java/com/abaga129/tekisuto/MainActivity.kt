package com.abaga129.tekisuto

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.abaga129.tekisuto.database.DictionaryRepository
import com.abaga129.tekisuto.service.AccessibilityOcrService
import com.abaga129.tekisuto.ui.BaseEdgeToEdgeActivity
import com.abaga129.tekisuto.ui.anki.AnkiDroidConfigActivity
import com.abaga129.tekisuto.ui.profile.ProfileManagerActivity
import com.abaga129.tekisuto.ui.settings.SettingsActivity
import com.abaga129.tekisuto.viewmodel.DictionaryInfo
import com.abaga129.tekisuto.viewmodel.MainViewModel
import com.abaga129.tekisuto.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch

class MainActivity : BaseEdgeToEdgeActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var profileViewModel: ProfileViewModel
    private lateinit var statusTextView: TextView
    private lateinit var settingsButton: Button
    private lateinit var manageDictionariesButton: Button
    private lateinit var browseDictionaryButton: Button
    private lateinit var ocrSettingsButton: Button
    private lateinit var configureAnkiButton: Button
    private lateinit var manageProfilesButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var dictionaryInfoTextView: TextView
    private lateinit var currentProfileTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState) // This calls enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        
        // Apply insets to the root view to avoid status bar overlap
        applyInsetsToView(android.R.id.content)

        // Initialize ViewModels
        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        profileViewModel = ViewModelProvider(this).get(ProfileViewModel::class.java)

        // Initialize repository from application
        val repository = DictionaryRepository(applicationContext)
        viewModel.initRepository(repository)

        // Initialize UI components
        statusTextView = findViewById(R.id.status_text_view)
        settingsButton = findViewById(R.id.settings_button)
        manageDictionariesButton = findViewById(R.id.manage_dictionaries_button)
        browseDictionaryButton = findViewById(R.id.browse_dictionary_button)
        ocrSettingsButton = findViewById(R.id.ocr_settings_button)
        configureAnkiButton = findViewById(R.id.configure_anki_button)
        manageProfilesButton = findViewById(R.id.manage_profiles_button)
        progressBar = findViewById(R.id.import_progress_bar)
        dictionaryInfoTextView = findViewById(R.id.dictionary_info_text_view)
        currentProfileTextView = findViewById(R.id.current_profile_text)

        // Hide progress bar initially
        progressBar.visibility = View.GONE

        // Set click listener for the settings button
        settingsButton.setOnClickListener {
            openAccessibilitySettings()
        }

        // Set click listener for manage dictionaries button
        manageDictionariesButton.setOnClickListener {
            openDictionaryManager()
        }
        
        // Set click listener for the browse dictionary button
        browseDictionaryButton.setOnClickListener {
            openDictionaryBrowser()
        }
        
        // Set click listener for the OCR settings button
        ocrSettingsButton.setOnClickListener {
            openOcrSettings()
        }
        
        // Set click listener for the Configure AnkiDroid button
        configureAnkiButton.setOnClickListener {
            openAnkiConfig()
        }
        
        // Set click listener for the Manage Profiles button
        manageProfilesButton.setOnClickListener {
            openProfileManager()
        }

        // Observe accessibility service status
        viewModel.isAccessibilityServiceEnabled.observe(this) { isEnabled ->
            updateStatusText(isEnabled)
        }

        // Observe import progress
        viewModel.importProgress.observe(this) { progress ->
            updateImportProgress(progress)
        }

        // Observe dictionary info
        viewModel.dictionaryInfo.observe(this) { info ->
            updateDictionaryInfo(info)
        }
        
        // Observe current profile
        profileViewModel.currentProfile.observe(this) { profile ->
            if (profile != null) {
                currentProfileTextView.text = getString(R.string.current_profile, profile.name)
                currentProfileTextView.visibility = View.VISIBLE
            } else {
                currentProfileTextView.visibility = View.GONE
            }
        }

        // Check for existing dictionary on startup
        lifecycleScope.launch {
            val count = repository.getDictionaryEntryCount()
            if (count > 0) {
                dictionaryInfoTextView.text = getString(R.string.dictionary_entries_count, count)
                dictionaryInfoTextView.visibility = View.VISIBLE
            } else {
                dictionaryInfoTextView.visibility = View.GONE
            }
        }
        
        // Load profiles
        profileViewModel.loadProfiles()
    }

    override fun onResume() {
        super.onResume()
        // Check if accessibility service is enabled
        viewModel.checkAccessibilityServiceStatus(this, AccessibilityOcrService::class.java)
        
        // Also start a periodic check to update the status continuously
        startPeriodicServiceCheck()
        
        // Refresh current profile
        profileViewModel.loadCurrentProfile()
        
        // Add a delay to ensure the service status is properly checked
        Handler(Looper.getMainLooper()).postDelayed({
            // Show the floating button if the service is enabled
            if (viewModel.isAccessibilityServiceEnabled.value == true) {
                // Call the service to show the floating button
                showFloatingButtonIfHidden()
                android.util.Log.d("MainActivity", "Attempting to show floating button after delay")
            }
        }, 1000) // 1 second delay
    }
    
    private val serviceCheckHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val serviceCheckRunnable = object : Runnable {
        override fun run() {
            // Check the service status
            viewModel.checkAccessibilityServiceStatus(this@MainActivity, AccessibilityOcrService::class.java)
            
            // Schedule the next check
            serviceCheckHandler.postDelayed(this, 2000) // Check every 2 seconds
        }
    }
    
    private fun startPeriodicServiceCheck() {
        // Remove any existing callbacks to avoid duplicates
        serviceCheckHandler.removeCallbacks(serviceCheckRunnable)
        
        // Start the periodic check
        serviceCheckHandler.post(serviceCheckRunnable)
    }
    
    override fun onPause() {
        super.onPause()
        // Stop the periodic check when activity is paused
        serviceCheckHandler.removeCallbacks(serviceCheckRunnable)
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_ocr_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun updateStatusText(isEnabled: Boolean) {
        statusTextView.text = if (isEnabled) {
            getString(R.string.service_enabled)
        } else {
            getString(R.string.service_disabled)
        }
        
        // Update text color based on status
        val colorRes = if (isEnabled) {
            android.R.color.holo_green_dark
        } else {
            android.R.color.holo_red_dark
        }
        statusTextView.setTextColor(resources.getColor(colorRes, theme))
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun openDictionaryManager() {
        val intent = Intent(this, com.abaga129.tekisuto.ui.dictionary.DictionaryManagerActivity::class.java)
        startActivity(intent)
    }
    
    private fun openDictionaryBrowser() {
        val intent = Intent(this, com.abaga129.tekisuto.ui.dictionary.DictionaryBrowserActivity::class.java)
        startActivity(intent)
    }
    
    private fun openOcrSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }
    
    private fun openAnkiConfig() {
        val intent = Intent(this, com.abaga129.tekisuto.ui.anki.AnkiDroidConfigActivity::class.java)
        startActivity(intent)
    }
    
    private fun openProfileManager() {
        val intent = Intent(this, ProfileManagerActivity::class.java)
        startActivity(intent)
    }

    private fun updateImportProgress(progress: Int) {
        when {
            progress < 0 -> {
                // Error occurred
                progressBar.visibility = View.GONE
            }
            progress == 0 -> {
                // Just started
                progressBar.isIndeterminate = true
                progressBar.visibility = View.VISIBLE
            }
            progress == 100 -> {
                // Completed
                progressBar.visibility = View.GONE
                Toast.makeText(this, getString(R.string.import_success), Toast.LENGTH_SHORT).show()
            }
            else -> {
                // In progress
                progressBar.isIndeterminate = false
                progressBar.progress = progress
            }
        }
    }

    private fun updateDictionaryInfo(info: DictionaryInfo?) {
        if (info != null) {
            val infoText = getString(
                R.string.dictionary_info,
                info.title,
                info.sourceLanguage,
                info.targetLanguage
            )
            dictionaryInfoTextView.text = infoText
            dictionaryInfoTextView.visibility = View.VISIBLE
        } else {
            dictionaryInfoTextView.visibility = View.GONE
        }
    }
    
    /**
     * Shows the floating button if it was previously hidden and the service is running
     */
    private fun showFloatingButtonIfHidden() {
        try {
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
            
            // Set the preference to show the button
            prefs.edit().putBoolean(com.abaga129.tekisuto.util.PreferenceKeys.FLOATING_BUTTON_VISIBLE, true).apply()
            
            // First try to use the direct service instance
            val serviceInstance = com.abaga129.tekisuto.service.AccessibilityOcrService.getInstance()
            if (serviceInstance != null) {
                android.util.Log.d("MainActivity", "Using direct service instance to show floating button")
                serviceInstance.showFloatingButton()
            } else {
                // Fallback to broadcasting
                android.util.Log.d("MainActivity", "Service instance not available, using broadcast")
                val intent = Intent(com.abaga129.tekisuto.service.AccessibilityOcrService.ACTION_SHOW_FLOATING_BUTTON)
                intent.setPackage(packageName) // Make it an explicit intent
                sendBroadcast(intent)
                android.util.Log.d("MainActivity", "Sent broadcast to show floating button")
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error showing floating button", e)
        }
    }
}