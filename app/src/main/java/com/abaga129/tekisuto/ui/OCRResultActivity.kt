package com.abaga129.tekisuto.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.io.File
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.abaga129.tekisuto.R
import com.abaga129.tekisuto.database.DictionaryRepository
import com.abaga129.tekisuto.ui.adapter.DictionaryMatchAdapter
import com.abaga129.tekisuto.ui.ocr.AnkiExportManager
import com.abaga129.tekisuto.ui.ocr.AudioManager
import com.abaga129.tekisuto.ui.ocr.DictionarySearchManager
import com.abaga129.tekisuto.ui.ocr.OCRTextProcessor
import com.abaga129.tekisuto.util.AnkiDroidHelper
import com.abaga129.tekisuto.util.ProfileSettingsManager
import com.abaga129.tekisuto.util.SpeechService
import com.abaga129.tekisuto.viewmodel.OCRResultViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.room.Room
import com.abaga129.tekisuto.BuildConfig
import com.abaga129.tekisuto.database.AppDatabase
import com.abaga129.tekisuto.service.AccessibilityOcrService

/**
 * Activity that displays OCR results and provides dictionary lookup functionality.
 * This activity has been refactored to use separate manager classes for different responsibilities.
 * The layout adapts to different screen sizes and orientations, with special handling for foldables.
 */
