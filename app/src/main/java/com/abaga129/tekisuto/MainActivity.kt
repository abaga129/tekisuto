package com.abaga129.tekisuto

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.abaga129.tekisuto.database.DictionaryRepository
import com.abaga129.tekisuto.service.AccessibilityOcrService
import com.abaga129.tekisuto.ui.settings.SettingsActivity
import com.abaga129.tekisuto.viewmodel.DictionaryInfo
import com.abaga129.tekisuto.viewmodel.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var statusTextView: TextView
    private lateinit var settingsButton: Button
    private lateinit var importDictionaryButton: Button
    private lateinit var browseDictionaryButton: Button
    private lateinit var ocrSettingsButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var dictionaryInfoTextView: TextView

    // Create a launcher for file selection
    private val selectDictionaryLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            importYomitanDictionary(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize ViewModel
        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)

        // Initialize repository from application
        val repository = DictionaryRepository(applicationContext)
        viewModel.initRepository(repository)

        // Initialize UI components
        statusTextView = findViewById(R.id.status_text_view)
        settingsButton = findViewById(R.id.settings_button)
        importDictionaryButton = findViewById(R.id.import_dictionary_button)
        browseDictionaryButton = findViewById(R.id.browse_dictionary_button)
        ocrSettingsButton = findViewById(R.id.ocr_settings_button)
        progressBar = findViewById(R.id.import_progress_bar)
        dictionaryInfoTextView = findViewById(R.id.dictionary_info_text_view)

        // Hide progress bar initially
        progressBar.visibility = View.GONE

        // Set click listener for the settings button
        settingsButton.setOnClickListener {
            openAccessibilitySettings()
        }

        // Set click listener for the import dictionary button
        importDictionaryButton.setOnClickListener {
            openDictionaryPicker()
        }
        
        // Set click listener for the browse dictionary button
        browseDictionaryButton.setOnClickListener {
            openDictionaryBrowser()
        }
        
        // Set click listener for the OCR settings button
        ocrSettingsButton.setOnClickListener {
            openOcrSettings()
        }

        // Observe accessibility service status
        viewModel.isAccessibilityServiceEnabled.observe(this) { isEnabled ->
            updateStatusText(isEnabled)
        }

        // Observe import progress
        viewModel.importProgress.observe(this) { progress ->
            updateImportProgress(progress)
        }

        // Observe dictionary info
        viewModel.dictionaryInfo.observe(this) { info ->
            updateDictionaryInfo(info)
        }

        // Check for existing dictionary on startup
        lifecycleScope.launch {
            val count = repository.getDictionaryEntryCount()
            if (count > 0) {
                dictionaryInfoTextView.text = getString(R.string.dictionary_entries_count, count)
                dictionaryInfoTextView.visibility = View.VISIBLE
            } else {
                dictionaryInfoTextView.visibility = View.GONE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Check if accessibility service is enabled
//        viewModel.checkAccessibilityServiceStatus(this, AccessibilityOcrService::class.java)
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_ocr_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun updateStatusText(isEnabled: Boolean) {
        statusTextView.text = if (isEnabled) {
            getString(R.string.service_enabled)
        } else {
            getString(R.string.service_disabled)
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun openDictionaryPicker() {
        selectDictionaryLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
    }
    
    private fun openDictionaryBrowser() {
        val intent = Intent(this, com.abaga129.tekisuto.ui.dictionary.DictionaryBrowserActivity::class.java)
        startActivity(intent)
    }
    
    private fun openOcrSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    private fun importYomitanDictionary(uri: Uri) {
        // Show a toast to indicate the import has started
        Toast.makeText(this, getString(R.string.importing_dictionary), Toast.LENGTH_SHORT).show()

        // Show progress bar
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true
        importDictionaryButton.isEnabled = false

        // Launch a coroutine to handle the import in the background
        lifecycleScope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    // Call your ViewModel method to handle the actual import
                    viewModel.importYomitanDictionary(this@MainActivity, uri)
                }

                // Show result toast based on import success
                val messageResId = if (success) R.string.import_success else R.string.import_error
                Toast.makeText(this@MainActivity, getString(messageResId), Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                // Handle exception
                Log.e("MainActivity", "Error importing dictionary", e)
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.import_error_with_message, e.message),
                    Toast.LENGTH_LONG
                ).show()

                // Reset UI
                progressBar.visibility = View.GONE
                importDictionaryButton.isEnabled = true
            }
        }
    }

    private fun updateImportProgress(progress: Int) {
        when {
            progress < 0 -> {
                // Error occurred
                progressBar.visibility = View.GONE
                importDictionaryButton.isEnabled = true
            }
            progress == 0 -> {
                // Just started
                progressBar.isIndeterminate = true
                progressBar.visibility = View.VISIBLE
                importDictionaryButton.isEnabled = false
            }
            progress == 100 -> {
                // Completed
                progressBar.visibility = View.GONE
                importDictionaryButton.isEnabled = true
                Toast.makeText(this, getString(R.string.import_success), Toast.LENGTH_SHORT).show()
            }
            else -> {
                // In progress
                progressBar.isIndeterminate = false
                progressBar.progress = progress
            }
        }
    }

    private fun updateDictionaryInfo(info: DictionaryInfo?) {
        if (info != null) {
            val infoText = getString(
                R.string.dictionary_info,
                info.title,
                info.sourceLanguage,
                info.targetLanguage
            )
            dictionaryInfoTextView.text = infoText
            dictionaryInfoTextView.visibility = View.VISIBLE
        } else {
            dictionaryInfoTextView.visibility = View.GONE
        }
    }
}