package com.abaga129.tekisuto.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.room.Room
import com.abaga129.tekisuto.database.AppDatabase
import com.abaga129.tekisuto.util.ProfileSettingsManager
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext

private const val TAG = "TesseractOcrService"

/**
 * Implementation of OcrService using Tesseract OCR
 */
class TesseractOcrService(private val context: Context) : OcrService, CoroutineScope {
    
    private val job = kotlinx.coroutines.SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val profileSettingsManager = ProfileSettingsManager(context)
    private var lastProfileId: Long = -1
    private var initialized = AtomicBoolean(false)
    private var tessBaseAPI: TessBaseAPI? = null
    
    init {
        // Get the preferred language from SharedPreferences (will be updated by profile settings)
        val language = prefs.getString("ocr_language", "eng") ?: "eng"
        
        // Initialize Tesseract in the background
        initializeTesseract(language)
        
        Log.d(TAG, "Initialized Tesseract OCR with language: $language")
        
        // Store current profile ID for change detection
        lastProfileId = profileSettingsManager.getCurrentProfileId()
    }
    
    /**
     * Preprocess the image for better OCR results
     */
    private fun preprocessImage(bitmap: Bitmap): Bitmap {
        // Get original dimensions
        val width = bitmap.width
        val height = bitmap.height
        
        // Check if the image is too large (>2000 pixels in any dimension)
        val maxDimension = 2000
        if (width > maxDimension || height > maxDimension) {
            // Calculate the new dimensions while maintaining aspect ratio
            val aspectRatio = width.toFloat() / height.toFloat()
            val newWidth: Int
            val newHeight: Int
            
            if (width > height) {
                newWidth = maxDimension
                newHeight = (newWidth / aspectRatio).toInt()
            } else {
                newHeight = maxDimension
                newWidth = (newHeight * aspectRatio).toInt()
            }
            
            // Create a scaled down version of the bitmap
            return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        }
        
        // If no preprocessing is needed, return the original bitmap
        return bitmap
    }
    
    /**
     * Download language data from the Tesseract GitHub repository
     * 
     * @param languageCode The Tesseract language code
     * @param outputFile The file to save the downloaded data to
     */
    private fun downloadLanguageData(languageCode: String, outputFile: File) {
        launch {
            try {
                withContext(Dispatchers.IO) {
                    // URL for Tesseract language data (using tessdata_fast for smaller files)
                    val url = URL("https://github.com/tesseract-ocr/tessdata_fast/raw/main/$languageCode.traineddata")
                    
                    val connection = url.openConnection() as HttpURLConnection
                    connection.connect()
                    
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                        Log.e(TAG, "Failed to download language data: HTTP ${connection.responseCode}")
                        return@withContext
                    }
                    
                    // Download the file
                    val inputStream: InputStream = connection.inputStream
                    val outputStream = FileOutputStream(outputFile)
                    
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                    
                    outputStream.close()
                    inputStream.close()
                    
                    Log.d(TAG, "Downloaded language data for $languageCode")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading language data", e)
            }
        }
    }
    
