package com.abaga129.tekisuto.ui.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.abaga129.tekisuto.R
import com.abaga129.tekisuto.ui.adapter.VoiceSelectionAdapter
import com.abaga129.tekisuto.util.AzureVoiceInfo
import com.abaga129.tekisuto.util.AzureVoiceService
import com.abaga129.tekisuto.util.SpeechService
import com.abaga129.tekisuto.util.VoiceTestHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Dialog for selecting an Azure Text-to-Speech voice
 */
class VoiceSelectionDialog(
    context: Context,
    private val currentVoiceName: String,
    private val languageCode: String,
    private val onVoiceSelected: (String) -> Unit
) : Dialog(context), CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    // UI components
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchBox: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: TextView
    private lateinit var btnOk: Button
    private lateinit var btnCancel: Button
    private lateinit var btnRefresh: Button
    private lateinit var btnTestVoice: Button
    
    // Adapters and services
    private lateinit var adapter: VoiceSelectionAdapter
    private lateinit var voiceService: AzureVoiceService
    private lateinit var speechService: SpeechService
    private lateinit var voiceTestHelper: VoiceTestHelper
    
    // Selected voice
    private var selectedVoice: AzureVoiceInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_voice_selection)
        
        // Set dialog title
        setTitle(R.string.voice_selection_title)
        
        // Initialize services
        voiceService = AzureVoiceService(context)
        speechService = SpeechService(context)
        voiceTestHelper = VoiceTestHelper(context)
        
        // Initialize UI components
        recyclerView = findViewById(R.id.voices_recycler_view)
        searchBox = findViewById(R.id.search_box)
        progressBar = findViewById(R.id.progress_bar)
        emptyView = findViewById(R.id.empty_view)
        btnOk = findViewById(R.id.btn_ok)
        btnCancel = findViewById(R.id.btn_cancel)
        btnRefresh = findViewById(R.id.btn_refresh)
        btnTestVoice = findViewById(R.id.btn_test_voice)
        
        // Initialize test voice button state
        btnTestVoice.isEnabled = false
        
        // Set up RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = VoiceSelectionAdapter { voice ->
            selectedVoice = voice
            btnOk.isEnabled = true
            btnTestVoice.isEnabled = true
        }
        recyclerView.adapter = adapter
        
        // Initialize button states
        btnOk.isEnabled = false
        btnTestVoice.isEnabled = false
        
        // Set up listeners
        setupListeners()
        
        // Load voices
        loadVoices()
        
        // Set dialog size
        window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }
    
    /**
     * Set up event listeners for UI components
     */
    private fun setupListeners() {
        // Search box text change listener
        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: Editable?) {
                adapter.filter(s.toString())
            }
        })
        
        // Button click listeners
        btnOk.setOnClickListener {
            selectedVoice?.let { voice ->
                onVoiceSelected(voice.name)
                dismiss()
            }
        }
        
        btnCancel.setOnClickListener {
            dismiss()
        }
        
        btnRefresh.setOnClickListener {
            voiceService.clearCache()
            loadVoices()
        }
        
        btnTestVoice.setOnClickListener {
            selectedVoice?.let { voice ->
                testSelectedVoice(voice.name)
            }
        }
    }
    
    /**
     * Test the selected voice
     */
    private fun testSelectedVoice(voiceName: String) {
        // Disable test button during test
        btnTestVoice.isEnabled = false
        
        // Launch a coroutine to test the voice
        launch {
            val success = voiceTestHelper.testVoice(voiceName, languageCode)
            
            // Re-enable test button
            btnTestVoice.isEnabled = true
            
            if (!success) {
                // Show toast with error message
                Toast.makeText(
                    context,
                    "Failed to test voice. Check API key and internet connection.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    /**
     * Load available voices from the Azure service
     */
    private fun loadVoices() {
        launch {
            try {
                // Show progress and hide other views
                progressBar.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
                emptyView.visibility = View.GONE
                
                // Get voices
                val voices = withContext(Dispatchers.IO) {
                    voiceService.getVoices()
                }
                
                // Filter voices to appropriate language if specified
                val filteredVoices = if (languageCode.isNotEmpty()) {
                    // Improved filtering to ensure we get language-specific voices
                    voices.filter { 
                        it.shortName.startsWith(languageCode, ignoreCase = true) || 
                        it.locale.startsWith(languageCode, ignoreCase = true) 
                    }
                } else {
                    voices
                }
                
                // Log the filtering results for debugging
                android.util.Log.d("VoiceSelectionDialog", "Language code: $languageCode")
                android.util.Log.d("VoiceSelectionDialog", "Total voices: ${voices.size}")
                android.util.Log.d("VoiceSelectionDialog", "Filtered voices: ${filteredVoices.size}")
                if (filteredVoices.isNotEmpty()) {
                    android.util.Log.d("VoiceSelectionDialog", "First voice: ${filteredVoices[0].name}, locale: ${filteredVoices[0].locale}")
                }
                
                // Update UI based on results
                if (filteredVoices.isEmpty()) {
                    emptyView.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    adapter.setVoices(filteredVoices, currentVoiceName)
                    recyclerView.visibility = View.VISIBLE
                    emptyView.visibility = View.GONE
                    
                    // Enable OK button if current voice is in the list
                    if (currentVoiceName.isNotEmpty()) {
                        selectedVoice = filteredVoices.find { it.name == currentVoiceName }
                        btnOk.isEnabled = selectedVoice != null
                    }
                }
                
                // Hide progress
                progressBar.visibility = View.GONE
                
            } catch (e: Exception) {
                // Show error
                emptyView.text = context.getString(R.string.error_loading_voices)
                emptyView.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
                progressBar.visibility = View.GONE
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        job.cancel()
    }
}