package com.abaga129.tekisuto.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.abaga129.tekisuto.R
import com.abaga129.tekisuto.database.DictionaryEntryEntity
import com.abaga129.tekisuto.ui.adapter.DictionaryMatchAdapter
import com.abaga129.tekisuto.ui.anki.AnkiDroidConfigActivity
import com.abaga129.tekisuto.util.AnkiDroidHelper
import com.abaga129.tekisuto.util.WordTokenizerFlow
import com.abaga129.tekisuto.viewmodel.OCRResultViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OCRResultActivity : AppCompatActivity(), DictionaryMatchAdapter.OnAnkiExportListener {

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
    
    // No longer using translation popup
    private var ocrLanguage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ocr_result)

        // Initialize AnkiDroid helper
        ankiDroidHelper = AnkiDroidHelper(this)

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
        // Nothing special to clean up
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
        
        // Create flow layout with word buttons
        WordTokenizerFlow.createClickableWordsFlow(
            context = this,
            parentViewGroup = textContainer,
            text = text,
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
                val success = withContext(Dispatchers.IO) {
                    ankiDroidHelper.addNoteToAnkiDroid(
                        word = entry.term,
                        reading = entry.reading,
                        definition = entry.definition,
                        partOfSpeech = entry.partOfSpeech,
                        context = viewModel.ocrText.value ?: "",
                        screenshotPath = viewModel.screenshotPath.value,
                        translation = viewModel.translatedText.value ?: ""
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
}