    /**
     * Initialize the Tesseract OCR engine
     * 
     * @param language The language to use for OCR
     */
    private fun initializeTesseract(language: String) {
        // Don't initialize if already done
        if (initialized.get()) {
            return
        }
        
        launch {
            try {
                withContext(Dispatchers.IO) {
                    // Create a tessdata directory in the app's private storage
                    val appDir = context.getExternalFilesDir(null) ?: context.filesDir
                    val tessdataDir = File(appDir, "tessdata")
                    if (!tessdataDir.exists()) {
                        tessdataDir.mkdirs()
                    }
                    
                    // Check if language data file exists
                    val languageCode = convertLanguageToTesseractCode(language)
                    val languageFile = File(tessdataDir, "$languageCode.traineddata")
                    
                    if (!languageFile.exists()) {
                        // Copy language data from assets
                        copyLanguageDataFromAssets(languageCode, tessdataDir)
                    }
                    
                    // Initialize Tesseract API
                    tessBaseAPI = TessBaseAPI()
                    val dataPath = appDir.absolutePath
                    val success = tessBaseAPI?.init(dataPath, languageCode)
                    
                    if (success == true) {
                        Log.d(TAG, "Tesseract initialized successfully with language: $languageCode")
                        initialized.set(true)
                    } else {
                        Log.e(TAG, "Failed to initialize Tesseract with language: $languageCode")
                        cleanupTesseractAPI()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing Tesseract", e)
                cleanupTesseractAPI()
            }
        }
    }
    
    /**
     * Copy language data from assets to external storage
     * 
     * @param languageCode The Tesseract language code
     * @param tessdataDir The directory to copy the data to
     */
    private fun copyLanguageDataFromAssets(languageCode: String, tessdataDir: File) {
        try {
            // First check if the language data exists in assets
            val assetManager = context.assets
            val assetFiles = assetManager.list("tessdata") ?: emptyArray()
            
            val trainedDataFilename = "$languageCode.traineddata"
            if (assetFiles.contains(trainedDataFilename)) {
                // Copy from assets
                val inputStream = assetManager.open("tessdata/$trainedDataFilename")
                val outputFile = File(tessdataDir, trainedDataFilename)
                
                val outputStream = FileOutputStream(outputFile)
                inputStream.copyTo(outputStream)
                
                inputStream.close()
                outputStream.close()
                
                Log.d(TAG, "Copied $trainedDataFilename from assets to $tessdataDir")
            } else {
                // Language data not included in the app
                Log.e(TAG, "Language data for $languageCode not found in assets")
                // Download the language data from GitHub
                val outputFile = File(tessdataDir, trainedDataFilename)
                downloadLanguageData(languageCode, outputFile)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error copying language data from assets", e)
        }
    }
    
    /**
     * Convert app language code to Tesseract language code
     * 
     * @param language The language code from the app
     * @return The corresponding Tesseract language code
     */
    private fun convertLanguageToTesseractCode(language: String): String {
        return when (language) {
            "english" -> "eng"
            "spanish" -> "spa"
            "french" -> "fra"
            "german" -> "deu"
            "italian" -> "ita"
            "portuguese" -> "por"
            "chinese" -> "chi_sim" // Simplified Chinese
            "devanagari" -> "hin" // Hindi (Devanagari script)
            "japanese" -> "jpn"
            "korean" -> "kor"
            else -> "eng" // Default to English
        }
    }
    
    /**
     * Recognize text from a bitmap image
     *
     * @param bitmap The bitmap image to analyze
     * @param callback Callback function with the recognized text
     */
    override fun recognizeText(bitmap: Bitmap, callback: (String) -> Unit) {
        // Check if the language preference has changed
        refreshTesseractIfNeeded()
        
        // Wait for initialization to complete
        if (!initialized.get()) {
            // If not initialized, try again after a delay
            Log.d(TAG, "Tesseract not initialized yet, waiting...")
            launch {
                withContext(Dispatchers.IO) {
                    // Wait for up to 3 seconds for initialization
                    var attempts = 0
                    while (!initialized.get() && attempts < 30) {
                        Thread.sleep(100)
                        attempts++
                    }
                    
                    if (initialized.get()) {
                        processImage(bitmap, callback)
                    } else {
                        Log.e(TAG, "Tesseract initialization timeout")
                        callback("Error: Tesseract initialization failed")
                    }
                }
            }
        } else {
            // Process the image
            processImage(bitmap, callback)
        }
    }
    
    /**
     * Recognize text from a bitmap image with a specific profile
     *
     * @param bitmap The bitmap image to analyze
     * @param profileId Optional profile ID to use for specific recognition
     * @param callback Callback function with the recognized text
     */
    override fun recognizeText(bitmap: Bitmap, profileId: Long, callback: (String) -> Unit) {
        // If a specific profile ID is provided and it's different from last used, update settings
        if (profileId != -1L && profileId != lastProfileId) {
            launch {
                refreshTesseractForProfile(profileId)
                recognizeText(bitmap, callback)
            }
        } else {
            // Check if the language preference has changed for current profile
            refreshTesseractIfNeeded()
            recognizeText(bitmap, callback)
        }
    }
    
    /**
     * Process the image with Tesseract OCR
     */
    private fun processImage(bitmap: Bitmap, callback: (String) -> Unit) {
        launch {
            try {
                val recognizedText = withContext(Dispatchers.IO) {
                    if (tessBaseAPI == null || !initialized.get()) {
                        return@withContext "Error: Tesseract not initialized"
                    }
                    
                    // Preprocess the image for better OCR results
                    val processedBitmap = preprocessImage(bitmap)
                    
                    // Set image
                    tessBaseAPI?.setImage(processedBitmap)
                    
                    // Set image is not binary (grayscale or color)
                    tessBaseAPI?.setVariable("tessedit_pageseg_mode", "3") // Fully automatic page segmentation
                    tessBaseAPI?.setVariable("tessedit_ocr_engine_mode", "3") // Default, based on what is available
                    
                    // Get OCR result
                    val text = tessBaseAPI?.utF8Text ?: ""
                    
                    // Clear previous image to free memory
                    tessBaseAPI?.clear()
                    
                    // Recycle the processed bitmap if it's different from the original
                    if (processedBitmap != bitmap) {
                        processedBitmap.recycle()
                    }
                    
                    return@withContext text
                }
                
                Log.d(TAG, "Text recognition successful with Tesseract")
                
                // Apply any post-processing to improve OCR results
                val processedText = postProcessRecognizedText(recognizedText)
                
                // Check if translation is needed
                val shouldTranslate = prefs.getBoolean("translate_ocr_text", false)
                if (shouldTranslate) {
                    // In a real implementation, you'd call a translation service here
                    // For this example, just log the fact that translation would be applied
                    val targetLanguage = prefs.getString("translate_target_language", "en") ?: "en"
                    Log.d(TAG, "Translation requested to language: $targetLanguage")
                    
                    // Here you would call a translation service, but for now just return the original text
                    callback(processedText)
                } else {
                    callback(processedText)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during Tesseract OCR processing", e)
                callback("Error recognizing text: ${e.message}")
            }
        }
    }
    
    /**
     * Refresh Tesseract for a specific profile
     */
    private suspend fun refreshTesseractForProfile(profileId: Long) {
        try {
            // Load profile from database
            val database = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "tekisuto_dictionary${com.abaga129.tekisuto.BuildConfig.DB_NAME_SUFFIX}.db"
            ).build()
            
            val profile = withContext(Dispatchers.IO) {
                database.profileDao().getProfileById(profileId)
            }
            
            if (profile != null) {
                // Apply profile settings
                profileSettingsManager.loadProfileSettings(profile)
                lastProfileId = profileId
                
                // Update Tesseract based on profile's language setting
                val language = prefs.getString("ocr_language", "english") ?: "english"
                
                // Reinitialize Tesseract with the new language
                reinitializeTesseract(language)
                
                Log.d(TAG, "Updated Tesseract OCR for profile: ${profile.name}, language: $language")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing Tesseract for profile", e)
        }
    }
    
    /**
     * Refresh Tesseract if the language preference has changed
     */
    private fun refreshTesseractIfNeeded() {
        val currentProfileId = profileSettingsManager.getCurrentProfileId()
        
        // Check if profile has changed
        if (currentProfileId != lastProfileId) {
            launch {
                refreshTesseractForProfile(currentProfileId)
            }
            return
        }
        
        // Check if language has changed
        val currentLanguage = prefs.getString("ocr_language", "english") ?: "english"
        
        // We need to check if the current language is different from what Tesseract is using
        if (tessBaseAPI != null && initialized.get()) {
            val currentTessLanguage = tessBaseAPI?.getInitLanguagesAsString()
            val desiredTessLanguage = convertLanguageToTesseractCode(currentLanguage)
            
            if (currentTessLanguage != desiredTessLanguage) {
                reinitializeTesseract(currentLanguage)
            }
        } else {
            initializeTesseract(currentLanguage)
        }
        
        Log.d(TAG, "Using Tesseract OCR with language: $currentLanguage")
    }
    
    /**
     * Reinitialize Tesseract with a new language
     */
    private fun reinitializeTesseract(language: String) {
        launch {
            cleanupTesseractAPI()
            initialized.set(false)
            initializeTesseract(language)
        }
    }
    
    /**
     * Apply post-processing to improve OCR results
     */
    private fun postProcessRecognizedText(text: String): String {
        var processedText = text
        
        // Replace common OCR errors
        processedText = processedText.replace(Regex("\\s+"), " ") // Replace multiple spaces with a single space
        
        // Additional post-processing can be added here
        
        return processedText.trim()
    }
    
    /**
     * Extract individual words from the text for dictionary matching
     * 
     * @param text The OCR text result
     * @return List of individual words
     */
    override fun extractWords(text: String): List<String> {
        return text.split(Regex("[\\s,.。、!?]+"))
            .filter { it.isNotEmpty() }
            .distinct()
    }
    
    /**
     * Update service configuration
     */
    override fun updateConfiguration() {
        refreshTesseractIfNeeded()
    }
    
    /**
     * Clean up resources
     */
    override fun cleanup() {
        cleanupTesseractAPI()
        job.cancel()
    }
    
    /**
     * Clean up Tesseract API resources
     */
    private fun cleanupTesseractAPI() {
        tessBaseAPI?.recycle()
        tessBaseAPI = null
    }
}