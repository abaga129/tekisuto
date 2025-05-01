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

            // Handle the dictionary badge with frequency information
            lifecycleScope?.launch {
                try {
                    // Programmatically create and display badge when needed, rather than relying on inflated layout
                    // Get the dictionary name
                    val dictionaryMeta = dictionaryRepository?.getDictionaryMetadata(entry.dictionaryId)
                    val dictionaryName = dictionaryMeta?.title ?: "DICT"

                    // Get frequency data for this word in this dictionary
                    val frequencyEntity = dictionaryRepository?.getFrequencyForWordInDictionary(entry.term, entry.dictionaryId)

                    // DEBUG: Add detailed logging about frequency data
                    Log.d("DictionaryAdapter", "Frequency lookup for '${entry.term}' in dictionary ${entry.dictionaryId}")
                    Log.d("DictionaryAdapter", "Frequency data found: ${frequencyEntity != null}")
                    if (frequencyEntity != null) {
                        Log.d("DictionaryAdapter", "Frequency value: #${frequencyEntity.frequency}")
                    }

                    withContext(Dispatchers.Main) {
                        // IMPORTANT: Find or create our badge view
                        // First try to find an existing badge (unlikely to succeed based on previous attempts)
                        var badgeView = itemView.findViewById<androidx.cardview.widget.CardView>(R.id.dictionary_badge)
                        var badgeContainer: LinearLayout? = null
                        var nameTextView: TextView? = null
                        var valueTextView: TextView? = null

                        // If no badge found, create our own programmatically
                        if (badgeView == null) {
                            // Get the main container where the badge should be added - simplified approach
                            // The main card contains a ConstraintLayout as its first child
                            val cardView = itemView.findViewById<androidx.cardview.widget.CardView>(R.id.dictionary_item_card)
                            val constraintLayout = if (cardView != null && cardView.childCount > 0) {
                                cardView.getChildAt(0) as? androidx.constraintlayout.widget.ConstraintLayout
                            } else null

                            // Detailed logging about the view hierarchy
                            Log.d("DictionaryAdapter", "Card view found: ${cardView != null}")
                            if (cardView != null) {
                                Log.d("DictionaryAdapter", "Card view child count: ${cardView.childCount}")
                                if (cardView.childCount > 0) {
                                    Log.d("DictionaryAdapter", "First child type: ${cardView.getChildAt(0)?.javaClass?.simpleName}")
                                }
                            }

                            if (constraintLayout != null) {
                                Log.d("DictionaryAdapter", "Creating badge programmatically")

                                // Generate consistent IDs for our badge components
                                val badgeId = 100001
                                val containerId = 100002
                                val nameTextId = 100003
                                val valueTextId = 100004

                                // Create badge card view
                                badgeView = androidx.cardview.widget.CardView(itemView.context).apply {
                                    id = badgeId  // Use a consistent ID
                                    radius = itemView.context.resources.getDimension(R.dimen.card_corner_radius) / 2
                                    useCompatPadding = false
                                    cardElevation = 0f
                                    setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.badge_background))
                                    visibility = View.GONE // Start hidden
                                    tag = "custom_dictionary_badge" // Add a tag for identification

                                    // Set fixed width/height constraints for more predictable layout
                                    val params = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.WRAP_CONTENT,
                                        ViewGroup.LayoutParams.WRAP_CONTENT
                                    )
                                    layoutParams = params
                                }

                                // Create linear layout for badge content
                                badgeContainer = LinearLayout(itemView.context).apply {
                                    id = containerId
                                    orientation = LinearLayout.VERTICAL
                                    setPadding(
                                        dpToPx(4, itemView.context),
                                        dpToPx(4, itemView.context),
                                        dpToPx(4, itemView.context),
                                        dpToPx(4, itemView.context)
                                    )
                                }

                                // Create dictionary name text view
                                nameTextView = TextView(itemView.context).apply {
                                    id = nameTextId
                                    textSize = 10f
                                    setTextColor(Color.WHITE)
                                    typeface = Typeface.DEFAULT_BOLD
                                    maxLines = 1
                                    ellipsize = TextUtils.TruncateAt.END
                                }

                                // Create frequency value text view
                                valueTextView = TextView(itemView.context).apply {
                                    id = valueTextId
                                    textSize = 9f
                                    setTextColor(Color.WHITE)
                                    maxLines = 1
                                    ellipsize = TextUtils.TruncateAt.END
                                }

                                // Add text views to container
                                badgeContainer.addView(nameTextView)
                                badgeContainer.addView(valueTextView)

                                // Add container to badge
                                badgeView.addView(badgeContainer)

                                // Add badge to constraint layout with proper positioning
                                val layoutParams = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                ).apply {
                                    topToBottom = R.id.term_text
                                    startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                                    topMargin = dpToPx(4, itemView.context)

                                    // We'll update the definition text constraints after adding the badge
                                }

                                constraintLayout.addView(badgeView, layoutParams)

                                // Make part_of_speech_text view constrained to our new badge view
                                val posTextView = itemView.findViewById<TextView>(R.id.part_of_speech_text)
                                if (posTextView != null) {
                                    val posParams = posTextView.layoutParams as ConstraintLayout.LayoutParams
                                    posParams.startToEnd = badgeView.id
                                    posTextView.layoutParams = posParams
                                }

                                // Now that the badge is added to the layout, fix the definition text constraints
                                val definitionTextView = itemView.findViewById<TextView>(R.id.definition_text)
                                if (definitionTextView != null) {
                                    try {
                                        val defParams = definitionTextView.layoutParams as? ConstraintLayout.LayoutParams
                                        if (defParams != null) {
                                            // Update definition to be below our badge instead of the dictionary_badge from layout
                                            defParams.topToBottom = badgeView.id

                                            // Apply the updated constraints
                                            definitionTextView.layoutParams = defParams

                                            // Force a layout pass to update the constraints
                                            definitionTextView.requestLayout()

                                            Log.d("DictionaryAdapter", "Updated definition text constraints to be below badge ID: ${badgeView.id}")
                                        }
                                    } catch (e: Exception) {
                                        Log.e("DictionaryAdapter", "Error updating definition constraints", e)
                                    }
                                }

                                Log.d("DictionaryAdapter", "Badge created and added to layout")
                            } else {
                                Log.e("DictionaryAdapter", "Could not find constraint layout to add badge")
                            }
                        } else {
                            // If badge already exists, get the child views
                            Log.d("DictionaryAdapter", "Badge already exists, getting child views")
                            badgeContainer = badgeView.getChildAt(0) as? LinearLayout
                            if (badgeContainer != null && badgeContainer.childCount >= 2) {
                                nameTextView = badgeContainer.getChildAt(0) as? TextView
                                valueTextView = badgeContainer.getChildAt(1) as? TextView
                            }
                        }

                        // If we have all necessary views, set the data
                        if (badgeView != null && nameTextView != null && valueTextView != null) {
                            nameTextView.text = dictionaryName

                            // Get frequency data from the repository
                            val dictionaryMeta = dictionaryRepository?.getDictionaryMetadata(entry.dictionaryId)

                            // If dictionary metadata exists, show the badge with or without frequency
                            if (dictionaryMeta != null) {
                                // Always set the dictionary name
                                nameTextView.text = dictionaryMeta.title.take(4) // Keep it short

                                // Display frequency data if available and valid
                                if (frequencyEntity != null && frequencyEntity.frequency > 0) {
                                    // Format frequency to show rank (e.g., #685, 18667)
                                    val frequencyRank = frequencyEntity.frequency
                                    valueTextView.text = "#$frequencyRank"

                                    // Update the badge background color based on frequency
                                    val color = when {
                                        frequencyRank <= 100 -> ContextCompat.getColor(itemView.context, R.color.badge_common) // Very common (top 100)
                                        frequencyRank <= 1000 -> ContextCompat.getColor(itemView.context, R.color.badge_uncommon) // Common (top 1000)
                                        else -> ContextCompat.getColor(itemView.context, R.color.badge_rare) // Less common
                                    }
                                    badgeView.setCardBackgroundColor(color)
                                    Log.d("DictionaryAdapter", "Badge card color set to $color for frequency $frequencyRank")
                                } else {
                                    // No frequency data, but we still want to show dictionary name
                                    valueTextView.text = "N/A"
                                    badgeView.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.badge_background))
                                    Log.d("DictionaryAdapter", "No frequency data, showing badge with dict name only")
                                }

                                // Always show the badge when we have dictionary metadata
                                badgeView.visibility = View.VISIBLE
                                Log.d("DictionaryAdapter", "Badge set to VISIBLE for word '${entry.term}'")
                            } else {
                                // No dictionary metadata, hide the badge
                                badgeView.visibility = View.GONE
                                Log.d("DictionaryAdapter", "Badge set to GONE for word '${entry.term}' - no dictionary metadata")
                            }
                        } else {
                            // FALLBACK: If we can't create a badge, add frequency info to the reading text
                            if (frequencyEntity != null && frequencyEntity.frequency > 0) {
                                val frequencyRank = frequencyEntity.frequency

                                // Choose color based on frequency
                                val colorResId = when {
                                    frequencyRank <= 100 -> R.color.badge_common
                                    frequencyRank <= 1000 -> R.color.badge_uncommon
                                    else -> R.color.badge_rare
                                }

                                val color = ContextCompat.getColor(itemView.context, colorResId)

                                // If reading is visible, append frequency to it
                                if (readingTextView.visibility == View.VISIBLE) {
                                    val currentText = readingTextView.text.toString()
                                    readingTextView.text = "$currentText (#$frequencyRank)"

                                    // Set part of text color for frequency
                                    try {
                                        val spannable = android.text.SpannableString(readingTextView.text)
                                        spannable.setSpan(
                                            android.text.style.ForegroundColorSpan(color),
                                            currentText.length,
                                            readingTextView.text.length,
                                            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                                        )
                                        readingTextView.text = spannable
                                    } catch (e: Exception) {
                                        Log.e("DictionaryAdapter", "Error applying color span to reading text", e)
                                    }
                                }
                                // If part of speech is visible, append frequency to it
                                else if (partOfSpeechTextView.visibility == View.VISIBLE) {
                                    val currentText = partOfSpeechTextView.text.toString()
                                    partOfSpeechTextView.text = "$currentText (#$frequencyRank)"

                                    // Set part of text color for frequency
                                    try {
                                        val spannable = android.text.SpannableString(partOfSpeechTextView.text)
                                        spannable.setSpan(
                                            android.text.style.ForegroundColorSpan(color),
                                            currentText.length,
                                            partOfSpeechTextView.text.length,
                                            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                                        )
                                        partOfSpeechTextView.text = spannable
                                    } catch (e: Exception) {
                                        Log.e("DictionaryAdapter", "Error applying color span to part of speech text", e)
                                    }
                                }
                                // Last resort: append to term text
                                else if (termTextView.visibility == View.VISIBLE) {
                                    val currentText = termTextView.text.toString()
                                    termTextView.text = "$currentText (#$frequencyRank)"

                                    // Set part of text color for frequency
                                    try {
                                        val spannable = android.text.SpannableString(termTextView.text)
                                        spannable.setSpan(
                                            android.text.style.ForegroundColorSpan(color),
                                            currentText.length,
                                            termTextView.text.length,
                                            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                                        )
                                        termTextView.text = spannable
                                    } catch (e: Exception) {
                                        Log.e("DictionaryAdapter", "Error applying color span to term text", e)
                                    }
                                }

                                Log.d("DictionaryAdapter", "Used fallback method to display frequency #$frequencyRank")
                            }

                            Log.e("DictionaryAdapter", "Could not create or find all required badge views")
                        }
                    }
                } catch (e: Exception) {
                    // Log error but don't crash if badge view is missing or can't be configured
                    Log.e("DictionaryAdapter", "Error setting frequency badge", e)
                }
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

            // Reset text in reading view (in case we added frequency as fallback)
            if (readingTextView.visibility == View.VISIBLE) {
                // Try to detect if we added frequency info
                val text = readingTextView.text.toString()
                if (text.contains(" (#")) {
                    val originalText = text.substringBefore(" (#")
                    readingTextView.text = originalText
                }
            }

            // Reset text in part of speech view (in case we added frequency as fallback)
            if (partOfSpeechTextView.visibility == View.VISIBLE) {
                val text = partOfSpeechTextView.text.toString()
                if (text.contains(" (#")) {
                    val originalText = text.substringBefore(" (#")
                    partOfSpeechTextView.text = originalText
                }
            }

            // Find any badge CardView in the item (whether from layout or dynamically created)
            try {
                // First try by tag - our custom badge has a tag
                val customBadge = findViewWithTagRecursive(itemView, "custom_dictionary_badge")
                if (customBadge != null && customBadge is View) {
                    customBadge.visibility = View.GONE
                    Log.d("DictionaryAdapter", "Reset custom badge visibility using tag")
                    return
                }

                // Next try by ID - our custom badge has ID 100001
                val badgeById = findViewWithId(itemView, 100001)
                if (badgeById != null) {
                    badgeById.visibility = View.GONE
                    Log.d("DictionaryAdapter", "Reset custom badge visibility using ID")
                    return
                }
            } catch (e: Exception) {
                Log.e("DictionaryAdapter", "Error resetting badge visibility", e)
            }
        }

        /**
         * Utility method to find a view with a specific tag in the view hierarchy
         */
        private fun findViewWithTagRecursive(root: View, tag: Any): View? {
            if (root.tag == tag) {
                return root
            }

            if (root is ViewGroup) {
                for (i in 0 until root.childCount) {
                    val child = root.getChildAt(i)
                    val result = findViewWithTagRecursive(child, tag)
                    if (result != null) {
                        return result
                    }
                }
            }

            return null
        }

        /**
         * Utility method to find a view with a specific ID in the view hierarchy
         */
        private fun findViewWithId(root: View, id: Int): View? {
            if (root.id == id) {
                return root
            }

            if (root is ViewGroup) {
                for (i in 0 until root.childCount) {
                    val child = root.getChildAt(i)
                    val result = findViewWithId(child, id)
                    if (result != null) {
                        return result
                    }
                }
            }

            return null
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