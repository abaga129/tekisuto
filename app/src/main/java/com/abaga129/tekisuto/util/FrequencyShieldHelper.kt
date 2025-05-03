package com.abaga129.tekisuto.util

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.abaga129.tekisuto.R

/**
 * Helper class for handling frequency shield display and coloring
 */
class FrequencyShieldHelper {
    companion object {
        // Frequency thresholds for color coding
        private const val COMMON_THRESHOLD = 2000   // Frequencies 1-2000 are common
        private const val UNCOMMON_THRESHOLD = 10000 // Frequencies 2001-10000 are uncommon
        // Anything above 10000 is rare
        
        /**
         * Configure the frequency shield with appropriate colors and text
         *
         * @param context The context for accessing resources
         * @param shieldContainer The container LinearLayout of the shield
         * @param dictionaryNameView The TextView for the dictionary name (left part of shield)
         * @param frequencyValueView The TextView for the frequency value (right part of shield)
         * @param dictionaryName The name of the dictionary
         * @param frequency The word frequency value
         */
        fun setupFrequencyShield(
            context: Context,
            shieldContainer: LinearLayout,
            dictionaryNameView: TextView,
            frequencyValueView: TextView,
            dictionaryName: String,
            frequency: Int
        ) {
            // Set the dictionary name (left side of shield)
            dictionaryNameView.text = dictionaryName.lowercase()
            
            // Set the frequency value (right side of shield)
            frequencyValueView.text = context.getString(R.string.frequency_shield_common, frequency)
            
            // Get the right side background drawable for color changes
            val rightBackground = frequencyValueView.background as? GradientDrawable
            
            // Determine the color based on frequency thresholds
            val colorResId = when {
                frequency <= COMMON_THRESHOLD -> R.color.badge_common      // Common words (green)
                frequency <= UNCOMMON_THRESHOLD -> R.color.badge_uncommon  // Uncommon words (orange)
                else -> R.color.badge_rare                                 // Rare words (gray)
            }
            
            // Apply the color to the right side of the shield
            rightBackground?.setColor(ContextCompat.getColor(context, colorResId))
            
            // Make the shield visible
            shieldContainer.visibility = android.view.View.VISIBLE
        }
        
        /**
         * Hide the frequency shield when no frequency data is available
         * 
         * @param shieldContainer The container LinearLayout of the shield
         */
        fun hideFrequencyShield(shieldContainer: LinearLayout) {
            shieldContainer.visibility = android.view.View.GONE
        }
        
        /**
         * Get a short name for the dictionary 
         * Extracts a reasonable short name from the full dictionary title
         *
         * @param fullDictionaryName The full dictionary name/title
         * @return A short name suitable for display in the shield
         */
        fun getShortDictionaryName(fullDictionaryName: String): String {
            // Common dictionary name patterns to extract from
            return when {
                fullDictionaryName.contains("JMdict", ignoreCase = true) -> "jmdict"
                fullDictionaryName.contains("KANJIDIC", ignoreCase = true) -> "kanjidic"
                fullDictionaryName.contains("EDICT", ignoreCase = true) -> "edict"
                fullDictionaryName.contains("CEDICT", ignoreCase = true) -> "cedict"
                // For others, take the first word or first 7 characters, whichever is shorter
                else -> {
                    val firstWord = fullDictionaryName.split(" ").firstOrNull() ?: fullDictionaryName
                    if (firstWord.length <= 7) {
                        firstWord.lowercase()
                    } else {
                        firstWord.substring(0, 7).lowercase()
                    }
                }
            }
        }
    }
}