package com.abaga129.tekisuto.ui.dictionary

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.abaga129.tekisuto.R
import com.abaga129.tekisuto.database.DictionaryMetadataEntity
import com.abaga129.tekisuto.database.DictionaryRepository
import com.abaga129.tekisuto.viewmodel.DictionaryManagerViewModel
import com.google.android.material.floatingactionbutton.FloatingActionButton

class DictionaryManagerActivity : AppCompatActivity() {

    private lateinit var viewModel: DictionaryManagerViewModel
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: DictionaryAdapter

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

        // Set up the action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.dictionary_manager_title)

        // Initialize views
        recyclerView = findViewById(R.id.dictionaries_recycler_view)
        emptyView = findViewById(R.id.empty_view)
        progressBar = findViewById(R.id.progress_bar)

        // Initialize the ViewModel
        val repository = DictionaryRepository(applicationContext)
        val factory = DictionaryManagerViewModel.Factory(repository)
        viewModel = ViewModelProvider(this, factory)[DictionaryManagerViewModel::class.java]

        // Initialize the adapter
        adapter = DictionaryAdapter(
            onDeleteClick = { dictionary, _ -> confirmDeleteDictionary(dictionary) },
            onMoveUpClick = { position -> viewModel.moveDictionaryUp(position) },
            onMoveDownClick = { position -> viewModel.moveDictionaryDown(position) }
        )

        // Set up the RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Import button setup
        val fabImport: FloatingActionButton = findViewById(R.id.fab_import_dictionary)
        fabImport.setOnClickListener {
            openDictionaryPicker()
        }

        // Observe dictionaries
        viewModel.dictionaries.observe(this) { dictionaries ->
            adapter.submitList(dictionaries)
            updateEmptyView(dictionaries.isEmpty())
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

    private fun updateEmptyView(isEmpty: Boolean) {
        emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
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