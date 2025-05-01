package com.abaga129.tekisuto.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import androidx.preference.PreferenceManager
import androidx.room.Room
import com.abaga129.tekisuto.R
import com.abaga129.tekisuto.database.AppDatabase
import com.abaga129.tekisuto.database.ProfileDao
import com.abaga129.tekisuto.database.ProfileEntity
import com.abaga129.tekisuto.ui.ImageCropActivity
import com.abaga129.tekisuto.util.OcrHelper
import com.abaga129.tekisuto.util.PreferenceKeys
import com.abaga129.tekisuto.util.ProfileSettingsManager
import com.abaga129.tekisuto.util.ScreenshotHelper
import com.abaga129.tekisuto.viewmodel.ProfileViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.CoroutineContext

class AccessibilityOcrService : AccessibilityService(), FloatingButtonHandler.FloatingButtonCallback, 
                               CoroutineScope, SharedPreferences.OnSharedPreferenceChangeListener {
                               
    companion object {
        const val ACTION_SHOW_FLOATING_BUTTON = "com.abaga129.tekisuto.action.SHOW_FLOATING_BUTTON"
        
        /**
         * Static instance of the service for direct access
         */
        private var instance: AccessibilityOcrService? = null
        
        /**
         * Get the current instance of the service
         */
        fun getInstance(): AccessibilityOcrService? {
            return instance
        }
    }
    
    // BroadcastReceiver to handle showing the floating button
    private val showButtonReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == ACTION_SHOW_FLOATING_BUTTON) {
                android.util.Log.d("AccessibilityOcrService", "Received broadcast to show floating button")
                showFloatingButton()
            }
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var menuLayout: FrameLayout
    private var isMenuVisible = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var ocrHelper: OcrHelper
    private lateinit var screenshotHelper: ScreenshotHelper
    private lateinit var prefs: SharedPreferences
    
    // Profile handling
    private lateinit var profileSettingsManager: ProfileSettingsManager
    private lateinit var profileDao: ProfileDao
    private var currentProfile: ProfileEntity? = null
    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job
    private var profileViewModel: ProfileViewModel? = null
    
    // Floating button handler
    private lateinit var floatingButtonHandler: FloatingButtonHandler
    
    // App whitelist manager
    private lateinit var appWhitelistManager: com.abaga129.tekisuto.util.AppWhitelistManager
    
    // Current package name
    private var currentPackageName: String = ""
    
    // Prevent processing duplicate events
    private var lastEventTime = 0L
    private var lastEventPackage = ""
    private val EVENT_DEBOUNCE_TIME = 300L // 300ms debounce for events
    
    // Track pending visibility operations
    private var pendingVisibilityRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        // Set the static instance
        instance = this
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        ocrHelper = OcrHelper(this)
        screenshotHelper = ScreenshotHelper(this)
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        
        // Initialize profile settings manager
        profileSettingsManager = ProfileSettingsManager(this)
        
        // Initialize app whitelist manager
        appWhitelistManager = com.abaga129.tekisuto.util.AppWhitelistManager(this)
        
        // Initialize database and DAO
        val database = Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "tekisuto_dictionary${com.abaga129.tekisuto.BuildConfig.DB_NAME_SUFFIX}.db"
        ).build()
        profileDao = database.profileDao()
        
        // Load current profile
        loadCurrentProfile()
        
        // Register preference change listener
        prefs.registerOnSharedPreferenceChangeListener(this)
        
        // Initialize floating button handler
        floatingButtonHandler = FloatingButtonHandler(this)
        floatingButtonHandler.setCallback(this)
        
        // Register broadcast receiver for showing the floating button
        val intentFilter = android.content.IntentFilter(ACTION_SHOW_FLOATING_BUTTON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(showButtonReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(showButtonReceiver, intentFilter)
        }
        Log.d("AccessibilityOcrService", "Registered broadcast receiver for showing floating button")
        
        Log.d("AccessibilityOcrService", "Service created")
    }

    override fun onServiceConnected() {
        Log.d("AccessibilityOcrService", "onServiceConnected - API level: ${Build.VERSION.SDK_INT}")

        // Create menu layout first to ensure it's initialized
        createMenuLayout()

        // Check if the floating button should be visible using the consolidated logic
        if (shouldShowFloatingButton(currentPackageName)) {
            Log.d("AccessibilityOcrService", "Showing floating button (initial service connection)")
            floatingButtonHandler.createFloatingButton()
        } else {
            Log.d("AccessibilityOcrService", "Not showing floating button (initial service connection)")
        }
    }
    
    // Flag to track ongoing visibility operations
    private var isProcessingVisibilityChange = false
    
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Check if this is a window state changed event (app switched)
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // Sometimes the package name can be null, so check it first
            val packageName = event.packageName?.toString() ?: return
            val currentTime = System.currentTimeMillis()
            
            // Skip system UI and identical package events that happen too quickly
            if (packageName == "android" || packageName.startsWith("com.android.systemui")) {
                return
            }
            
            // More aggressive debounce with longer time window for the same package
            if (packageName == lastEventPackage && 
                currentTime - lastEventTime < EVENT_DEBOUNCE_TIME) {
                Log.d("AccessibilityOcrService", "Ignoring duplicate event for $packageName (debounced)")
                return
            }
            
            // Update tracking variables
            lastEventTime = currentTime
            lastEventPackage = packageName
            
            // Check if package actually changed and we're not already processing a visibility change
            if (packageName != currentPackageName && !isProcessingVisibilityChange) {
                Log.d("AccessibilityOcrService", "App changed to: $packageName")
                
                // Mark that we're starting a visibility processing operation
                isProcessingVisibilityChange = true
                
                // Update current package name
                currentPackageName = packageName
                
                // Check if this is a whitelisted app and button is already visible - if so, do nothing
                val isWhitelisted = appWhitelistManager.isAppWhitelisted(packageName)
                val isButtonVisible = floatingButtonHandler.isVisible()
                
                if (isWhitelisted && isButtonVisible) {
                    Log.d("AccessibilityOcrService", "Whitelisted app with button already visible, skipping visibility change")
                    isProcessingVisibilityChange = false
                    return
                }
                
                // Update floating button visibility with our improved method
                updateFloatingButtonVisibility(packageName)
                
                // Reset the processing flag after a delay that's longer than our visibility operations
                mainHandler.postDelayed({
                    isProcessingVisibilityChange = false
                    Log.d("AccessibilityOcrService", "Visibility change processing completed for $packageName")
                    
                    // Ensure button is in the correct state after all operations
                    validateButtonState(packageName)
                }, 600) // Double the visibility operation delay to ensure it completes
            }
        }
    }
    
    /**
     * Validates and corrects the button state if needed after operations complete
     */
    private fun validateButtonState(packageName: String) {
        val shouldShow = shouldShowFloatingButton(packageName)
        val isVisible = floatingButtonHandler.isVisible()
        val isButtonHiddenManually = !prefs.getBoolean(PreferenceKeys.FLOATING_BUTTON_VISIBLE, true)
        
        if (shouldShow && !isVisible) {
            Log.d("AccessibilityOcrService", "Final validation: Button should be visible but isn't. Showing button.")
            floatingButtonHandler.createFloatingButton()
        } else if (!shouldShow && isVisible && isButtonHiddenManually) {
            // Only hide the button if it was manually hidden through the "Exit Tekisuto" button
            Log.d("AccessibilityOcrService", "Final validation: Button should be hidden (manually) but isn't. Hiding button.")
            floatingButtonHandler.hideFloatingButton()
        } else {
            Log.d("AccessibilityOcrService", "Final validation: Button state is correct or maintained (visible=$isVisible).")
        }
    }
    
    /**
     * Single source of truth for determining if the floating button should be visible
     */
    private fun shouldShowFloatingButton(packageName: String): Boolean {
        // First check if whitelist is enabled
        val isWhitelistEnabled = appWhitelistManager.isWhitelistEnabled()
        
        return if (isWhitelistEnabled) {
            // When whitelist is enabled, only show for whitelisted apps
            appWhitelistManager.isAppWhitelisted(packageName)
        } else {
            // When whitelist is disabled, use the general visibility preference
            prefs.getBoolean(PreferenceKeys.FLOATING_BUTTON_VISIBLE, true)
        }
    }
    
    /**
     * Update floating button visibility based on current app
     * This method ensures button visibility is persistent when needed
     */
    private fun updateFloatingButtonVisibility(packageName: String) {
        // Cancel any pending visibility changes
        pendingVisibilityRunnable?.let { mainHandler.removeCallbacks(it) }
        
        val shouldShow = shouldShowFloatingButton(packageName)
        val isCurrentlyVisible = floatingButtonHandler.isVisible()
        
        // For whitelisted apps, only ensure the button is shown and never hide it
        // This prevents toggling issues caused by multiple events
        if (shouldShow) {
            // If the button should be shown but isn't visible, show it
            if (!isCurrentlyVisible) {
                Log.d("AccessibilityOcrService", "Scheduling show floating button for $packageName")
                
                pendingVisibilityRunnable = Runnable {
                    // Double-check the state before making changes to prevent race conditions
                    if (!floatingButtonHandler.isVisible()) {
                        Log.d("AccessibilityOcrService", "Actually showing floating button for $packageName")
                        floatingButtonHandler.createFloatingButton()
                    } else {
                        Log.d("AccessibilityOcrService", "Button already visible, no action needed")
                    }
                    pendingVisibilityRunnable = null
                }
                
                // Add a slight delay to allow the app transition to complete
                mainHandler.postDelayed(pendingVisibilityRunnable!!, 300)
            } else {
                Log.d("AccessibilityOcrService", "Button already visible for whitelisted app $packageName, maintaining visibility")
            }
        } else if (!shouldShow && isCurrentlyVisible) {
            // Only hide the button if it was manually closed with "Exit Tekisuto" button
            // Check if the button was manually hidden (not just temporarily hidden)
            val isButtonHiddenManually = !prefs.getBoolean(PreferenceKeys.FLOATING_BUTTON_VISIBLE, true)
            
            if (isButtonHiddenManually) {
                Log.d("AccessibilityOcrService", "Scheduling hide floating button for $packageName (manually hidden)")
                
                pendingVisibilityRunnable = Runnable {
                    // Double-check the state before making changes
                    if (floatingButtonHandler.isVisible()) {
                        Log.d("AccessibilityOcrService", "Actually hiding floating button for $packageName")
                        floatingButtonHandler.hideFloatingButton()
                    } else {
                        Log.d("AccessibilityOcrService", "Button already hidden, no action needed")
                    }
                    pendingVisibilityRunnable = null
                }
                
                // Add a slight delay to allow the app transition to complete
                mainHandler.postDelayed(pendingVisibilityRunnable!!, 300)
            } else {
                Log.d("AccessibilityOcrService", "Button visibility maintained despite shouldShow=$shouldShow")
            }
        } else {
            Log.d("AccessibilityOcrService", "No visibility change needed for $packageName (shouldShow=$shouldShow, isVisible=$isCurrentlyVisible)")
        }
    }
    
    /**
     * Refresh the floating button visibility based on current settings
     * This is called when settings change to ensure the button visibility is updated
     */
    fun refreshFloatingButtonVisibility() {
        if (currentPackageName.isNotEmpty()) {
            Log.d("AccessibilityOcrService", "Refreshing floating button visibility for $currentPackageName")
            updateFloatingButtonVisibility(currentPackageName)
        } else {
            Log.d("AccessibilityOcrService", "No current package name, checking general visibility preference")
            
            // Determine if button should be visible based on general preference
            val shouldShowButton = prefs.getBoolean(PreferenceKeys.FLOATING_BUTTON_VISIBLE, true)
            
            // Only show the button if needed
            if (shouldShowButton && !floatingButtonHandler.isVisible()) {
                mainHandler.postDelayed({
                    Log.d("AccessibilityOcrService", "Showing floating button during refresh (no package)")
                    floatingButtonHandler.createFloatingButton()
                }, 300)
            }
        }
    }

    override fun onInterrupt() {
        // Not needed for this implementation
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // Clear the static instance if it's this instance
        if (instance == this) {
            instance = null
        }

        // Unregister preference change listener
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        
        // Unregister broadcast receiver
        try {
            unregisterReceiver(showButtonReceiver)
            Log.d("AccessibilityOcrService", "Unregistered broadcast receiver")
        } catch (e: Exception) {
            Log.e("AccessibilityOcrService", "Error unregistering receiver", e)
        }
        
        // Cancel coroutine jobs
        job.cancel()

        if (isMenuVisible) {
            hideMenu()
        }

        // Remove floating button if it exists
        if (floatingButtonHandler.isVisible()) {
            floatingButtonHandler.hideFloatingButton()
        }
    }
    
    /**
     * Shows the floating button if it's not currently visible and updates the preference
     */
    fun showFloatingButton() {
        // Only show if not already visible
        if (!floatingButtonHandler.isVisible()) {
            Log.d("AccessibilityOcrService", "Showing floating button")
            floatingButtonHandler.createFloatingButton()
            
            // Update the preference
            prefs.edit().putBoolean(PreferenceKeys.FLOATING_BUTTON_VISIBLE, true).apply()
        }
    }
    
    /**
     * Set the ProfileViewModel for the service
     * This is needed because services don't have ViewModelProvider
     */
    fun setProfileViewModel(viewModel: ProfileViewModel) {
        this.profileViewModel = viewModel
        
        // Set the ProfileViewModel to the OcrHelper for language-aware operations
        if (::ocrHelper.isInitialized) {
            ocrHelper.setProfileViewModel(viewModel)
        }
    }
    
    /**
     * Load the current profile and apply its settings
     */
    private fun loadCurrentProfile() {
        launch {
            try {
                // Get current profile ID
                val currentProfileId = profileSettingsManager.getCurrentProfileId()
                
                if (currentProfileId != -1L) {
                    // Try to load profile by ID
                    val profile = withContext(Dispatchers.IO) {
                        profileDao.getProfileById(currentProfileId)
                    }
                    
                    if (profile != null) {
                        // Apply settings from profile
                        profileSettingsManager.loadProfileSettings(profile)
                        currentProfile = profile
                        Log.d("AccessibilityOcrService", "Loaded profile: ${profile.name}")
                        return@launch
                    }
                }
                
                // If no current profile found, load default
                val defaultProfile = withContext(Dispatchers.IO) {
                    profileDao.getDefaultProfile()
                }
                
                if (defaultProfile != null) {
                    // Apply settings from default profile
                    profileSettingsManager.loadProfileSettings(defaultProfile)
                    currentProfile = defaultProfile
                    Log.d("AccessibilityOcrService", "Loaded default profile: ${defaultProfile.name}")
                } else {
                    Log.w("AccessibilityOcrService", "No profiles found, using default settings")
                }
            } catch (e: Exception) {
                Log.e("AccessibilityOcrService", "Error loading profile", e)
            }
        }
    }
    
    /**
     * Handle preference changes
     */
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == null) return
        
        Log.d("AccessibilityOcrService", "Preference changed: $key")
        
        // If the current profile ID changed, reload profile
        if (key == ProfileSettingsManager.CURRENT_PROFILE_ID) {
            loadCurrentProfile()
        }
        
        // Handle floating button visibility preference or whitelist changes
        if (key == PreferenceKeys.FLOATING_BUTTON_VISIBLE || 
            key == PreferenceKeys.ENABLE_APP_WHITELIST || 
            key == PreferenceKeys.APP_WHITELIST) {
            
            Log.d("AccessibilityOcrService", "Button visibility or whitelist settings changed")
            refreshFloatingButtonVisibility()
        }
    }

    private fun createMenuLayout() {
        Log.d("AccessibilityOcrService", "Creating menu layout")
        menuLayout = FrameLayout(this)

        // Get themed context to apply correct theme colors
        val themedContext = ContextThemeWrapper(this, R.style.Theme_Tekisuto)
        val inflater = LayoutInflater.from(themedContext)
        inflater.inflate(R.layout.popup_menu, menuLayout)

        // Find and setup the OCR button
        val ocrButton = menuLayout.findViewById<Button>(R.id.btn_ocr)
        ocrButton?.setOnClickListener {
            Log.d("AccessibilityOcrService", "OCR button clicked")
            performOcr()
        }

        // Find and setup the close button
        val closeButton = menuLayout.findViewById<Button>(R.id.btn_close)
        closeButton?.setOnClickListener {
            Log.d("AccessibilityOcrService", "Close button clicked")
            hideMenu()
        }

        // Find and setup the exit Tekisuto button
        val exitButton = menuLayout.findViewById<Button>(R.id.btn_exit_tekisuto)
        exitButton?.setOnClickListener {
            Log.d("AccessibilityOcrService", "Exit Tekisuto button clicked")
            hideMenu()
            floatingButtonHandler.hideFloatingButton()
            
            // Save the state in shared preferences
            prefs.edit().putBoolean(PreferenceKeys.FLOATING_BUTTON_VISIBLE, false).apply()

            // Show a toast notification to let the user know the button has been hidden
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    this,
                    "Tekisuto floating button has been hidden",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        Log.d("AccessibilityOcrService", "Menu layout created successfully")
    }

    private fun showMenu() {
        Log.d("AccessibilityOcrService", "showMenu() called")
        if (!::menuLayout.isInitialized) {
            Log.d("AccessibilityOcrService", "Menu layout not initialized, creating it")
            createMenuLayout()
        }

        // Store floating button visibility state before showing menu
        val wasButtonVisible = floatingButtonHandler.isVisible()
        Log.d("AccessibilityOcrService", "Floating button visible before showing menu: $wasButtonVisible")

        // Create window parameters with appropriate theme styling
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        // Let's make sure we're using the right theme resources for dark/light mode
        val typedValue = TypedValue()
        val theme = getApplicationContext().getTheme()
        theme.resolveAttribute(android.R.attr.windowBackground, typedValue, true)

        try {
            windowManager.addView(menuLayout, params)
            isMenuVisible = true
            Log.d("AccessibilityOcrService", "Menu successfully shown")
            
            // Restore floating button if it disappeared during menu showing
            if (wasButtonVisible && !floatingButtonHandler.isVisible()) {
                Log.d("AccessibilityOcrService", "Restoring floating button after showing menu")
                mainHandler.postDelayed({
                    floatingButtonHandler.createFloatingButton()
                }, 100)
            }
        } catch (e: Exception) {
            Log.e("AccessibilityOcrService", "Error showing menu", e)
        }
    }

    private fun hideMenu() {
        Log.d("AccessibilityOcrService", "hideMenu() called")
        
        // Store floating button visibility state before hiding menu
        val wasButtonVisible = floatingButtonHandler.isVisible()
        Log.d("AccessibilityOcrService", "Floating button visible before hiding menu: $wasButtonVisible")
        
        if (isMenuVisible) {
            try {
                windowManager.removeView(menuLayout)
                Log.d("AccessibilityOcrService", "Menu successfully hidden")
            } catch (e: Exception) {
                Log.e("AccessibilityOcrService", "Error hiding menu", e)
            } finally {
                isMenuVisible = false
            }
            
            // Ensure floating button visibility is maintained after menu is hidden
            if (wasButtonVisible && !floatingButtonHandler.isVisible()) {
                Log.d("AccessibilityOcrService", "Restoring floating button after hiding menu")
                mainHandler.postDelayed({
                    floatingButtonHandler.createFloatingButton()
                }, 100)
            }
        } else {
            Log.d("AccessibilityOcrService", "Menu is not visible, nothing to hide")
        }
    }

    /**
     * Implementation of FloatingButtonCallback interface
     */
    override fun onSingleTap() {
        Log.d("AccessibilityOcrService", "Single tap detected")
        if (isMenuVisible) {
            hideMenu()
        } else {
            // Show the menu without hiding the floating button
            showMenu()
            
            // Ensure the button remains visible
            if (!floatingButtonHandler.isVisible()) {
                Log.d("AccessibilityOcrService", "Restoring floating button visibility while showing menu")
                floatingButtonHandler.createFloatingButton()
            }
        }
    }

    /**
     * Implementation of FloatingButtonCallback interface
     */
    override fun onDoubleTap() {
        Log.d("AccessibilityOcrService", "Double tap detected")
        performOcr()
    }

    /**
     * Perform OCR process with simplified screenshot handling
     */
    private fun performOcr() {
        Log.d("AccessibilityOcrService", "performOcr() called")
        // Hide the menu before taking the screenshot
        hideMenu()
        
        // Store the floating button visibility state - also check the preference
        val wasButtonVisible = floatingButtonHandler.isVisible()
        // Check if button should be visible generally, not just temporarily
        val shouldRestoreButton = wasButtonVisible || 
                                 (shouldShowFloatingButton(currentPackageName) && 
                                  prefs.getBoolean(PreferenceKeys.FLOATING_BUTTON_VISIBLE, true))
        
        Log.d("AccessibilityOcrService", "Button state before OCR: visible=$wasButtonVisible, shouldRestore=$shouldRestoreButton")
        
        // Temporarily hide the floating button before screenshot
        if (floatingButtonHandler.isVisible()) {
            Log.d("AccessibilityOcrService", "Temporarily hiding floating button for screenshot")
            floatingButtonHandler.hideFloatingButton()
        }

        // Small delay before taking screenshot
        mainHandler.postDelayed({
            Log.d("AccessibilityOcrService", "Taking screenshot after delay")
            screenshotHelper.takeScreenshot { bitmap ->
                // Process the screenshot result
                processScreenshotResult(bitmap, shouldRestoreButton)
            }
        }, 200)
    }
    
    /**
     * Process the screenshot result and launch image crop activity if successful
     */
    private fun processScreenshotResult(bitmap: Bitmap?, shouldRestoreButton: Boolean) {
        // Restore the floating button with some delay to ensure it happens
        // after any potential window transitions
        if (shouldRestoreButton) {
            mainHandler.postDelayed({
                Log.d("AccessibilityOcrService", "Restoring floating button after screenshot")
                if (!floatingButtonHandler.isVisible()) {
                    floatingButtonHandler.createFloatingButton()
                    Log.d("AccessibilityOcrService", "Button restored successfully")
                } else {
                    Log.d("AccessibilityOcrService", "Button already visible, no need to restore")
                }
            }, 300)
        }
        
        if (bitmap != null) {
            Log.d("AccessibilityOcrService", "Screenshot captured successfully")
            // Save screenshot temporarily
            val screenshotFile = saveScreenshotToFile(bitmap)
            screenshotFile?.let {
                Log.d("AccessibilityOcrService", "Screenshot saved to: ${it.absolutePath}")
                // Launch the image crop activity
                val intent = Intent(this, ImageCropActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("SCREENSHOT_PATH", it.absolutePath)
                    
                    // Pass current profile settings
                    val profileId = currentProfile?.id ?: -1L
                    putExtra("PROFILE_ID", profileId)
                    
                    // Pass flag to indicate if button should be restored
                    putExtra("RESTORE_FLOATING_BUTTON", shouldRestoreButton)
                }

                try {
                    Log.d("AccessibilityOcrService", "Starting ImageCropActivity")
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e("AccessibilityOcrService", "Error starting activity: ${e.message}")
                }
            } ?: run {
                Log.e("AccessibilityOcrService", "Failed to save screenshot to file")
            }
        } else {
            Log.e("AccessibilityOcrService", "Screenshot capture failed, bitmap is null")
        }
    }

    private fun saveScreenshotToFile(bitmap: Bitmap): File? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "Screenshot_$timeStamp.jpg"
        val storageDir = getExternalFilesDir(null)

        return try {
            val file = File(storageDir, fileName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            file
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
}