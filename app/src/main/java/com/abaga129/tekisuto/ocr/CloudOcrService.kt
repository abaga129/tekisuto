package com.abaga129.tekisuto.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log

private const val TAG = "CloudOcrService"

/**
 * Placeholder implementation for backwards compatibility.
 * This service is deprecated and will redirect to MLKit OCR service.
 */
class CloudOcrService(private val context: Context) : OcrService {
    
    // Use MLKit as the fallback service
    private val fallbackService = MLKitOcrService(context)
    
    init {
        Log.w(TAG, "Cloud OCR Service is deprecated and has been removed. Falling back to MLKit OCR.")
    }
    
    /**
     * Redirect to MLKit OCR service
     */
    override fun recognizeText(bitmap: Bitmap, callback: (String) -> Unit) {
        Log.w(TAG, "Using MLKit OCR instead of removed Cloud OCR")
        fallbackService.recognizeText(bitmap, callback)
    }
    
    /**
     * Redirect to MLKit OCR service
     */
    override fun recognizeText(bitmap: Bitmap, profileId: Long, callback: (String) -> Unit) {
        Log.w(TAG, "Using MLKit OCR instead of removed Cloud OCR")
        fallbackService.recognizeText(bitmap, profileId, callback)
    }
    
    /**
     * Redirect to MLKit OCR service
     */
    override fun extractWords(text: String): List<String> {
        return fallbackService.extractWords(text)
    }
    
    /**
     * Update configuration by updating the fallback service
     */
    override fun updateConfiguration() {
        fallbackService.updateConfiguration()
    }
    
    /**
     * Clean up resources
     */
    override fun cleanup() {
        fallbackService.cleanup()
    }
}