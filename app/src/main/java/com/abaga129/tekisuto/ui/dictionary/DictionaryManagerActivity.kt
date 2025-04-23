package com.abaga129.tekisuto.ui.dictionary

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.abaga129.tekisuto.R
import com.abaga129.tekisuto.database.DictionaryMetadataEntity
import com.abaga129.tekisuto.database.DictionaryRepository
import com.abaga129.tekisuto.ui.BaseEdgeToEdgeActivity
import com.abaga129.tekisuto.util.ProfileSettingsManager
import com.abaga129.tekisuto.viewmodel.DictionaryManagerViewModel
import com.abaga129.tekisuto.viewmodel.ProfileViewModel
import com.google.android.material.floatingactionbutton.FloatingActionButton

class DictionaryManagerActivity : BaseEdgeToEdgeActivity() {

    private lateinit var viewModel: DictionaryManagerViewModel
    private lateinit var profileViewModel: ProfileViewModel
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: DictionaryAdapter
    private lateinit var profileSettingsManager: ProfileSettingsManager
    private var showOnlyProfileDictionaries = false

    // Launcher for dictionary file selection
    private val selectDictionaryLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            importDictionary(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dictionary_manager)
        
        // Apply insets to the root view
        applyInsetsToView(android.R.id.content)

        // Set up the action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.dictionary_manager_title)

        // Initialize views
        recyclerView = findViewById(R.id.dictionaries_recycler_view)
        emptyView = findViewById(R.id.empty_view)
        progressBar = findViewById(R.id.progress_bar)

        // Initialize profile settings manager
        profileSettingsManager = ProfileSettingsManager(applicationContext)

        // Initialize the dictionary repository and ViewModel
        val repository = DictionaryRepository(applicationContext)
        val factory = DictionaryManagerViewModel.Factory(repository)
        viewModel = ViewModelProvider(this, factory)[DictionaryManagerViewModel::class.java]

        // Initialize the profile ViewModel
        profileViewModel = ViewModelProvider(this)[ProfileViewModel::class.java]

        // Set the active profile in the dictionary ViewModel
        val activeProfileId = profileSettingsManager.getCurrentProfileId()
        if (activeProfileId > 0) {
            viewModel.setActiveProfile(activeProfileId)
            profileViewModel.loadCurrentProfile()
        }

        // Initialize the adapter with profile association handler
        adapter = DictionaryAdapter(
            onDeleteClick = { dictionary, _ -> confirmDeleteDictionary(dictionary) },
            onMoveUpClick = { position -> viewModel.moveDictionaryUp(position) },
            onMoveDownClick = { position -> viewModel.moveDictionaryDown(position) },
            onProfileAssociationChanged = { dictionary, isChecked ->
                if (isChecked) {
                    viewModel.addDictionaryToProfile(dictionary.id)
                } else {
                    viewModel.removeDictionaryFromProfile(dictionary.id)
                }
            }
        )

        // Set up the RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Import button setup
        val fabImport: FloatingActionButton = findViewById(R.id.fab_import_dictionary)
        fabImport.setOnClickListener {
            openDictionaryPicker()
        }

        // Observe dictionaries and all dictionaries (used in toggle view)
        viewModel.dictionaries.observe(this) { dictionaries ->
            // Load all dictionaries
            val allDictionaries = viewModel.allDictionaries.value ?: emptyList()
            
            if (dictionaries.isEmpty() && allDictionaries.isEmpty()) {
                // No dictionaries at all
                updateEmptyView(true)
            } else {
                updateEmptyView(false)
                
                if (dictionaries.isEmpty() && showOnlyProfileDictionaries) {
                    // If profile has no dictionaries but there are dictionaries available,
                    // automatically switch to show all dictionaries
                    showOnlyProfileDictionaries = false
                    invalidateOptionsMenu() // Update menu text
                    adapter.setShowProfileCheckbox(true) // Show checkboxes
                    adapter.submitDictionaryList(allDictionaries, dictionaries)
                } else if (showOnlyProfileDictionaries) {
                    // Show only profile dictionaries
                    adapter.submitDictionaryList(dictionaries, dictionaries)
                } else {
                    // Show all dictionaries with checkmarks for those in profile
                    adapter.submitDictionaryList(allDictionaries, dictionaries)
                }
            }
        }

        // Update adapter display mode based on profile
        profileViewModel.currentProfile.observe(this) { profile ->
            if (profile != null) {
                supportActionBar?.subtitle = getString(R.string.current_profile, profile.name)
                
                // Show checkboxes in adapter when in "all dictionaries" mode
                adapter.setShowProfileCheckbox(!showOnlyProfileDictionaries)
            }
        }

