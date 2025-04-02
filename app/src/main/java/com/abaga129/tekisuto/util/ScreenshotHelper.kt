package com.abaga129.tekisuto.util

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
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

    /**
     * Take a screenshot using appropriate method based on API level
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
                            callback(bitmap)
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
                        callback(bitmap)
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

    private fun cleanup() {
        imageReader?.close()
        imageReader = null
        virtualDisplay?.release()
        virtualDisplay = null
    }
}