package com.abaga129.tekisuto.ui.dictionary

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.emoji2.text.EmojiCompat
import androidx.emoji2.widget.EmojiTextView
import androidx.lifecycle.coroutineScope
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.abaga129.tekisuto.R
import com.abaga129.tekisuto.database.DictionaryRepository
import com.abaga129.tekisuto.ui.adapter.DictionaryMatchAdapter
import com.abaga129.tekisuto.ui.adapter.DictionaryMatchItemDecoration
import com.abaga129.tekisuto.viewmodel.DictionaryBrowserViewModel

class DictionaryBrowserFragment : Fragment(), DictionaryMatchAdapter.OnAnkiExportListener {

    private lateinit var viewModel: DictionaryBrowserViewModel
    private lateinit var repository: DictionaryRepository
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchEditText: EditText
    private lateinit var clearSearchButton: ImageButton
    private lateinit var loadingProgressBar: ProgressBar
    private lateinit var noResultsTextView: TextView
    private lateinit var dictionaryStatsTextView: TextView
    
    private val dictionaryAdapter = DictionaryMatchAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        setHasOptionsMenu(true)
        return inflater.inflate(R.layout.fragment_dictionary_browser, container, false)
    }
    
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.dictionary_browser_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear_dictionary -> {
                clearDictionary()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize ViewModel
        viewModel = ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application))
            .get(DictionaryBrowserViewModel::class.java)
        
        // Initialize repository
        repository = DictionaryRepository(requireContext())
        viewModel.initRepository(repository)
        
        // Initialize views
        recyclerView = view.findViewById(R.id.dictionary_recycler_view)
        searchEditText = view.findViewById(R.id.search_edit_text)
        clearSearchButton = view.findViewById(R.id.clear_search_button)
        loadingProgressBar = view.findViewById(R.id.loading_progress_bar)
        noResultsTextView = view.findViewById(R.id.no_results_text_view)
        dictionaryStatsTextView = view.findViewById(R.id.dictionary_stats)
        
        // Setup RecyclerView
        recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = dictionaryAdapter
            addItemDecoration(DictionaryMatchItemDecoration(resources.getDimensionPixelSize(R.dimen.dictionary_item_spacing)))
        }
        
        // Configure the adapter with lifecycle scope, repository, and Anki export listener
        dictionaryAdapter.setLifecycleScope(lifecycle.coroutineScope)
        dictionaryAdapter.setDictionaryRepository(repository)
        dictionaryAdapter.setOnAnkiExportListener(this)
        
        // Setup search functionality
        setupSearch()
        
        // Observe ViewModel
        observeViewModel()
        
        // Load initial data
        viewModel.loadAllEntries()
    }
    
    private fun setupSearch() {
        // Text change listener for search
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                clearSearchButton.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                
                // Debounce search queries
                searchEditText.removeCallbacks(searchRunnable)
                searchEditText.postDelayed(searchRunnable, 300)
            }
            
            override fun afterTextChanged(s: Editable?) {}
        })
        
        // Clear search button
        clearSearchButton.setOnClickListener {
            searchEditText.setText("")
            viewModel.loadAllEntries()
        }
    }
    
    private val searchRunnable = Runnable {
        val query = searchEditText.text.toString().trim()
        if (query.isNotEmpty()) {
            viewModel.setSearchQuery(query)
        } else {
            viewModel.loadAllEntries()
        }
    }
    
    private fun observeViewModel() {
        // Observe dictionary entries
        viewModel.entries.observe(viewLifecycleOwner) { entries ->
            dictionaryAdapter.submitList(entries)
            
            // Show/hide no results message
            val isLoading = viewModel.isLoading.value ?: false
            val showNoResults = entries.isEmpty() && !isLoading
            noResultsTextView.isVisible = showNoResults
            recyclerView.isVisible = entries.isNotEmpty()
        }
        
        // Observe loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            loadingProgressBar.isVisible = isLoading
            if (isLoading) {
                noResultsTextView.isVisible = false
            } else {
                // Show no results if needed
                val entries = viewModel.entries.value ?: emptyList()
                noResultsTextView.isVisible = entries.isEmpty()
                recyclerView.isVisible = entries.isNotEmpty()
            }
        }
        
        // Observe dictionary stats
        viewModel.entryCount.observe(viewLifecycleOwner) { count ->
            try {
                // Try to use EmojiCompat if available
                if (dictionaryStatsTextView is EmojiTextView) {
                    // EmojiTextView will handle emoji processing automatically
                    dictionaryStatsTextView.text = getString(R.string.dictionary_stats, count)
                } else {
                    // For TextView, manually process with EmojiCompat
                    val statsText = getString(R.string.dictionary_stats, count)
                    try {
                        dictionaryStatsTextView.text = EmojiCompat.get().process(statsText)
                    } catch (e: Exception) {
                        Log.e("DictionaryBrowserFragment", "Error processing emoji in stats text: ${e.message}")
                        dictionaryStatsTextView.text = statsText
                    }
                }
            } catch (e: Exception) {
                Log.e("DictionaryBrowserFragment", "Error setting stats text with emoji: ${e.message}")
                // Fallback to standard text setting
                dictionaryStatsTextView.text = getString(R.string.dictionary_stats, count)
            }
        }
    }
    
    private fun clearDictionary() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.clear_dictionary_title)
            .setMessage(R.string.clear_dictionary_message)
            .setPositiveButton(R.string.clear) { _, _ ->
                try {
                    // Use the ViewModel to clear the dictionary
                    viewModel.clearDictionary()
                    
                    // Show success message
                    Toast.makeText(
                        requireContext(),
                        R.string.dictionary_cleared,
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    // Show error message
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.error_clearing_dictionary, e.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    /**
     * Handle Anki export request from the adapter
     */
    override fun onExportToAnki(entry: com.abaga129.tekisuto.database.DictionaryEntryEntity) {
        // Launch AnkiDroid config activity for exporting
        val intent = android.content.Intent(requireContext(), com.abaga129.tekisuto.ui.anki.AnkiDroidConfigActivity::class.java).apply {
            putExtra("term", entry.term)
            putExtra("reading", entry.reading)
            putExtra("definition", entry.definition)
            putExtra("partOfSpeech", entry.partOfSpeech)
            putExtra("dictionaryId", entry.dictionaryId)
            putExtra("isHtmlContent", entry.isHtmlContent)
        }
        startActivity(intent)
    }
    
    /**
     * Refresh the UI when returning to the fragment to update exported status indicators
     */
    override fun onResume() {
        super.onResume()
        
        // Force refresh the adapter to update exported status indicators
        val currentEntries = viewModel.entries.value
        if (currentEntries != null && currentEntries.isNotEmpty()) {
            // Recreate the list to force DiffUtil to recheck all items
            dictionaryAdapter.submitList(null)
            dictionaryAdapter.submitList(currentEntries.toList())
            android.util.Log.d("DictionaryBrowserFragment", "Refreshed adapter with ${currentEntries.size} entries to update exported status")
        }
    }
}