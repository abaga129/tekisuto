package com.abaga129.tekisuto.util

import android.content.Context
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.SuperscriptSpan
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.abaga129.tekisuto.R

/**
 * Helper class for handling pitch accent display
 */
class PitchAccentHelper {
    companion object {
        private const val TAG = "PitchAccentHelper"
        
        /**
         * Configure the pitch accent view with appropriate styles
         *
         * @param context The context for accessing resources
         * @param pitchAccentView The TextView for displaying the pitch accent data
         * @param pitchAccent The pitch accent pattern to display
         */
        fun setupPitchAccentView(
            context: Context,
            pitchAccentView: TextView,
            pitchAccent: String
        ) {
            // Try to format the pitch accent based on its type
            val formattedText = formatPitchAccent(context, pitchAccent)
            
            // Set the text in the view
            pitchAccentView.text = formattedText
            
            // Make the view visible
            pitchAccentView.visibility = View.VISIBLE
        }
        
        /**
         * Format the pitch accent pattern appropriately
         * 
         * @param context The context for accessing resources
         * @param pitchAccent The pitch accent pattern to format
         * @return A formatted SpannableString for display
         */
        private fun formatPitchAccent(context: Context, pitchAccent: String): SpannableString {
            // Check if it's just a number (indicating the mora position of the pitch drop)
            if (pitchAccent.matches(Regex("^\\d+$"))) {
                val position = pitchAccent.toIntOrNull() ?: 0
                val text = when (position) {
                    0 -> "平板型 (heiban) [0]"
                    else -> "起伏型 (kifuku) [$position]"
                }
                return SpannableString(text)
            }
            
            // Check if it's already a formatted pattern (e.g., "か↓れー")
            if (pitchAccent.contains("↓") || pitchAccent.contains("↑")) {
                // Note: This is a simple implementation. More sophisticated handling 
                // could be added for different formats.
                return formatAccentPattern(context, pitchAccent)
            }
            
            // Default: return as-is
            return SpannableString(pitchAccent)
        }
        
        /**
         * Format an accent pattern that uses symbols like ↓ and ↑
         * 
         * @param context The context for accessing resources
         * @param pattern The pattern to format (e.g., "か↓れー")
         * @return A formatted SpannableString with colored symbols
         */
        private fun formatAccentPattern(context: Context, pattern: String): SpannableString {
            val spannableString = SpannableString(pattern)
            
            // Find accent marks and apply styling
            val downArrow = "↓"
            val upArrow = "↑"
            
            // Apply red color to downward arrows (pitch drop)
            var startIndex = pattern.indexOf(downArrow)
            while (startIndex >= 0) {
                spannableString.setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(context, R.color.accent_drop_color)),
                    startIndex,
                    startIndex + downArrow.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                startIndex = pattern.indexOf(downArrow, startIndex + 1)
            }
            
            // Apply blue color to upward arrows (pitch rise)
            startIndex = pattern.indexOf(upArrow)
            while (startIndex >= 0) {
                spannableString.setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(context, R.color.accent_rise_color)),
                    startIndex,
                    startIndex + upArrow.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                startIndex = pattern.indexOf(upArrow, startIndex + 1)
            }
            
            return spannableString
        }
        
        /**
         * Hide the pitch accent view when no pitch accent data is available
         * 
         * @param pitchAccentView The TextView for the pitch accent
         */
        fun hidePitchAccentView(pitchAccentView: TextView) {
            pitchAccentView.visibility = View.GONE
        }
        
        /**
         * Parse numeric pitch accent pattern to a visual representation
         * Example: "3" for かわいい becomes "か↓わいい"
         * 
         * @param reading The reading of the word (hiragana/katakana)
         * @param position The position of the pitch accent drop (0 = heiban, no drop)
         * @return A visual representation of the pitch pattern
         */
        fun generateVisualPattern(reading: String, position: Int): String {
            if (position == 0) {
                // Heiban (flat) pattern - no drop
                return reading + "⟿" // Flat right arrow to indicate no drop
            }
            
            // Handle the case where position is beyond the word length
            val safePosition = minOf(position, reading.length)
            
            // Insert drop arrow at the correct position
            return reading.substring(0, safePosition) + "↓" + 
                   reading.substring(safePosition)
        }
    }
}