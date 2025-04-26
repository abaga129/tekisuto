package com.abaga129.tekisuto.util

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.preference.PreferenceManager
import com.abaga129.tekisuto.ocr.GoogleLensOcrService
import com.abaga129.tekisuto.ocr.OcrService
import com.abaga129.tekisuto.ocr.OcrServiceType
import com.abaga129.tekisuto.viewmodel.ProfileViewModel

private const val TAG = "OcrHelper"

/**
 * Helper class to handle OCR operations using selected OCR service
 */
class OcrHelper(private val context: Context) {
    
    private var ocrService: OcrService
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private var profileViewModel: ProfileViewModel? = null
    
    init {
        // Get the preferred OCR service from SharedPreferences
        val serviceType = prefs.getString("ocr_service", OcrServiceType.MLKIT) ?: OcrServiceType.MLKIT
        
        // Create the OCR service based on preference
        ocrService = OcrService.createService(context, serviceType)
        
        Log.d(TAG, "Initialized OCR helper with service: $serviceType")
    }
    
    /**
     * Recognize text from a bitmap image
     *
     * @param bitmap The bitmap image to analyze
     * @param callback Callback function with the recognized text
     */
    fun recognizeText(bitmap: Bitmap, callback: (String) -> Unit) {
        // Check if the service preference has changed
        checkAndUpdateServiceIfNeeded()
        
        // Use the OCR service to recognize text
        ocrService.recognizeText(bitmap, callback)
    }
    
    /**
     * Recognize text from a bitmap image
     *
     * @param bitmap The bitmap image to analyze
     * @param profileId Optional profile ID to use for specific recognition
     * @param callback Callback function with the recognized text
     */
    fun recognizeText(bitmap: Bitmap, profileId: Long = -1L, callback: (String) -> Unit) {
        // Check if the service preference has changed
        checkAndUpdateServiceIfNeeded()
        
        // Use the OCR service to recognize text with profile
        ocrService.recognizeText(bitmap, profileId, callback)
    }
    
    /**
     * Extract individual words from the text for dictionary matching
     * 
     * @param text The OCR text result
     * @return List of individual words
     */
    fun extractWords(text: String): List<String> {
        return ocrService.extractWords(text)
    }
    
    /**
     * Check if the OCR service preference has changed and update if needed
     */
    private fun checkAndUpdateServiceIfNeeded() {
        val currentServiceType = prefs.getString("ocr_service", OcrServiceType.MLKIT) ?: OcrServiceType.MLKIT
        
        // Check if service type has changed
        if (ocrService.javaClass.simpleName.lowercase() != getServiceClassName(currentServiceType)) {
            Log.d(TAG, "OCR service changed to: $currentServiceType, recreating service")
            
            // Clean up old service
            ocrService.cleanup()
            
            // Create new service
            ocrService = OcrService.createService(context, currentServiceType)
            
            // If the new service is GoogleLensOcrService and we have a ProfileViewModel, set it
            if (ocrService is GoogleLensOcrService && profileViewModel != null) {
                (ocrService as GoogleLensOcrService).setProfileViewModel(profileViewModel!!)
            }
        } else {
            // If the service is the same, just update its configuration
            ocrService.updateConfiguration()
        }
    }
    
    /**
     * Get the class name for the specified service type
     */
    private fun getServiceClassName(serviceType: String): String {
        return when (serviceType) {
            OcrServiceType.MLKIT -> "mlkitocrservice"
            // Removed Cloud OCR Service
            OcrServiceType.GOOGLE_LENS -> "googlelensocrservice"
            OcrServiceType.TESSERACT -> "tesseractocrservice"
            // Add more service types as they are implemented
            else -> "mlkitocrservice" // Default
        }
    }
    
    /**
     * Set the ProfileViewModel for profile-aware OCR services
     * 
     * @param viewModel The ProfileViewModel instance
     */
    fun setProfileViewModel(viewModel: ProfileViewModel) {
        this.profileViewModel = viewModel
        
        // If the current OCR service is GoogleLensOcrService, set the ViewModel
        if (ocrService is GoogleLensOcrService) {
            (ocrService as GoogleLensOcrService).setProfileViewModel(viewModel)
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        ocrService.cleanup()
    }
}