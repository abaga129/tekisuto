package com.abaga129.tekisuto.ui.anki

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.abaga129.tekisuto.R
import com.abaga129.tekisuto.ui.BaseEdgeToEdgeActivity
import com.abaga129.tekisuto.util.AnkiPackageImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity for importing words from Anki .apkg files
 */
class AnkiPackageImportActivity : BaseEdgeToEdgeActivity() {

    private lateinit var selectFileButton: Button
    private lateinit var fieldSpinner: Spinner
    private lateinit var deckSpinner: Spinner
    private lateinit var importButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    
    private lateinit var ankiImporter: AnkiPackageImporter
    
    private var selectedUri: Uri? = null
    private var fields: List<String> = emptyList()
    private var decks: List<String> = emptyList()
    private var selectedFieldIndex: Int = -1
    private var selectedDeckName: String? = null
    
    // File picker launcher
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            handleSelectedFile(it)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_anki_package_import)
        
        // Apply insets to the root view
        applyInsetsToView(android.R.id.content)
        
        // Set up the action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.import_anki_package_title)
        
        // Initialize the importer
        ankiImporter = AnkiPackageImporter(this)
        
        // Initialize views
        selectFileButton = findViewById(R.id.select_apkg_button)
        fieldSpinner = findViewById(R.id.field_spinner)
        deckSpinner = findViewById(R.id.deck_spinner)
        importButton = findViewById(R.id.import_button)
        progressBar = findViewById(R.id.progress_bar)
        statusText = findViewById(R.id.status_text)
        
        // Setup button clicks
        selectFileButton.setOnClickListener {
            openFilePicker()
        }
        
        importButton.setOnClickListener {
            importWordsFromPackage()
        }
        
        // Setup spinners
        setupFieldSpinner()
        setupDeckSpinner()
        
        // Initially disable import button
        updateImportButtonState()
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
    
    private fun openFilePicker() {
        filePickerLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
    }
    
    private fun handleSelectedFile(uri: Uri) {
        selectedUri = uri
        
        // Show progress bar
        progressBar.visibility = View.VISIBLE
        statusText.text = getString(R.string.processing_file)
        
        // Disable UI while processing
        selectFileButton.isEnabled = false
        importButton.isEnabled = false
        fieldSpinner.isEnabled = false
        deckSpinner.isEnabled = false
        
        // Extract fields and decks from the package
        lifecycleScope.launch {
            try {
                val (extractedFields, extractedDecks) = ankiImporter.extractFieldsAndDecks(uri)
                
                withContext(Dispatchers.Main) {
                    if (extractedFields.isEmpty()) {
                        Toast.makeText(
                            this@AnkiPackageImportActivity,
                            R.string.no_fields_found,
                            Toast.LENGTH_SHORT
                        ).show()
                        statusText.text = getString(R.string.no_fields_found)
                    } else {
                        // Update fields
                        fields = extractedFields
                        updateFieldSpinner()
                        
                        // Update decks
                        decks = extractedDecks
                        updateDeckSpinner()
                        
                        statusText.text = getString(R.string.file_processed)
                    }
                    
                    // Re-enable UI
                    selectFileButton.isEnabled = true
                    fieldSpinner.isEnabled = fields.isNotEmpty()
                    deckSpinner.isEnabled = decks.isNotEmpty()
                    updateImportButtonState()
                    
                    // Hide progress bar
                    progressBar.visibility = View.GONE
                }
            } catch (e: Exception) {
                Log.e("AnkiImport", "Error processing APKG file", e)
                
                withContext(Dispatchers.Main) {
                    // Check for specific error messages
                    when {
                        e.message?.contains("please update to the latest anki version") == true -> {
                            // Show a dialog with Anki compatibility instructions
                            val alertDialog = androidx.appcompat.app.AlertDialog.Builder(this@AnkiPackageImportActivity)
                                .setTitle("Incompatible Anki Package")
                                .setMessage(
                                    "This .apkg file was created with a newer version of Anki " +
                                    "and contains a compatibility message.\n\n" +
                                    "To fix this, please:\n" +
                                    "1. Open Anki Desktop 2.1.x\n" +
                                    "2. Select the deck to export\n" +
                                    "3. Click 'Export'\n" +
                                    "4. Enable 'Legacy format (Anki 2.0)'\n" +
                                    "5. Click 'Export' and try importing the new file"
                                )
                                .setPositiveButton("OK", null)
                                .create()
                            alertDialog.show()
                            
                            statusText.text = "Incompatible Anki package. Please use legacy export format."
                        }
                        e.message?.contains("CRC") == true || e.message?.contains("zip") == true -> {
                            // Likely ZIP file corruption
                            Toast.makeText(
                                this@AnkiPackageImportActivity,
                                "The .apkg file appears to be corrupted. Please try exporting it again.",
                                Toast.LENGTH_LONG
                            ).show()
                            
                            statusText.text = "Corrupted .apkg file detected. Please try exporting again."
                        }
                        else -> {
                            Toast.makeText(
                                this@AnkiPackageImportActivity,
                                getString(R.string.error_processing_file, e.message),
                                Toast.LENGTH_LONG
                            ).show()
                            
                            statusText.text = getString(R.string.error_processing_file, e.message)
                        }
                    }
                    
                    // Re-enable UI
                    selectFileButton.isEnabled = true
                    progressBar.visibility = View.GONE
                }
            }
        }
    }
    
    private fun setupFieldSpinner() {
        fieldSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (position > 0 && fields.isNotEmpty()) {
                    selectedFieldIndex = position - 1 // -1 because of "Select field" item
                    updateImportButtonState()
                } else {
                    selectedFieldIndex = -1
                    updateImportButtonState()
                }
            }
            
            override fun onNothingSelected(parent: AdapterView<*>) {
                selectedFieldIndex = -1
                updateImportButtonState()
            }
        }
    }
    
    private fun setupDeckSpinner() {
        deckSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (position > 0 && decks.isNotEmpty()) {
                    selectedDeckName = decks[position - 1] // -1 because of "All decks" item
                } else {
                    selectedDeckName = null
                }
            }
            
            override fun onNothingSelected(parent: AdapterView<*>) {
                selectedDeckName = null
            }
        }
    }
    
    private fun updateFieldSpinner() {
        val fieldNames = mutableListOf<String>()
        fieldNames.add(getString(R.string.select_field))
        fieldNames.addAll(fields)
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, fieldNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        fieldSpinner.adapter = adapter
    }
    
    private fun updateDeckSpinner() {
        val deckNames = mutableListOf<String>()
        deckNames.add(getString(R.string.all_decks))
        deckNames.addAll(decks)
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, deckNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        deckSpinner.adapter = adapter
    }
    
    private fun updateImportButtonState() {
        importButton.isEnabled = selectedUri != null && selectedFieldIndex >= 0
    }
    
    private fun importWordsFromPackage() {
        val uri = selectedUri ?: return
        
        if (selectedFieldIndex < 0) {
            Toast.makeText(this, R.string.select_field_error, Toast.LENGTH_SHORT).show()
            return
        }
        
        // Show progress bar
        progressBar.visibility = View.VISIBLE
        statusText.text = getString(R.string.importing_words_from_anki)
        
        // Disable UI while importing
        selectFileButton.isEnabled = false
        importButton.isEnabled = false
        fieldSpinner.isEnabled = false
        deckSpinner.isEnabled = false
        
        // Import words
        lifecycleScope.launch {
            try {
                val importCount = ankiImporter.importWords(
                    uri = uri,
                    fieldIndex = selectedFieldIndex,
                    deckName = selectedDeckName
                )
                
                withContext(Dispatchers.Main) {
                    // Show success message
                    Toast.makeText(
                        this@AnkiPackageImportActivity,
                        getString(R.string.imported_words_count, importCount),
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    statusText.text = getString(R.string.imported_words_count, importCount)
                    
                    // Re-enable UI
                    selectFileButton.isEnabled = true
                    fieldSpinner.isEnabled = fields.isNotEmpty()
                    deckSpinner.isEnabled = decks.isNotEmpty()
                    updateImportButtonState()
                    
                    // Hide progress bar
                    progressBar.visibility = View.GONE
                }
            } catch (e: Exception) {
                Log.e("AnkiImport", "Error importing words", e)
                
                withContext(Dispatchers.Main) {
                    // Display a more detailed error dialog for compatibility errors
                    when {
                        e is IllegalStateException && e.message?.contains("legacy export") == true -> {
                            // Show a dialog with instructions for Anki compatibility issues
                            val alertDialog = androidx.appcompat.app.AlertDialog.Builder(this@AnkiPackageImportActivity)
                                .setTitle("Incompatible Anki Package")
                                .setMessage(
                                    "This .apkg file was created with a newer version of Anki " +
                                    "and contains a compatibility message.\n\n" +
                                    "To fix this, please:\n" +
                                    "1. Open Anki Desktop 2.1.x\n" +
                                    "2. Select the deck to export\n" +
                                    "3. Click 'Export'\n" +
                                    "4. Enable 'Legacy format (Anki 2.0)'\n" +
                                    "5. Click 'Export' and try importing the new file"
                                )
                                .setPositiveButton("OK", null)
                                .create()
                            alertDialog.show()
                            
                            statusText.text = "Incompatible Anki package. Please use legacy export format."
                        }
                        else -> {
                            // Show a regular error toast
                            Toast.makeText(
                                this@AnkiPackageImportActivity,
                                getString(R.string.error_importing_words, e.message),
                                Toast.LENGTH_LONG
                            ).show()
                            
                            statusText.text = getString(R.string.error_importing_words, e.message)
                        }
                    }
                    
                    // Re-enable UI
                    selectFileButton.isEnabled = true
                    fieldSpinner.isEnabled = fields.isNotEmpty()
                    deckSpinner.isEnabled = decks.isNotEmpty()
                    updateImportButtonState()
                    
                    // Hide progress bar
                    progressBar.visibility = View.GONE
                }
            }
        }
    }
}