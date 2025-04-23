package com.abaga129.tekisuto.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.abaga129.tekisuto.R
import com.abaga129.tekisuto.util.AzureVoiceInfo

/**
 * RecyclerView adapter for displaying and selecting Azure voices
 */
class VoiceSelectionAdapter(
    private val onVoiceSelectedListener: (AzureVoiceInfo) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_VOICE = 1
    }

    // Data for the adapter
    private var allVoices: List<AzureVoiceInfo> = emptyList()
    private var filteredItems: List<Any> = emptyList()
    private var selectedVoiceName: String? = null

    /**
     * ViewHolder for voice items
     */
    inner class VoiceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.text_voice_name)
        private val detailsTextView: TextView = itemView.findViewById(R.id.text_voice_details)
        private val localeTextView: TextView = itemView.findViewById(R.id.text_voice_locale)
        private val statusTextView: TextView = itemView.findViewById(R.id.text_voice_status)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val item = filteredItems[position]
                    if (item is AzureVoiceInfo) {
                        selectedVoiceName = item.name
                        onVoiceSelectedListener(item)
                        notifyDataSetChanged()
                    }
                }
            }
        }

        fun bind(voice: AzureVoiceInfo) {
            // Display the voice name and gender
            nameTextView.text = voice.displayName
            
            // Display voice gender and type
            val genderText = if (voice.gender == "Female") {
                itemView.context.getString(R.string.voice_gender_female)
            } else {
                itemView.context.getString(R.string.voice_gender_male)
            }
            detailsTextView.text = "$genderText, ${voice.voiceType}"
            
            // Display locale
            localeTextView.text = voice.locale
            
            // Display preview status if applicable
            if (voice.status == "Preview") {
                statusTextView.visibility = View.VISIBLE
                statusTextView.text = itemView.context.getString(R.string.voice_status_preview)
            } else {
                statusTextView.visibility = View.GONE
            }
            
            // Highlight the selected voice
            if (voice.name == selectedVoiceName) {
                itemView.setBackgroundResource(android.R.color.holo_blue_light)
            } else {
                itemView.setBackgroundResource(android.R.color.transparent)
            }
        }
    }

    /**
     * ViewHolder for section header items
     */
    inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val headerTextView: TextView = itemView.findViewById(R.id.text_header)
        
        fun bind(header: String) {
            headerTextView.text = header
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_voice_header, parent, false)
                HeaderViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_voice, parent, false)
                VoiceViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = filteredItems[position]
        when (holder) {
            is HeaderViewHolder -> holder.bind(item as String)
            is VoiceViewHolder -> holder.bind(item as AzureVoiceInfo)
        }
    }

    override fun getItemCount(): Int = filteredItems.size

    override fun getItemViewType(position: Int): Int {
        return when (filteredItems[position]) {
            is String -> TYPE_HEADER
            else -> TYPE_VOICE
        }
    }

    /**
     * Set the list of voices and organize them by language group
     */
    fun setVoices(voices: List<AzureVoiceInfo>, currentVoiceName: String? = null) {
        this.allVoices = voices
        this.selectedVoiceName = currentVoiceName
        
        // Apply filtering and grouping
        applyFilterAndGrouping("")
    }

    /**
     * Filter the list of voices based on the search query and group them by language
     */
    fun filter(query: String) {
        applyFilterAndGrouping(query)
    }

    /**
     * Apply filtering and grouping to the voice list
     */
    private fun applyFilterAndGrouping(query: String) {
        val filteredVoices = if (query.isBlank()) {
            allVoices
        } else {
            allVoices.filter { voice ->
                voice.name.contains(query, ignoreCase = true) ||
                voice.displayName.contains(query, ignoreCase = true) ||
                voice.locale.contains(query, ignoreCase = true) ||
                voice.gender.contains(query, ignoreCase = true) ||
                voice.shortName.contains(query, ignoreCase = true)
            }
        }
        
        // Group by language and sort
        val grouped = filteredVoices.groupBy { it.getLanguageGroupKey() }
        
        // Create the list of items with headers
        val items = mutableListOf<Any>()
        grouped.keys.sorted().forEach { langKey ->
            val voices = grouped[langKey]
            if (!voices.isNullOrEmpty()) {
                // Get a representative voice to get the language name
                val languageName = voices.first().getLanguageGroupName()
                // Add header
                items.add(languageName)
                // Add sorted voices
                items.addAll(voices.sortedBy { it.displayName })
            }
        }
        
        this.filteredItems = items
        notifyDataSetChanged()
    }
}