package com.abaga129.tekisuto.util

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.abaga129.tekisuto.R

/**
 * Helper class for displaying pitch accent information in a Yomichan-style format
 * Provides visual graphs and formatted text for Japanese pitch accent patterns
 */
class PitchAccentDisplayHelper {
    companion object {
        private const val TAG = "PitchAccentDisplay"
        
        /**
         * Setup the complete pitch accent display including graph and text
         *
         * @param context The context for accessing resources
         * @param container The main container for the pitch accent display
         * @param reading The reading of the word (hiragana/katakana)
         * @param pitchAccent The pitch accent pattern (number or formatted string)
         */
        fun setupPitchAccentDisplay(
            context: Context,
            container: LinearLayout,
            reading: String,
            pitchAccent: String
        ) {
            // Find child views
            val readingView = container.findViewById<TextView>(R.id.pitch_accent_reading)
            val graphContainer = container.findViewById<LinearLayout>(R.id.pitch_graph_container)
            val patternTypeView = container.findViewById<TextView>(R.id.pitch_pattern_type)
            
            if (readingView == null || graphContainer == null) {
                container.visibility = View.GONE
                return
            }
            
            // Parse the pitch accent data
            val pitchData = parsePitchAccentData(reading, pitchAccent)
            if (pitchData == null) {
                container.visibility = View.GONE
                return
            }
            
            // Setup the reading text with pitch markings
            setupReadingText(context, readingView, pitchData)
            
            // Setup the visual pitch graph
            setupPitchGraph(context, graphContainer, pitchData)
            
            // Setup pattern type indicator if needed
            if (patternTypeView != null) {
                setupPatternType(context, patternTypeView, pitchData)
            }
            
            // Make the container visible
            container.visibility = View.VISIBLE
        }
        
        /**
         * Hide the pitch accent display
         */
        fun hidePitchAccentDisplay(container: LinearLayout) {
            container.visibility = View.GONE
        }
        
        /**
         * Data class to hold parsed pitch accent information
         */
        private data class PitchAccentData(
            val reading: String,
            val pitchPosition: Int, // 0 = heiban, >0 = position of drop
            val morae: List<String>, // Individual mora units
            val patternType: PitchPatternType,
            val visualPattern: String // Reading with visual markers
        )
        
        /**
         * Enum for different pitch accent pattern types
         */
        private enum class PitchPatternType(val displayName: String) {
            HEIBAN("平板型"),      // Flat, no drop
            ATAMADAKA("頭高型"),   // Drop after first mora
            NAKADAKA("中高型"),    // Drop in middle
            ODAKA("尾高型")        // Drop at end (before particle)
        }
        
        /**
         * Parse pitch accent data from various formats
         */
        private fun parsePitchAccentData(reading: String, pitchAccent: String): PitchAccentData? {
            if (reading.isEmpty()) return null
            
            // Convert reading to mora list
            val morae = convertToMorae(reading)
            if (morae.isEmpty()) return null
            
            // Parse pitch position
            val pitchPosition = when {
                pitchAccent.matches(Regex("^\\d+$")) -> pitchAccent.toIntOrNull() ?: 0
                pitchAccent.contains("↓") -> findPitchDropPosition(pitchAccent, morae)
                else -> 0
            }
            
            // Determine pattern type
            val patternType = determinePatternType(pitchPosition, morae.size)
            
            // Generate visual pattern
            val visualPattern = generateVisualPattern(morae, pitchPosition)
            
            return PitchAccentData(reading, pitchPosition, morae, patternType, visualPattern)
        }
        
        /**
         * Convert Japanese text to mora units
         * Handles basic hiragana/katakana combinations
         */
        private fun convertToMorae(text: String): List<String> {
            val morae = mutableListOf<String>()
            var i = 0
            
            while (i < text.length) {
                val currentChar = text[i]
                
                // Check for small characters (っ, ゃ, ゅ, ょ, etc.)
                if (i + 1 < text.length) {
                    val nextChar = text[i + 1]
                    if (isSmallKana(nextChar)) {
                        morae.add(text.substring(i, i + 2))
                        i += 2
                        continue
                    }
                }
                
                morae.add(currentChar.toString())
                i++
            }
            
            return morae
        }
        
        /**
         * Check if a character is a small kana (ゃ、ゅ、ょ、っ、etc.)
         */
        private fun isSmallKana(char: Char): Boolean {
            return char in setOf('ゃ', 'ゅ', 'ょ', 'っ', 'ャ', 'ュ', 'ョ', 'ッ')
        }
        
        /**
         * Find pitch drop position from pattern string like "か↓れー"
         */
        private fun findPitchDropPosition(pattern: String, morae: List<String>): Int {
            val dropIndex = pattern.indexOf('↓')
            if (dropIndex == -1) return 0
            
            // Count morae before the drop marker
            val beforeDrop = pattern.substring(0, dropIndex)
            return convertToMorae(beforeDrop).size
        }
        
        /**
         * Determine the pattern type based on pitch position and word length
         */
        private fun determinePatternType(pitchPosition: Int, moraeCount: Int): PitchPatternType {
            return when (pitchPosition) {
                0 -> PitchPatternType.HEIBAN
                1 -> PitchPatternType.ATAMADAKA
                moraeCount -> PitchPatternType.ODAKA
                else -> PitchPatternType.NAKADAKA
            }
        }
        
        /**
         * Generate visual pattern with pitch markers
         */
        private fun generateVisualPattern(morae: List<String>, pitchPosition: Int): String {
            if (pitchPosition == 0) {
                return morae.joinToString("")
            }
            
            val result = StringBuilder()
            for (i in morae.indices) {
                result.append(morae[i])
                if (i + 1 == pitchPosition) {
                    result.append("↓")
                }
            }
            return result.toString()
        }
        
        /**
         * Setup the reading text with pitch accent markings
         */
        private fun setupReadingText(context: Context, textView: TextView, pitchData: PitchAccentData) {
            val spannableString = SpannableString(pitchData.visualPattern)
            
            // Apply styling to pitch drop markers
            val dropArrowIndex = pitchData.visualPattern.indexOf("↓")
            if (dropArrowIndex >= 0) {
                spannableString.setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(context, R.color.accent_drop_color)),
                    dropArrowIndex,
                    dropArrowIndex + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannableString.setSpan(
                    StyleSpan(android.graphics.Typeface.BOLD),
                    dropArrowIndex,
                    dropArrowIndex + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            
            textView.text = spannableString
        }
        
        /**
         * Setup the visual pitch graph using dots and lines
         */
        private fun setupPitchGraph(context: Context, container: LinearLayout, pitchData: PitchAccentData) {
            container.removeAllViews()
            
            val morae = pitchData.morae
            val pitchPosition = pitchData.pitchPosition
            
            if (morae.isEmpty()) return
            
            // Calculate pitch levels for each mora
            val pitchLevels = calculatePitchLevels(morae.size, pitchPosition)
            
            // Create visual elements for each mora
            for (i in morae.indices) {
                // Add pitch dot
                val dot = createPitchDot(context, pitchLevels[i])
                container.addView(dot)
                
                // Add connecting line (except for last element)
                if (i < morae.size - 1) {
                    val line = createConnectingLine(context, pitchLevels[i], pitchLevels[i + 1])
                    container.addView(line)
                }
            }
        }
        
        /**
         * Calculate pitch levels for each mora (true = high, false = low)
         */
        private fun calculatePitchLevels(moraeCount: Int, pitchPosition: Int): List<Boolean> {
            val levels = mutableListOf<Boolean>()
            
            for (i in 0 until moraeCount) {
                when {
                    pitchPosition == 0 -> {
                        // Heiban: low first, then high
                        levels.add(i > 0)
                    }
                    pitchPosition == 1 -> {
                        // Atamadaka: high first, then low
                        levels.add(i == 0)
                    }
                    i < pitchPosition -> {
                        // Before drop: low first, then high
                        levels.add(i > 0)
                    }
                    else -> {
                        // After drop: low
                        levels.add(false)
                    }
                }
            }
            
            return levels
        }
        
        /**
         * Create a pitch dot view
         */
        private fun createPitchDot(context: Context, isHigh: Boolean): View {
            val dot = View(context)
            val size = dpToPx(context, 6)
            val layoutParams = LinearLayout.LayoutParams(size, size)
            
            // Position the dot based on pitch level
            if (isHigh) {
                layoutParams.topMargin = dpToPx(context, 4)
            } else {
                layoutParams.topMargin = dpToPx(context, 14)
            }
            layoutParams.marginEnd = dpToPx(context, 2)
            
            dot.layoutParams = layoutParams
            
            // Style the dot
            val drawable = GradientDrawable()
            drawable.shape = GradientDrawable.OVAL
            drawable.setColor(ContextCompat.getColor(context, 
                if (isHigh) R.color.pitch_high else R.color.pitch_low))
            dot.background = drawable
            
            return dot
        }
        
        /**
         * Create a connecting line between pitch dots
         */
        private fun createConnectingLine(context: Context, fromHigh: Boolean, toHigh: Boolean): View {
            val line = View(context)
            val width = dpToPx(context, 12)
            val height = dpToPx(context, 2)
            
            val layoutParams = LinearLayout.LayoutParams(width, height)
            
            // Position based on pitch transition
            when {
                fromHigh && toHigh -> layoutParams.topMargin = dpToPx(context, 7)      // High to high
                !fromHigh && !toHigh -> layoutParams.topMargin = dpToPx(context, 17)  // Low to low
                fromHigh && !toHigh -> layoutParams.topMargin = dpToPx(context, 12)   // High to low (diagonal)
                !fromHigh && toHigh -> layoutParams.topMargin = dpToPx(context, 12)   // Low to high (diagonal)
            }
            
            line.layoutParams = layoutParams
            line.setBackgroundColor(ContextCompat.getColor(context, R.color.pitch_connector))
            
            return line
        }
        
        /**
         * Setup pattern type indicator
         */
        private fun setupPatternType(context: Context, textView: TextView, pitchData: PitchAccentData) {
            val patternText = "[${pitchData.pitchPosition}] ${pitchData.patternType.displayName}"
            textView.text = patternText
            textView.visibility = View.VISIBLE
        }
        
        /**
         * Convert dp to pixels
         */
        private fun dpToPx(context: Context, dp: Int): Int {
            return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp.toFloat(),
                context.resources.displayMetrics
            ).toInt()
        }
    }
}
