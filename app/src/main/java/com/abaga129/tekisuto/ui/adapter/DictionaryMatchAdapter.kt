package com.abaga129.tekisuto.ui.adapter

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
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.abaga129.tekisuto.R
import com.abaga129.tekisuto.database.DictionaryEntryEntity
import com.abaga129.tekisuto.ui.anki.AnkiDroidConfigActivity
import com.abaga129.tekisuto.util.AnkiDroidHelper
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

    interface OnAnkiExportListener {
        fun onExportToAnki(entry: DictionaryEntryEntity)
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dictionary_match, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = getItem(position)
        holder.bind(entry, ankiExportListener)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val termTextView: TextView = itemView.findViewById(R.id.term_text)
        private val readingTextView: TextView = itemView.findViewById(R.id.reading_text)
        private val partOfSpeechTextView: TextView = itemView.findViewById(R.id.part_of_speech_text)
        private val definitionTextView: TextView = itemView.findViewById(R.id.definition_text)
        private val exportToAnkiButton: ImageButton = itemView.findViewById(R.id.export_to_anki_button)

        fun bind(entry: DictionaryEntryEntity, ankiExportListener: OnAnkiExportListener?) {
            // Log entry details for debugging
            Log.d("DictionaryAdapter", "Processing entry: ${entry.term}, isHtml=${entry.isHtmlContent}")
            
            // Display term or show placeholder if empty
            if (entry.term.isNotBlank()) {
                termTextView.text = entry.term
                termTextView.visibility = View.VISIBLE
            } else {
                termTextView.visibility = View.GONE
            }
            
            // Display reading or hide if empty
            if (entry.reading.isNotBlank()) {
                readingTextView.text = entry.reading
                readingTextView.visibility = View.VISIBLE
            } else {
                readingTextView.visibility = View.GONE
            }
            
            // Display part of speech or hide if empty
            if (entry.partOfSpeech.isNotBlank()) {
                partOfSpeechTextView.text = entry.partOfSpeech
                partOfSpeechTextView.visibility = View.VISIBLE
            } else {
                partOfSpeechTextView.visibility = View.GONE
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
                        
                        definitionTextView.text = htmlContent
                        Log.d("DictionaryAdapter", "HTML rendered successfully. Length: ${htmlContent.length}")
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
                    definitionTextView.text = entry.definition
                        .replace("\n", "\n\n") // Double-space between list items for better readability
                    Log.d("DictionaryAdapter", "Setting plain text definition")
                }
                
                android.util.Log.d("DictionaryAdapter", "Setting definition: ${entry.definition.take(100)}...")
                definitionTextView.visibility = View.VISIBLE
            } else {
                definitionTextView.text = "(No definition available)"
                android.util.Log.d("DictionaryAdapter", "No definition available for term: ${entry.term}")
                definitionTextView.visibility = View.VISIBLE
            }
            
            // Set up export to Anki button
            exportToAnkiButton.setOnClickListener {
                ankiExportListener?.onExportToAnki(entry)
            }

            // Log the entry for debugging
            android.util.Log.d("DictionaryAdapter", "Entry: [term=${entry.term}, reading=${entry.reading}, " +
                    "partOfSpeech=${entry.partOfSpeech}, definition=${entry.definition.take(30)}...]")
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