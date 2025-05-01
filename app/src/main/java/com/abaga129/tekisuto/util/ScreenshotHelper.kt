package com.abaga129.tekisuto.util

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.Executor

/**
 * Helper class to capture screenshots from accessibility service
 */
class ScreenshotHelper(private val service: AccessibilityService) {

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executor { command -> handler.post(command) }

    /**
     * Take a screenshot using the Android accessibility screenshot API
     *
     * @param callback Function to handle the captured bitmap
     */
    fun takeScreenshot(callback: (Bitmap?) -> Unit) {
        Log.d("ScreenshotHelper", "Taking screenshot")
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