package com.abaga129.tekisuto.service

import android.accessibilityservice.AccessibilityService
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
import com.abaga129.tekisuto.R
import com.abaga129.tekisuto.ui.ImageCropActivity
import com.abaga129.tekisuto.util.OcrHelper
import com.abaga129.tekisuto.util.ScreenshotHelper
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AccessibilityOcrService : AccessibilityService(), FloatingButtonHandler.FloatingButtonCallback {

    private lateinit var windowManager: WindowManager
    private lateinit var menuLayout: FrameLayout
    private var isMenuVisible = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var ocrHelper: OcrHelper
    private lateinit var screenshotHelper: ScreenshotHelper
    private lateinit var prefs: SharedPreferences
    
    // Floating button handler
    private lateinit var floatingButtonHandler: FloatingButtonHandler

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        ocrHelper = OcrHelper(this)
        screenshotHelper = ScreenshotHelper(this)
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        
        // Initialize floating button handler
        floatingButtonHandler = FloatingButtonHandler(this)
        floatingButtonHandler.setCallback(this)
    }

    override fun onServiceConnected() {
        Log.d("AccessibilityOcrService", "onServiceConnected - API level: ${Build.VERSION.SDK_INT}")

        // Create menu layout first to ensure it's initialized
        createMenuLayout()

        // Show floating button
        floatingButtonHandler.createFloatingButton()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Not needed for this implementation
    }

    override fun onInterrupt() {
        // Not needed for this implementation
    }

    override fun onDestroy() {
        super.onDestroy()

        if (isMenuVisible) {
            hideMenu()
        }

        // Remove floating button if it exists
        if (floatingButtonHandler.isVisible()) {
            floatingButtonHandler.hideFloatingButton()
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

            // This will effectively disable the service until the user enables it again
            disableSelf()

            // Show a toast notification to let the user know the service has been disabled
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    this,
                    "Tekisuto service has been stopped",
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