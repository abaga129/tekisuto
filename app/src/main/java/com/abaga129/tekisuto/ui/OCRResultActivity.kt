package com.abaga129.tekisuto.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageView
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
import com.abaga129.tekisuto.viewmodel.OCRResultViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OCRResultActivity : AppCompatActivity(), DictionaryMatchAdapter.OnAnkiExportListener {

    private lateinit var viewModel: OCRResultViewModel
    private lateinit var screenshotImageView: ImageView
    private lateinit var ocrTextView: TextView
    private lateinit var copyButton: Button
    private lateinit var saveButton: Button
    private lateinit var closeButton: Button
    private lateinit var searchButton: Button
    private lateinit var dictionaryMatchesRecyclerView: RecyclerView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var noMatchesTextView: TextView
    private lateinit var selectionHintTextView: TextView
    private lateinit var dictionaryMatchAdapter: DictionaryMatchAdapter
    private lateinit var ankiButton: Button
    
    private lateinit var ankiDroidHelper: AnkiDroidHelper
    
    private var actionMode: ActionMode? = null
    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.text_selection_menu, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            return false
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.action_lookup_selected -> {
                    searchSelectedText()
                    mode.finish()
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            actionMode = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ocr_result)

        // Initialize AnkiDroid helper
        ankiDroidHelper = AnkiDroidHelper(this)

        // Initialize ViewModel
        viewModel = ViewModelProvider(this).get(OCRResultViewModel::class.java)
        viewModel.initDictionaryRepository(applicationContext)

        // Initialize views
        screenshotImageView = findViewById(R.id.screenshot_image_view)
        ocrTextView = findViewById(R.id.ocr_text_view)
        copyButton = findViewById(R.id.copy_button)
        saveButton = findViewById(R.id.save_button)
        closeButton = findViewById(R.id.close_button)
        searchButton = findViewById(R.id.search_button)
        dictionaryMatchesRecyclerView = findViewById(R.id.dictionary_matches_recycler)
        loadingIndicator = findViewById(R.id.loading_indicator)
        noMatchesTextView = findViewById(R.id.no_matches_text)
        selectionHintTextView = findViewById(R.id.selection_hint)
        ankiButton = findViewById(R.id.configure_anki_button)
        
        // Setup text selection action mode
        ocrTextView.customSelectionActionModeCallback = actionModeCallback
        
        // Show context menu on long press
        ocrTextView.setOnLongClickListener {
            if (ocrTextView.hasSelection() && actionMode == null) {
                actionMode = startActionMode(actionModeCallback)
            }
            false // Return false to allow default long click behavior
        }

        // Set up RecyclerView
        dictionaryMatchAdapter = DictionaryMatchAdapter()
        dictionaryMatchAdapter.setLifecycleScope(lifecycleScope)
        dictionaryMatchAdapter.setOnAnkiExportListener(this)
        
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

        // Set data to ViewModel and adapter
        viewModel.setOcrText(ocrText)
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

    private fun setupObservers() {
        // Observe OCR text
        viewModel.ocrText.observe(this) { text ->
            ocrTextView.text = text
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
            android.util.Log.d("OCRResultActivity", "Received ${matches.size} dictionary matches")
            matches.take(3).forEachIndexed { index, entry ->
                android.util.Log.d("OCRResultActivity", "Match $index: term=${entry.term}, " +
                        "definition=${entry.definition.take(50)}${if(entry.definition.length > 50) "..." else ""}")
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
    }

    private fun setupClickListeners() {
        // Search dictionary button
        searchButton.setOnClickListener {
            searchSelectedText()
        }

        // Copy button
        copyButton.setOnClickListener {
            copyToClipboard(viewModel.ocrText.value ?: "")
        }

        // Save button
        saveButton.setOnClickListener {
            viewModel.saveOcrTextToFile(this)
        }

        // Close button
        closeButton.setOnClickListener {
            finish()
        }
        
        // Configure AnkiDroid button
        ankiButton.setOnClickListener {
            startActivity(Intent(this, AnkiDroidConfigActivity::class.java))
        }
    }
    
    /**
     * Searches the dictionary with selected text or full OCR text if no selection
     */
    private fun searchSelectedText() {
        // Check if there's selected text
        val selectedText = ocrTextView.text?.substring(
            ocrTextView.selectionStart.takeIf { it >= 0 } ?: 0,
            ocrTextView.selectionEnd.takeIf { it >= 0 && it <= ocrTextView.text.length }
                ?: ocrTextView.text.length
        )

        // Only use selected text if something is actually selected
        val textToSearch = if (ocrTextView.hasSelection() && !selectedText.isNullOrBlank()) {
            android.util.Log.d("OCRResultActivity", "Searching for selected text: $selectedText")
            selectedText.toString().also {
                Toast.makeText(this, "Searching for: $it", Toast.LENGTH_SHORT).show()
            }
        } else {
            android.util.Log.d("OCRResultActivity", "No text selected, searching full OCR text")
            null
        }

        viewModel.findDictionaryMatches(textToSearch)
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
                        screenshotPath = viewModel.screenshotPath.value
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