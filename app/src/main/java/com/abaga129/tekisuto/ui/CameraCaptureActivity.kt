package com.abaga129.tekisuto.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.ScaleGestureDetector
import android.view.MotionEvent
import android.widget.Toast
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.abaga129.tekisuto.R
import com.abaga129.tekisuto.service.AccessibilityOcrService
import com.abaga129.tekisuto.util.OcrHelper
import com.abaga129.tekisuto.util.ProfileSettingsManager
import com.abaga129.tekisuto.viewmodel.ProfileViewModel
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

class CameraCaptureActivity : BaseEdgeToEdgeActivity() {

    private lateinit var viewFinder: PreviewView
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var ocrHelper: OcrHelper
    private lateinit var profileViewModel: ProfileViewModel
    private var profileId: Long = -1L
    
    // Camera zoom variables
    private var camera: Camera? = null
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private lateinit var zoomLevelText: TextView

    companion object {
        private const val TAG = "CameraCaptureActivity"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_RESTORE_FLOATING_BUTTON = "restore_floating_button"
        
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    private var shouldRestoreFloatingButton: Boolean = false

    private val activityResultLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        var permissionGranted = true
        permissions.entries.forEach {
            if (it.key in REQUIRED_PERMISSIONS && !it.value)
                permissionGranted = false
        }
        if (!permissionGranted) {
            Toast.makeText(
                baseContext,
                "Camera permission is required for this feature.",
                Toast.LENGTH_SHORT
            ).show()
            finish()
        } else {
            startCamera()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_capture)
        
        // Get profile ID and floating button restore flag from intent
        profileId = intent.getLongExtra(EXTRA_PROFILE_ID, -1L)
        shouldRestoreFloatingButton = intent.getBooleanExtra(EXTRA_RESTORE_FLOATING_BUTTON, false)

        // Initialize ViewModels and helpers
        profileViewModel = ViewModelProvider(this)[ProfileViewModel::class.java]
        ocrHelper = OcrHelper(this)
        
        // Set profile to OCR helper if provided
        if (profileId != -1L) {
            ocrHelper.setProfileViewModel(profileViewModel)
        }

        viewFinder = findViewById(R.id.viewFinder)
        zoomLevelText = findViewById(R.id.zoom_level_text)
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Initialize zoom gesture detector
        scaleGestureDetector = ScaleGestureDetector(this, ScaleListener())
        
        // Set touch listener for zoom
        viewFinder.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            true
        }

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            activityResultLauncher.launch(REQUIRED_PERMISSIONS)
        }

        // Set up the capture button
        findViewById<androidx.appcompat.widget.AppCompatImageButton>(R.id.image_capture_button).setOnClickListener {
            takePhoto()
        }

        // Set up the close button
        findViewById<androidx.appcompat.widget.AppCompatImageButton>(R.id.close_button).setOnClickListener {
            finish()
        }

        // Set up zoom controls
        findViewById<androidx.appcompat.widget.AppCompatImageButton>(R.id.zoom_in_button).setOnClickListener {
            camera?.let { cam ->
                val currentZoom = cam.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                setZoom(currentZoom * 1.5f)
            }
        }

        findViewById<androidx.appcompat.widget.AppCompatImageButton>(R.id.zoom_out_button).setOnClickListener {
            camera?.let { cam ->
                val currentZoom = cam.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                setZoom(currentZoom / 1.5f)
            }
        }
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        // Create time stamped name and MediaStore entry
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        
        // Create a file in the app's external files directory (same as ImageCropActivity)
        val photoFile = File(getExternalFilesDir(null), "camera_ocr_$name.jpg")

        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputFileOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                    Toast.makeText(
                        baseContext,
                        "Photo capture failed: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Log.d(TAG, "Photo capture succeeded: ${photoFile.absolutePath}")
                    processImage(photoFile)
                }
            }
        )
    }

    private fun processImage(imageFile: File) {
        try {
            // Load bitmap from file
            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
            
            if (bitmap != null) {
                Log.d(TAG, "Bitmap loaded successfully, size: ${bitmap.width}x${bitmap.height}")
                // Perform OCR on the captured image with profile
                ocrHelper.recognizeText(bitmap, profileId) { recognizedText ->
                    Log.d(TAG, "OCR completed, text length: ${recognizedText.length}")
                    Log.d(TAG, "OCR result preview: ${recognizedText.take(100)}")
                    
                    // TEMPORARY DEBUG: Always use test text if OCR returns empty
                    val finalText = if (recognizedText.isNotEmpty()) {
                        recognizedText
                    } else {
                        "DEBUG: Camera captured image but no text detected. Image path: ${imageFile.absolutePath}"
                    }
                    
                    runOnUiThread {
                        Log.d(TAG, "Starting OCRResultActivity with text and image path: ${imageFile.absolutePath}")
                        // Start OCR Result Activity with the recognized text
                        val intent = Intent(this, OCRResultActivity::class.java).apply {
                            putExtra("OCR_TEXT", finalText)
                            putExtra("SCREENSHOT_PATH", imageFile.absolutePath)
                            putExtra("PROFILE_ID", profileId)
                            putExtra("RESTORE_FLOATING_BUTTON", shouldRestoreFloatingButton)
                        }
                        startActivity(intent)
                        finish()
                    }
                }
            } else {
                Log.e(TAG, "Failed to decode bitmap from file: ${imageFile.absolutePath}")
                Toast.makeText(this, "Failed to load captured image", Toast.LENGTH_SHORT).show()
                // Delete file if bitmap loading failed
                if (imageFile.exists()) {
                    imageFile.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image", e)
            Toast.makeText(this, "Error processing image: ${e.message}", Toast.LENGTH_SHORT).show()
            // Delete file if processing failed
            if (imageFile.exists()) {
                imageFile.delete()
            }
        }
        // Note: We don't delete the file here anymore if OCR succeeded,
        // as OCRResultActivity needs it for display
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setTargetRotation(viewFinder.display.rotation)
                .build()

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera and store camera reference
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )

                // Set initial zoom to 1x
                camera?.cameraInfo?.zoomState?.observe(this) { zoomState ->
                    // Update zoom level text
                    runOnUiThread {
                        zoomLevelText.text = String.format("%.1fx", zoomState.zoomRatio)
                    }
                }

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        
        // Restore floating button if needed
        if (shouldRestoreFloatingButton) {
            restoreFloatingButton()
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        // Restore floating button when user presses back
        if (shouldRestoreFloatingButton) {
            restoreFloatingButton()
        }
    }

    private fun restoreFloatingButton() {
        try {
            val serviceInstance = AccessibilityOcrService.getInstance()
            serviceInstance?.showFloatingButton()
            Log.d(TAG, "Floating button restored")
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring floating button", e)
        }
    }

    /**
     * Scale gesture detector for pinch-to-zoom
     */
    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            camera?.let { camera ->
                val currentZoomRatio = camera.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                val delta = detector.scaleFactor
                val newZoom = currentZoomRatio * delta
                
                // Apply zoom with limits
                camera.cameraControl.setZoomRatio(newZoom)
            }
            return true
        }
    }

    /**
     * Set zoom level programmatically with proper bounds checking
     */
    private fun setZoom(zoomRatio: Float) {
        camera?.let { camera ->
            val zoomState = camera.cameraInfo.zoomState.value
            if (zoomState != null) {
                val clampedRatio = max(zoomState.minZoomRatio, min(zoomRatio, zoomState.maxZoomRatio))
                camera.cameraControl.setZoomRatio(clampedRatio)
                Log.d(TAG, "Setting zoom to: $clampedRatio (requested: $zoomRatio, bounds: ${zoomState.minZoomRatio}-${zoomState.maxZoomRatio})")
            } else {
                // Fallback if zoom state is not available
                val clampedRatio = max(1f, min(zoomRatio, 10f)) // Conservative bounds
                camera.cameraControl.setZoomRatio(clampedRatio)
                Log.d(TAG, "Setting zoom to: $clampedRatio (fallback bounds)")
            }
        }
    }
}