        // Observe loading state
        viewModel.isLoading.observe(this) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        // Observe errors
        viewModel.error.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            }
        }

        // Observe import progress
        viewModel.importProgress.observe(this) { progress ->
            updateImportProgress(progress)
        }

        // Load dictionaries
        viewModel.loadDictionaries()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.dictionary_manager_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        // Update the toggle menu item text based on current state
        val toggleItem = menu.findItem(R.id.action_toggle_view)
        
        // Check if the current profile has dictionaries
        val profileHasDictionaries = (viewModel.dictionaries.value?.isNotEmpty() == true)
        
        // Update menu text based on state and whether profile has dictionaries
        toggleItem.title = when {
            showOnlyProfileDictionaries -> 
                getString(R.string.show_all_dictionaries)
            profileHasDictionaries -> 
                getString(R.string.show_profile_dictionaries)
            else -> 
                "Show All Dictionaries" // Currently showing all since profile has none
        }
        
        // Disable toggle if there are no dictionaries in the profile and we're already showing all
        toggleItem.isEnabled = profileHasDictionaries || showOnlyProfileDictionaries
        
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            R.id.action_toggle_view -> {
                // Toggle between showing all dictionaries and profile dictionaries
                val profileDictionaries = viewModel.dictionaries.value ?: emptyList()
                
                // Check if we're trying to switch to profile dictionaries but there are none
                if (!showOnlyProfileDictionaries && profileDictionaries.isEmpty()) {
                    // User trying to switch to empty profile dictionaries - show a toast explaining the issue
                    Toast.makeText(
                        this,
                        getString(R.string.profile_no_dictionaries),
                        Toast.LENGTH_LONG
                    ).show()
                    
                    // Keep showing all dictionaries since the profile has none
                    showOnlyProfileDictionaries = false
                } else {
                    // Normal toggle behavior
                    showOnlyProfileDictionaries = !showOnlyProfileDictionaries
                }
                
                // Show checkboxes only when showing all dictionaries
                adapter.setShowProfileCheckbox(!showOnlyProfileDictionaries)
                
                // Refresh the dictionaries display
                viewModel.loadDictionaries()
                
                // Update the menu item
                invalidateOptionsMenu()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun updateEmptyView(isEmpty: Boolean) {
        if (isEmpty) {
            // No dictionaries at all
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            emptyView.text = getString(R.string.no_dictionaries)
        } else if (showOnlyProfileDictionaries && (viewModel.dictionaries.value?.isEmpty() == true)) {
            // Has dictionaries but none in this profile
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            emptyView.text = getString(R.string.no_profile_dictionaries)
        } else {
            // Has dictionaries to show
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }
    
    /**
     * Checks if the active profile has any dictionaries
     */
    private fun profileHasDictionaries(): Boolean {
        return viewModel.dictionaries.value?.isNotEmpty() == true
    }

    private fun openDictionaryPicker() {
        selectDictionaryLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
    }

    private fun importDictionary(uri: Uri) {
        // Show progress bar
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true

        // Show toast
        Toast.makeText(this, getString(R.string.importing_dictionary), Toast.LENGTH_SHORT).show()

        // Start import
        viewModel.importDictionary(this, uri)
    }

    private fun updateImportProgress(progress: Int) {
        when {
            progress < 0 -> {
                // Error occurred
                progressBar.visibility = View.GONE
            }
            progress == 0 -> {
                // Just started
                progressBar.isIndeterminate = true
                progressBar.visibility = View.VISIBLE
            }
            progress == 100 -> {
                // Completed
                progressBar.visibility = View.GONE
                Toast.makeText(this, getString(R.string.import_success), Toast.LENGTH_SHORT).show()
            }
            else -> {
                // In progress
                progressBar.isIndeterminate = false
                progressBar.progress = progress
            }
        }
    }

    private fun confirmDeleteDictionary(dictionary: DictionaryMetadataEntity) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_dictionary))
            .setMessage(getString(R.string.delete_dictionary_confirm))
            .setPositiveButton(getString(android.R.string.ok)) { _, _ ->
                deleteDictionary(dictionary.id)
            }
            .setNegativeButton(getString(android.R.string.cancel), null)
            .show()
    }

    private fun deleteDictionary(dictionaryId: Long) {
        viewModel.deleteDictionary(dictionaryId)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}