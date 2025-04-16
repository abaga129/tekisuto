package com.abaga129.tekisuto.service

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Resources
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.cardview.widget.CardView
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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class AccessibilityOcrService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private lateinit var menuLayout: FrameLayout
    private var isMenuVisible = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var ocrHelper: OcrHelper
    private lateinit var screenshotHelper: ScreenshotHelper
    private lateinit var prefs: SharedPreferences
    
    // Floating button variables
    private var floatingView: View? = null
    private var isFloatingButtonVisible = false
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var floatingParams: WindowManager.LayoutParams? = null
    private val CLICK_DRAG_TOLERANCE = 10f // Tolerance for considering a press as a click rather than a drag
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    private var isDragging = false
    private var lastActionTime: Long = 0
    private val CLICK_TIME_THRESHOLD = 200L // Time in ms to consider a touch as a click
    private var buttonSize: Int = 0 // To track the floating button size for boundary calculations
    
    // Long press detection variables
    private var longPressHandler = Handler(Looper.getMainLooper())
    private var isLongPress = false
    private var touchStartTime: Long = 0
    private var longPressRunnable: Runnable? = null
    private var isLongPressEnabled = true
    private var longPressDuration = 500L // Default, will be loaded from preferences

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        ocrHelper = OcrHelper(this)
        screenshotHelper = ScreenshotHelper(this)
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        
        // Load long press settings
        loadLongPressSettings()
        
        // Get screen dimensions for boundary checking
        updateScreenDimensions()
    }
    
    /**
     * Load long press settings from preferences
     */
    private fun loadLongPressSettings() {
        isLongPressEnabled = prefs.getBoolean("enable_long_press_capture", true)
        longPressDuration = prefs.getInt("long_press_duration", 500).toLong()
        Log.d("AccessibilityOcrService", "Long press settings loaded: enabled=$isLongPressEnabled, duration=$longPressDuration ms")
    }
    
    /**
     * Update screen dimensions to ensure proper boundary checking
     */
    private fun updateScreenDimensions() {
        val displayMetrics = Resources.getSystem().displayMetrics
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels
        Log.d("AccessibilityOcrService", "Screen dimensions: $screenWidth x $screenHeight")
    }
    
    /**
     * Create a simple indicator in case the main one fails
     */
    private fun createSimpleIndicator() {
        try {
            // Create a small logo indicator instead of just a dot
            val inflater = LayoutInflater.from(this)
            val indicatorView = inflater.inflate(R.layout.minimized_indicator, null)
            
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            
            params.gravity = Gravity.TOP or Gravity.START
            params.x = prefs.getInt("floating_button_x", 100)
            params.y = prefs.getInt("floating_button_y", 100)
            
            // Make the indicator draggable too
            indicatorView.setOnTouchListener(createDragTouchListener(params))
            
            indicatorView.setOnClickListener {
                try {
                    windowManager.removeView(indicatorView)
                    createFloatingButton()
                } catch (e: Exception) {
                    Log.e("AccessibilityOcrService", "Error removing simple indicator", e)
                }
            }
            
            try {
                windowManager.addView(indicatorView, params)
                Log.d("AccessibilityOcrService", "Successfully added simple indicator")
            } catch (e: Exception) {
                Log.e("AccessibilityOcrService", "Error adding simple indicator", e)
            }
        } catch (e: Exception) {
            Log.e("AccessibilityOcrService", "Error creating simple indicator", e)
            // Fallback to an ultra-simple indicator if everything else fails
            createUltraSimpleIndicator()
        }
    }
    
    /**
     * Creates an ultra-simple text-based indicator as a last resort
     */
    private fun createUltraSimpleIndicator() {
        try {
            // Create an ImageButton with the app icon instead of text
            val button = ImageButton(this)
            button.setImageResource(R.mipmap.ic_launcher_round)
            button.background = null // Remove background for a cleaner look
            button.setPadding(8, 8, 8, 8)
            
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            
            params.gravity = Gravity.TOP or Gravity.START
            val posX = keepPositionWithinBounds(prefs.getInt("floating_button_x", 100), 0, screenWidth - 50)
            val posY = keepPositionWithinBounds(prefs.getInt("floating_button_y", 100), 0, screenHeight - 50)
            params.x = posX
            params.y = posY
            
            // Make the button draggable
            button.setOnTouchListener(createDragTouchListener(params))
            
            button.setOnClickListener {
                try {
                    windowManager.removeView(button)
                    createFloatingButton()
                } catch (e: Exception) {
                    Log.e("AccessibilityOcrService", "Error removing ultra simple indicator", e)
                }
            }
            
            try {
                windowManager.addView(button, params)
                Log.d("AccessibilityOcrService", "Successfully added ultra simple indicator")
            } catch (e: Exception) {
                Log.e("AccessibilityOcrService", "Error adding ultra simple indicator", e)
            }
        } catch (e: Exception) {
            Log.e("AccessibilityOcrService", "Error creating ultra simple indicator", e)
        }
    }

    override fun onServiceConnected() {
        Log.d("AccessibilityOcrService", "onServiceConnected - API level: ${Build.VERSION.SDK_INT}")
        
        // Create menu layout first to ensure it's initialized
        createMenuLayout()
        
        // Make sure screen dimensions are up to date
        updateScreenDimensions()
        
        // Show floating button instead of using the accessibility button
        createFloatingButton()
    }

    /**
     * Create a draggable floating button for all API levels using the Tekisuto logo
     */
    private fun createFloatingButton() {
        if (!isFloatingButtonVisible) {
            Log.d("AccessibilityOcrService", "Creating Tekisuto logo floating button")
            try {
                val inflater = LayoutInflater.from(this)
                floatingView = inflater.inflate(R.layout.draggable_floating_button, null)
            
                // Set up the floating button layout parameters
                floatingParams = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                )
                
                // Retrieve saved position from preferences or use default
                val posX = prefs.getInt("floating_button_x", 100)
                val posY = prefs.getInt("floating_button_y", 100)
                
                // Make sure position is within screen bounds
                val validX = keepPositionWithinBounds(posX, 0, screenWidth - 100)
                val validY = keepPositionWithinBounds(posY, 0, screenHeight - 100)
                
                floatingParams?.gravity = Gravity.TOP or Gravity.START
                floatingParams?.x = validX
                floatingParams?.y = validY
                
                // Find the OCR button (main logo area) and set touch listener for click and long press
                val ocrButton = floatingView?.findViewById<ImageButton>(R.id.floating_ocr_button)
                ocrButton?.setOnTouchListener { view, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            // Start tracking for long press
                            touchStartTime = System.currentTimeMillis()
                            isLongPress = false
                            
                            // Load the latest settings in case they changed
                            loadLongPressSettings()
                            
                            // Create and post a runnable for long press detection
                            if (isLongPressEnabled) {
                                longPressRunnable = Runnable {
                                    Log.d("AccessibilityOcrService", "Long press detected")
                                    isLongPress = true
                                    performOcr() // Directly perform OCR without showing menu
                                }
                                longPressHandler.postDelayed(longPressRunnable!!, longPressDuration)
                            }
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            // If the user moved their finger more than the tolerance, cancel long press
                            val deltaX = Math.abs(event.x - initialTouchX)
                            val deltaY = Math.abs(event.y - initialTouchY)
                            if (deltaX > CLICK_DRAG_TOLERANCE || deltaY > CLICK_DRAG_TOLERANCE) {
                                longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                            }
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            // Remove the long press callback
                            longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                            
                            // If it's not a long press and the touch was short, treat as a click
                            if (!isLongPress && System.currentTimeMillis() - touchStartTime < CLICK_TIME_THRESHOLD) {
                                Log.d("AccessibilityOcrService", "Tekisuto logo button clicked")
                                if (isMenuVisible) {
                                    hideMenu()
                                } else {
                                    showMenu()
                                }
                                view.performClick()
                            }
                            true
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            // Just in case, remove the callback
                            longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                            true
                        }
                        else -> false
                    }
                }
                
                // Find the close button (X in the corner) and set click listener
                val closeButton = floatingView?.findViewById<ImageButton>(R.id.btn_close_floating)
                closeButton?.setOnClickListener {
                    Log.d("AccessibilityOcrService", "Close button clicked")
                    hideFloatingButton()
                }
                
                // Make the card view draggable
                val cardView = floatingView?.findViewById<CardView>(R.id.floating_button_card)
                cardView?.setOnTouchListener { view, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            // Record the time when touch started
                            lastActionTime = System.currentTimeMillis()
                            isDragging = false
                            
                            // Save initial position
                            initialX = floatingParams?.x ?: 0
                            initialY = floatingParams?.y ?: 0
                            
                            // Save touch point
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            
                            // Measure the button for boundary calculations 
                            // if we haven't already
                            if (buttonSize == 0 && cardView.width > 0) {
                                buttonSize = cardView.width
                                Log.d("AccessibilityOcrService", "Measured button size: $buttonSize")
                            }
                            
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            // Calculate movement
                            val dx = event.rawX - initialTouchX
                            val dy = event.rawY - initialTouchY
                            
                            // If we've moved enough, mark as dragging
                            if (!isDragging && (abs(dx) > CLICK_DRAG_TOLERANCE || abs(dy) > CLICK_DRAG_TOLERANCE)) {
                                isDragging = true
                            }
                            
                            if (isDragging) {
                                // Calculate new position with boundary checking
                                val newX = keepPositionWithinBounds(
                                    (initialX + dx).toInt(), 
                                    0, 
                                    screenWidth - (buttonSize.takeIf { it > 0 } ?: 100)
                                )
                                
                                val newY = keepPositionWithinBounds(
                                    (initialY + dy).toInt(), 
                                    0, 
                                    screenHeight - (buttonSize.takeIf { it > 0 } ?: 100)
                                )
                                
                                // Update position
                                floatingParams?.x = newX
                                floatingParams?.y = newY
                                
                                // Update the layout
                                if (floatingView != null && floatingParams != null) {
                                    windowManager.updateViewLayout(floatingView, floatingParams)
                                }
                            }
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            // Check if this was a quick tap
                            val isQuickTap = System.currentTimeMillis() - lastActionTime < CLICK_TIME_THRESHOLD
                            
                            // Check movement distance
                            val dx = abs(event.rawX - initialTouchX)
                            val dy = abs(event.rawY - initialTouchY)
                            val isSmallMovement = dx < CLICK_DRAG_TOLERANCE && dy < CLICK_DRAG_TOLERANCE
                            
                            if (isQuickTap && isSmallMovement) {
                                // It was a click, propagate to child views
                                view.performClick()
                            } else if (isDragging) {
                                // It was a drag, save the final position to preferences
                                val editor = prefs.edit()
                                editor.putInt("floating_button_x", floatingParams?.x ?: 0)
                                editor.putInt("floating_button_y", floatingParams?.y ?: 0)
                                editor.apply()
                                Log.d("AccessibilityOcrService", "Saved button position: ${floatingParams?.x}, ${floatingParams?.y}")
                            }
                            isDragging = false
                            true
                        }
                        else -> false
                    }
                }
                
                // Add the floating button to the window
                try {
                    windowManager.addView(floatingView, floatingParams)
                    isFloatingButtonVisible = true
                    Log.d("AccessibilityOcrService", "Successfully added Tekisuto logo floating button to window")
                    
                    // Measure the button size after adding to window
                    floatingView?.post {
                        val button = floatingView?.findViewById<CardView>(R.id.floating_button_card)
                        if (button != null) {
                            buttonSize = max(button.width, button.height)
                            Log.d("AccessibilityOcrService", "Updated button size: $buttonSize")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AccessibilityOcrService", "Error adding Tekisuto logo floating button", e)
                }
            } catch (e: Exception) {
                Log.e("AccessibilityOcrService", "Error creating Tekisuto logo floating button", e)
                // Try a simplified floating button as fallback
                createSimplifiedFloatingButton()
            }
        } else {
            Log.d("AccessibilityOcrService", "Floating button already visible")
        }
    }
    
    /**
     * Make sure a position value stays within screen bounds
     */
    private fun keepPositionWithinBounds(position: Int, minValue: Int, maxValue: Int): Int {
        return max(minValue, min(position, maxValue))
    }
    
    /**
     * Hide the floating button when not in use
     */
    private fun hideFloatingButton() {
        if (isFloatingButtonVisible && floatingView != null) {
            try {
                windowManager.removeView(floatingView)
                isFloatingButtonVisible = false
                Log.d("AccessibilityOcrService", "Successfully hidden floating button")
                
                // Create a small indicator that can be used to bring back the floating button
                createMinimizedIndicator()
            } catch (e: Exception) {
                Log.e("AccessibilityOcrService", "Error hiding floating button", e)
            }
        }
    }
    
    /**
     * Create a simplified floating button in case the main one fails
     */
    private fun createSimplifiedFloatingButton() {
        Log.d("AccessibilityOcrService", "Creating simplified Tekisuto logo button")
        try {
            // Try to use a simplified layout with the Tekisuto logo
            val inflater = LayoutInflater.from(this)
            val simplifiedView = inflater.inflate(R.layout.minimized_indicator, null)
            
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            
            params.gravity = Gravity.TOP or Gravity.START
            val posX = keepPositionWithinBounds(prefs.getInt("floating_button_x", 100), 0, screenWidth - 50)
            val posY = keepPositionWithinBounds(prefs.getInt("floating_button_y", 100), 0, screenHeight - 50)
            params.x = posX
            params.y = posY
            
            simplifiedView.setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // Start tracking for long press
                        touchStartTime = System.currentTimeMillis()
                        isLongPress = false
                        
                        // Load the latest settings in case they changed
                        loadLongPressSettings()
                        
                        // Create and post a runnable for long press detection
                        if (isLongPressEnabled) {
                            longPressRunnable = Runnable {
                                Log.d("AccessibilityOcrService", "Long press detected on simplified button")
                                isLongPress = true
                                performOcr() // Directly perform OCR without showing menu
                            }
                            longPressHandler.postDelayed(longPressRunnable!!, longPressDuration)
                        }
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        // If the user moved their finger more than the tolerance, cancel long press
                        val deltaX = Math.abs(event.x - initialTouchX)
                        val deltaY = Math.abs(event.y - initialTouchY)
                        if (deltaX > CLICK_DRAG_TOLERANCE || deltaY > CLICK_DRAG_TOLERANCE) {
                            longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        // Remove the long press callback
                        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                        
                        // If it's not a long press and the touch was short, treat as a click
                        if (!isLongPress && System.currentTimeMillis() - touchStartTime < CLICK_TIME_THRESHOLD) {
                            Log.d("AccessibilityOcrService", "Simplified button clicked")
                            if (isMenuVisible) {
                                hideMenu()
                            } else {
                                showMenu()
                            }
                            view.performClick()
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        // Just in case, remove the callback
                        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                        true
                    }
                    else -> false
                }
            }
            
            // Make the simplified view draggable too
            simplifiedView.setOnTouchListener(createDragTouchListener(params))
            
            try {
                windowManager.addView(simplifiedView, params)
                floatingView = simplifiedView
                floatingParams = params
                isFloatingButtonVisible = true
                Log.d("AccessibilityOcrService", "Successfully added simplified Tekisuto logo button")
            } catch (e: Exception) {
                Log.e("AccessibilityOcrService", "Error adding simplified Tekisuto logo button", e)
                createUltraSimplifiedButton()
            }
        } catch (e: Exception) {
            Log.e("AccessibilityOcrService", "Error creating simplified Tekisuto logo button", e)
            createUltraSimplifiedButton()
        }
    }
    
    /**
     * Create an ultra-simplified button as the last resort
     */
    private fun createUltraSimplifiedButton() {
        Log.d("AccessibilityOcrService", "Creating ultra-simplified button")
        try {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            
            params.gravity = Gravity.TOP or Gravity.START
            val posX = keepPositionWithinBounds(prefs.getInt("floating_button_x", 100), 0, screenWidth - 50)
            val posY = keepPositionWithinBounds(prefs.getInt("floating_button_y", 100), 0, screenHeight - 50)
            params.x = posX
            params.y = posY
            
            // Create an ImageButton with the app icon
            val button = ImageButton(this)
            button.setImageResource(R.mipmap.ic_launcher_round)
            button.background = null // Remove background for a cleaner look
            button.setPadding(8, 8, 8, 8)
            button.setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // Start tracking for long press
                        touchStartTime = System.currentTimeMillis()
                        isLongPress = false
                        
                        // Load the latest settings in case they changed
                        loadLongPressSettings()
                        
                        // Create and post a runnable for long press detection
                        if (isLongPressEnabled) {
                            longPressRunnable = Runnable {
                                Log.d("AccessibilityOcrService", "Long press detected on ultra-simplified button")
                                isLongPress = true
                                performOcr() // Directly perform OCR without showing menu
                            }
                            longPressHandler.postDelayed(longPressRunnable!!, longPressDuration)
                        }
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        // If the user moved their finger more than the tolerance, cancel long press
                        val deltaX = Math.abs(event.x - initialTouchX)
                        val deltaY = Math.abs(event.y - initialTouchY)
                        if (deltaX > CLICK_DRAG_TOLERANCE || deltaY > CLICK_DRAG_TOLERANCE) {
                            longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        // Remove the long press callback
                        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                        
                        // If it's not a long press and the touch was short, treat as a click
                        if (!isLongPress && System.currentTimeMillis() - touchStartTime < CLICK_TIME_THRESHOLD) {
                            Log.d("AccessibilityOcrService", "Ultra-simplified button clicked")
                            if (isMenuVisible) {
                                hideMenu()
                            } else {
                                showMenu()
                            }
                            view.performClick()
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        // Just in case, remove the callback
                        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                        true
                    }
                    else -> false
                }
            }
            
            // Make the button draggable
            button.setOnTouchListener(createDragTouchListener(params))
            
            try {
                windowManager.addView(button, params)
                floatingView = button
                floatingParams = params
                isFloatingButtonVisible = true
                Log.d("AccessibilityOcrService", "Successfully added ultra-simplified button")
            } catch (e: Exception) {
                Log.e("AccessibilityOcrService", "Error adding ultra-simplified button", e)
            }
        } catch (e: Exception) {
            Log.e("AccessibilityOcrService", "Error creating ultra-simplified button", e)
        }
    }
    
    /**
     * Creates a reusable touch listener for drag functionality
     */
    private fun createDragTouchListener(params: WindowManager.LayoutParams): View.OnTouchListener {
        return View.OnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Record the time when touch started
                    lastActionTime = System.currentTimeMillis()
                    touchStartTime = System.currentTimeMillis()
                    isDragging = false
                    isLongPress = false
                    
                    // Save initial position
                    initialX = params.x
                    initialY = params.y
                    
                    // Save touch point
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    
                    // Load the latest settings in case they changed
                    loadLongPressSettings()
                    
                    // Create and post a runnable for long press detection
                    if (isLongPressEnabled) {
                        longPressRunnable = Runnable {
                            Log.d("AccessibilityOcrService", "Long press detected on draggable area")
                            isLongPress = true
                            performOcr() // Directly perform OCR without showing menu
                        }
                        longPressHandler.postDelayed(longPressRunnable!!, longPressDuration)
                    }
                    
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // Calculate movement
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    
                    // If we've moved enough, mark as dragging and cancel long press
                    if (!isDragging && (abs(dx) > CLICK_DRAG_TOLERANCE || abs(dy) > CLICK_DRAG_TOLERANCE)) {
                        isDragging = true
                        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                    }
                    
                    if (isDragging) {
                        // Calculate new position with boundary checking
                        val newX = keepPositionWithinBounds(
                            (initialX + dx).toInt(), 
                            0, 
                            screenWidth - 50
                        )
                        
                        val newY = keepPositionWithinBounds(
                            (initialY + dy).toInt(), 
                            0, 
                            screenHeight - 50
                        )
                        
                        // Update position
                        params.x = newX
                        params.y = newY
                        
                        // Update the layout
                        windowManager.updateViewLayout(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // Remove any pending long press callbacks
                    longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                    
                    // Check if this was a quick tap
                    val isQuickTap = System.currentTimeMillis() - lastActionTime < CLICK_TIME_THRESHOLD
                    
                    // Check movement distance
                    val dx = abs(event.rawX - initialTouchX)
                    val dy = abs(event.rawY - initialTouchY)
                    val isSmallMovement = dx < CLICK_DRAG_TOLERANCE && dy < CLICK_DRAG_TOLERANCE
                    
                    if (isQuickTap && isSmallMovement && !isLongPress) {
                        // It was a click, propagate to child views
                        view.performClick()
                    } else if (isDragging) {
                        // It was a drag, save the final position to preferences
                        val editor = prefs.edit()
                        editor.putInt("floating_button_x", params.x)
                        editor.putInt("floating_button_y", params.y)
                        editor.apply()
                        Log.d("AccessibilityOcrService", "Saved button position: ${params.x}, ${params.y}")
                    }
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    // Just in case, remove the callback
                    longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                    isDragging = false
                    true
                }
                else -> false
            }
        }
    }
    
    /**
     * Create a small indicator that can be used to bring back the floating button
     */
    private fun createMinimizedIndicator() {
        try {
            val inflater = LayoutInflater.from(this)
            val indicatorView = inflater.inflate(R.layout.minimized_indicator, null)
        
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            
            // Use the same position as the floating button with boundary checking
            params.gravity = Gravity.TOP or Gravity.START
            val posX = keepPositionWithinBounds(prefs.getInt("floating_button_x", 100), 0, screenWidth - 50)
            val posY = keepPositionWithinBounds(prefs.getInt("floating_button_y", 100), 0, screenHeight - 50)
            params.x = posX
            params.y = posY
            
            // Set click listener to restore the floating button
            indicatorView.setOnClickListener {
                try {
                    windowManager.removeView(indicatorView)
                    createFloatingButton()
                } catch (e: Exception) {
                    Log.e("AccessibilityOcrService", "Error removing indicator", e)
                }
            }
            
            // Make the indicator draggable too
            indicatorView.setOnTouchListener(createDragTouchListener(params))
            
            try {
                windowManager.addView(indicatorView, params)
                Log.d("AccessibilityOcrService", "Successfully added minimized indicator")
            } catch (e: Exception) {
                Log.e("AccessibilityOcrService", "Error adding minimized indicator", e)
            }
        } catch (e: Exception) {
            Log.e("AccessibilityOcrService", "Error creating minimized indicator", e)
            // Create a simple indicator as fallback
            createSimpleIndicator()
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
        
        if (isMenuVisible) {
            hideMenu()
        }
        
        // Remove floating button if it exists
        if (isFloatingButtonVisible && floatingView != null) {
            try {
                windowManager.removeView(floatingView)
                isFloatingButtonVisible = false
            } catch (e: Exception) {
                Log.e("AccessibilityOcrService", "Error removing floating button", e)
            }
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
            // The hideMenu() call has been moved to inside performOcr()
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
            hideFloatingButton()
            
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