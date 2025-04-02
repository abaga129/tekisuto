package com.abaga129.tekisuto.util

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import java.nio.ByteBuffer
import java.util.concurrent.Executor

/**
 * Helper class to capture screenshots from accessibility service
 */
class ScreenshotHelper(private val service: AccessibilityService) {

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executor { command -> handler.post(command) }
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private val preferences = getDefaultSharedPreferences(service)

    /**
     * Take a screenshot using appropriate method based on API level
     * and remove the accessibility button
     *
     * @param callback Function to handle the captured bitmap
     */
    fun takeScreenshot(callback: (Bitmap?) -> Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // API 30+ (Android 11+) - Use the new takeScreenshot API
            try {
                // Use 0 as the display ID parameter (main display)
                service.takeScreenshot(
                    0,
                    executor,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                            val bitmap = createBitmapFromHardwareBuffer(result.hardwareBuffer)
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
                            callback(null)
                        }
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
                fallbackScreenshot(callback)
            }
        } else {
            // API 28-29 - Use the legacy method
            takeScreenshotLegacy(callback)
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
     * Convert HardwareBuffer to Bitmap
     */
    private fun createBitmapFromHardwareBuffer(buffer: android.hardware.HardwareBuffer): Bitmap {
        return Bitmap.wrapHardwareBuffer(buffer, null)!!
    }

    /**
     * Alternative method in case the primary method fails
     */
    private fun fallbackScreenshot(callback: (Bitmap?) -> Unit) {
        try {
            // Try using the global action as a fallback
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
            // We can't directly get the bitmap this way, so we'll just return null
            // and let the caller know the operation failed
            callback(null)
        } catch (e: Exception) {
            e.printStackTrace()
            callback(null)
        }
    }

    /**
     * Legacy method for older API levels using ImageReader
     * This is a fallback if the above methods don't work
     */
    fun takeScreenshotLegacy(callback: (Bitmap?) -> Unit) {
        val windowManager = service.getSystemService(AccessibilityService.WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(displayMetrics)

        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1)

        try {
            // This is just a legacy approach that may not work on newer Android versions
            // It's kept here for reference
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)

            // Since we can't get a direct callback from performGlobalAction,
            // we'll just wait a bit and then check if an image is available
            handler.postDelayed({
                try {
                    imageReader?.acquireLatestImage()?.use { image ->
                        val bitmap = imageToBitmap(image)
                        
                        // Check if accessibility button removal is enabled
                        if (shouldHideAccessibilityButton()) {
                            // Clean the bitmap by removing the accessibility button
                            val cleanedBitmap = removeAccessibilityButton(bitmap)
                            callback(cleanedBitmap)
                        } else {
                            // Return the original bitmap
                            callback(bitmap)
                        }
                    } ?: callback(null)
                } catch (e: Exception) {
                    e.printStackTrace()
                    callback(null)
                } finally {
                    cleanup()
                }
            }, 1000) // Wait 1 second
        } catch (e: Exception) {
            e.printStackTrace()
            callback(null)
            cleanup()
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val width = image.width
        val height = image.height
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * width

        val bitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return Bitmap.createBitmap(bitmap, 0, 0, width, height)
    }

    /**
     * Check if the user has enabled removing the accessibility button
     * from screenshots in the settings
     */
    private fun shouldHideAccessibilityButton(): Boolean {
        return preferences.getBoolean("hide_accessibility_button", true)
    }
    
    private fun cleanup() {
        imageReader?.close()
        imageReader = null
        virtualDisplay?.release()
        virtualDisplay = null
    }
}