package com.abaga129.tekisuto.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.preference.PreferenceManager
import com.abaga129.tekisuto.util.ProfileSettingsManager
import com.abaga129.tekisuto.viewmodel.ProfileViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern
import kotlin.coroutines.CoroutineContext

private const val TAG = "GoogleLensOcrService"

/**
 * Implementation of OcrService using Google Lens API
 * This service uses a workaround approach to access Google Lens OCR without requiring an API key
 */
class GoogleLensOcrService(private val context: Context) : OcrService, CoroutineScope {
    
    private val job = kotlinx.coroutines.SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val profileSettingsManager = ProfileSettingsManager(context)
    private var lastProfileId: Long = -1
    
    // ProfileViewModel reference - this will be null in most cases unless set externally
    private var profileViewModel: com.abaga129.tekisuto.viewmodel.ProfileViewModel? = null
    
    // Flag to track if the required libraries are available
    private var googleLensAvailable = false
    
    // Helper regex for parsing response headers
    private val locationParamRegex = Pattern.compile("(\\w+)=([^&]+)")
    
    init {
        // Check if the required dependencies are available
        try {
            // Check if org.json is available (standard in Android)
            Class.forName("org.json.JSONArray")
            googleLensAvailable = true
            
            Log.d(TAG, "Initialized Google Lens OCR service")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Google Lens OCR service", e)
            googleLensAvailable = false
        }
        
        // Store current profile ID for change detection
        lastProfileId = profileSettingsManager.getCurrentProfileId()
    }
    