class OCRResultActivity : BaseEdgeToEdgeActivity(),
    DictionaryMatchAdapter.OnAnkiExportListener,
    DictionaryMatchAdapter.OnAudioPlayListener {

    // ViewModel
    private lateinit var viewModel: OCRResultViewModel
    
    // UI components
    private lateinit var screenshotImageView: ImageView
    private lateinit var textContainer: ViewGroup
    private lateinit var closeButton: Button
    private lateinit var dictionaryMatchesRecyclerView: RecyclerView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var noMatchesTextView: TextView
    private lateinit var selectionHintTextView: TextView
    private lateinit var dictionarySearchEditText: EditText
    private lateinit var dictionarySearchButton: Button
    
    // Service helpers
    private lateinit var ankiDroidHelper: AnkiDroidHelper
    private lateinit var speechService: SpeechService
    private lateinit var profileSettingsManager: ProfileSettingsManager
    private lateinit var dictionaryRepository: DictionaryRepository
    
    // Manager classes
    private lateinit var textProcessor: OCRTextProcessor
    private lateinit var audioManager: AudioManager
    private lateinit var ankiExportManager: AnkiExportManager
    private lateinit var dictionarySearchManager: DictionarySearchManager
    
    // Data
    private var fullOcrText: String = ""
    private var profileId: Long = -1L
    private var ocrLanguage: String? = null
    private var shouldRestoreFloatingButton: Boolean = false
    
    private val TAG: String = "OCRResultActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ocr_result)

        // Apply insets to the root view for edge-to-edge display
        applyInsetsToView(android.R.id.content)

        // Initialize service helpers
        initializeServices()
        
        // Initialize ViewModel
        viewModel = ViewModelProvider(this).get(OCRResultViewModel::class.java)
        viewModel.initDictionaryRepository(applicationContext)
        viewModel.initTranslationHelper(applicationContext)
        viewModel.initProfileSettingsManager(applicationContext)

        // Initialize views
        initializeViews()
        
        // Initialize manager classes
        initializeManagers()
        
        // Set up RecyclerView
        setupRecyclerView()

        // Get data from intent
        processIntentData()
        
        // Set up observers for ViewModel data
        setupObservers()
        
        // Set up button click listeners
        setupClickListeners()
        
        // Pre-initialize tokenizers for CJK languages
        preInitializeTokenizers()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop any playing audio
        audioManager.stopAudio()
        
        // Ensure floating button is restored when activity is destroyed
        if (shouldRestoreFloatingButton && !isFinishing) {
            restoreFloatingButton()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Handle configuration changes, particularly for foldables
        adjustLayoutForScreenSize()
    }
    
    /**
     * Initialize service helpers
     */
    private fun initializeServices() {
        ankiDroidHelper = AnkiDroidHelper(this)
        speechService = SpeechService(this)
        profileSettingsManager = ProfileSettingsManager(this)
        dictionaryRepository = DictionaryRepository.getInstance(this)
    }
    
    /**
     * Initialize views
     */
    private fun initializeViews() {
        screenshotImageView = findViewById(R.id.screenshot_image_view)
        textContainer = findViewById(R.id.text_container)
        closeButton = findViewById(R.id.close_button)
        dictionaryMatchesRecyclerView = findViewById(R.id.dictionary_matches_recycler)
        loadingIndicator = findViewById(R.id.loading_indicator)
        noMatchesTextView = findViewById(R.id.no_matches_text)
        selectionHintTextView = findViewById(R.id.selection_hint)
        dictionarySearchEditText = findViewById(R.id.dictionary_search_edit_text)
        dictionarySearchButton = findViewById(R.id.dictionary_search_button)
        
        // Adjust layout based on screen size and orientation
        adjustLayoutForScreenSize()
    }
    
    /**
     * Adjust layout elements based on screen size and orientation
     * This helps with foldable devices and different aspect ratios
     */
    private fun adjustLayoutForScreenSize() {
        // Get display metrics
        val displayMetrics = resources.displayMetrics
        val widthDp = displayMetrics.widthPixels / displayMetrics.density
        val heightDp = displayMetrics.heightPixels / displayMetrics.density
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        
        // Log screen dimensions for debugging
        Log.d(TAG, "Screen dimensions: ${widthDp}dp × ${heightDp}dp, isLandscape: $isLandscape")
        
        // Adjust image view size constraints for different screen sizes
        val params = screenshotImageView.layoutParams
        if (params != null) {
            // For narrow screens like foldables in portrait mode
            if (widthDp < 400 && !isLandscape) {
                Log.d(TAG, "Adjusting layout for narrow screen")
                // Limit the height of the image view to avoid taking too much space
                try {
                    val constraintParams = params as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                    constraintParams.matchConstraintMaxHeight = displayMetrics.density.toInt() * 180
                    constraintParams.dimensionRatio = "H,4:3" // Adjust aspect ratio for better fit
                    screenshotImageView.layoutParams = constraintParams
                } catch (e: Exception) {
                    Log.e(TAG, "Error adjusting image view constraints", e)
                }
            }
        }
    }
    
    /**
     * Initialize manager classes
     */
    private fun initializeManagers() {
        // Initialize text processor
        textProcessor = OCRTextProcessor(this)
        
        // Initialize audio manager
        audioManager = AudioManager(this, lifecycleScope, speechService)
        
        // Initialize Anki export manager
        ankiExportManager = AnkiExportManager(
            this,
            lifecycleScope,
            ankiDroidHelper,
            speechService,
            audioManager
        )
        
        // Initialize dictionary search manager
        dictionarySearchManager = DictionarySearchManager(
            this,
            viewModel,
            dictionarySearchEditText,
            this,
            dictionaryRepository,
            speechService,
            textProcessor
        )
        dictionarySearchManager.initialize()
        dictionarySearchManager.setLifecycleScope(lifecycleScope)
        dictionarySearchManager.setOnAnkiExportListener(this)
        dictionarySearchManager.setOnAudioPlayListener(this)
    }
    
    /**
     * Set up RecyclerView for dictionary matches
     */
    private fun setupRecyclerView() {
        dictionaryMatchesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@OCRResultActivity)
            adapter = dictionarySearchManager.getAdapter()
            addItemDecoration(
                com.abaga129.tekisuto.ui.adapter.DictionaryMatchItemDecoration(
                    resources.getDimensionPixelSize(R.dimen.dictionary_item_spacing)
                )
            )
        }
    }
    
    /**
     * Process intent data
     */
    private fun processIntentData() {
        val ocrText = intent.getStringExtra("OCR_TEXT") ?: ""
        val screenshotPath = intent.getStringExtra("SCREENSHOT_PATH")
        ocrLanguage = intent.getStringExtra("OCR_LANGUAGE")
        profileId = intent.getLongExtra("PROFILE_ID", -1L)
        
        // Log received data for debugging
        Log.d(TAG, "Received OCR text length: ${ocrText.length}")
        Log.d(TAG, "Received OCR text preview: ${ocrText.take(100)}")
        Log.d(TAG, "Received screenshot path: $screenshotPath")
        Log.d(TAG, "Screenshot path exists: ${screenshotPath?.let { File(it).exists() }}")
        
        // Get floating button restoration flag
        shouldRestoreFloatingButton = intent.getBooleanExtra("RESTORE_FLOATING_BUTTON", false)
        Log.d(TAG, "Should restore floating button: $shouldRestoreFloatingButton")

        // Log profile ID
        Log.d(TAG, "Using profile ID: $profileId")

        // Apply profile settings if a specific profile ID was provided
        if (profileId != -1L) {
            applyProfileSettings()
        }

        // Set data to ViewModel and adapter
        viewModel.setOcrText(ocrText, ocrLanguage)
        dictionarySearchManager.setOcrText(ocrText)

        if (screenshotPath != null) {
            viewModel.setScreenshotPath(screenshotPath)
            dictionarySearchManager.setScreenshotPath(screenshotPath)
        }
        
        // No longer performing automatic dictionary search on startup
        // Users can tap on words or use the search field to look up specific terms
    }
    
    /**
     * Apply profile settings based on profile ID
     */
    private fun applyProfileSettings() {
        Log.d(TAG, "Applying settings from profile ID: $profileId")
        lifecycleScope.launch {
            try {
                // Load the profile in background to not block UI
                withContext(Dispatchers.IO) {
                    val database = Room.databaseBuilder(
                        applicationContext,
                        AppDatabase::class.java,
                        "tekisuto_dictionary${BuildConfig.DB_NAME_SUFFIX}.db"
                    ).build()

                    val profile = database.profileDao().getProfileById(profileId)
                    if (profile != null) {
                        // Apply profile settings on main thread
                        withContext(Dispatchers.Main) {
                            profileSettingsManager.loadProfileSettings(profile)
                            Log.d(TAG, "Applied settings from profile: ${profile.name}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading profile settings", e)
            }
        }
    }
    
    /**
     * Pre-initialize tokenizers for CJK languages
     */
    private fun preInitializeTokenizers() {
        viewModel.ocrText.value?.let { ocrText ->
            when {
                // For Japanese
                ocrLanguage == "japanese" || com.abaga129.tekisuto.util.JapaneseTokenizer.isLikelyJapanese(ocrText) -> {
                    Log.d(TAG, "Pre-initializing Japanese tokenizer")
                    lifecycleScope.launch(Dispatchers.IO) {
                        // Initialize the tokenizer in background
                        com.abaga129.tekisuto.util.JapaneseTokenizer.tokenize(ocrText)
                    }
                }
                // For Chinese
                ocrLanguage == "chinese" || com.abaga129.tekisuto.util.ChineseTokenizer.isLikelyChinese(ocrText) -> {
                    Log.d(TAG, "Pre-initializing Chinese tokenizer")
                    lifecycleScope.launch(Dispatchers.IO) {
                        // Initialize the tokenizer in background
                        com.abaga129.tekisuto.util.ChineseTokenizer.tokenize(ocrText)
                    }
                }
                // For all other languages, no special tokenizer initialization needed
                else -> {
                    Log.d(TAG, "No special tokenizer initialization needed for language: $ocrLanguage")
                }
            }
        }
    }
    
    /**
     * Set up observers for ViewModel data
     */
    private fun setupObservers() {
        // Observe OCR text
        viewModel.ocrText.observe(this) { text ->
            fullOcrText = text
            // Set up clickable text with our text processor
            textProcessor.setupClickableText(
                textContainer,
                text,
                ocrLanguage,
                { word -> dictionarySearchManager.searchTerm(word) },
                { word -> dictionarySearchManager.updateSearchField(word) }
            )
        }

        // Observe screenshot path
        viewModel.screenshotPath.observe(this) { path ->
            if (path.isNotEmpty()) {
                val bitmap = BitmapFactory.decodeFile(path)
                screenshotImageView.setImageBitmap(bitmap)
            }
        }

        // Observe dictionary matches for UI updates
        viewModel.dictionaryMatches.observe(this) { matches ->
            Log.d(TAG, "Dictionary matches updated: ${matches.size} matches found")
            noMatchesTextView.visibility = if (matches.isEmpty()) View.VISIBLE else View.GONE
            dictionaryMatchesRecyclerView.visibility = if (matches.isEmpty()) View.GONE else View.VISIBLE
            
            // Force a layout refresh
            dictionaryMatchesRecyclerView.adapter?.notifyDataSetChanged()
            
            // Update the search hint to indicate all matches are shown
            if (matches.isNotEmpty() && matches.size > 1) {
                val searchTerm = dictionarySearchEditText.text.toString().trim()
                if (searchTerm.isNotEmpty()) {
                    selectionHintTextView.text = getString(R.string.showing_all_matches, matches.size, searchTerm)
                    selectionHintTextView.visibility = View.VISIBLE
                }
            } else {
                selectionHintTextView.text = getString(R.string.tap_word_hint)
            }
            
            // Ensure appropriate minimum height based on matches
            val layoutParams = dictionaryMatchesRecyclerView.layoutParams
            if (matches.isNotEmpty()) {
                // Calculate height based on number of matches to show more items
                // Base height calculation: more entries = more height, with a maximum
                val matchCount = matches.size
                val heightPerItem = 150 // dp approximation for each dictionary entry
                val maxHeightDp = 500 // maximum height in dp
                val minHeightDp = 160 // minimum height in dp
                
                // Calculate the desired height in dp, then convert to pixels
                val desiredHeightDp = Math.min(Math.max(minHeightDp, matchCount * heightPerItem / 2), maxHeightDp)
                val desiredHeightPx = (desiredHeightDp * resources.displayMetrics.density).toInt()
                
                // Set the calculated height
                layoutParams.height = desiredHeightPx
                Log.d(TAG, "Setting RecyclerView height to ${desiredHeightPx}px for ${matchCount} matches")
            } else {
                // Default minimum height when no matches
                layoutParams.height = (80 * resources.displayMetrics.density).toInt()
            }
            dictionaryMatchesRecyclerView.layoutParams = layoutParams
        }

        // Observe searching state
        viewModel.isSearching.observe(this) { isSearching ->
            loadingIndicator.visibility = if (isSearching) View.VISIBLE else View.GONE
        }

        // Observe save operation result
        viewModel.saveResult.observe(this) { result ->
            Toast.makeText(this, result, Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Set up button click listeners
     */
    private fun setupClickListeners() {
        // Dictionary search button
        dictionarySearchButton.setOnClickListener {
            val searchTerm = dictionarySearchEditText.text.toString().trim()
            if (searchTerm.isNotEmpty()) {
                dictionarySearchManager.searchTerm(searchTerm)
                // Hide keyboard when search button is clicked
                hideKeyboard()
            }
        }

        // Setup dictionary search EditText action listener
        dictionarySearchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val searchTerm = dictionarySearchEditText.text.toString().trim()
                if (searchTerm.isNotEmpty()) {
                    dictionarySearchManager.searchTerm(searchTerm)
                    // Hide keyboard when search is initiated from keyboard
                    hideKeyboard()
                }
                true
            } else {
                false
            }
        }

        // Close button
        closeButton.setOnClickListener {
            // Ensure floating button is restored if needed
            if (shouldRestoreFloatingButton) {
                restoreFloatingButton()
            }
            // Use finishAndRemoveTask to fully close the activity and return to the previous app
            finishAndRemoveTask()
        }
    }

    // OnAudioPlayListener implementation
    override fun onPlayAudio(term: String, language: String) {
        audioManager.playAudio(term, language)
    }

    // OnAnkiExportListener implementation
    override fun onExportToAnki(entry: com.abaga129.tekisuto.database.DictionaryEntryEntity) {
        ankiExportManager.exportToAnki(
            entry,
            viewModel.ocrText.value,
            viewModel.screenshotPath.value,
            viewModel.translatedText.value,
            ocrLanguage
        )
    }
    
    /**
     * Helper method to restore the floating button via AccessibilityOcrService
     */
    private fun restoreFloatingButton() {
        try {
            val accessibilityService = com.abaga129.tekisuto.service.AccessibilityOcrService.getInstance()
            if (accessibilityService != null) {
                Log.d(TAG, "Requesting floating button restoration from service")
                accessibilityService.showFloatingButton()
            } else {
                Log.d(TAG, "Service instance not available, sending broadcast to show button")
                // Fallback: send broadcast to show floating button
                val intent = Intent(AccessibilityOcrService.ACTION_SHOW_FLOATING_BUTTON)
                sendBroadcast(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring floating button: ${e.message}")
        }
    }
    
    /**
     * Hide the keyboard
     */
    private fun hideKeyboard() {
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(dictionarySearchEditText.windowToken, 0)
    }
}