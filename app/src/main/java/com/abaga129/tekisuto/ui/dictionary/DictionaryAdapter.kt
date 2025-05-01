package com.abaga129.tekisuto.ui.dictionary

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.abaga129.tekisuto.R
import com.abaga129.tekisuto.database.DictionaryMetadataEntity

class DictionaryAdapter(
    private val onDeleteClick: (DictionaryMetadataEntity, Int) -> Unit,
    private val onMoveUpClick: (Int) -> Unit,
    private val onMoveDownClick: (Int) -> Unit,
    private val onProfileAssociationChanged: ((DictionaryMetadataEntity, Boolean) -> Unit)? = null
) : ListAdapter<DictionaryAdapter.DictionaryItem, DictionaryAdapter.DictionaryViewHolder>(DictionaryDiffCallback()) {

    private var showProfileCheckbox = false
    
    // Wrapper class that includes dictionary and its profile status
    data class DictionaryItem(
        val dictionary: DictionaryMetadataEntity,
        var isInProfile: Boolean = false
    )
    
    fun submitDictionaryList(dictionaries: List<DictionaryMetadataEntity>, inProfileDictionaries: List<DictionaryMetadataEntity>? = null) {
        // Handle null safely
        val profileDicts = inProfileDictionaries ?: emptyList()
        
        val items = dictionaries.map { dictionary ->
            DictionaryItem(
                dictionary = dictionary,
                isInProfile = profileDicts.any { it.id == dictionary.id }
            )
        }
        submitList(items)
    }
    
    fun setShowProfileCheckbox(show: Boolean) {
        showProfileCheckbox = show
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DictionaryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dictionary, parent, false)
        return DictionaryViewHolder(view)
    }

    override fun onBindViewHolder(holder: DictionaryViewHolder, position: Int) {
        val dictionaryItem = getItem(position)
        holder.bind(dictionaryItem, position)
    }

    inner class DictionaryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView = itemView.findViewById(R.id.dictionary_title)
        private val infoTextView: TextView = itemView.findViewById(R.id.dictionary_info)
        private val priorityTextView: TextView = itemView.findViewById(R.id.dictionary_priority)
        private val btnUp: ImageButton = itemView.findViewById(R.id.btn_up)
        private val btnDown: ImageButton = itemView.findViewById(R.id.btn_down)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btn_delete)
        private val profileCheckbox: CheckBox = itemView.findViewById(R.id.profile_checkbox)

        fun bind(dictionaryItem: DictionaryItem, position: Int) {
            val dictionary = dictionaryItem.dictionary
            
            titleTextView.text = dictionary.title
            
            // Create info text with author and entry count
            val authorText = itemView.context.getString(
                R.string.dictionary_author, 
                dictionary.author.ifEmpty { "Unknown" }
            )
            val entryCountText = itemView.context.getString(
                R.string.dictionary_entry_count,
                dictionary.entryCount
            )
            
            // Only include language info if both source and target are available
            val infoText = if (dictionary.sourceLanguage.isEmpty() || dictionary.targetLanguage.isEmpty()) {
                "$authorText\n$entryCountText"
            } else {
                val languagesText = itemView.context.getString(
                    R.string.dictionary_languages,
                    dictionary.sourceLanguage.uppercase(),
                    dictionary.targetLanguage.uppercase()
                )
                "$authorText\n$entryCountText\n$languagesText"
            }
            
            infoTextView.text = infoText
            
            // Show priority
            priorityTextView.text = itemView.context.getString(
                R.string.dictionary_priority_format,
                dictionary.priority
            )
            
            // Handle profile checkbox
            profileCheckbox.visibility = if (showProfileCheckbox) View.VISIBLE else View.GONE
            profileCheckbox.isChecked = dictionaryItem.isInProfile
            profileCheckbox.setOnCheckedChangeListener { _, isChecked ->
                onProfileAssociationChanged?.invoke(dictionary, isChecked)
            }
            
            // Disable up button for first item
            btnUp.isEnabled = position > 0
            btnUp.alpha = if (position > 0) 1.0f else 0.3f
            
            // Disable down button for last item
            btnDown.isEnabled = position < itemCount - 1
            btnDown.alpha = if (position < itemCount - 1) 1.0f else 0.3f
            
            // Set click listeners
            btnDelete.setOnClickListener {
                onDeleteClick(dictionary, position)
            }
            
            btnUp.setOnClickListener {
                if (position > 0) {
                    onMoveUpClick(position)
                }
            }
            
            btnDown.setOnClickListener {
                if (position < itemCount - 1) {
                    onMoveDownClick(position)
                }
            }
        }
    }
}

class DictionaryDiffCallback : DiffUtil.ItemCallback<DictionaryAdapter.DictionaryItem>() {
    override fun areItemsTheSame(oldItem: DictionaryAdapter.DictionaryItem, newItem: DictionaryAdapter.DictionaryItem): Boolean {
        return oldItem.dictionary.id == newItem.dictionary.id
    }

    override fun areContentsTheSame(oldItem: DictionaryAdapter.DictionaryItem, newItem: DictionaryAdapter.DictionaryItem): Boolean {
        return oldItem.dictionary == newItem.dictionary &&
               oldItem.isInProfile == newItem.isInProfile
    }
}