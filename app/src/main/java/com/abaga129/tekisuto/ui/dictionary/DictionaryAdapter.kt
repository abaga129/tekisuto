package com.abaga129.tekisuto.ui.dictionary

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
    private val onMoveDownClick: (Int) -> Unit
) : ListAdapter<DictionaryMetadataEntity, DictionaryAdapter.DictionaryViewHolder>(DictionaryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DictionaryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dictionary, parent, false)
        return DictionaryViewHolder(view)
    }

    override fun onBindViewHolder(holder: DictionaryViewHolder, position: Int) {
        val dictionary = getItem(position)
        holder.bind(dictionary, position)
    }

    inner class DictionaryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView = itemView.findViewById(R.id.dictionary_title)
        private val infoTextView: TextView = itemView.findViewById(R.id.dictionary_info)
        private val priorityTextView: TextView = itemView.findViewById(R.id.dictionary_priority)
        private val btnUp: ImageButton = itemView.findViewById(R.id.btn_up)
        private val btnDown: ImageButton = itemView.findViewById(R.id.btn_down)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btn_delete)

        fun bind(dictionary: DictionaryMetadataEntity, position: Int) {
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
            val languagesText = itemView.context.getString(
                R.string.dictionary_languages,
                dictionary.sourceLanguage.uppercase(),
                dictionary.targetLanguage.uppercase()
            )
            
            infoTextView.text = "$authorText\n$entryCountText\n$languagesText"
            
            // Show priority
            priorityTextView.text = itemView.context.getString(
                R.string.dictionary_priority_format,
                dictionary.priority
            )
            
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

class DictionaryDiffCallback : DiffUtil.ItemCallback<DictionaryMetadataEntity>() {
    override fun areItemsTheSame(oldItem: DictionaryMetadataEntity, newItem: DictionaryMetadataEntity): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: DictionaryMetadataEntity, newItem: DictionaryMetadataEntity): Boolean {
        return oldItem == newItem
    }
}