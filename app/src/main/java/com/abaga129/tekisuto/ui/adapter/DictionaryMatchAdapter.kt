package com.abaga129.tekisuto.ui.adapter

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.text.Html
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.emoji2.text.EmojiCompat
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.abaga129.tekisuto.R
import com.abaga129.tekisuto.database.DictionaryEntryEntity
import com.abaga129.tekisuto.database.DictionaryRepository
import com.abaga129.tekisuto.util.FrequencyShieldHelper
import com.abaga129.tekisuto.util.LanguageDetector
import com.abaga129.tekisuto.util.SpeechService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Adapter for displaying dictionary matches
 */
class DictionaryMatchAdapter : ListAdapter<DictionaryEntryEntity, DictionaryMatchAdapter.ViewHolder>(DiffCallback()) {

    private var lifecycleScope: LifecycleCoroutineScope? = null
    private var ocrText: String? = null
    private var screenshotPath: String? = null
    private var ankiExportListener: OnAnkiExportListener? = null
    private var audioListener: OnAudioPlayListener? = null
    private var speechService: SpeechService? = null
    private var dictionaryRepository: DictionaryRepository? = null

    interface OnAnkiExportListener {
        fun onExportToAnki(entry: DictionaryEntryEntity)
    }

    interface OnAudioPlayListener {
        fun onPlayAudio(term: String, language: String)
    }

    fun setLifecycleScope(scope: LifecycleCoroutineScope) {
        this.lifecycleScope = scope
    }

    fun setOcrText(text: String) {
        this.ocrText = text
    }

    fun setScreenshotPath(path: String) {
        this.screenshotPath = path
    }

    fun setOnAnkiExportListener(listener: OnAnkiExportListener) {
        this.ankiExportListener = listener
    }

    fun setOnAudioPlayListener(listener: OnAudioPlayListener) {
        this.audioListener = listener
    }

    fun setSpeechService(service: SpeechService) {
        this.speechService = service
    }

    fun setDictionaryRepository(repository: DictionaryRepository) {
        this.dictionaryRepository = repository
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dictionary_match, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = getItem(position)
        holder.bind(entry, ankiExportListener, audioListener, speechService, dictionaryRepository, lifecycleScope)
    }

    /**
     * Force refresh of exported word status when notifyDataSetChanged is called
     */
    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        // Clear any previous state to ensure fresh checking of exported status
        holder.resetExportedState()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val termTextView: TextView = itemView.findViewById(R.id.term_text)
        private val readingTextView: TextView = itemView.findViewById(R.id.reading_text)
        private val partOfSpeechTextView: TextView = itemView.findViewById(R.id.part_of_speech_text)
        private val frequencyTextView: TextView = itemView.findViewById(R.id.frequency_text)
        private val definitionTextView: TextView = itemView.findViewById(R.id.definition_text)
        private val exportToAnkiButton: ImageButton = itemView.findViewById(R.id.export_to_anki_button)
        private val playAudioButton: ImageButton = itemView.findViewById(R.id.play_audio_button)
        private val cardView: CardView = itemView.findViewById(R.id.dictionary_item_card)
        
        // Frequency shield views
        private val frequencyShieldContainer: LinearLayout? = itemView.findViewById(R.id.shield_container)
        private val dictionaryNameView: TextView? = itemView.findViewById(R.id.shield_dictionary_name)
        private val frequencyValueView: TextView? = itemView.findViewById(R.id.shield_frequency_value)

