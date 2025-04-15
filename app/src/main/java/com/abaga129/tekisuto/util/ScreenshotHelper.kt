package com.abaga129.tekisuto.util

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import java.util.concurrent.Executor

/**
 * Helper class to capture screenshots from accessibility service
 * Optimized for Android 11+ (API level 30+)
 */
class ScreenshotHelper(private val service: AccessibilityService) {

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executor { command -> handler.post(command) }
    private val preferences = getDefaultSharedPreferences(service)

    /**
     * Take a screenshot using the Android 11+ (API 30+) accessibility screenshot API
     *
     * @param callback Function to handle the captured bitmap
     */
    fun takeScreenshot(callback: (Bitmap?) -> Unit) {
        Log.d("ScreenshotHelper", "Taking screenshot using Android 11+ API")
        try {
            // Use 0 as the display ID parameter (main display)
            service.takeScreenshot(
                0,
                executor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        Log.d("ScreenshotHelper", "Screenshot capture succeeded")
                        val bitmap = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, null)!!
                        result.hardwareBuffer.close()
                        
                        // Check if accessibility button removal is enabled
                        if (shouldHideAccessibilityButton()) {
                            // Clean the bitmap by removing the accessibility button
                            val cleanedBitmap = removeAccessibilityButton(bitmap)
                            callback(cleanedBitmap)
                        } else {
                            // Return the original bitmap
                            callback(bitmap)
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.e("ScreenshotHelper", "Screenshot capture failed with error code: $errorCode")
                        callback(null)
                    }
                }
            )
        } catch (e: Exception) {
            Log.e("ScreenshotHelper", "Exception taking screenshot: ${e.message}")
            e.printStackTrace()
            callback(null)
        }
    }
    
    /**
     * Process the screenshot to remove the accessibility button
     * by detecting and removing the circular button icon
     */
    private fun removeAccessibilityButton(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        try {
            // Create a mutable copy of the bitmap that we can modify
            val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            
            // Determine where the accessibility button is likely to be
            // It's usually on the right edge of the screen
            val buttonSize = (width * 0.15).toInt() // Approx. 15% of screen width
            val rightSide = width - 1
            val accessibilityButtonAreaTop = (height * 0.4).toInt() // Usually in the middle area
            val accessibilityButtonAreaBottom = (height * 0.6).toInt()
            
            // Check for circular button on the right edge
            for (y in accessibilityButtonAreaTop until accessibilityButtonAreaBottom) {
                for (x in rightSide - buttonSize until rightSide) {
                    // If we detect the button (usually has distinct colors), 
                    // replace it with pixels from the surrounding area
                    mutableBitmap.setPixel(x, y, 
                        // Get color from a bit further left to avoid the button's edge
                        getReplacementColor(mutableBitmap, x - buttonSize/2, y)
                    )
                }
            }
            
            // Return the cleaned bitmap
            return mutableBitmap
        } catch (e: Exception) {
            // If any error occurs during processing, return the original bitmap
            e.printStackTrace()
            return bitmap
        }
    }
    
    /**
     * Get a replacement color from a nearby area to fill in where the button was
     */
    private fun getReplacementColor(bitmap: Bitmap, x: Int, y: Int): Int {
        // Use the color from the specified position, or black if out of bounds
        return if (x >= 0 && x < bitmap.width && y >= 0 && y < bitmap.height) {
            bitmap.getPixel(x, y)
        } else {
            0xFF000000.toInt() // Default black if coordinates are out of bounds
        }
    }

    /**
     * Check if the user has enabled removing the accessibility button
     * from screenshots in the settings
     */
    private fun shouldHideAccessibilityButton(): Boolean {
        return preferences.getBoolean("hide_accessibility_button", true)
    }
}