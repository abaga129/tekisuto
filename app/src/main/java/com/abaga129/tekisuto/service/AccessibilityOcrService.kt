package com.abaga129.tekisuto.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import com.abaga129.tekisuto.util.ProfileSettingsManager
import com.abaga129.tekisuto.util.ScreenshotHelper
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

    private lateinit var windowManager: WindowManager
    private lateinit var menuLayout: FrameLayout
    private var isMenuVisible = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var ocrHelper: OcrHelper
    private lateinit var screenshotHelper: ScreenshotHelper
    private lateinit var prefs: SharedPreferences
    
    // Broadcast receiver for showing the floating button
    private val floatingButtonReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_SHOW_FLOATING_BUTTON) {
                Log.d("AccessibilityOcrService", "Received broadcast to show floating button")
                showFloatingButton()
            }
        }
    }
    
    // Profile handling
    private lateinit var profileSettingsManager: ProfileSettingsManager
    private lateinit var profileDao: ProfileDao
    private var currentProfile: ProfileEntity? = null
    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job
    
    // Floating button handler
    private lateinit var floatingButtonHandler: FloatingButtonHandler

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
        
        // No need to initialize wasButtonManuallyHidden since we've removed it
        
        // Register the broadcast receiver for showing the floating button
        val filter = IntentFilter(ACTION_SHOW_FLOATING_BUTTON)
        registerReceiver(floatingButtonReceiver, filter)
        Log.d("AccessibilityOcrService", "Registered broadcast receiver for showing floating button")
    }

    override fun onServiceConnected() {
        Log.d("AccessibilityOcrService", "onServiceConnected - API level: ${Build.VERSION.SDK_INT}")

        // Create menu layout first to ensure it's initialized
        createMenuLayout()

        // Check if the floating button should be visible
        val shouldShowButton = prefs.getBoolean(com.abaga129.tekisuto.util.PreferenceKeys.FLOATING_BUTTON_VISIBLE, true)
        
        if (shouldShowButton) {
            Log.d("AccessibilityOcrService", "Showing floating button (preference is true or not set)")
            floatingButtonHandler.createFloatingButton()
        } else {
            Log.d("AccessibilityOcrService", "Not showing floating button (preference is false)")
        }
    }

    // App whitelist manager
    private lateinit var appWhitelistManager: com.abaga129.tekisuto.util.AppWhitelistManager
    
    // Current package name
    private var currentPackageName: String = ""
    
    // Prevent processing duplicate events
    private var lastEventTime = 0L
    private var lastEventPackage = ""
    private val EVENT_DEBOUNCE_TIME = 300L // 300ms debounce for events
    
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
            
            // Debounce events for the same package
            if (packageName == lastEventPackage && 
                currentTime - lastEventTime < EVENT_DEBOUNCE_TIME) {
                Log.d("AccessibilityOcrService", "Ignoring duplicate event for $packageName (debounced)")
                return
            }
            
            // Update tracking variables
            lastEventTime = currentTime
            lastEventPackage = packageName
            
            // Only process if the package actually changed
            if (packageName != currentPackageName) {
                Log.d("AccessibilityOcrService", "App changed to: $packageName")
                
                // Update current package name
                currentPackageName = packageName
                
                // Check if the app is in the whitelist and update button visibility
                checkAppWhitelist(packageName)
            }
        }
    }
    
    // Track pending visibility operations
    private var pendingVisibilityRunnable: Runnable? = null

    /**
     * Check if the current app is in the whitelist and show the button if needed.
     * The button will appear when needed and will only be hidden when manually closed.
     */
    private fun checkAppWhitelist(packageName: String) {
        // Check if whitelist is enabled and if this app is in the whitelist
        val isWhitelistEnabled = appWhitelistManager.isWhitelistEnabled()
        val isAppWhitelisted = appWhitelistManager.isAppWhitelisted(packageName)
        
        Log.d("AccessibilityOcrService", "Whitelist enabled: $isWhitelistEnabled, " +
                "App whitelisted: $isAppWhitelisted, Package: $packageName")
        
        // Cancel any pending visibility changes
        pendingVisibilityRunnable?.let { mainHandler.removeCallbacks(it) }
        
        // Determine whether the button should be shown
        val shouldShowButton = if (isWhitelistEnabled) {
            // When whitelist is enabled, only show for whitelisted apps
            isAppWhitelisted
        } else {
            // When whitelist is disabled, use the general visibility preference
            prefs.getBoolean(com.abaga129.tekisuto.util.PreferenceKeys.FLOATING_BUTTON_VISIBLE, true)
        }
        
        // Only show the button if it's not already visible and should be shown
        if (shouldShowButton && !floatingButtonHandler.isVisible()) {
            pendingVisibilityRunnable = Runnable {
                if (isWhitelistEnabled && isAppWhitelisted) {
                    Log.d("AccessibilityOcrService", "Showing floating button for whitelisted app: $packageName")
                } else {
                    Log.d("AccessibilityOcrService", "Showing floating button for $packageName")
                }
                floatingButtonHandler.createFloatingButton()
            }
            
            // Add a slight delay to allow the app transition to complete
            mainHandler.postDelayed(pendingVisibilityRunnable!!, 300)
        }
    }
    
    /**
     * Refresh the floating button visibility based on current settings
     * This is called when the whitelist is disabled to ensure the button
     * shows correctly for all apps
     */
    fun refreshFloatingButtonVisibility() {
        // Cancel any pending visibility changes
        pendingVisibilityRunnable?.let { mainHandler.removeCallbacks(it) }
        
        if (currentPackageName.isNotEmpty()) {
            Log.d("AccessibilityOcrService", "Refreshing floating button visibility for $currentPackageName")
            
            // Check whitelist settings
            val isWhitelistEnabled = appWhitelistManager.isWhitelistEnabled()
            val isAppWhitelisted = appWhitelistManager.isAppWhitelisted(currentPackageName)
            
            // Determine whether the button should be shown
            val shouldShowButton = if (isWhitelistEnabled) {
                isAppWhitelisted
            } else {
                prefs.getBoolean(com.abaga129.tekisuto.util.PreferenceKeys.FLOATING_BUTTON_VISIBLE, true)
            }
            
            // Only show the button if needed
            if (shouldShowButton && !floatingButtonHandler.isVisible()) {
                mainHandler.postDelayed({
                    Log.d("AccessibilityOcrService", "Showing floating button during refresh for $currentPackageName")
                    floatingButtonHandler.createFloatingButton()
                }, 300)
            }
        } else {
            Log.d("AccessibilityOcrService", "No current package name, checking general visibility preference")
            
            // Determine if button should be visible based on general preference
            val shouldShowButton = prefs.getBoolean(com.abaga129.tekisuto.util.PreferenceKeys.FLOATING_BUTTON_VISIBLE, true)
            
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
        
        // Unregister the broadcast receiver
        try {
            unregisterReceiver(floatingButtonReceiver)
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
            prefs.edit().putBoolean(com.abaga129.tekisuto.util.PreferenceKeys.FLOATING_BUTTON_VISIBLE, true).apply()
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
        
        // Handle floating button visibility preference changes
        if (key == com.abaga129.tekisuto.util.PreferenceKeys.FLOATING_BUTTON_VISIBLE) {
            val shouldShowButton = sharedPreferences?.getBoolean(key, true) ?: true
            
            if (shouldShowButton) {
                // Preference changed to show the button
                Log.d("AccessibilityOcrService", "Preference changed to show floating button")
                
                if (!floatingButtonHandler.isVisible()) {
                    Log.d("AccessibilityOcrService", "Showing floating button due to preference change")
                    floatingButtonHandler.createFloatingButton()
                }
            } else {
                // Preference changed to hide the button
                Log.d("AccessibilityOcrService", "Preference changed to hide floating button")
                
                if (floatingButtonHandler.isVisible()) {
                    Log.d("AccessibilityOcrService", "Hiding floating button due to preference change")
                    floatingButtonHandler.hideFloatingButton()
                }
            }
        }
        
        // Handle app whitelist preference changes
        if (key == com.abaga129.tekisuto.util.PreferenceKeys.ENABLE_APP_WHITELIST || 
            key == com.abaga129.tekisuto.util.PreferenceKeys.APP_WHITELIST) {
            
            Log.d("AccessibilityOcrService", "App whitelist settings changed")
            
            // Re-check current app against whitelist
            if (currentPackageName.isNotEmpty()) {
                checkAppWhitelist(currentPackageName)
            }
        }
        
        // Other preference changes don't need special handling in the service
        // since they are directly accessed from SharedPreferences when needed
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
            prefs.edit().putBoolean(com.abaga129.tekisuto.util.PreferenceKeys.FLOATING_BUTTON_VISIBLE, false).apply()

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
        } catch (e: Exception) {
            Log.e("AccessibilityOcrService", "Error showing menu", e)
        }
    }

    private fun hideMenu() {
        Log.d("AccessibilityOcrService", "hideMenu() called")
        if (isMenuVisible) {
            try {
                windowManager.removeView(menuLayout)
                Log.d("AccessibilityOcrService", "Menu successfully hidden")
            } catch (e: Exception) {
                Log.e("AccessibilityOcrService", "Error hiding menu", e)
            } finally {
                isMenuVisible = false
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
            showMenu()
        }
    }

    /**
     * Implementation of FloatingButtonCallback interface
     */
    override fun onDoubleTap() {
        Log.d("AccessibilityOcrService", "Double tap detected")
        performOcr()
    }

    private fun performOcr() {
        Log.d("AccessibilityOcrService", "performOcr() called")
        // Hide the menu before taking the screenshot
        hideMenu()
        
        // Store the floating button visibility state
        val wasButtonVisible = floatingButtonHandler.isVisible()
        
        // Temporarily hide the floating button before screenshot
        if (wasButtonVisible) {
            Log.d("AccessibilityOcrService", "Temporarily hiding floating button for screenshot")
            floatingButtonHandler.hideFloatingButton()
        }

        // Add a small delay to ensure the UI elements are fully hidden before taking the screenshot
        mainHandler.postDelayed({
            Log.d("AccessibilityOcrService", "Taking screenshot after delay")
            // Now take the screenshot
            screenshotHelper.takeScreenshot { bitmap ->
                // Restore the floating button immediately after screenshot is taken
                if (wasButtonVisible) {
                    Log.d("AccessibilityOcrService", "Restoring floating button after screenshot")
                    floatingButtonHandler.createFloatingButton()
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
        }, 200) // 200ms delay should be enough for the UI elements to disappear from screen
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