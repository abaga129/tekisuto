package com.abaga129.tekisuto.ui.anki

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.abaga129.tekisuto.R
import com.abaga129.tekisuto.util.AnkiDroidHelper
import com.ichi2.anki.api.AddContentApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AnkiDroidConfigActivity : AppCompatActivity() {

    private lateinit var ankiDroidHelper: AnkiDroidHelper
    
    // UI Components
    private lateinit var statusTextView: TextView
    private lateinit var deckSpinner: Spinner
    private lateinit var modelSpinner: Spinner
    private lateinit var wordFieldSpinner: Spinner
    private lateinit var readingFieldSpinner: Spinner
    private lateinit var definitionFieldSpinner: Spinner
    private lateinit var screenshotFieldSpinner: Spinner
    private lateinit var contextFieldSpinner: Spinner
    private lateinit var partOfSpeechFieldSpinner: Spinner
    private lateinit var saveButton: Button
    private lateinit var testButton: Button
    
    // Data
    private var decks: Map<Long, String> = emptyMap()
    private var models: Map<Long, String> = emptyMap()
    private var fields: Array<String> = emptyArray()
    private var selectedDeckId: Long = 0
    private var selectedModelId: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_anki_droid_config)
        
        // Enable up navigation
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        ankiDroidHelper = AnkiDroidHelper(this)
        
        // Initialize UI components
        initializeViews()
        
        // Check if AnkiDroid is available
        checkAnkiDroidAvailability()
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
    
    private fun initializeViews() {
        statusTextView = findViewById(R.id.anki_status_text)
        deckSpinner = findViewById(R.id.deck_spinner)
        modelSpinner = findViewById(R.id.model_spinner)
        wordFieldSpinner = findViewById(R.id.word_field_spinner)
        readingFieldSpinner = findViewById(R.id.reading_field_spinner)
        definitionFieldSpinner = findViewById(R.id.definition_field_spinner)
        screenshotFieldSpinner = findViewById(R.id.screenshot_field_spinner)
        contextFieldSpinner = findViewById(R.id.context_field_spinner)
        partOfSpeechFieldSpinner = findViewById(R.id.part_of_speech_field_spinner)
        saveButton = findViewById(R.id.save_config_button)
        testButton = findViewById(R.id.test_anki_button)
        
        // Set up spinners
        setupDeckSpinner()
        setupModelSpinner()
        
        // Set up save button
        saveButton.setOnClickListener {
            saveConfiguration()
        }
        
        // Set up test button
        testButton.setOnClickListener {
            testAnkiDroidExport()
        }
    }
    
    private fun checkAnkiDroidAvailability() {
        if (ankiDroidHelper.isAnkiDroidAvailable()) {
            statusTextView.text = getString(R.string.anki_status_available)
            statusTextView.setTextColor(getColor(R.color.status_enabled))
            
            // Load saved configuration
            loadSavedConfiguration()
            
            // Load AnkiDroid data
            loadAnkiDroidData()
        } else {
            statusTextView.text = getString(R.string.anki_status_unavailable)
            statusTextView.setTextColor(getColor(R.color.status_disabled))
            disableConfiguration()
        }
    }
    
    private fun disableConfiguration() {
        deckSpinner.isEnabled = false
        modelSpinner.isEnabled = false
        wordFieldSpinner.isEnabled = false
        readingFieldSpinner.isEnabled = false
        definitionFieldSpinner.isEnabled = false
        screenshotFieldSpinner.isEnabled = false
        contextFieldSpinner.isEnabled = false
        partOfSpeechFieldSpinner.isEnabled = false
        saveButton.isEnabled = false
        testButton.isEnabled = false
    }
    
    private fun loadSavedConfiguration() {
        selectedDeckId = ankiDroidHelper.getSavedDeckId()
        selectedModelId = ankiDroidHelper.getSavedModelId()
        
        // Field mappings will be loaded after fields are available
    }
    
    private fun loadAnkiDroidData() {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Load decks and models
                    decks = ankiDroidHelper.getAvailableDecks()
                    models = ankiDroidHelper.getAvailableModels()
                }
                
                // Update UI with data
                updateDeckSpinner()
                updateModelSpinner()
                
                // Load fields for selected model
                if (selectedModelId > 0) {
                    loadFieldsForModel(selectedModelId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@AnkiDroidConfigActivity,
                        getString(R.string.anki_load_data_error),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    private fun setupDeckSpinner() {
        deckSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (position > 0 && decks.isNotEmpty()) {
                    val deckId = decks.keys.toList()[position - 1] // -1 because of "Select deck" item
                    selectedDeckId = deckId
                }
            }
            
            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }
    }
    
    private fun setupModelSpinner() {
        modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (position > 0 && models.isNotEmpty()) {
                    val modelId = models.keys.toList()[position - 1] // -1 because of "Select model" item
                    if (selectedModelId != modelId) {
                        selectedModelId = modelId
                        loadFieldsForModel(modelId)
                    }
                }
            }
            
            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }
    }
    
    private fun updateDeckSpinner() {
        val deckNames = mutableListOf<String>()
        deckNames.add(getString(R.string.select_deck))
        deckNames.addAll(decks.values)
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, deckNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        deckSpinner.adapter = adapter
        
        // Select saved deck
        if (selectedDeckId > 0) {
            val deckIndex = decks.keys.indexOf(selectedDeckId)
            if (deckIndex >= 0) {
                deckSpinner.setSelection(deckIndex + 1) // +1 for "Select deck" item
            }
        }
    }
    
    private fun updateModelSpinner() {
        val modelNames = mutableListOf<String>()
        modelNames.add(getString(R.string.select_model))
        modelNames.addAll(models.values)
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modelNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        modelSpinner.adapter = adapter
        
        // Select saved model
        if (selectedModelId > 0) {
            val modelIndex = models.keys.indexOf(selectedModelId)
            if (modelIndex >= 0) {
                modelSpinner.setSelection(modelIndex + 1) // +1 for "Select model" item
            }
        }
    }
    
    private fun loadFieldsForModel(modelId: Long) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    fields = ankiDroidHelper.getFieldsForModel(modelId)
                }
                
                // Update field spinners
                updateFieldSpinners()
                
                // Load saved field mappings
                val fieldMappings = ankiDroidHelper.getSavedFieldMappings()
                
                // Set selected fields
                if (fields.isNotEmpty()) {
                    if (fieldMappings.word < fields.size) {
                        wordFieldSpinner.setSelection(fieldMappings.word + 1) // +1 for "Select field" item
                    }
                    if (fieldMappings.reading < fields.size) {
                        readingFieldSpinner.setSelection(fieldMappings.reading + 1)
                    }
                    if (fieldMappings.definition < fields.size) {
                        definitionFieldSpinner.setSelection(fieldMappings.definition + 1)
                    }
                    if (fieldMappings.screenshot < fields.size) {
                        screenshotFieldSpinner.setSelection(fieldMappings.screenshot + 1)
                    }
                    if (fieldMappings.context < fields.size) {
                        contextFieldSpinner.setSelection(fieldMappings.context + 1)
                    }
                    if (fieldMappings.partOfSpeech < fields.size) {
                        partOfSpeechFieldSpinner.setSelection(fieldMappings.partOfSpeech + 1)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@AnkiDroidConfigActivity,
                        getString(R.string.anki_load_fields_error),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    private fun updateFieldSpinners() {
        val fieldNames = mutableListOf<String>()
        fieldNames.add(getString(R.string.select_field))
        fieldNames.addAll(fields)
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, fieldNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        
        wordFieldSpinner.adapter = adapter
        readingFieldSpinner.adapter = adapter
        definitionFieldSpinner.adapter = adapter
        screenshotFieldSpinner.adapter = adapter
        contextFieldSpinner.adapter = adapter
        partOfSpeechFieldSpinner.adapter = adapter
    }
    
    private fun saveConfiguration() {
        if (selectedDeckId == 0L) {
            Toast.makeText(this, getString(R.string.select_deck_error), Toast.LENGTH_SHORT).show()
            return
        }
        
        if (selectedModelId == 0L) {
            Toast.makeText(this, getString(R.string.select_model_error), Toast.LENGTH_SHORT).show()
            return
        }
        
        // Get field indices, subtract 1 to account for "Select field" item
        val wordField = wordFieldSpinner.selectedItemPosition - 1
        val readingField = readingFieldSpinner.selectedItemPosition - 1
        val definitionField = definitionFieldSpinner.selectedItemPosition - 1
        val screenshotField = screenshotFieldSpinner.selectedItemPosition - 1
        val contextField = contextFieldSpinner.selectedItemPosition - 1
        val partOfSpeechField = partOfSpeechFieldSpinner.selectedItemPosition - 1
        
        // Validate that at least word and definition fields are selected
        if (wordField < 0) {
            Toast.makeText(this, getString(R.string.word_field_required), Toast.LENGTH_SHORT).show()
            return
        }
        
        if (definitionField < 0) {
            Toast.makeText(this, getString(R.string.definition_field_required), Toast.LENGTH_SHORT).show()
            return
        }
        
        // Save configuration
        ankiDroidHelper.saveConfiguration(
            selectedDeckId,
            selectedModelId,
            wordField,
            readingField,
            definitionField,
            screenshotField,
            contextField,
            partOfSpeechField
        )
        
        Toast.makeText(this, getString(R.string.config_saved), Toast.LENGTH_SHORT).show()
    }
    
    private fun testAnkiDroidExport() {
        if (selectedDeckId == 0L || selectedModelId == 0L) {
            Toast.makeText(this, getString(R.string.anki_configuration_not_set), Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            try {
                // Create test note
                val success = withContext(Dispatchers.IO) {
                    ankiDroidHelper.addNoteToAnkiDroid(
                        "テスト",
                        "てすと",
                        "Test word from Tekisuto",
                        "Test",
                        "This is a test export from Tekisuto app.",
                        null
                    )
                }
                
                if (success) {
                    Toast.makeText(this@AnkiDroidConfigActivity, 
                        getString(R.string.test_note_added), 
                        Toast.LENGTH_SHORT).show()
                    
                    // Open AnkiDroid
                    ankiDroidHelper.launchAnkiDroid()
                } else {
                    Toast.makeText(this@AnkiDroidConfigActivity, 
                        getString(R.string.test_note_failed), 
                        Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@AnkiDroidConfigActivity, 
                    getString(R.string.test_note_error, e.message), 
                    Toast.LENGTH_SHORT).show()
            }
        }
    }
}