package com.abaga129.tekisuto

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.abaga129.tekisuto.database.DictionaryRepository
import com.abaga129.tekisuto.service.AccessibilityOcrService
import com.abaga129.tekisuto.ui.BaseEdgeToEdgeActivity
import com.abaga129.tekisuto.ui.anki.AnkiDroidConfigActivity
import com.abaga129.tekisuto.ui.profile.ProfileManagerActivity
import com.abaga129.tekisuto.ui.settings.SettingsActivity
import com.abaga129.tekisuto.util.AnkiDroidHelper
import com.abaga129.tekisuto.viewmodel.MainViewModel
import com.abaga129.tekisuto.viewmodel.ParserViewModel
import com.abaga129.tekisuto.viewmodel.ProfileViewModel
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.launch

class MainActivity : BaseEdgeToEdgeActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var viewModel: MainViewModel
    private lateinit var profileViewModel: ProfileViewModel
    private lateinit var statusTextView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var dictionaryInfoTextView: TextView
    private lateinit var currentProfileTextView: TextView
    private lateinit var toolbar: Toolbar
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var ankiDroidHelper: AnkiDroidHelper

    companion object {
        private const val ANKIDROID_PERMISSION_REQUEST = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState) // This calls enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        
        // Initialize AnkiDroid helper
        ankiDroidHelper = AnkiDroidHelper(this)
        
        // Set up the toolbar
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        
        // Set up the navigation drawer
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener(this)
        
        // Set up the hamburger icon and drawer toggle
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        
        // Apply insets to the root view to avoid status bar overlap
        applyInsetsToView(android.R.id.content)

        // Initialize ViewModels
        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        profileViewModel = ViewModelProvider(this).get(ProfileViewModel::class.java)

        // Initialize repository from application
        val repository = DictionaryRepository(applicationContext)
        
        // Initialize UI components
        statusTextView = findViewById(R.id.status_text_view)
        progressBar = findViewById(R.id.import_progress_bar)
        dictionaryInfoTextView = findViewById(R.id.dictionary_info_text_view)
        currentProfileTextView = findViewById(R.id.current_profile_text)

        // Hide progress bar initially
        progressBar.visibility = View.GONE

        // Observe accessibility service status
        viewModel.isAccessibilityServiceEnabled.observe(this) { isEnabled ->
            updateStatusText(isEnabled)
        }

        // Observe import progress
        ParserViewModel.getInstance().importProgress.observe(this) {progress ->
            updateImportProgress(progress)
        }

        // Observe dictionary info
        ParserViewModel.getInstance().dictionaryInfo.observe(this) { info ->
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
        
        // Set the ProfileViewModel to the service if it's running
        setProfileViewModelToService()
        
        // Check AnkiDroid permission status
        checkAnkiDroidPermission()
        
        // Add a delay to ensure the service status is properly checked
        Handler(Looper.getMainLooper()).postDelayed({
            // Show the floating button if the service is enabled
            if (viewModel.isAccessibilityServiceEnabled.value == true) {
                // Call the service to show the floating button
                showFloatingButtonIfHidden()
                android.util.Log.d("MainActivity", "Attempting to show floating button after delay")
            }
        }, 1000) // 1 second delay
        
        // Update navigation menu state
        updateNavigationMenuState()
    }

    /**
     * Check AnkiDroid permission status and request if needed
     */
    private fun checkAnkiDroidPermission() {
        try {
            // First check if AnkiDroid is installed
            if (!ankiDroidHelper.isApiAvailable()) {
                android.util.Log.d("MainActivity", "AnkiDroid not installed")
                return
            }
            
            // Check if permission is needed
            if (ankiDroidHelper.shouldRequestPermission()) {
                android.util.Log.d("MainActivity", "AnkiDroid permission needed, requesting...")
                ankiDroidHelper.requestPermission(this, ANKIDROID_PERMISSION_REQUEST)
            } else {
                android.util.Log.d("MainActivity", "AnkiDroid permission already granted")
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error checking AnkiDroid permission", e)
        }
    }

    /**
     * Handle permission request results
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            ANKIDROID_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    android.util.Log.d("MainActivity", "AnkiDroid permission granted")
                    Toast.makeText(this, "AnkiDroid permission granted", Toast.LENGTH_SHORT).show()
                } else {
                    android.util.Log.d("MainActivity", "AnkiDroid permission denied")
                    Toast.makeText(this, "AnkiDroid permission denied. You can enable it later in AnkiDroid settings.", Toast.LENGTH_LONG).show()
                }
            }
        }
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
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
    
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_accessibility_settings -> {
                openAccessibilitySettings()
            }
            R.id.nav_manage_dictionaries -> {
                openDictionaryManager()
            }
            R.id.nav_browse_dictionary -> {
                openDictionaryBrowser()
            }
            R.id.nav_ocr_settings -> {
                openOcrSettings()
            }
            R.id.nav_configure_anki -> {
                openAnkiConfig()
            }
            R.id.nav_manage_profiles -> {
                openProfileManager()
            }
            R.id.nav_show_floating_button -> {
                // Check if service is enabled before allowing this action
                if (viewModel.isAccessibilityServiceEnabled.value == true) {
                    showFloatingButtonIfHidden()
                    Toast.makeText(this, "Floating button shown", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Please enable the accessibility service first", Toast.LENGTH_LONG).show()
                }
            }
        }
        
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
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
        
        // Update navigation menu state
        updateNavigationMenuState()
    }
    
    private fun updateNavigationMenuState() {
        val menu = navigationView.menu
        val showFloatingButtonItem = menu.findItem(R.id.nav_show_floating_button)
        showFloatingButtonItem?.isEnabled = viewModel.isAccessibilityServiceEnabled.value == true
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

    private fun updateDictionaryInfo(info: ParserViewModel.DictionaryInfo?) {
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
     * Set the ProfileViewModel to the AccessibilityOcrService if it's running
     */
    private fun setProfileViewModelToService() {
        try {
            // Get the service instance
            val serviceInstance = AccessibilityOcrService.getInstance()
            if (serviceInstance != null) {
                android.util.Log.d("MainActivity", "Setting ProfileViewModel to AccessibilityOcrService")
                serviceInstance.setProfileViewModel(profileViewModel)
            } else {
                android.util.Log.d("MainActivity", "AccessibilityOcrService instance not available")
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error setting ProfileViewModel to service", e)
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