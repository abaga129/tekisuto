package com.abaga129.tekisuto.service

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowMetrics
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager
import com.abaga129.tekisuto.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Handles the creation and management of floating buttons for the Accessibility OCR Service.
 * This class encapsulates all floating button functionality including:
 * - Creating and showing the floating button
 * - Handling drag operations
 * - Managing button visibility
 * - Implementing double-tap detection
 */
class FloatingButtonHandler(private val service: AccessibilityOcrService) {

    interface FloatingButtonCallback {
        fun onSingleTap()
        fun onDoubleTap()
    }

    private val windowManager: WindowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(service)
    private val mainHandler = Handler(Looper.getMainLooper())

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
    
    // System insets variables
    private var statusBarInset: Int = 0
    private var navigationBarInset: Int = 0

    // Double tap detection variables
    private var doubleTapHandler = Handler(Looper.getMainLooper())
    private var lastTapTime: Long = 0
    private var doubleTapTimeoutRunnable: Runnable? = null
    private var isDoubleTapEnabled = true
    private var doubleTapTimeout = 300L // Default, will be loaded from preferences

    // Callback for button actions
    private var callback: FloatingButtonCallback? = null

    init {
        // Load settings and update screen dimensions
        loadDoubleTapSettings()
        updateScreenDimensions()
        updateSystemInsets()
    }

    /**
     * Set the callback for button tap actions
     */
    fun setCallback(callback: FloatingButtonCallback) {
        this.callback = callback
    }

    /**
     * Load double tap settings from preferences
     */
    private fun loadDoubleTapSettings() {
        // Reuse the existing preference keys initially, can be updated later
        isDoubleTapEnabled = prefs.getBoolean("enable_long_press_capture", true)
        doubleTapTimeout = prefs.getInt("long_press_duration", 300).toLong()
        Log.d("FloatingButtonHandler", "Double tap settings loaded: enabled=$isDoubleTapEnabled, timeout=$doubleTapTimeout ms")
    }

    /**
     * Update screen dimensions to ensure proper boundary checking
     */
    private fun updateScreenDimensions() {
        val displayMetrics = Resources.getSystem().displayMetrics
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels
        Log.d("FloatingButtonHandler", "Screen dimensions: $screenWidth x $screenHeight")
    }
    
    /**
     * Get and update system insets information
     */
    private fun updateSystemInsets() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val windowMetrics: WindowMetrics = windowManager.currentWindowMetrics
                val windowInsets = windowMetrics.windowInsets
                
