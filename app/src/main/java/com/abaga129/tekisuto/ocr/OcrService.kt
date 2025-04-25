package com.abaga129.tekisuto.ocr

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineScope

/**
 * Interface for OCR services
 */
interface OcrService {
    /**
     * Recognize text from a bitmap image
     *
     * @param bitmap The bitmap image to analyze
     * @param callback Callback function with the recognized text
     */
    fun recognizeText(bitmap: Bitmap, callback: (String) -> Unit)
    
    /**
     * Recognize text from a bitmap image with a specific profile
     *
     * @param bitmap The bitmap image to analyze
     * @param profileId Optional profile ID to use for specific recognition
     * @param callback Callback function with the recognized text
     */
    fun recognizeText(bitmap: Bitmap, profileId: Long, callback: (String) -> Unit)
    
    /**
     * Extract individual words from the text for dictionary matching
     * 
     * @param text The OCR text result
     * @return List of individual words
     */
    fun extractWords(text: String): List<String>
    
    /**
     * Clean up resources
     */
    fun cleanup()
    
    /**
     * Update service configuration if needed
     */
    fun updateConfiguration()
    
    companion object {
        /**
         * Factory method to create an OCR service based on the selected provider
         *
         * @param context Application context
         * @param serviceType The type of OCR service to create
         * @return An implementation of OcrService
         */
        fun createService(context: Context, serviceType: String): OcrService {
            return when (serviceType) {
                OcrServiceType.MLKIT -> MLKitOcrService(context)
                OcrServiceType.CLOUD -> CloudOcrService(context)
                OcrServiceType.GOOGLE_LENS -> GoogleLensOcrService(context)
                // Add more service types here as they are implemented
                else -> MLKitOcrService(context) // Default fallback
            }
        }
    }
}

/**
 * OCR Service Type constants
 */
object OcrServiceType {
    const val MLKIT = "mlkit"
    const val CLOUD = "cloud"
    const val GOOGLE_LENS = "glens"
    // Add more service types as they are implemented
}