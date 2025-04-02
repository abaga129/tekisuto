package com.abaga129.tekisuto.util

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.abaga129.tekisuto.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

/**
 * Utility class to tokenize text and create clickable spans for each word
 */
class TextTokenizer {
    companion object {
        private const val TAG = "TextTokenizer"
        
        /**
         * Tokenize text and set clickable spans for each word
         * 
         * @param textView TextView to set the clickable text on
         * @param text Text to tokenize
         * @param onClick Callback when a word is clicked
         */
        fun setClickableWords(
            textView: TextView, 
            text: String, 
            context: Context,
            coroutineScope: CoroutineScope,
            onWordClick: (String) -> Unit
        ) {
            // Enable clickable spans in the TextView
            textView.movementMethod = LinkMovementMethod.getInstance()
            
            // Create spannableString from text
            val spannableString = SpannableString(text)
            
            // Find words in the text
            val words = findWords(text)
            
            // Create clickable span for each word
            for (wordInfo in words) {
                val clickableSpan = object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        val word = text.substring(wordInfo.startIndex, wordInfo.endIndex)
                        Log.d(TAG, "Word clicked: $word")
                        
                        // Execute the callback
                        onWordClick(word)
                    }
                    
                    override fun updateDrawState(ds: TextPaint) {
                        super.updateDrawState(ds)
                        // Customize appearance of clickable words
                        ds.isUnderlineText = false  // No underline by default
                        ds.color = ContextCompat.getColor(context, R.color.clickable_word)
                    }
                }
                
                // Apply span to the word
                spannableString.setSpan(
                    clickableSpan, 
                    wordInfo.startIndex, 
                    wordInfo.endIndex, 
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            
            // Set the spannable text on the TextView
            textView.text = spannableString
        }
        
        /**
         * Find words in the text and their positions
         * 
         * @param text Text to find words in
         * @return List of WordInfo objects
         */
        private fun findWords(text: String): List<WordInfo> {
            val words = mutableListOf<WordInfo>()
            val regex = Regex("[^\\s,.。、!?:;\\n\\r\\t]+")
            
            // Find all word matches
            val matches = regex.findAll(text)
            
            for (match in matches) {
                words.add(WordInfo(match.range.first, match.range.last + 1))
            }
            
            return words
        }
    }
    
    /**
     * Word information class to store word position in text
     */
    data class WordInfo(val startIndex: Int, val endIndex: Int)
}