package com.abaga129.tekisuto.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.abaga129.tekisuto.R
import com.abaga129.tekisuto.util.OcrHelper
import com.yalantis.ucrop.UCrop
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ImageCropActivity : AppCompatActivity() {

    private lateinit var ocrHelper: OcrHelper
    private var screenshotPath: String? = null
    private val TAG = "ImageCropActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_crop)

        Log.d(TAG, "ImageCropActivity onCreate called")
        ocrHelper = OcrHelper(this)

        // Get the screenshot path from the intent
        screenshotPath = intent.getStringExtra("SCREENSHOT_PATH")
        Log.d(TAG, "Screenshot path from intent: $screenshotPath")
        
        if (screenshotPath.isNullOrEmpty()) {
            Log.e(TAG, "No screenshot path provided")
            Toast.makeText(this, "Error: No screenshot provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Verify file exists
        val file = File(screenshotPath!!)
        if (!file.exists()) {
            Log.e(TAG, "Screenshot file does not exist at path: $screenshotPath")
            Toast.makeText(this, "Error: Cannot find screenshot file", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // File exists, log the size
        Log.d(TAG, "Screenshot file exists, size: ${file.length()} bytes")

        // Start the cropping activity
        startCrop()
    }

    private fun startCrop() {
        try {
            Log.d(TAG, "Starting crop process")
            val sourceFile = File(screenshotPath!!)
            val sourceUri = Uri.fromFile(sourceFile)
            val destinationUri = Uri.fromFile(File(cacheDir, "cropped_" + System.currentTimeMillis() + ".jpg"))

            Log.d(TAG, "Source URI: $sourceUri")
            Log.d(TAG, "Destination URI: $destinationUri")

            // Configure UCrop options
            val options = UCrop.Options().apply {
                setCompressionQuality(90)
                setHideBottomControls(false)
                setFreeStyleCropEnabled(true)
                setStatusBarColor(resources.getColor(R.color.purple_700, theme))
                setToolbarColor(resources.getColor(R.color.purple_500, theme))
                setToolbarTitle(getString(R.string.crop_image))
            }
            
            try {
                // Verify the image can be loaded before passing to UCrop
                val testBitmap = BitmapFactory.decodeFile(sourceFile.absolutePath)
                if (testBitmap == null) {
                    Log.e(TAG, "Could not decode bitmap from file")
                    throw IllegalStateException("Could not decode bitmap from file")
                }
                Log.d(TAG, "Successfully verified bitmap can be loaded from file")
                testBitmap.recycle() // Free the memory
            } catch (e: Exception) {
                Log.e(TAG, "Error verifying bitmap: ${e.message}")
                throw e // Rethrow to be caught by outer catch block
            }
            
            Log.d(TAG, "Starting UCrop activity")
            // Start UCrop activity
            UCrop.of(sourceUri, destinationUri)
                .withOptions(options)
                .start(this)
            Log.d(TAG, "UCrop.start() called")

        } catch (e: Exception) {
            Log.e(TAG, "Error starting crop: ${e.message}")
            e.printStackTrace()
            Toast.makeText(this, "Error: Could not crop image: ${e.message}", Toast.LENGTH_LONG).show()
            
            // As a fallback, try to process the original image without cropping
            try {
                Log.d(TAG, "Attempting to process original image without cropping")
                processOriginalImage()
            } catch (fallbackError: Exception) {
                Log.e(TAG, "Fallback also failed: ${fallbackError.message}")
                fallbackError.printStackTrace()
                finish()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == UCrop.REQUEST_CROP && resultCode == RESULT_OK) {
            val resultUri = UCrop.getOutput(data!!)
            if (resultUri != null) {
                processCroppedImage(resultUri)
            } else {
                Toast.makeText(this, "Error: Could not get cropped image", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else if (resultCode == UCrop.RESULT_ERROR) {
            val cropError = UCrop.getError(data!!)
            Log.e(TAG, "Crop error: ${cropError?.message}")
            Toast.makeText(this, "Error during cropping", Toast.LENGTH_SHORT).show()
            finish()
        } else if (resultCode == RESULT_CANCELED) {
            // User cancelled the crop, exit and return to previous app
            Log.d(TAG, "User cancelled crop operation, finishing activity")
            finishAndRemoveTask()
        }
    }

    private fun processCroppedImage(uri: Uri) {
        try {
            val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(uri))
            val croppedFile = saveScreenshotToFile(bitmap)
            if (croppedFile != null) {
                performOcrAndShowResults(bitmap, croppedFile)
            } else {
                Toast.makeText(this, "Error: Could not save cropped image", Toast.LENGTH_SHORT).show()
                finish()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing cropped image: ${e.message}")
            Toast.makeText(this, "Error processing cropped image", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun processOriginalImage() {
        try {
            val file = File(screenshotPath!!)
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            performOcrAndShowResults(bitmap, file)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing original image: ${e.message}")
            Toast.makeText(this, "Error processing original image", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun performOcrAndShowResults(bitmap: Bitmap, file: File) {
        ocrHelper.recognizeText(bitmap) { text ->
            runOnUiThread {
                // Get current OCR language from preferences
                val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
                val ocrLanguage = prefs.getString("ocr_language", "latin") ?: "latin"
                
                // Launch result activity with recognized text
                val intent = Intent(this, OCRResultActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("OCR_TEXT", text)
                    putExtra("SCREENSHOT_PATH", file.absolutePath)
                    putExtra("OCR_LANGUAGE", ocrLanguage)
                }
                startActivity(intent)
                finish() // Close this activity
            }
        }
    }

    private fun saveScreenshotToFile(bitmap: Bitmap): File? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "CroppedScreenshot_$timeStamp.jpg"
        val storageDir = getExternalFilesDir(null)

        return try {
            val file = File(storageDir, fileName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            file
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
}