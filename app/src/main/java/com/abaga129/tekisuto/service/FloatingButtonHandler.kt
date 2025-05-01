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
    
    // To track and prevent concurrent operations
    private var isOperationInProgress = false

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
        // Load preference values
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
        statusBarInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val windowMetrics = windowManager.currentWindowMetrics
                val insets = windowMetrics.windowInsets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
                )
                
                navigationBarInset = insets.bottom
                Log.d("FloatingButtonHandler", "System insets from API: statusBar=${insets.top}, navigationBar=${insets.bottom}")
                insets.top
            } catch (e: Exception) {
                Log.e("FloatingButtonHandler", "Error getting system insets", e)
                getStatusBarHeightFromResources()
            }
        } else {
            getStatusBarHeightFromResources()
        }
        
        Log.d("FloatingButtonHandler", "Using status bar height: $statusBarInset")
    }
    
    /**
     * Fallback method to get status bar height from resources
     */
    private fun getStatusBarHeightFromResources(): Int {
        val resourceId = service.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            val height = service.resources.getDimensionPixelSize(resourceId)
            Log.d("FloatingButtonHandler", "Status bar height from resources: $height")
            height
        } else {
            // Default fallback if resource not found
            val height = (24 * service.resources.displayMetrics.density).toInt()
            Log.d("FloatingButtonHandler", "Using default status bar height: $height")
            height
        }
    }

    // Timeout for operations to prevent deadlocks
    private val OPERATION_TIMEOUT = 1000L

    /**
     * Create and show floating button with improved stability
     */
    fun createFloatingButton() {
        // Don't create if already visible
        if (isFloatingButtonVisible) {
            Log.d("FloatingButtonHandler", "Button already visible, skipping creation")
            return
        }
        
        // If operation in progress, wait a bit and check again to prevent race conditions
        if (isOperationInProgress) {
            Log.d("FloatingButtonHandler", "Operation in progress, scheduling retry after timeout")
            
            // Schedule a retry after the operation timeout
            mainHandler.postDelayed({
                if (!isFloatingButtonVisible && !isOperationInProgress) {
                    Log.d("FloatingButtonHandler", "Retrying button creation after timeout")
                    createFloatingButton()
                } else {
                    Log.d("FloatingButtonHandler", "Skipping retry - button is now visible or another operation started")
                }
            }, OPERATION_TIMEOUT)
            
            return
        }
        
        // Mark operation as in progress with timeout protection
        isOperationInProgress = true
        
        // Set a timeout to prevent permanent locks if something goes wrong
        mainHandler.postDelayed({
            if (isOperationInProgress) {
                Log.w("FloatingButtonHandler", "Operation timeout - resetting operation in progress flag")
                isOperationInProgress = false
            }
        }, OPERATION_TIMEOUT)
        
        Log.d("FloatingButtonHandler", "Creating floating button")
        
        try {
            // Clean up any existing view
            removeExistingView()
            
            // Create the view
            val inflater = LayoutInflater.from(service)
            val buttonView = inflater.inflate(R.layout.minimized_indicator, null)
            
            // Create layout parameters
            val params = createDefaultLayoutParams()
            
            // Position the button
            setupButtonPosition(params)
            
            // Add touch listener
            buttonView.setOnTouchListener(createDragTouchListener(params))
            
            // Add the view
            try {
                windowManager.addView(buttonView, params)
                floatingView = buttonView
                floatingParams = params
                isFloatingButtonVisible = true
                Log.d("FloatingButtonHandler", "Successfully added floating button at position: ${params.x}, ${params.y}")
            } catch (e: Exception) {
                Log.e("FloatingButtonHandler", "Error adding floating button", e)
                isFloatingButtonVisible = false
                floatingView = null
            }
        } catch (e: Exception) {
            Log.e("FloatingButtonHandler", "Error creating floating button", e)
            isFloatingButtonVisible = false
            floatingView = null
        } finally {
            isOperationInProgress = false
        }
    }

    /**
     * Helper method to remove any existing view
     */
    private fun removeExistingView() {
        if (floatingView != null) {
            try {
                windowManager.removeView(floatingView)
                floatingView = null
            } catch (e: Exception) {
                // Just log and continue
                Log.d("FloatingButtonHandler", "Clean up of old view: ${e.message}")
            }
        }
    }

    /**
     * Create default layout parameters for the button
     */
    private fun createDefaultLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    /**
     * Setup the button position with proper boundary checking
     */
    private fun setupButtonPosition(params: WindowManager.LayoutParams) {
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
        
        // Update screen dimensions in case they've changed
        updateScreenDimensions()
        
        // Keep within bounds
        params.x = keepPositionWithinBounds(savedX, 0, screenWidth - 50)
        params.y = keepPositionWithinBounds(savedY, statusBarInset, screenHeight - 50)
    }
    
    /**
     * Create a minimized indicator that can be used to bring back the floating button
     * This method is now just an alias for createFloatingButton for backward compatibility
     */
    fun createMinimizedIndicator() {
        Log.d("FloatingButtonHandler", "createMinimizedIndicator() called, using unified button creation")
        createFloatingButton()
    }
    
    /**
     * Hide the floating button with improved error handling and stability
     */
    fun hideFloatingButton() {
        // If not visible, nothing to do
        if (!isFloatingButtonVisible) {
            Log.d("FloatingButtonHandler", "Button already hidden, skipping hide request")
            return
        }
        
        // If operation in progress, wait a bit and retry
        if (isOperationInProgress) {
            Log.d("FloatingButtonHandler", "Operation in progress, scheduling hide retry after timeout")
            
            // Schedule a retry after the operation timeout
            mainHandler.postDelayed({
                if (isFloatingButtonVisible && !isOperationInProgress) {
                    Log.d("FloatingButtonHandler", "Retrying hide after timeout")
                    hideFloatingButton()
                } else {
                    Log.d("FloatingButtonHandler", "Skipping hide retry - button is now hidden or another operation started")
                }
            }, OPERATION_TIMEOUT)
            
            return
        }
        
        if (floatingView != null) {
            // Mark operation as in progress with timeout protection
            isOperationInProgress = true
            
            // Set a timeout to prevent permanent locks
            mainHandler.postDelayed({
                if (isOperationInProgress) {
                    Log.w("FloatingButtonHandler", "Hide operation timeout - resetting operation flag")
                    isOperationInProgress = false
                }
            }, OPERATION_TIMEOUT)
            
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
                // Always ensure we update these state flags
                isFloatingButtonVisible = false
                floatingView = null
                isOperationInProgress = false
            }
        } else {
            // If we have no view but flag says visible, correct the state
            if (isFloatingButtonVisible) {
                Log.w("FloatingButtonHandler", "Button marked as visible but no view exists, correcting state")
                isFloatingButtonVisible = false
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