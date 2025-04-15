package com.abaga129.tekisuto.service

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.FrameLayout
import androidx.core.graphics.drawable.toBitmap
import com.abaga129.tekisuto.R
import com.abaga129.tekisuto.ui.ImageCropActivity
import com.abaga129.tekisuto.ui.OCRResultActivity
import com.abaga129.tekisuto.util.OcrHelper
import com.abaga129.tekisuto.util.ScreenshotHelper
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AccessibilityOcrService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private lateinit var menuLayout: FrameLayout
    private var isMenuVisible = false
    private var buttonController: AccessibilityButtonController? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var ocrHelper: OcrHelper
    private lateinit var screenshotHelper: ScreenshotHelper
    
    // Floating action button for API 28 (pre-accessibility button availability)
    private var floatingActionButton: View? = null
    private var isFloatingButtonVisible = false
//    private var mAccessibilityButtonController: AccessibilityButtonController? = null
//    private var accessibilityButtonCallback:
//            AccessibilityButtonController.AccessibilityButtonCallback? = null
    private var isAccessibilityButtonAvailable: Boolean = false

    // Create an instance of the callback (only used on API 29+)
    private val accessibilityButtonCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        object : AccessibilityButtonController.AccessibilityButtonCallback() {
            override fun onClicked(controller: AccessibilityButtonController) {
                if (isMenuVisible) {
                    hideMenu()
                } else {
                    showMenu()
                }
            }

            override fun onAvailabilityChanged(controller: AccessibilityButtonController, available: Boolean) {
                // Optional: Handle availability changes
            }
        }
    } else null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        ocrHelper = OcrHelper(this)
        screenshotHelper = ScreenshotHelper(this)
    }

//    override fun onServiceConnected() {
//        super.onServiceConnected()
//
//        // Initialize the accessibility button
//        buttonController = accessibilityButtonController
//        buttonController.registerAccessibilityButtonCallback(accessibilityButtonCallback)
//
//        // Create the floating menu layout (but don't show it yet)
//        createMenuLayout()
//    }

    override fun onServiceConnected() {
        Log.d("AccessibilityOcrService", "onServiceConnected - API level: ${Build.VERSION.SDK_INT}")
        
        // Create menu layout first to ensure it's initialized
        createMenuLayout()
        
        // Request the accessibility button if available (API 30+)
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_ACCESSIBILITY_BUTTON
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // API 30+
            Log.d("AccessibilityOcrService", "Setting up for API 30+ (Android 11+)")
            // Get button controller and register callback on Android 11+
            buttonController = accessibilityButtonController
            accessibilityButtonCallback?.let { callback ->
                buttonController?.registerAccessibilityButtonCallback(callback)
                Log.d("AccessibilityOcrService", "Registered accessibility button callback for API 30+")
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // API 29
            Log.d("AccessibilityOcrService", "Setting up for API 29 (Android 10)")
            // On Android 10, we can use the button but in a slightly different way
            buttonController = accessibilityButtonController
            accessibilityButtonCallback?.let { callback ->
                buttonController?.registerAccessibilityButtonCallback(callback)
                Log.d("AccessibilityOcrService", "Registered accessibility button callback for API 29")
            }
        } else {
            // On API 28, we'll use a floating button instead
            Log.d("AccessibilityOcrService", "Running on API 28 or below, using floating button")
            createFloatingButton()
        }
    }
    
    /**
     * Create a floating button for API level 28 devices that don't support the accessibility button
     */
    private fun createFloatingButton() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && !isFloatingButtonVisible) {
            Log.d("AccessibilityOcrService", "Creating floating button for API 28")
            val inflater = LayoutInflater.from(this)
            floatingActionButton = inflater.inflate(R.layout.floating_button, null)
            
            // Set up the floating button layout parameters
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.BOTTOM or Gravity.END
            params.x = 100 // Increase this value to move button further from edge
            params.y = 100 // Increase this value to move button further from edge
            
            // Find the actual ImageButton and set click listener on it
            val imageButton = floatingActionButton?.findViewById<View>(R.id.floating_ocr_button)
            imageButton?.setOnClickListener {
                Log.d("AccessibilityOcrService", "Floating button clicked")
                if (isMenuVisible) {
                    Log.d("AccessibilityOcrService", "Hiding menu")
                    hideMenu()
                } else {
                    Log.d("AccessibilityOcrService", "Showing menu")
                    showMenu()
                }
            }
            
            // Also set click listener on container as fallback
            floatingActionButton?.setOnClickListener {
                Log.d("AccessibilityOcrService", "Floating button container clicked")
                if (isMenuVisible) {
                    hideMenu()
                } else {
                    showMenu()
                }
            }
            
            // Add the floating button to the window
            try {
                windowManager.addView(floatingActionButton, params)
                isFloatingButtonVisible = true
                Log.d("AccessibilityOcrService", "Successfully added floating button to window")
            } catch (e: Exception) {
                Log.e("AccessibilityOcrService", "Error adding floating button", e)
            }
        } else {
            Log.d("AccessibilityOcrService", "Skipping floating button creation - not on API 28 or button already visible")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Not needed for this implementation
    }

    override fun onInterrupt() {
        // Not needed for this implementation
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // Unregister callback if applicable (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && buttonController != null && accessibilityButtonCallback != null) {
            buttonController?.unregisterAccessibilityButtonCallback(accessibilityButtonCallback)
        }
        
        if (isMenuVisible) {
            hideMenu()
        }
        
        // Remove floating button if it exists
        if (isFloatingButtonVisible && floatingActionButton != null) {
            try {
                windowManager.removeView(floatingActionButton)
                isFloatingButtonVisible = false
            } catch (e: Exception) {
                Log.e("AccessibilityOcrService", "Error removing floating button", e)
            }
        }
    }

    private fun createMenuLayout() {
        Log.d("AccessibilityOcrService", "Creating menu layout")
        menuLayout = FrameLayout(this)
        val inflater = LayoutInflater.from(this)
        inflater.inflate(R.layout.popup_menu, menuLayout)

        // Find and setup the OCR button
        val ocrButton = menuLayout.findViewById<Button>(R.id.btn_ocr)
        ocrButton?.setOnClickListener {
            Log.d("AccessibilityOcrService", "OCR button clicked")
            performOcr()
            // The hideMenu() call has been moved to inside performOcr()
        }

        // Find and setup the close button
        val closeButton = menuLayout.findViewById<Button>(R.id.btn_close)
        closeButton?.setOnClickListener {
            Log.d("AccessibilityOcrService", "Close button clicked")
            hideMenu()
        }
        
        Log.d("AccessibilityOcrService", "Menu layout created successfully")
    }

    private fun showMenu() {
        Log.d("AccessibilityOcrService", "showMenu() called")
        if (!::menuLayout.isInitialized) {
            Log.d("AccessibilityOcrService", "Menu layout not initialized, creating it")
            createMenuLayout()
        }
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

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

    private fun performOcr() {
        Log.d("AccessibilityOcrService", "performOcr() called")
        // Hide the menu before taking the screenshot
        hideMenu()
        
        // Add a small delay to ensure the menu is fully hidden before taking the screenshot
        mainHandler.postDelayed({
            Log.d("AccessibilityOcrService", "Taking screenshot after delay")
            // Now take the screenshot
            screenshotHelper.takeScreenshot { bitmap ->
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
        }, 200) // 200ms delay should be enough for the menu to disappear from screen
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