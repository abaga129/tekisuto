package com.abaga129.tekisuto.ui.adapter

import android.content.Context
import android.content.Intent
import android.text.Html
import android.text.Spanned
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.emoji2.text.EmojiCompat
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import android.content.res.ColorStateList
import android.util.TypedValue
import androidx.core.content.ContextCompat
import com.abaga129.tekisuto.R
import com.abaga129.tekisuto.database.DictionaryEntryEntity
import com.abaga129.tekisuto.database.DictionaryRepository
import com.abaga129.tekisuto.ui.anki.AnkiDroidConfigActivity
import com.abaga129.tekisuto.util.AnkiDroidHelper
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
        // Removed declaration of frequencyTextView as a property - we'll access it directly when needed
        private val definitionTextView: TextView = itemView.findViewById(R.id.definition_text)
        private val exportToAnkiButton: ImageButton = itemView.findViewById(R.id.export_to_anki_button)
        private val playAudioButton: ImageButton = itemView.findViewById(R.id.play_audio_button)
        private val cardView: androidx.cardview.widget.CardView = itemView.findViewById(R.id.dictionary_item_card)

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
            Log.d("DictionaryAdapter", "Processing entry: ${entry.term}, isHtml=${entry.isHtmlContent}")
            
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
            
            // Safely handle frequency TextView which might be null in some layouts
            try {
                val freqTextView = itemView.findViewById<TextView>(R.id.frequency_text)
                if (freqTextView != null) {
                    // Display frequency data if available
                    if (entry.frequency != null) {
                        // Format frequency to show rank (lower number = more frequent/common)
                        freqTextView.text = itemView.context.getString(R.string.frequency_info, entry.frequency)
                        freqTextView.visibility = View.VISIBLE
                        
                        // Adjust color based on frequency - more common words get brighter colors
                        val frequencyRank = entry.frequency
                        val color = when {
                            frequencyRank <= 100 -> ContextCompat.getColor(itemView.context, R.color.accent) // Very common (top 100)
                            frequencyRank <= 1000 -> ContextCompat.getColor(itemView.context, R.color.frequency_color) // Common (top 1000)
                            else -> ContextCompat.getColor(itemView.context, R.color.part_of_speech_color) // Less common
                        }
                        freqTextView.setBackgroundColor(color)
                    } else {
                        freqTextView.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                // Log error but don't crash if frequency view is missing
                Log.e("DictionaryAdapter", "Error setting frequency text", e)
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
                    // Determine language from the dictionary entry or default to Japanese if unknown
                    val language = guessLanguageFromEntry(entry)
                    
                    // Show "generating" toast and log the action
                    Toast.makeText(
                        itemView.context, 
                        R.string.generating_audio, 
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    // Add debug logging
                    Log.e("DictionaryMatchAdapter", "🔊 Play button clicked for term: '${entry.term}', language: $language")
                    
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
            // Note: We don't reset the termTextView text here as it will be set during bind()
        }
        
        /**
         * Determine the language of the dictionary entry for audio generation
         * 
         * 1. First check if we have dictionary metadata with sourceLanguage
         * 2. If not, try to detect based on characters in the term/reading
         * 3. Lastly, use a Latin character analysis to distinguish European languages
         */
        private fun guessLanguageFromEntry(entry: DictionaryEntryEntity): String {
            // Log detailed information for debugging
            Log.e("DictionaryAdapter", "Determining language for term: '${entry.term}', dictionaryId: ${entry.dictionaryId}")
            
            // Use DictionaryLanguageHelper to get the language
            val languageHelper = com.abaga129.tekisuto.util.DictionaryLanguageHelper(itemView.context)
            val sourceLanguage = languageHelper.getSourceLanguage(entry.dictionaryId)
            
            if (sourceLanguage != null) {
                Log.e("DictionaryAdapter", "Using stored source language for dictionary ${entry.dictionaryId}: $sourceLanguage")
                return sourceLanguage // Already mapped to Azure format by the helper
            }
            
            // Try to determine language from text patterns
            val text = entry.term + " " + entry.reading
            
            // Check for CJK languages first
            if (text.matches(Regex(".*[\u3040-\u309F\u30A0-\u30FF\u4E00-\u9FFF]+.*"))) {
                // Japanese (Hiragana, Katakana, Kanji)
                return "ja"
            }
            
            if (text.matches(Regex(".*[\u4E00-\u9FFF]+.*")) && 
                !text.contains("[\u3040-\u309F\u30A0-\u30FF]")) {
                // Chinese (Han characters without Japanese kana)
                return "zh"
            }
            
            if (text.matches(Regex(".*[\uAC00-\uD7A3]+.*"))) {
                // Korean (Hangul)
                return "ko"
            }
            
            // For Latin-script languages, look for distinctive patterns
            return when {
                // Spanish - check for distinctive Spanish characters
                text.contains("á") || text.contains("é") || 
                text.contains("í") || text.contains("ó") || 
                text.contains("ú") || text.contains("ü") || 
                text.contains("ñ") || text.contains("¿") || 
                text.contains("¡") || entry.term.endsWith("ción") || 
                entry.term.endsWith("dad") -> "es"
                
                // French - check for distinctive French patterns
                text.contains("à") || text.contains("â") || 
                text.contains("ç") || text.contains("é") || 
                text.contains("è") || text.contains("ê") || 
                text.contains("ë") || text.contains("î") || 
                text.contains("ï") || text.contains("ô") || 
                text.contains("ù") || text.contains("û") || 
                text.contains("ü") || entry.term.endsWith("eau") -> "fr"
                
                // German - check for distinctive German characters/patterns
                text.contains("ä") || text.contains("ö") || 
                text.contains("ü") || text.contains("ß") ||
                entry.term.endsWith("ung") || entry.term.endsWith("heit") -> "de"
                
                // Italian - check for distinctive Italian patterns
                text.contains("à") || text.contains("è") || 
                text.contains("é") || text.contains("ì") || 
                text.contains("í") || text.contains("ò") || 
                text.contains("ó") || text.contains("ù") ||
                entry.term.endsWith("zione") || entry.term.endsWith("ità") -> "it"
                
                // Russian - check for Cyrillic
                text.contains(Regex(".*[А-Яа-я]+.*").pattern) -> "ru"
                
                // Default to English for other Latin scripts
                else -> "en"
            }
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
}