package com.abaga129.tekisuto.ui.anki

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.abaga129.tekisuto.R
import com.abaga129.tekisuto.ui.BaseEdgeToEdgeActivity
import com.abaga129.tekisuto.util.AnkiDroidHelper
import com.abaga129.tekisuto.util.PitchAccentExportHelper
import com.abaga129.tekisuto.viewmodel.ProfileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AnkiDroidConfigActivity : BaseEdgeToEdgeActivity() {

    private lateinit var ankiDroidHelper: AnkiDroidHelper
    private lateinit var profileViewModel: ProfileViewModel
    
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
    private lateinit var translationFieldSpinner: Spinner
    private lateinit var audioFieldSpinner: Spinner
    private lateinit var pitchAccentFieldSpinner: Spinner
    private lateinit var saveButton: Button
    private lateinit var testButton: Button
    private lateinit var importAnkiPackageButton: Button
    
    // Data
    private var decks: Map<Long, String> = emptyMap()
    private var models: Map<Long, String> = emptyMap()
    private var fields: Array<String> = emptyArray()
    private var selectedDeckId: Long = 0
    private var selectedModelId: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_anki_droid_config)
        
        // Apply insets to the root view
        applyInsetsToView(android.R.id.content)
        
        // Enable up navigation
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // Initialize ProfileViewModel
        profileViewModel = ViewModelProvider(this).get(ProfileViewModel::class.java)
        
        // Initialize AnkiDroidHelper
        ankiDroidHelper = AnkiDroidHelper(this)
        
        // Initialize UI components
        initializeViews()
        
        // Observe current profile
        profileViewModel.currentProfile.observe(this) { profile ->
            if (profile != null) {
                Log.d("AnkiDroidConfig", "Received profile: ${profile.name} (ID: ${profile.id}), AnkiDroid deck ID: ${profile.ankiDeckId}, model ID: ${profile.ankiModelId}")
                ankiDroidHelper.setActiveProfile(profile)
                
                // Update title to include profile name
                supportActionBar?.title = getString(R.string.anki_config_title) + " - " + profile.name
                
                // Reload the AnkiDroid configuration
                checkAnkiDroidAvailability()
            } else {
                Log.w("AnkiDroidConfig", "Received null profile")
            }
        }
        
        // Load profile - this will trigger the observer above
        profileViewModel.loadCurrentProfile()
    }
    
    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.anki_config_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_reset_anki_config -> {
                showResetConfirmationDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showResetConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.reset_anki_config)
            .setMessage(R.string.reset_anki_config_confirm)
            .setPositiveButton(R.string.ok) { dialog: android.content.DialogInterface, which: Int ->
                // Clear configuration
                ankiDroidHelper.clearConfiguration()
                
                // Reset UI to default state
                setupDeckSpinner()
                setupModelSpinner()
                
                // Notify user
                Toast.makeText(this, R.string.reset_anki_config_done, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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
        translationFieldSpinner = findViewById(R.id.translation_field_spinner)
        audioFieldSpinner = findViewById(R.id.audio_field_spinner)
        pitchAccentFieldSpinner = findViewById(R.id.pitch_accent_field_spinner)
        saveButton = findViewById(R.id.save_config_button)
        testButton = findViewById(R.id.test_anki_button)
        importAnkiPackageButton = findViewById(R.id.import_anki_package_button)
        
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
        
        // Set up import button
        importAnkiPackageButton.setOnClickListener {
            launchAnkiPackageImport()
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
        translationFieldSpinner.isEnabled = false
        audioFieldSpinner.isEnabled = false
        pitchAccentFieldSpinner.isEnabled = false
        saveButton.isEnabled = false
        testButton.isEnabled = false
        // We still allow importing from .apkg files even if AnkiDroid is not installed
    }
    
    private fun launchAnkiPackageImport() {
        val intent = Intent(this, AnkiPackageImportActivity::class.java)
        startActivity(intent)
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
                    if (fieldMappings.translation < fields.size) {
                        translationFieldSpinner.setSelection(fieldMappings.translation + 1)
                    }
                    if (fieldMappings.audio >= 0 && fieldMappings.audio < fields.size) {
                        audioFieldSpinner.setSelection(fieldMappings.audio + 1)
                    }
                    
                    // Set pitch accent field if available
                    val profile = profileViewModel.currentProfile.value
                    if (profile != null && profile.ankiFieldPitchAccent >= 0 && profile.ankiFieldPitchAccent < fields.size) {
                        pitchAccentFieldSpinner.setSelection(profile.ankiFieldPitchAccent + 1)
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
        translationFieldSpinner.adapter = adapter
        audioFieldSpinner.adapter = adapter
        pitchAccentFieldSpinner.adapter = adapter
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
        val translationField = translationFieldSpinner.selectedItemPosition - 1
        val audioField = audioFieldSpinner.selectedItemPosition - 1
        val pitchAccentField = pitchAccentFieldSpinner.selectedItemPosition - 1
        
        // Validate that at least word and definition fields are selected
        if (wordField < 0) {
            Toast.makeText(this, getString(R.string.word_field_required), Toast.LENGTH_SHORT).show()
            return
        }
        
        if (definitionField < 0) {
            Toast.makeText(this, getString(R.string.definition_field_required), Toast.LENGTH_SHORT).show()
            return
        }
        
        // Save configuration to AnkiDroidHelper
        ankiDroidHelper.saveConfiguration(
            selectedDeckId,
            selectedModelId,
            wordField,
            readingField,
            definitionField,
            screenshotField,
            contextField,
            partOfSpeechField,
            translationField,
            audioField,
            pitchAccentField
        )
        
        // Save configuration to current profile
        val currentProfile = profileViewModel.currentProfile.value
        if (currentProfile != null) {
            val updatedProfile = currentProfile.copy(
                ankiDeckId = selectedDeckId,
                ankiModelId = selectedModelId,
                ankiFieldWord = wordField,
                ankiFieldReading = readingField,
                ankiFieldDefinition = definitionField,
                ankiFieldScreenshot = screenshotField,
                ankiFieldContext = contextField,
                ankiFieldPartOfSpeech = partOfSpeechField,
                ankiFieldTranslation = translationField,
                ankiFieldAudio = audioField,
                ankiFieldPitchAccent = pitchAccentField
            )
            
            // Update the profile in the database
            profileViewModel.updateProfile(updatedProfile)
            
            Log.d("AnkiDroidConfigActivity", "Saved AnkiDroid configuration to profile: ${updatedProfile.name} (ID: ${updatedProfile.id})")
        } else {
            Log.w("AnkiDroidConfigActivity", "No active profile found - configuration saved to legacy preferences only")
        }
        
        Toast.makeText(this, getString(R.string.config_saved), Toast.LENGTH_SHORT).show()
    }
    
    private fun testAnkiDroidExport() {
        if (selectedDeckId == 0L || selectedModelId == 0L) {
            Toast.makeText(this, getString(R.string.anki_configuration_not_set), Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            try {
                // Get the pitch accent field
                val pitchAccentField = pitchAccentFieldSpinner.selectedItemPosition - 1
                
                // Create test note with enhanced pitch accent
                val success = withContext(Dispatchers.IO) {
                    // Generate enhanced pitch accent text for test
                    val pitchAccentText = if (pitchAccentField >= 0) {
                        PitchAccentExportHelper.generatePitchAccentForExport(
                            this@AnkiDroidConfigActivity,
                            "てすと",
                            "0"
                        ) ?: "平板型 [0] (てすと)"
                    } else null
                    
                    ankiDroidHelper.addNoteToAnkiDroid(
                        "テスト",
                        "てすと",
                        "Test word from Tekisuto with enhanced pitch accent formatting",
                        "Test",
                        "This is a test export from Tekisuto app showing enhanced pitch accent features.",
                        null,
                        "Test (Translation)",
                        null,  // audio path is null for test
                        pitchAccentText
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