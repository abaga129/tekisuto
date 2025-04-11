package com.abaga129.tekisuto.util

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import com.abaga129.tekisuto.R
import com.abaga129.tekisuto.ui.view.FlowLayout
import com.abaga129.tekisuto.ui.view.WordButton

/**
 * Utility class to tokenize text into WordButtons in a FlowLayout
 */
class WordTokenizerFlow {
    companion object {
        private const val TAG = "WordTokenizerFlow"
        
        // Map to keep track of word buttons
        private val wordButtonsMap = mutableMapOf<String, WordButton>()
        
        /**
         * Highlight a specific word in the flow layout
         * 
         * @param word Word to highlight
         * @param highlight Whether to highlight or unhighlight
         */
        fun highlightWord(word: String, highlight: Boolean = true) {
            wordButtonsMap[word.lowercase()]?.isHighlighted = highlight
        }
        
        /**
         * Clear all highlights
         */
        fun clearHighlights() {
            wordButtonsMap.values.forEach { it.isHighlighted = false }
        }
        
        /**
         * Create a flow layout with clickable word buttons from OCR text
         * 
         * @param context The context
         * @param text OCR text to tokenize
         * @param onWordClick Callback for word button click
         * @param onWordLongClick Callback for word button long click (for dictionary lookup)
         * @return A ScrollView containing the FlowLayout with word buttons
         */
        fun createClickableWordsFlow(
            context: Context,
            parentViewGroup: ViewGroup,
            text: String,
            onWordClick: (String) -> Unit,
            onWordLongClick: (String) -> Boolean
        ): ScrollView {
            // Remove any existing views
            parentViewGroup.removeAllViews()
            
            // Create a flow layout for the words
            val flowLayout = FlowLayout(context).apply {
                setPadding(
                    resources.getDimensionPixelSize(R.dimen.flow_layout_padding),
                    resources.getDimensionPixelSize(R.dimen.flow_layout_padding),
                    resources.getDimensionPixelSize(R.dimen.flow_layout_padding),
                    resources.getDimensionPixelSize(R.dimen.flow_layout_padding)
                )
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            
            // Find words in the text
            val words = findWords(text)
            
            // Clear previous word buttons
            wordButtonsMap.clear()
            
            // Create a button for each word
            for (word in words) {
                val wordButton = WordButton(context).apply {
                    setWord(word)
                    setOnClickListener {
                        onWordClick(word)
                    }
                    setOnLongClickListener {
                        onWordLongClick(word)
                    }
                }
                
                // Store in map (lowercase for case-insensitive access)
                wordButtonsMap[word.lowercase()] = wordButton
                
                flowLayout.addView(wordButton)
            }
            
            // Create a scroll view to hold the flow layout
            val scrollView = ScrollView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                addView(flowLayout)
            }
            
            // Add to parent
            parentViewGroup.addView(scrollView)
            
            return scrollView
        }
        
        /**
         * Find words in the text
         * 
         * @param text Text to find words in
         * @return List of words
         */
        private fun findWords(text: String): List<String> {
            // Check if the text is likely Japanese
            if (JapaneseTokenizer.isLikelyJapanese(text)) {
                android.util.Log.d(TAG, "Detected Japanese text, using Japanese tokenizer")
                
                // Use Japanese tokenizer which returns meaningful words
                return JapaneseTokenizer.tokenize(text)
            }
            // Check if the text is likely Chinese
            else if (ChineseTokenizer.isLikelyChinese(text)) {
                android.util.Log.d(TAG, "Detected Chinese text, using Chinese tokenizer")
                
                // Use Chinese tokenizer which returns meaningful words
                return ChineseTokenizer.tokenize(text)
            } 
            else {
                // Standard tokenization for other languages
                val words = mutableListOf<String>()
                val regex = Regex("[^\\s,.。、!?:;\\n\\r\\t]+")
                
                // Find all word matches
                val matches = regex.findAll(text)
                
                for (match in matches) {
                    words.add(match.value)
                }
                
                return words
            }
        }
    }
}