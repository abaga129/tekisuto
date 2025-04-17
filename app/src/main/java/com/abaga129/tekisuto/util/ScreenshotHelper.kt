package com.abaga129.tekisuto.util

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.Executor

/**
 * Helper class to capture screenshots from accessibility service
 * Optimized for Android 11+ (API level 30+)
 */
class ScreenshotHelper(private val service: AccessibilityService) {

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executor { command -> handler.post(command) }

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
                        
                        // Return the bitmap directly since we now hide the floating button
                        // before taking screenshots
                        callback(bitmap)
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
}