    /**
     * Recognize text from a bitmap image
     *
     * @param bitmap The bitmap image to analyze
     * @param callback Callback function with the recognized text
     */
    override fun recognizeText(bitmap: Bitmap, callback: (String) -> Unit) {
        if (!googleLensAvailable) {
            callback("Error: Google Lens OCR service not available. Required dependencies missing.")
            return
        }
        
        // Process the image in a background thread
        launch {
            try {
                val result = processImageWithGoogleLens(bitmap)
                withContext(Dispatchers.Main) {
                    callback(result)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during text recognition", e)
                withContext(Dispatchers.Main) {
                    callback("Error recognizing text: ${e.message}")
                }
            }
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
            // Update the profile ID
            lastProfileId = profileId
            
            // If there is a profile switch, make sure we're using the latest settings
            val profileManager = ProfileSettingsManager(context)
            // This loads the profile settings into SharedPreferences
            val profile = profileViewModel?.currentProfile?.value
            if (profile != null) {
                profileManager.loadProfileSettings(profile)
            }
        }
        
        // Call the main recognizeText method
        recognizeText(bitmap, callback)
    }
    
    /**
     * Process the image with Google Lens
     * 
     * @param bitmap The bitmap image to analyze
     * @return The recognized text
     */
    private suspend fun processImageWithGoogleLens(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        try {
            // Convert bitmap to PNG byte array
            val imageBytes = preprocessImage(bitmap)
            
            // For debugging, save to temporary file
            val tempDir = context.cacheDir
            val tempFile = File(tempDir, "google_lens_temp.png")
            FileOutputStream(tempFile).use { it.write(imageBytes) }
            
            Log.d(TAG, "Saved temporary image for Google Lens: ${tempFile.absolutePath}")
            
            // Step 1: Upload the image to Google Lens
            val url = "https://lens.google.com/v3/upload"
            
            // Set up connection for upload
            val uploadConnection = URL(url).openConnection() as HttpURLConnection
            uploadConnection.requestMethod = "POST"
            uploadConnection.doOutput = true
            uploadConnection.setRequestProperty("Host", "lens.google.com")
            uploadConnection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0")
            uploadConnection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            // Set Accept-Language based on OCR language setting
uploadConnection.setRequestProperty("Accept-Language", getAcceptLanguageHeader())
            uploadConnection.setRequestProperty("Accept-Encoding", "gzip, deflate, br, zstd")
            uploadConnection.setRequestProperty("Referer", "https://www.google.com/")
            uploadConnection.setRequestProperty("Origin", "https://www.google.com")
            uploadConnection.setRequestProperty("Alt-Used", "lens.google.com")
            uploadConnection.setRequestProperty("Connection", "keep-alive")
            uploadConnection.setRequestProperty("Upgrade-Insecure-Requests", "1")
            uploadConnection.setRequestProperty("Sec-Fetch-Dest", "document")
            uploadConnection.setRequestProperty("Sec-Fetch-Mode", "navigate")
            uploadConnection.setRequestProperty("Sec-Fetch-Site", "same-site")
            uploadConnection.setRequestProperty("Priority", "u=0, i")
            uploadConnection.setRequestProperty("TE", "trailers")
            uploadConnection.setRequestProperty("Cookie", "SOCS=CAESEwgDEgk0ODE3Nzk3MjQaAmVuIAEaBgiA_LyaBg")
            
            // Create multipart form data
            val boundary = "----WebKitFormBoundary${System.currentTimeMillis()}"
            uploadConnection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            
            // Write the image data to the request
            uploadConnection.outputStream.use { os ->
                os.write("--$boundary\r\n".toByteArray())
                os.write("Content-Disposition: form-data; name=\"encoded_image\"; filename=\"image.png\"\r\n".toByteArray())
                os.write("Content-Type: image/png\r\n\r\n".toByteArray())
                os.write(imageBytes)
                os.write("\r\n--$boundary--\r\n".toByteArray())
            }
            
            // Check response code
            val uploadResponseCode = uploadConnection.responseCode
            if (uploadResponseCode != 303) {
                Log.e(TAG, "Error uploading image to Google Lens: Response code $uploadResponseCode")
                return@withContext "Error: Failed to upload image to Google Lens (Response: $uploadResponseCode)"
            }
            
            // Get redirect location with session IDs
            val location = uploadConnection.getHeaderField("Location")
            if (location == null) {
                Log.e(TAG, "No Location header in response")
                return@withContext "Error: No redirect location found in Google Lens response"
            }
            
            // Extract session parameters from redirect URL
            val locationParams = mutableMapOf<String, String>()
            val matcher = locationParamRegex.matcher(location)
            while (matcher.find()) {
                locationParams[matcher.group(1)] = matcher.group(2)
            }
            
            val vsrid = locationParams["vsrid"]
            val gsessionid = locationParams["gsessionid"]
            
            if (vsrid == null || gsessionid == null) {
                Log.e(TAG, "Required session parameters not found: vsrid=$vsrid, gsessionid=$gsessionid")
                return@withContext "Error: Missing required session parameters in Google Lens response"
            }
            
            // Step 2: Request metadata with session IDs
            val metadataUrl = "https://lens.google.com/qfmetadata?vsrid=$vsrid&gsessionid=$gsessionid"
            
            val metadataConnection = URL(metadataUrl).openConnection() as HttpURLConnection
            metadataConnection.requestMethod = "GET"
            metadataConnection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0")
            
            // Read metadata response
            val metadataResponse = metadataConnection.inputStream.bufferedReader().use { it.readText() }
            
            // The response format is unusual - we need the third line which contains the JSON data
            val responseLines = metadataResponse.lines()
            if (responseLines.size < 3) {
                Log.e(TAG, "Invalid metadata response format")
                return@withContext "Error: Invalid metadata response format from Google Lens"
            }
            
            // Parse the third line which contains the JSON data
            val jsonLine = responseLines[2]
            
            // Extract the text from the JSON data
            val result = parseTextFromGoogleLensJson(jsonLine)
            return@withContext result
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image with Google Lens", e)
            return@withContext "Error processing image with Google Lens: ${e.message}"
        }
    }
    
    /**
     * Parse the text from the Google Lens JSON response
     * Following the structure from the Python code
     */
    private fun parseTextFromGoogleLensJson(jsonText: String): String {
        try {
            val lensObject = JSONArray(jsonText)
            val resultBuilder = StringBuilder()
            
            try {
                // In the Python code, the text is in lens_object[0][2][0][0]
                if (lensObject.length() > 0) {
                    val firstLevel = lensObject.getJSONArray(0)
                    
                    if (firstLevel.length() > 2) {
                        val secondLevel = firstLevel.getJSONArray(2)
                        
                        if (secondLevel.length() > 0) {
                            val thirdLevel = secondLevel.getJSONArray(0)
                            
                            if (thirdLevel.length() > 0) {
                                val textBlocks = thirdLevel.getJSONArray(0)
                                
                                // Process text blocks
                                for (i in 0 until textBlocks.length()) {
                                    val block = textBlocks.getJSONArray(i)
                                    
                                    // Process each period block
                                    if (block.length() > 1) {
                                        val periodBlocks = block.getJSONArray(1)
                                        
                                        for (j in 0 until periodBlocks.length()) {
                                            val periodBlock = periodBlocks.getJSONArray(j)
                                            val periodBuilder = StringBuilder()
                                            
                                            // Process each sentence in the period
                                            if (periodBlock.length() > 0) {
                                                val sentenceBlocks = periodBlock.getJSONArray(0)
                                                
                                                for (k in 0 until sentenceBlocks.length()) {
                                                    val sentenceBlock = sentenceBlocks.getJSONArray(k)
                                                    
                                                    if (sentenceBlock.length() > 1) {
                                                        periodBuilder.append(sentenceBlock.getString(1))
                                                    }
                                                }
                                                
                                                if (periodBuilder.isNotEmpty()) {
                                                    resultBuilder.append(periodBuilder).append(" ")
                                                }
                                            }
                                        }
                                        
                                        // Add a newline after each text block
                                        resultBuilder.append("\n")
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: JSONException) {
                Log.e(TAG, "Error navigating JSON structure", e)
                
                // Fallback: Use a simplified approach to extract text
                // This tries to find all strings in the JSON that could be OCR results
                val textPattern = Pattern.compile("\"([\\p{L}\\p{N}\\p{P}\\s]{3,})\"")
                val matcher = textPattern.matcher(jsonText)
                
                // To avoid duplicates and unrelated content
                val addedTexts = mutableSetOf<String>()
                
                while (matcher.find()) {
                    val text = matcher.group(1)
                    
                    // Filter out likely non-content strings
                    if (!text.contains("http") && 
                        !text.contains("googleapis") && 
                        !text.contains("google.com") &&
                        !addedTexts.contains(text)) {
                        
                        resultBuilder.append(text).append("\n")
                        addedTexts.add(text)
                    }
                }
            }
            
            var result = resultBuilder.toString().trim()
            
            // If we couldn't extract text with any approach, return a meaningful error
            if (result.isEmpty()) {
                Log.e(TAG, "Could not extract text from Google Lens response")
                result = "Could not extract text from the image. Please try again with a clearer image."
            }
            
            return result
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Google Lens response", e)
            return "Error parsing text from Google Lens: ${e.message}"
        }
    }
    
    /**
     * Preprocess the image for Google Lens
     * Resizes and compresses the image if needed
     */
    private fun preprocessImage(bitmap: Bitmap): ByteArray {
        // Get the original dimensions
        val width = bitmap.width
        val height = bitmap.height
        
        // Maximum dimensions for Google Lens (to avoid excessive upload size)
        val maxDimension = 1600
        
        // Check if resizing is needed
        val targetBitmap = if (width > maxDimension || height > maxDimension) {
            // Calculate new dimensions
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
            
            // Resize the bitmap
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            // No resizing needed
            bitmap
        }
        
        // Convert to PNG format (Google Lens works best with PNG)
        val outputStream = ByteArrayOutputStream()
        targetBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        
        // If we created a new bitmap for resizing, recycle it
        if (targetBitmap != bitmap) {
            targetBitmap.recycle()
        }
        
        return outputStream.toByteArray()
    }
    
    /**
     * Extract individual words from the text for dictionary matching
     * 
     * @param text The OCR text result
     * @return List of individual words
     */
    override fun extractWords(text: String): List<String> {
        // A simple implementation that splits by common separators
        return text.split(Regex("[\\s,.。、!?]+"))
            .filter { it.isNotEmpty() }
            .distinct()
    }
    
    /**
     * Update service configuration if needed
     */
    override fun updateConfiguration() {
        // Google Lens service doesn't require configuration updates
    }
    
    /**
     * Set the ProfileViewModel instance for profile-aware operations
     * 
     * @param viewModel The ProfileViewModel instance
     */
    fun setProfileViewModel(viewModel: com.abaga129.tekisuto.viewmodel.ProfileViewModel) {
        this.profileViewModel = viewModel
    }
    
    /**
     * Get the appropriate Accept-Language header based on OCR language setting
     * 
     * @return Formatted Accept-Language header string
     */
    private fun getAcceptLanguageHeader(): String {
        // Get the current OCR language setting
        val ocrLanguage = prefs.getString(ProfileSettingsManager.OCR_LANGUAGE, "latin") ?: "latin"
        
        // Map OCR language to Accept-Language header value
        return when (ocrLanguage) {
            "japanese" -> "ja-JP;q=0.9,ja;q=0.8,en;q=0.5"
            "chinese" -> "zh-CN;q=0.9,zh;q=0.8,en;q=0.5"
            "korean" -> "ko-KR;q=0.9,ko;q=0.8,en;q=0.5"
            "devanagari" -> "hi-IN;q=0.9,hi;q=0.8,en;q=0.5"
            "latin" -> "en-US;q=0.9,en;q=0.8"
            else -> "en-US;q=0.9,en;q=0.8" // Default to English
        }
    }
    
    /**
     * Clean up resources
     */
    override fun cleanup() {
        job.cancel()
    }
}