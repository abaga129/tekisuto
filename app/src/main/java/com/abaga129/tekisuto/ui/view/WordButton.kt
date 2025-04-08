package com.abaga129.tekisuto.ui.view

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatButton
import com.abaga129.tekisuto.R

/**
 * A button representing a word in OCR text
 */
class WordButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.buttonStyle
) : AppCompatButton(context, attrs, defStyleAttr) {
    
    var originalText: String = ""
        private set
    
    var isHighlighted: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                updateBackgroundBasedOnState()
            }
        }
    
    init {
        background = context.getDrawable(R.drawable.clickable_word_background)
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        includeFontPadding = false
        setPadding(
            resources.getDimensionPixelSize(R.dimen.word_button_padding_horizontal),
            resources.getDimensionPixelSize(R.dimen.word_button_padding_vertical),
            resources.getDimensionPixelSize(R.dimen.word_button_padding_horizontal),
            resources.getDimensionPixelSize(R.dimen.word_button_padding_vertical)
        )
        
        // Set text appearance
        setTextColor(context.getColor(R.color.text_light))
        textSize = 14f
        isAllCaps = false // Prevent automatic capitalization
        
        // Apply initial background and text color
        updateBackgroundBasedOnState()
    }
    
    private fun updateBackgroundBasedOnState() {
        // Update background drawable
        background = context.getDrawable(
            if (isHighlighted) 
                R.drawable.clickable_word_background_highlighted 
            else 
                R.drawable.clickable_word_background
        )
        
        // Update text color based on state
        setTextColor(
            context.getColor(
                if (isHighlighted) 
                    R.color.text_dark 
                else 
                    R.color.text_light
            )
        )
    }
    
    /**
     * Set word text, converting to lowercase if needed
     */
    fun setWord(word: String) {
        originalText = word
        
        // Convert to lowercase for display
        val displayText = word.lowercase()
        text = displayText
    }
}