        /**
         * Get theme-aware surface color to respect light/dark theme
         */
        private fun getThemeSurfaceColor(): Int {
            val typedValue = TypedValue()
            val theme = itemView.context.theme
            theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)
            return typedValue.data
        }

        fun bind(
            entry: DictionaryEntryEntity,
            ankiExportListener: OnAnkiExportListener?,
            audioListener: OnAudioPlayListener?,
            speechService: SpeechService?,
            dictionaryRepository: DictionaryRepository?,
            lifecycleScope: LifecycleCoroutineScope?
        ) {
            // Log entry details for debugging
            Log.d("DictionaryAdapter", "Processing entry: ${entry.term}, isHtml=${entry.isHtmlContent}, dictionaryId=${entry.dictionaryId}")

            // Process text with EmojiCompat
            val emojiCompat = EmojiCompat.get()

            // Display term or show placeholder if empty
            if (entry.term.isNotBlank()) {
                // Use EmojiCompat to process the text
                try {
                    val processedTerm = emojiCompat.process(entry.term)
                    termTextView.text = processedTerm
                } catch (e: Exception) {
                    Log.e("DictionaryAdapter", "Error processing emoji in term: ${e.message}")
                    termTextView.text = entry.term
                }
                termTextView.visibility = View.VISIBLE
            } else {
                termTextView.visibility = View.GONE
            }

            // Display reading or hide if empty
            if (entry.reading.isNotBlank()) {
                try {
                    val processedReading = emojiCompat.process(entry.reading)
                    readingTextView.text = processedReading
                } catch (e: Exception) {
                    Log.e("DictionaryAdapter", "Error processing emoji in reading: ${e.message}")
                    readingTextView.text = entry.reading
                }
                readingTextView.visibility = View.VISIBLE
            } else {
                readingTextView.visibility = View.GONE
            }

            // Display part of speech or hide if empty
            if (entry.partOfSpeech.isNotBlank()) {
                try {
                    val processedPos = emojiCompat.process(entry.partOfSpeech)
                    partOfSpeechTextView.text = processedPos
                } catch (e: Exception) {
                    Log.e("DictionaryAdapter", "Error processing emoji in part of speech: ${e.message}")
                    partOfSpeechTextView.text = entry.partOfSpeech
                }
                partOfSpeechTextView.visibility = View.VISIBLE
            } else {
                partOfSpeechTextView.visibility = View.GONE
            }

            // Check for frequency data and display if available
            // Hide legacy frequency text view (now handled by shield)
            frequencyTextView.visibility = View.GONE
            
            // Use the shield for frequency display if we have the right views
            if (dictionaryRepository != null && lifecycleScope != null && 
                frequencyShieldContainer != null && dictionaryNameView != null && frequencyValueView != null) {
                
                // Initially hide the shield until we have data
                FrequencyShieldHelper.hideFrequencyShield(frequencyShieldContainer)
                
                lifecycleScope.launch {
                    try {
                        // Try to fetch frequency data for this term in the specific dictionary
                        var frequencyData = dictionaryRepository.getFrequencyForWordInDictionary(entry.term, entry.dictionaryId)
                        var dictionaryMetadata = dictionaryRepository.getDictionaryMetadata(entry.dictionaryId)
                        
                        // If not found in specific dictionary, try to find in any dictionary
                        if (frequencyData == null) {
                            Log.d("DictionaryAdapter", "No frequency in dictionary ${entry.dictionaryId}, checking all dictionaries...")
                            frequencyData = dictionaryRepository.getFrequencyForWord(entry.term)
                            
                            // If found in another dictionary, get that dictionary's metadata
                            if (frequencyData != null) {
                                dictionaryMetadata = dictionaryRepository.getDictionaryMetadata(frequencyData.dictionaryId)
                                Log.d("DictionaryAdapter", "Found frequency in dictionary ${frequencyData.dictionaryId}: #${frequencyData.frequency}")
                            }
                        }
                        
                        // Update UI on the main thread
                        withContext(Dispatchers.Main) {
                            if (frequencyData != null && dictionaryMetadata != null) {
                                // Get a shortened dictionary name suitable for display in the shield
                                val shortDictionaryName = FrequencyShieldHelper.getShortDictionaryName(dictionaryMetadata.title)
                                
                                // Configure the shield with the data
                                FrequencyShieldHelper.setupFrequencyShield(
                                    itemView.context,
                                    frequencyShieldContainer,
                                    dictionaryNameView,
                                    frequencyValueView,
                                    shortDictionaryName,
                                    frequencyData.frequency
                                )
                                
                                Log.d("DictionaryAdapter", "Displayed frequency shield for '${entry.term}': ${shortDictionaryName} #${frequencyData.frequency}")
                            } else {
                                // Hide the shield if we don't have data
                                FrequencyShieldHelper.hideFrequencyShield(frequencyShieldContainer)
                                Log.d("DictionaryAdapter", "No frequency data available for '${entry.term}'")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("DictionaryAdapter", "Error retrieving frequency data: ${e.message}", e)
                        withContext(Dispatchers.Main) {
                            // Hide the shield on error
                            FrequencyShieldHelper.hideFrequencyShield(frequencyShieldContainer)
                        }
                    }
                }
            } else {
                // If we're missing any required views, hide the legacy frequency view
                frequencyTextView.visibility = View.GONE
            }

            // Display definition or show placeholder if empty
            if (entry.definition.isNotBlank()) {
                // Check if the content is HTML
                Log.d("DictionaryAdapter", "isHtmlContent flag: ${entry.isHtmlContent}")
                Log.d("DictionaryAdapter", "Definition content (first 100 chars): ${entry.definition.take(100)}")

                // Double-check content format
                val looksLikeRawJson = entry.definition.contains("\"tag\":") ||
                                       entry.definition.contains("\"content\":") ||
                                       entry.definition.startsWith("{") ||
                                       entry.definition.startsWith("[")

                val looksLikeHtml = entry.definition.contains("<div") ||
                                    entry.definition.contains("<p>") ||
                                    entry.definition.contains("<ol") ||
                                    entry.definition.contains("<span")

                // Use HTML if:
                // 1. The isHtmlContent flag is set AND it doesn't look like raw JSON
                // 2. OR if it looks like HTML regardless of the flag (legacy content support)
                if ((entry.isHtmlContent && !looksLikeRawJson) || looksLikeHtml) {
                    try {
                        Log.d("DictionaryAdapter", "Rendering HTML content")

                        // Use Html.fromHtml to render the HTML content
                        val htmlContent: Spanned = Html.fromHtml(
                            entry.definition,
                            Html.FROM_HTML_MODE_COMPACT
                        )

                        // Process the spanned content with EmojiCompat
                        try {
                            val processedContent = emojiCompat.process(htmlContent)
                            if (processedContent != null) {
                                definitionTextView.text = processedContent
                                Log.d("DictionaryAdapter", "HTML rendered with emoji support. Length: ${processedContent.length}")
                            } else {
                                definitionTextView.text = htmlContent
                                Log.d("DictionaryAdapter", "EmojiCompat.process returned null, using original HTML content")
                            }
                        } catch (e: Exception) {
                            Log.e("DictionaryAdapter", "Error processing emoji in HTML content: ${e.message}", e)
                            // Fallback to regular HTML content
                            definitionTextView.text = htmlContent
                        }
                    } catch (e: Exception) {
                        Log.e("DictionaryAdapter", "Error rendering HTML: ${e.message}", e)
                        // Fallback to plain text on error
                        definitionTextView.text = "Error displaying definition. Please report this issue."
                    }
                } else {
                    // If it looks like raw JSON but was marked as HTML, log an error
                    if (entry.isHtmlContent && looksLikeRawJson) {
                        Log.e("DictionaryAdapter", "Entry marked as HTML but contains raw JSON/tags!")
                    }

                    // Format plain text definition - preserve line breaks and add spacing
                    try {
                        // Process with EmojiCompat first
                        val processedText = emojiCompat.process(entry.definition)

                        // Handle the null case safely
                        if (processedText != null) {
                            val processedString = processedText.toString()
                            if (processedString.contains("\n")) {
                                // If there are newlines, add extra spacing
                                definitionTextView.text = processedString.replace("\n", "\n\n")
                            } else {
                                // No newlines to replace
                                definitionTextView.text = processedString
                            }
                        } else {
                            // If processedText is null, use the original definition
                            if (entry.definition.contains("\n")) {
                                definitionTextView.text = entry.definition.replace("\n", "\n\n")
                            } else {
                                definitionTextView.text = entry.definition
                            }
                        }

                        Log.d("DictionaryAdapter", "Setting plain text definition with emoji support")
                    } catch (e: Exception) {
                        Log.e("DictionaryAdapter", "Error processing emoji in plain text: ${e.message}", e)
                        // Fallback to regular plain text
                        if (entry.definition.contains("\n")) {
                            definitionTextView.text = entry.definition.replace("\n", "\n\n")
                        } else {
                            definitionTextView.text = entry.definition
                        }
                        Log.d("DictionaryAdapter", "Setting plain text definition (without emoji support)")
                    }
                }

                definitionTextView.visibility = View.VISIBLE
            } else {
                definitionTextView.text = "(No definition available)"
                definitionTextView.visibility = View.VISIBLE
            }

            // Check if the word is already in AnkiDroid
            if (dictionaryRepository != null && lifecycleScope != null) {
                lifecycleScope.launch {
                    val isExported = dictionaryRepository.isWordExported(entry.term)

                    // Update the UI based on export status
                    withContext(Dispatchers.Main) {
                        if (isExported) {
                            // Change card background to indicate it's already exported
                            cardView.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.card_exported))

                            // Add a hint to the export button
                            exportToAnkiButton.setImageTintList(ColorStateList.valueOf(
                                ContextCompat.getColor(itemView.context, R.color.exported_hint)))

                            // Add a small text indicator to term text with emoji support
                            try {
                                val exportedText = emojiCompat.process("${entry.term} ✓")
                                if (exportedText != null) {
                                    termTextView.text = exportedText
                                } else {
                                    termTextView.text = "${entry.term} ✓"
                                }
                            } catch (e: Exception) {
                                Log.e("DictionaryAdapter", "Error processing emoji in exported term: ${e.message}")
                                termTextView.text = "${entry.term} ✓"
                            }
                        } else {
                            // Reset to normal colors - use theme-aware color
                            cardView.setCardBackgroundColor(getThemeSurfaceColor())
                            exportToAnkiButton.setImageTintList(null)

                            // Use emoji support for the term
                            try {
                                val termText = emojiCompat.process(entry.term)
                                if (termText != null) {
                                    termTextView.text = termText
                                } else {
                                    termTextView.text = entry.term
                                }
                            } catch (e: Exception) {
                                Log.e("DictionaryAdapter", "Error processing emoji in term: ${e.message}")
                                termTextView.text = entry.term
                            }
                        }
                    }
                }
            }

            // Set up export to Anki button
            exportToAnkiButton.setOnClickListener {
                ankiExportListener?.onExportToAnki(entry)
            }

            // Set up play audio button
            val sharedPrefs = itemView.context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
            val audioEnabled = sharedPrefs.getBoolean("enable_audio", true)

            if (audioEnabled && entry.term.isNotBlank()) {
                playAudioButton.visibility = View.VISIBLE
                playAudioButton.setOnClickListener {
                    // IMPORTANT: Always make a DIRECT check of the API key to work around any caching issues
                    // Get preferences directly
                    val prefManager = PreferenceManager.getDefaultSharedPreferences(itemView.context)
                    val azureKey = prefManager.getString("azure_speech_key", "")

                    // If no key in preferences, try app_preferences as backup
                    val hasApiKey = if (azureKey.isNullOrEmpty()) {
                        // Fallback to app_preferences
                        val appPrefs = itemView.context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
                        val backupKey = appPrefs.getString("azure_speech_key", "")
                        !backupKey.isNullOrEmpty()
                    } else {
                        true
                    }

                    // Determine language from the dictionary entry or default to Japanese if unknown
                    val language = guessLanguageFromEntry(entry)

                    // Show "generating" toast and log the action
                    Toast.makeText(
                        itemView.context,
                        R.string.generating_audio,
                        Toast.LENGTH_SHORT
                    ).show()

                    // Add debug logging
                    Log.d("DictionaryMatchAdapter", "🔊 Play button clicked for term: '${entry.term}', language: $language")

                    // Generate and play audio through listener
                    audioListener?.onPlayAudio(entry.term, language)
                }
            } else {
                playAudioButton.visibility = View.GONE
            }
        }

        /**
         * Reset the card to default state (used when recycling views)
         */
        fun resetExportedState() {
            // Reset to normal colors - use theme-aware color
            cardView.setCardBackgroundColor(getThemeSurfaceColor())
            exportToAnkiButton.setImageTintList(null)

            // Reset frequency shield if available
            frequencyShieldContainer?.let { FrequencyShieldHelper.hideFrequencyShield(it) }
        }

        /**
         * Determine the language of the dictionary entry for audio generation
         *
         * Uses the LanguageDetector utility class for more accurate detection
         * with ML Kit when possible, falling back to character-based detection.
         */
        private fun guessLanguageFromEntry(entry: DictionaryEntryEntity): String {
            // Log detailed information for debugging
            Log.d("DictionaryAdapter", "Determining language for term: '${entry.term}', dictionaryId: ${entry.dictionaryId}")

            // Combine term and reading for better detection
            val entryText = entry.term + " " + entry.reading

            // Use the LanguageDetector for detection
            val detectedLanguage = LanguageDetector.getInstance().detectLanguageSync(entryText)

            Log.d("DictionaryAdapter", "Detected language: $detectedLanguage")
            return detectedLanguage
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<DictionaryEntryEntity>() {
        override fun areItemsTheSame(oldItem: DictionaryEntryEntity, newItem: DictionaryEntryEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: DictionaryEntryEntity, newItem: DictionaryEntryEntity): Boolean {
            return oldItem == newItem
        }
    }

    /**
     * Utility method to convert dp to pixels
     */
    private fun dpToPx(dp: Int, context: Context): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}