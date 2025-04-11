package com.abaga129.tekisuto.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.LinkMovementMethod
import android.util.Log
// Removed unused imports
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.abaga129.tekisuto.R
import com.abaga129.tekisuto.database.DictionaryEntryEntity
import com.abaga129.tekisuto.database.DictionaryRepository
import com.abaga129.tekisuto.ui.adapter.DictionaryMatchAdapter
import com.abaga129.tekisuto.ui.anki.AnkiDroidConfigActivity
import com.abaga129.tekisuto.ui.settings.SettingsActivity
import com.abaga129.tekisuto.util.AnkiDroidHelper
import com.abaga129.tekisuto.util.SpeechService
import com.abaga129.tekisuto.util.WordTokenizerFlow
import com.abaga129.tekisuto.viewmodel.OCRResultViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class OCRResultActivity : AppCompatActivity(), 
    DictionaryMatchAdapter.OnAnkiExportListener,
    DictionaryMatchAdapter.OnAudioPlayListener {

    private lateinit var viewModel: OCRResultViewModel
    private lateinit var screenshotImageView: ImageView
    private lateinit var textContainer: ViewGroup
    private var fullOcrText: String = ""
    private lateinit var copyButton: Button
    private lateinit var saveButton: Button
    private lateinit var closeButton: Button
    private lateinit var searchButton: Button
    private lateinit var dictionaryMatchesRecyclerView: RecyclerView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var noMatchesTextView: TextView
    private lateinit var selectionHintTextView: TextView
    private lateinit var dictionaryMatchAdapter: DictionaryMatchAdapter
    private lateinit var dictionarySearchEditText: EditText
    private lateinit var dictionarySearchButton: Button
    
    private lateinit var ankiDroidHelper: AnkiDroidHelper
    private lateinit var speechService: SpeechService
    
    // Audio file cache for exports
    private val audioCache = mutableMapOf<String, File>()
    
    // Dictionary repository for exported words tracking
    private lateinit var dictionaryRepository: DictionaryRepository
    
    // No longer using translation popup
    private var ocrLanguage: String? = null
    private var TAG: String = "OCRResultActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ocr_result)

        // Initialize AnkiDroid helper
        ankiDroidHelper = AnkiDroidHelper(this)
        
        // Initialize Speech Service
        speechService = SpeechService(this)
        
        // Initialize Dictionary Repository
        dictionaryRepository = DictionaryRepository.getInstance(this)

        // Initialize ViewModel
        viewModel = ViewModelProvider(this).get(OCRResultViewModel::class.java)
        viewModel.initDictionaryRepository(applicationContext)
        viewModel.initTranslationHelper(applicationContext)

        // Initialize views
        screenshotImageView = findViewById(R.id.screenshot_image_view)
        textContainer = findViewById(R.id.text_container)
        copyButton = findViewById(R.id.copy_button)
        saveButton = findViewById(R.id.save_button)
        closeButton = findViewById(R.id.close_button)
        searchButton = findViewById(R.id.search_button)
        dictionaryMatchesRecyclerView = findViewById(R.id.dictionary_matches_recycler)
        loadingIndicator = findViewById(R.id.loading_indicator)
        noMatchesTextView = findViewById(R.id.no_matches_text)
        selectionHintTextView = findViewById(R.id.selection_hint)
        dictionarySearchEditText = findViewById(R.id.dictionary_search_edit_text)
        dictionarySearchButton = findViewById(R.id.dictionary_search_button)

        // Set up RecyclerView
        dictionaryMatchAdapter = DictionaryMatchAdapter()
        dictionaryMatchAdapter.setLifecycleScope(lifecycleScope)
        dictionaryMatchAdapter.setOnAnkiExportListener(this)
        dictionaryMatchAdapter.setOnAudioPlayListener(this)
        dictionaryMatchAdapter.setSpeechService(speechService)
        dictionaryMatchAdapter.setDictionaryRepository(dictionaryRepository)
        
        // Set up a text watcher for the dictionary search EditText to convert to lowercase
        dictionarySearchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: android.text.Editable?) {
                s?.let { editable ->
                    val text = editable.toString()
                    // If text is not already lowercase, convert it
                    if (text != text.lowercase()) {
                        // Remove the text watcher temporarily to avoid infinite loop
                        dictionarySearchEditText.removeTextChangedListener(this)
                        // Update the text to lowercase
                        dictionarySearchEditText.setText(text.lowercase())
                        // Move cursor to the end
                        dictionarySearchEditText.setSelection(dictionarySearchEditText.text.length)
                        // Add the text watcher back
                        dictionarySearchEditText.addTextChangedListener(this)
                    }
                }
            }
        })
        
        dictionaryMatchesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@OCRResultActivity)
            adapter = dictionaryMatchAdapter
            addItemDecoration(
                com.abaga129.tekisuto.ui.adapter.DictionaryMatchItemDecoration(
                    resources.getDimensionPixelSize(R.dimen.dictionary_item_spacing)
                )
            )
        }

        // Get data from intent
        val ocrText = intent.getStringExtra("OCR_TEXT") ?: ""
        val screenshotPath = intent.getStringExtra("SCREENSHOT_PATH")
        ocrLanguage = intent.getStringExtra("OCR_LANGUAGE")
        
        // Pre-initialize tokenizers for CJK languages
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
        }

        // Set data to ViewModel and adapter
        viewModel.setOcrText(ocrText, ocrLanguage)
        dictionaryMatchAdapter.setOcrText(ocrText)
        
        if (screenshotPath != null) {
            viewModel.setScreenshotPath(screenshotPath)
            dictionaryMatchAdapter.setScreenshotPath(screenshotPath)
        }

        // Set up observers
        setupObservers()

        // Set up button click listeners
        setupClickListeners()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Stop any playing audio
        speechService.stopAudio()
    }

    private fun setupObservers() {
        // Observe OCR text
        viewModel.ocrText.observe(this) { text ->
            fullOcrText = text
            setupClickableText(text)
        }

        // Observe screenshot path
        viewModel.screenshotPath.observe(this) { path ->
            if (path.isNotEmpty()) {
                val bitmap = BitmapFactory.decodeFile(path)
                screenshotImageView.setImageBitmap(bitmap)
            }
        }

        // Observe dictionary matches
        viewModel.dictionaryMatches.observe(this) { matches ->
            // Log dictionary matches for debugging
            Log.d("OCRResultActivity", "Received ${matches.size} dictionary matches")
            matches.take(3).forEachIndexed { index, entry ->
                Log.d("OCRResultActivity", "Match $index: term=${entry.term}, " +
                        "definition=${entry.definition.take(50)}${if(entry.definition.length > 50) "..." else ""}")
            }
            
            // Clear all highlights first
            WordTokenizerFlow.clearHighlights()
            
            // Highlight matched words
            matches.forEach { entry ->
                WordTokenizerFlow.highlightWord(entry.term)
            }
            
            dictionaryMatchAdapter.submitList(matches)
            noMatchesTextView.visibility = if (matches.isEmpty()) View.VISIBLE else View.GONE
            dictionaryMatchesRecyclerView.visibility = if (matches.isEmpty()) View.GONE else View.VISIBLE
        }

        // Observe searching state
        viewModel.isSearching.observe(this) { isSearching ->
            loadingIndicator.visibility = if (isSearching) View.VISIBLE else View.GONE
            searchButton.isEnabled = !isSearching
        }

        // Observe save operation result
        viewModel.saveResult.observe(this) { result ->
            Toast.makeText(this, result, Toast.LENGTH_SHORT).show()
        }
        
        // Note: We're not using word translation popups anymore since tapping performs dictionary lookup
        // Keeping this commented out in case we want to restore it later
        /*
        viewModel.wordTranslation.observe(this) { (word, translation) ->
            showWordTranslationPopup(word, translation)
        }
        
        viewModel.isTranslatingWord.observe(this) { isTranslating ->
            // Could show a mini loading indicator if needed
        }
        */
    }
    
    /**
     * Set up the OCR text as clickable word buttons
     */
    private fun setupClickableText(text: String) {
        // Change hint text to indicate tappable words
        selectionHintTextView.text = getString(R.string.tap_word_hint)
        
        // Handle vertical CJK text if detected
        val processedText = when {
            // For vertical Japanese text
            ocrLanguage == "japanese" && isVerticalJapaneseText(text) -> {
                Log.d(TAG, "Detected vertical Japanese text - processing for display")
                processVerticalJapaneseText(text)
            }
            // For vertical Chinese text
            ocrLanguage == "chinese" && isVerticalChineseText(text) -> {
                Log.d(TAG, "Detected vertical Chinese text - processing for display")
                com.abaga129.tekisuto.util.ChineseTokenizer.processVerticalChineseText(text)
            }
            // Default: use original text
            else -> text
        }
        
        // Create flow layout with word buttons
        WordTokenizerFlow.createClickableWordsFlow(
            context = this,
            parentViewGroup = textContainer,
            text = processedText, // Use the processed text
            onWordClick = { word ->
                // Handle word click - lookup in dictionary
                viewModel.findDictionaryMatches(word)
                // Populate the search field with the clicked word (lowercase)
                dictionarySearchEditText.setText(word.lowercase())
                // Show a brief message
                Toast.makeText(this, "Looking up: $word", Toast.LENGTH_SHORT).show()
            },
            onWordLongClick = { word ->
                // Long click can still do dictionary lookup for consistency
                viewModel.findDictionaryMatches(word)
                // Populate the search field with the clicked word (lowercase)
                dictionarySearchEditText.setText(word.lowercase())
                true // Consume the event
            }
        )
    }
    
    /**
     * Detect if the text is likely vertical Japanese text
     * based on patterns common in vertical OCR output
     */
    private fun isVerticalJapaneseText(text: String): Boolean {
        if (text.isEmpty()) return false
        
        // Only apply to Japanese language
        if (ocrLanguage != "japanese") return false
        
        // Japanese character pattern
        val japanesePattern = Regex("[\u3040-\u309F\u30A0-\u30FF\u4E00-\u9FFF]")
        
        // Check if text has Japanese characters
        if (!japanesePattern.containsMatchIn(text)) return false
        
        // If the text has a lot of short lines with Japanese characters, 
        // it's likely vertical Japanese text
        val lines = text.split("\n")
        
        // If most lines are short (1-4 characters), it's likely vertical text
        val shortLines = lines.count { line -> 
            line.length in 1..4 && japanesePattern.containsMatchIn(line) 
        }
        
        val shortLineRatio = if (lines.isNotEmpty()) {
            shortLines.toFloat() / lines.size
        } else 0f
        
        Log.d(TAG, "Vertical Japanese detection: $shortLineRatio of lines are short")
        
        // If more than 50% of the lines are short Japanese lines, it's vertical
        return shortLineRatio > 0.5
    }
    
    /**
     * Detect if the text is likely vertical Chinese text
     * based on patterns common in vertical OCR output
     */
    private fun isVerticalChineseText(text: String): Boolean {
        if (text.isEmpty()) return false
        
        // Only apply to Chinese language
        if (ocrLanguage != "chinese") return false
        
        // Chinese character pattern (mainly Hanzi)
        val chinesePattern = Regex("[\u4E00-\u9FFF]")
        
        // Check if text has Chinese characters
        if (!chinesePattern.containsMatchIn(text)) return false
        
        // If the text has a lot of short lines with Chinese characters, 
        // it's likely vertical Chinese text
        val lines = text.split("\n")
        
        // If most lines are short (1-4 characters), it's likely vertical text
        val shortLines = lines.count { line -> 
            line.length in 1..4 && chinesePattern.containsMatchIn(line) 
        }
        
        val shortLineRatio = if (lines.isNotEmpty()) {
            shortLines.toFloat() / lines.size
        } else 0f
        
        Log.d(TAG, "Vertical Chinese detection: $shortLineRatio of lines are short")
        
        // If more than 50% of the lines are short Chinese lines, it's vertical
        return shortLineRatio > 0.5
    }
    
    /**
     * Process vertical Japanese text to make it more readable
     * Recombines columns of text into proper paragraphs
     */
    private fun processVerticalJapaneseText(text: String): String {
        val lines = text.split("\n")
        if (lines.size <= 1) return text
        
        val sb = StringBuilder()
        val japanesePattern = Regex("[\u3040-\u309F\u30A0-\u30FF\u4E00-\u9FFF]")
        var currentColumn = StringBuilder()
        
        // Group characters from vertically oriented text
        for (i in lines.indices) {
            val line = lines[i].trim()
            
            // Skip empty lines
            if (line.isEmpty()) {
                // If we have content in the current column, add it
                if (currentColumn.isNotEmpty()) {
                    sb.append(currentColumn.toString())
                    sb.append("\n")
                    currentColumn.clear()
                }
                continue
            }
            
            // If line is very short and has Japanese characters, it's likely part of a vertical column
            if (line.length <= 4 && japanesePattern.containsMatchIn(line)) {
                // Add to current column without spaces (Japanese doesn't use spaces)
                currentColumn.append(line)
            } else {
                // This is a normal horizontal line or a line break
                // Add any accumulated column content first
                if (currentColumn.isNotEmpty()) {
                    sb.append(currentColumn.toString())
                    sb.append("\n")
                    currentColumn.clear()
                }
                
                // Then add this line
                sb.append(line)
                sb.append("\n")
            }
        }
        
        // Add any remaining column content
        if (currentColumn.isNotEmpty()) {
            sb.append(currentColumn.toString())
        }
        
        val result = sb.toString().trim()
        Log.d(TAG, "Processed vertical Japanese text: \nOriginal: $text\nProcessed: $result")
        
        return result
    }
    
    // Word translation popup method removed as we now perform dictionary lookup on tap

    private fun setupClickListeners() {
        // Search dictionary button
        searchButton.setOnClickListener {
            searchSelectedText()
        }
        
        // Dictionary search button
        dictionarySearchButton.setOnClickListener {
            val searchTerm = dictionarySearchEditText.text.toString().trim()
            if (searchTerm.isNotEmpty()) {
                // The text is already converted to lowercase by the TextWatcher
                viewModel.findDictionaryMatches(searchTerm)
            }
        }
        
        // Setup dictionary search EditText action listener
        dictionarySearchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val searchTerm = dictionarySearchEditText.text.toString().trim()
                if (searchTerm.isNotEmpty()) {
                    // The text is already converted to lowercase by the TextWatcher
                    viewModel.findDictionaryMatches(searchTerm)
                }
                true
            } else {
                false
            }
        }

        // Copy button
        copyButton.setOnClickListener {
            copyToClipboard(fullOcrText)
        }

        // Save button
        saveButton.setOnClickListener {
            viewModel.saveOcrTextToFile(this)
        }

        // Close button
        closeButton.setOnClickListener {
            finish()
        }
    }
    
    /**
     * Searches the dictionary with selected text or full OCR text if no selection
     */
    private fun searchSelectedText() {
        // Search the full OCR text since we're using buttons now
        viewModel.findDictionaryMatches(null)
    }

    private fun copyToClipboard(text: String) {
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("OCR Text", text)
        clipboardManager.setPrimaryClip(clipData)
        Toast.makeText(this, R.string.text_copied, Toast.LENGTH_SHORT).show()
    }
    
    // OnAudioPlayListener implementation
    override fun onPlayAudio(term: String, language: String) {
        // Create a single, unique ID for this audio request to track in logs
        val requestId = System.currentTimeMillis()
        
        // Check if audio is enabled in settings
        val sharedPrefs = getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        val audioEnabled = sharedPrefs.getBoolean("enable_audio", true)
        
        if (!audioEnabled) {
            Toast.makeText(this, R.string.audio_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        
        // Check for Azure Speech API key
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val apiKey = prefs.getString("azure_speech_key", "") ?: ""
        val region = prefs.getString("azure_speech_region", "eastus") ?: "eastus"
        
        // Add detailed logging for debugging
        Log.e(TAG, "🔈 [REQ-$requestId] Audio request - Term: '$term', Language: '$language'")
        Log.e(TAG, "🔑 [REQ-$requestId] API config - Key present: ${apiKey.isNotEmpty()}, Region: $region")
        
        if (apiKey.isEmpty()) {
            Log.e(TAG, "❌ [REQ-$requestId] Missing API key")
            Toast.makeText(this, R.string.audio_generation_missing_api_key, Toast.LENGTH_LONG).show()
            // Open settings activity
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            return
        }
        
        // Generate and play audio
        lifecycleScope.launch {
            try {
                // Show progress toast
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@OCRResultActivity, R.string.generating_audio, Toast.LENGTH_SHORT).show()
                }
                
                Log.e(TAG, "⏳ [REQ-$requestId] Starting speech generation")
                
                // Create a simple test file to verify storage permissions
                try {
                    val testFile = File(cacheDir, "test_audio_${requestId}.tmp")
                    testFile.writeText("Test file for audio generation")
                    Log.e(TAG, "✓ [REQ-$requestId] Storage test successful: ${testFile.absolutePath}")
                    testFile.delete()
                } catch (e: Exception) {
                    Log.e(TAG, "❌ [REQ-$requestId] Storage test failed", e)
                }
                
                // Generate audio for the term
                val audioFile = speechService.generateSpeech(term, language)
                
                if (audioFile != null && audioFile.exists() && audioFile.length() > 100) {
                    Log.e(TAG, "✓ [REQ-$requestId] Speech generation successful")
                    Log.e(TAG, "📄 [REQ-$requestId] File: ${audioFile.absolutePath}, Size: ${audioFile.length()} bytes")
                    
                    // Play the audio on the main thread
                    withContext(Dispatchers.Main) {
                        try {
                            // Show toast for playing audio
                            Toast.makeText(this@OCRResultActivity, R.string.audio_playing, Toast.LENGTH_SHORT).show()
                            
                            // Try to play the audio file
                            Log.e(TAG, "🔊 [REQ-$requestId] Playing audio")
                            speechService.playAudio(audioFile)
                            
                            // Cache the audio file for AnkiDroid export
                            val cacheKey = "${language}_${term}"
                            audioCache[cacheKey] = audioFile
                            
                            // Set up a backup way to play if MediaPlayer fails
                            Handler(Looper.getMainLooper()).postDelayed({
                                try {
                                    // Check if we need a backup playback attempt
                                    val player = MediaPlayer.create(this@OCRResultActivity, Uri.fromFile(audioFile))
                                    Log.e(TAG, "🔄 [REQ-$requestId] Backup playback check")
                                    if (player != null) {
                                        player.setOnCompletionListener { it.release() }
                                        player.start()
                                        Log.e(TAG, "✓ [REQ-$requestId] Backup playback started")
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "❌ [REQ-$requestId] Backup playback failed", e)
                                }
                            }, 2000) // Give the first attempt 2 seconds
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ [REQ-$requestId] Error during audio playback", e)
                            Toast.makeText(
                                this@OCRResultActivity,
                                "Error playing audio: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } else {
                    val fileStatus = if (audioFile == null) "null file" 
                                     else "exists: ${audioFile.exists()}, size: ${audioFile?.length() ?: 0} bytes"
                    Log.e(TAG, "❌ [REQ-$requestId] Speech generation failed - $fileStatus")
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@OCRResultActivity,
                            "Failed to generate audio. Check Azure API key in settings.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ [REQ-$requestId] Critical error in audio process", e)
                Log.e(TAG, "🔍 [REQ-$requestId] Stack trace: ${e.stackTraceToString()}")
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@OCRResultActivity,
                        "Error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    // OnAnkiExportListener implementation
    override fun onExportToAnki(entry: DictionaryEntryEntity) {
        if (!ankiDroidHelper.isAnkiDroidAvailable()) {
            Toast.makeText(this, R.string.anki_status_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        
        if (ankiDroidHelper.getSavedDeckId() == 0L || ankiDroidHelper.getSavedModelId() == 0L) {
            Toast.makeText(this, R.string.anki_configuration_not_set, Toast.LENGTH_SHORT).show()
            // Open Anki configuration activity
            val intent = Intent(this, AnkiDroidConfigActivity::class.java)
            startActivity(intent)
            return
        }
        
        lifecycleScope.launch {
            try {
                // Determine language from the entry
                val language = determineLanguage(entry)
                
                // Check if we need to generate audio
                var audioFilePath: String? = null
                val sharedPrefs = getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
                val audioEnabled = sharedPrefs.getBoolean("enable_audio", true)
                
                // Only generate audio if it's enabled
                if (audioEnabled) {
                    // Check if we already have audio for this term
                    val cacheKey = "${language}_${entry.term}"
                    var audioFile = audioCache[cacheKey]
                    
                    // If not in cache, generate it now
                    if (audioFile == null) {
                        audioFile = speechService.generateSpeech(entry.term, language)
                    }
                    
                    // If we have audio, save it for export
                    if (audioFile != null) {
                        val exportFile = speechService.saveAudioForExport(
                            audioFile, entry.term, language
                        )
                        audioFilePath = exportFile?.absolutePath
                    }
                }
                
                val success = withContext(Dispatchers.IO) {
                    ankiDroidHelper.addNoteToAnkiDroid(
                        word = entry.term,
                        reading = entry.reading,
                        definition = entry.definition,
                        partOfSpeech = entry.partOfSpeech,
                        context = viewModel.ocrText.value ?: "",
                        screenshotPath = viewModel.screenshotPath.value,
                        translation = viewModel.translatedText.value ?: "",
                        audioPath = audioFilePath
                    )
                }
                
                if (success) {
                    Toast.makeText(this@OCRResultActivity, R.string.export_success, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@OCRResultActivity, R.string.export_failed, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("OCRResultActivity", "Error exporting to AnkiDroid", e)
                Toast.makeText(
                    this@OCRResultActivity,
                    getString(R.string.export_error, e.message),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    /**
     * Determine the language of an entry for audio generation
     */
    private fun determineLanguage(entry: DictionaryEntryEntity): String {
        // First check the OCR language setting
        ocrLanguage?.let {
            return when (it) {
                "japanese" -> "ja"
                "chinese" -> "zh"
                "korean" -> "ko"
                else -> "en"
            }
        }
        
        // If OCR language is not set, guess from content
        val text = entry.term + " " + entry.reading
        
        return when {
            // Check for Japanese (Hiragana, Katakana, Kanji)
            text.contains(Regex("[\u3040-\u309F\u30A0-\u30FF\u4E00-\u9FFF]")) -> "ja"
            
            // Check for Chinese (mainly Han characters without Japanese-specific characters)
            text.contains(Regex("[\u4E00-\u9FFF]")) && 
            !text.contains(Regex("[\u3040-\u309F\u30A0-\u30FF]")) -> "zh"
            
            // Check for Korean (Hangul)
            text.contains(Regex("[\uAC00-\uD7A3]")) -> "ko"
            
            // Default to English
            else -> "en"
        }
    }
}