                val insets = windowInsets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.statusBars() or
                    WindowInsets.Type.navigationBars()
                )
                
                statusBarInset = insets.top
                navigationBarInset = insets.bottom
                
                Log.d("FloatingButtonHandler", "System insets: statusBar=$statusBarInset, navigationBar=$navigationBarInset")
            } catch (e: Exception) {
                Log.e("FloatingButtonHandler", "Error getting system insets", e)
                // Fallback to resource method for status bar height
                getStatusBarHeightFromResources()
            }
        } else {
            // Fallback for older Android versions
            getStatusBarHeightFromResources()
        }
    }
    
    /**
     * Fallback method to get status bar height from resources
     */
    private fun getStatusBarHeightFromResources() {
        val resourceId = service.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            statusBarInset = service.resources.getDimensionPixelSize(resourceId)
            Log.d("FloatingButtonHandler", "Status bar height from resources: $statusBarInset")
        } else {
            // Default fallback if resource not found
            statusBarInset = (24 * service.resources.displayMetrics.density).toInt()
            Log.d("FloatingButtonHandler", "Using default status bar height: $statusBarInset")
        }
    }

    /**
     * Create the main floating button with improved robustness
     */
    fun createFloatingButton() {
        // Don't create if already visible or operation in progress
        if (isFloatingButtonVisible || isOperationInProgress) {
            Log.d("FloatingButtonHandler", "Button already visible or operation in progress, skipping creation")
            return
        }
        
        isOperationInProgress = true
        Log.d("FloatingButtonHandler", "Creating Tekisuto logo button")
        
        try {
            // First, make sure any existing view is removed to prevent WindowManager leaks
            if (floatingView != null) {
                try {
                    windowManager.removeView(floatingView)
                    floatingView = null
                } catch (e: Exception) {
                    // Ignore errors here, just making sure any old views are cleaned up
                    Log.d("FloatingButtonHandler", "Clean up of old view: ${e.message}")
                }
            }
            
            // Try to use a layout with the Tekisuto logo
            val inflater = LayoutInflater.from(service)
            val simplifiedView = inflater.inflate(R.layout.minimized_indicator, null)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )

            params.gravity = Gravity.TOP or Gravity.START
            
            // Get saved position with default fallback
            val savedX = prefs.getInt(com.abaga129.tekisuto.util.PreferenceKeys.FLOATING_BUTTON_X, 100)
            var savedY = prefs.getInt(com.abaga129.tekisuto.util.PreferenceKeys.FLOATING_BUTTON_Y, 100)
            
            // Ensure Y position respects the status bar inset 
            if (savedY < statusBarInset) {
                savedY = statusBarInset
                // Save the corrected position
                prefs.edit().putInt(com.abaga129.tekisuto.util.PreferenceKeys.FLOATING_BUTTON_Y, savedY).apply()
                Log.d("FloatingButtonHandler", "Adjusted Y position to respect status bar: $savedY")
            }
            
            // Update screen dimensions in case they've changed (rotation, etc.)
            updateScreenDimensions()
            
            val posX = keepPositionWithinBounds(savedX, 0, screenWidth - 50)
            val posY = keepPositionWithinBounds(savedY, statusBarInset, screenHeight - 50)
            
            params.x = posX
            params.y = posY

            // Make the button draggable
            simplifiedView.setOnTouchListener(createDragTouchListener(params))

            try {
                windowManager.addView(simplifiedView, params)
                floatingView = simplifiedView
                floatingParams = params
                isFloatingButtonVisible = true
                Log.d("FloatingButtonHandler", "Successfully added Tekisuto logo button at position: $posX, $posY")
            } catch (e: Exception) {
                Log.e("FloatingButtonHandler", "Error adding Tekisuto logo button", e)
                createFallbackIndicator()
            }
        } catch (e: Exception) {
            Log.e("FloatingButtonHandler", "Error creating Tekisuto logo button", e)
            createFallbackIndicator()
        } finally {
            isOperationInProgress = false
        }
    }

    /**
     * Create a fallback indicator in case the main one fails,
     * with improved robustness
     */
    private fun createFallbackIndicator() {
        try {
            // First ensure any existing view is removed
            if (floatingView != null) {
                try {
                    windowManager.removeView(floatingView)
                    floatingView = null
                } catch (e: Exception) {
                    // Just log and continue
                    Log.d("FloatingButtonHandler", "Error cleaning up old view: ${e.message}")
                }
            }
            
            // Create a small logo indicator as a fallback
            val inflater = LayoutInflater.from(service)
            val indicatorView = inflater.inflate(R.layout.minimized_indicator, null)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )

            params.gravity = Gravity.TOP or Gravity.START
            val savedX = prefs.getInt(com.abaga129.tekisuto.util.PreferenceKeys.FLOATING_BUTTON_X, 100)
            var savedY = prefs.getInt(com.abaga129.tekisuto.util.PreferenceKeys.FLOATING_BUTTON_Y, 100)
            
            // Ensure Y position respects the status bar inset
            if (savedY < statusBarInset) {
                savedY = statusBarInset
            }
            
            // Make sure position is within screen bounds
            val posX = keepPositionWithinBounds(savedX, 0, screenWidth - 50)
            val posY = keepPositionWithinBounds(savedY, statusBarInset, screenHeight - 50)
            
            params.x = posX
            params.y = posY

            // Make the indicator draggable
            indicatorView.setOnTouchListener(createDragTouchListener(params))

            try {
                windowManager.addView(indicatorView, params)
                floatingView = indicatorView
                floatingParams = params
                isFloatingButtonVisible = true
                Log.d("FloatingButtonHandler", "Successfully added fallback indicator")
            } catch (e: Exception) {
                Log.e("FloatingButtonHandler", "Error adding fallback indicator", e)
                // Reset the state since we failed
                isFloatingButtonVisible = false
                floatingView = null
            }
        } catch (e: Exception) {
            Log.e("FloatingButtonHandler", "Error creating fallback indicator", e)
            // Reset the state since we failed
            isFloatingButtonVisible = false
            floatingView = null
        }
    }

    /**
     * Create a minimized indicator that can be used to bring back the floating button
     */
    fun createMinimizedIndicator() {
        try {
            val inflater = LayoutInflater.from(service)
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
            val savedX = prefs.getInt("floating_button_x", 100)
            var savedY = prefs.getInt("floating_button_y", 100)
            
            // Ensure Y position respects the status bar inset
            if (savedY < statusBarInset) {
                savedY = statusBarInset
            }
            
            val posX = keepPositionWithinBounds(savedX, 0, screenWidth - 50)
            val posY = keepPositionWithinBounds(savedY, statusBarInset, screenHeight - 50)
            
            params.x = posX
            params.y = posY

            // Make the indicator draggable with double tap detection
            indicatorView.setOnTouchListener(createDragTouchListener(params))

            try {
                windowManager.addView(indicatorView, params)
                floatingView = indicatorView
                floatingParams = params
                isFloatingButtonVisible = true
                Log.d("FloatingButtonHandler", "Successfully added minimized indicator")
            } catch (e: Exception) {
                Log.e("FloatingButtonHandler", "Error adding minimized indicator", e)
            }
        } catch (e: Exception) {
            Log.e("FloatingButtonHandler", "Error creating minimized indicator", e)
            // Create a fallback indicator
            createFallbackIndicator()
        }
    }

    // To track and prevent concurrent operations
    private var isOperationInProgress = false
    
    /**
     * Hide the floating button with improved error handling
     */
    fun hideFloatingButton() {
        if (isOperationInProgress) {
            Log.d("FloatingButtonHandler", "Another operation in progress, skipping hide request")
            return
        }
        
        if (isFloatingButtonVisible && floatingView != null) {
            isOperationInProgress = true
            try {
                windowManager.removeView(floatingView)
                Log.d("FloatingButtonHandler", "Successfully hidden floating button")
            } catch (e: Exception) {
                Log.e("FloatingButtonHandler", "Error hiding floating button", e)
                // If the view is already detached or not found, consider it hidden
                if (e is IllegalArgumentException && e.message?.contains("not attached") == true) {
                    Log.d("FloatingButtonHandler", "View was already detached")
                }
            } finally {
                isFloatingButtonVisible = false
                floatingView = null
                isOperationInProgress = false
            }
        }
    }

    /**
     * Check if floating button is currently visible
     */
    fun isVisible(): Boolean {
        return isFloatingButtonVisible
    }

    /**
     * Make sure a position value stays within screen bounds
     */
    private fun keepPositionWithinBounds(position: Int, minValue: Int, maxValue: Int): Int {
        return max(minValue, min(position, maxValue))
    }

    /**
     * Creates a reusable touch listener for drag functionality with tap detection
     */
    private fun createDragTouchListener(params: WindowManager.LayoutParams): View.OnTouchListener {
        return View.OnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Record the time when touch started
                    lastActionTime = System.currentTimeMillis()
                    isDragging = false

                    // Save initial position
                    initialX = params.x
                    initialY = params.y

                    // Save touch point
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY

                    // Load the latest settings
                    loadDoubleTapSettings()

                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // Calculate movement
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY

                    // If we've moved enough, mark as dragging
                    if (!isDragging && (abs(dx) > CLICK_DRAG_TOLERANCE || abs(dy) > CLICK_DRAG_TOLERANCE)) {
                        isDragging = true
                        // Cancel any pending double tap detection
                        doubleTapTimeoutRunnable?.let { doubleTapHandler.removeCallbacks(it) }
                        lastTapTime = 0L
                    }

                    if (isDragging) {
                        // Calculate new position with insets-aware boundary checking
                        val newX = keepPositionWithinBounds(
                            (initialX + dx).toInt(),
                            0,
                            screenWidth - 50
                        )

                        val newY = keepPositionWithinBounds(
                            (initialY + dy).toInt(),
                            statusBarInset, // Respect status bar inset as minimum Y
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
                    // Check if this was a quick tap
                    val isQuickTap = System.currentTimeMillis() - lastActionTime < CLICK_TIME_THRESHOLD

                    // Check movement distance
                    val dx = abs(event.rawX - initialTouchX)
                    val dy = abs(event.rawY - initialTouchY)
                    val isSmallMovement = dx < CLICK_DRAG_TOLERANCE && dy < CLICK_DRAG_TOLERANCE

                    if (isQuickTap && isSmallMovement) {
                        // This might be part of a double tap, or just a single tap
                        val currentTime = System.currentTimeMillis()

                        if (lastTapTime > 0 && currentTime - lastTapTime < doubleTapTimeout) {
                            // This is a double tap!
                            Log.d("FloatingButtonHandler", "Double tap detected")
                            lastTapTime = 0L // Reset tap detection

                            // Cancel any pending timeout
                            doubleTapTimeoutRunnable?.let { doubleTapHandler.removeCallbacks(it) }

                            // Notify the callback of double tap
                            callback?.onDoubleTap()
                        } else {
                            // This is the first tap - wait for potential double tap
                            Log.d("FloatingButtonHandler", "Single tap detected, waiting for potential double tap")
                            lastTapTime = currentTime
                            
                            // Set the timeout for double tap detection
                            doubleTapTimeoutRunnable = Runnable {
                                // If we reach here, it was a single tap
                                Log.d("FloatingButtonHandler", "Single tap confirmed (timeout passed)")
                                lastTapTime = 0L
                                
                                // Notify the callback of single tap
                                callback?.onSingleTap()
                            }
                            
                            // Post the runnable with the double tap timeout
                            doubleTapHandler.postDelayed(doubleTapTimeoutRunnable!!, doubleTapTimeout)
                        }

                        view.performClick()
                    } else if (isDragging) {
                        // Ensure final position respects status bar inset
                        if (params.y < statusBarInset) {
                            params.y = statusBarInset
                            windowManager.updateViewLayout(view, params)
                        }
                        
                        // It was a drag, save the final position to preferences
                        val editor = prefs.edit()
                        editor.putInt(com.abaga129.tekisuto.util.PreferenceKeys.FLOATING_BUTTON_X, params.x)
                        editor.putInt(com.abaga129.tekisuto.util.PreferenceKeys.FLOATING_BUTTON_Y, params.y)
                        editor.apply()
                        Log.d("FloatingButtonHandler", "Saved button position: ${params.x}, ${params.y}")
                    }
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    // Just in case, remove any pending callbacks
                    doubleTapTimeoutRunnable?.let { doubleTapHandler.removeCallbacks(it) }
                    isDragging = false
                    true
                }
                else -> false
            }
        }
    }
}