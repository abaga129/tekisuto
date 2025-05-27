package com.abaga129.tekisuto.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager

/**
 * Enhanced pitch accent export utility for AnkiDroid integration
 * Provides multiple export formats including plain text, HTML, and visual representations
 */
class PitchAccentExportHelper {
    
    companion object {
        private const val TAG = "PitchAccentExport"
        private const val PREF_PITCH_EXPORT_FORMAT = "pitch_export_format"
        private const val PREF_INCLUDE_PATTERN_TYPE = "pitch_include_pattern_type"
        private const val PREF_INCLUDE_VISUAL_GRAPH = "pitch_include_visual_graph"
        private const val PREF_INCLUDE_READING = "pitch_include_reading"
        private const val PREF_GRAPH_STYLE = "pitch_graph_style"
        
        /**
         * Export formats for pitch accent data
         */
        enum class ExportFormat {
            PLAIN_TEXT,      // "平板型 [0]"
            HTML_FORMATTED,  // HTML with styling
            VISUAL_GRAPH,    // HTML with visual pitch graph
            YOMICHAN_STYLE   // Formatted like Yomichan extension
        }
        
        /**
         * Graph styles for visual representation
         */
        enum class GraphStyle {
            DOTS_AND_LINES,  // Traditional dots connected by lines
            MODERN_BARS,     // Modern bar chart style
            MINIMALIST      // Clean, simple style
        }
        
        /**
         * Generate pitch accent export string based on user preferences
         */
        fun generatePitchAccentForExport(
            context: Context,
            reading: String,
            pitchAccent: String
        ): String? {
            if (reading.isEmpty() || pitchAccent.isEmpty()) {
                Log.d(TAG, "Empty reading or pitch accent data")
                return null
            }
            
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val formatName = prefs.getString(PREF_PITCH_EXPORT_FORMAT, ExportFormat.HTML_FORMATTED.name) 
                ?: ExportFormat.HTML_FORMATTED.name
            
            val format = try {
                ExportFormat.valueOf(formatName)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Invalid export format: $formatName, using HTML_FORMATTED")
                ExportFormat.HTML_FORMATTED
            }
            
            val includePatternType = prefs.getBoolean(PREF_INCLUDE_PATTERN_TYPE, true)
            val includeVisualGraph = prefs.getBoolean(PREF_INCLUDE_VISUAL_GRAPH, true)
            val includeReading = prefs.getBoolean(PREF_INCLUDE_READING, true)
            
            return when (format) {
                ExportFormat.PLAIN_TEXT -> generatePlainText(reading, pitchAccent, includePatternType, includeReading)
                ExportFormat.HTML_FORMATTED -> generateHtmlFormatted(reading, pitchAccent, includePatternType, includeReading)
                ExportFormat.VISUAL_GRAPH -> generateVisualGraph(context, reading, pitchAccent, includeVisualGraph, includePatternType, includeReading)
                ExportFormat.YOMICHAN_STYLE -> generateYomichanStyle(reading, pitchAccent)
            }
        }
        
        /**
         * Generate plain text format: "平板型 [0] (かれー)"
         */
        private fun generatePlainText(reading: String, pitchAccent: String, includePatternType: Boolean, includeReading: Boolean): String {
            val pitchPosition = parsePitchPosition(pitchAccent)
            val morae = convertToMorae(reading)
            val patternType = if (includePatternType) {
                getPatternTypeName(pitchPosition, morae.size)
            } else null
            
            return buildString {
                if (patternType != null) {
                    append(patternType)
                    append(" ")
                }
                append("[$pitchPosition]")
                if (includeReading && reading.isNotEmpty()) {
                    append(" ($reading)")
                }
            }
        }
        
        /**
         * Generate HTML formatted version with dark mode compatible styling (always enabled)
         */
        private fun generateHtmlFormatted(reading: String, pitchAccent: String, includePatternType: Boolean, includeReading: Boolean): String {
            val pitchPosition = parsePitchPosition(pitchAccent)
            val morae = convertToMorae(reading)
            val patternType = if (includePatternType) {
                getPatternTypeName(pitchPosition, morae.size)
            } else null
            
            return buildString {
                append("<div class='pitch-accent' style='")
                append("font-family: \"Noto Sans JP\", \"Hiragino Sans\", sans-serif; ")
                append("line-height: 1.5; ")
                append("padding: 6px 8px; ")
                append("border-radius: 6px; ")
                append("background: var(--card-bg, rgba(0,0,0,0.02)); ")
                append("color: var(--text-color, currentColor); ")
                append("display: inline-block;")
                append("'>")
                
                // Add CSS variables for theme compatibility
                append("<style scoped>")
                append("""
                    .pitch-accent {
                        --badge-bg: rgba(108, 117, 125, 0.1);
                        --badge-color: #6c757d;
                        --number-color: #e74c3c;
                        --drop-color: #e74c3c;
                    }
                    @media (prefers-color-scheme: dark) {
                        .pitch-accent {
                            --badge-bg: rgba(173, 181, 189, 0.15);
                            --badge-color: #adb5bd;
                            --number-color: #ff6b6b;
                            --drop-color: #ff6b6b;
                        }
                    }
                """.trimIndent())
                append("</style>")
                
                if (patternType != null) {
                    append("<span class='pattern-type' style='")
                    append("background: var(--badge-bg); ")
                    append("color: var(--badge-color); ")
                    append("padding: 3px 8px; ")
                    append("border-radius: 4px; ")
                    append("font-size: 0.85em; ")
                    append("font-weight: 500; ")
                    append("margin-right: 8px; ")
                    append("border: 1px solid var(--badge-color, #6c757d);")
                    append("'>")
                    append(patternType)
                    append("</span>")
                }
                
                append("<span class='pitch-number' style='")
                append("font-weight: bold; ")
                append("color: var(--number-color); ")
                append("font-size: 1.15em; ")
                append("text-shadow: 0 1px 2px rgba(0,0,0,0.1);")
                append("'>")
                append("[$pitchPosition]")
                append("</span>")
                
                if (includeReading && reading.isNotEmpty()) {
                    val visualReading = generateVisualReadingDarkMode(morae, pitchPosition)
                    append(" <span class='reading' style='")
                    append("color: var(--text-color, currentColor); ")
                    append("font-size: 1.1em; ")
                    append("margin-left: 6px; ")
                    append("font-weight: 500;")
                    append("'>")
                    append(visualReading)
                    append("</span>")
                }
                
                append("</div>")
            }
        }
        
        /**
         * Generate visual pitch graph in HTML with dark mode support (always enabled)
         */
        private fun generateVisualGraph(
            context: Context,
            reading: String, 
            pitchAccent: String, 
            includeGraph: Boolean, 
            includePatternType: Boolean,
            includeReading: Boolean
        ): String {
            val pitchPosition = parsePitchPosition(pitchAccent)
            val morae = convertToMorae(reading)
            
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val graphStyleName = prefs.getString(PREF_GRAPH_STYLE, GraphStyle.DOTS_AND_LINES.name) 
                ?: GraphStyle.DOTS_AND_LINES.name
            
            val graphStyle = try {
                GraphStyle.valueOf(graphStyleName)
            } catch (e: IllegalArgumentException) {
                GraphStyle.DOTS_AND_LINES
            }
            
            return buildString {
                // Dark mode compatible styling (always enabled)
                append("<div class='pitch-accent-visual' style='")
                append("font-family: \"Noto Sans JP\", \"Hiragino Sans\", sans-serif; ")
                append("padding: 12px; ")
                append("border: 1px solid var(--border-color, #e0e0e0); ")
                append("border-radius: 8px; ")
                append("background: var(--card-bg, #fafafa); ")
                append("margin: 6px 0; ")
                append("color: var(--text-color, #333); ")
                append("box-shadow: 0 1px 3px rgba(0,0,0,0.1);")
                append("'>")
                
                // CSS variables for dark mode compatibility (always enabled)
                append("<style>")
                append("""
                    .pitch-accent-visual {
                        --high-color: #e74c3c;
                        --low-color: #7f8c8d;
                        --connector-color: #bdc3c7;
                        --text-color: currentColor;
                        --badge-bg: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        --drop-color: #e74c3c;
                    }
                    @media (prefers-color-scheme: dark) {
                        .pitch-accent-visual {
                            --high-color: #ff6b6b;
                            --low-color: #a0a0a0;
                            --connector-color: #6c757d;
                            --text-color: #f8f9fa;
                            --badge-bg: linear-gradient(135deg, #74b9ff 0%, #0984e3 100%);
                            --drop-color: #ff6b6b;
                        }
                    }
                """.trimIndent())
                append("</style>")
                
                // Pattern type badge with improved contrast
                if (includePatternType) {
                    val patternType = getPatternTypeName(pitchPosition, morae.size)
                    append("<div class='pattern-badge' style='margin-bottom: 10px;'>")
                    append("<span style='")
                    append("background: var(--badge-bg); ")
                    append("color: white; ")
                    append("padding: 4px 10px; ")
                    append("border-radius: 14px; ")
                    append("font-size: 0.85em; ")
                    append("font-weight: 600; ")
                    append("text-shadow: 0 1px 2px rgba(0,0,0,0.3);")
                    append("'>")
                    append("$patternType [$pitchPosition]")
                    append("</span>")
                    append("</div>")
                }
                
                // Reading with visual markers - improved for dark mode (always enabled)
                if (includeReading && morae.isNotEmpty()) {
                    val visualReading = generateVisualReadingDarkMode(morae, pitchPosition)
                    append("<div class='pitch-reading' style='")
                    append("font-size: 1.3em; ")
                    append("margin-bottom: 12px; ")
                    append("font-weight: 500; ")
                    append("color: var(--text-color);")
                    append("'>")
                    append(visualReading)
                    append("</div>")
                }
                
                // Visual pitch graph with dark mode colors (always enabled)
                if (includeGraph && morae.isNotEmpty()) {
                    when (graphStyle) {
                        GraphStyle.DOTS_AND_LINES -> append(generateSvgPitchGraphDarkMode(morae, pitchPosition))
                        GraphStyle.MODERN_BARS -> append(generateModernBarGraphDarkMode(morae, pitchPosition))
                        GraphStyle.MINIMALIST -> append(generateMinimalistGraphDarkMode(morae, pitchPosition))
                    }
                }
                
                append("</div>")
            }
        }
        
        /**
         * Generate Yomichan-style format
         */
        private fun generateYomichanStyle(reading: String, pitchAccent: String): String {
            val pitchPosition = parsePitchPosition(pitchAccent)
            val morae = convertToMorae(reading)
            val visualReading = generateVisualReadingDarkMode(morae, pitchPosition)
            
            return buildString {
                append(visualReading)
                append(" [")
                append(pitchPosition)
                append("]")
            }
        }
        
        /**
         * Generate dark mode compatible SVG pitch graph (always enabled)
         */
        private fun generateSvgPitchGraphDarkMode(morae: List<String>, pitchPosition: Int): String {
            if (morae.isEmpty()) return ""
            
            val width = morae.size * 30 + 40
            val height = 55
            val pitchLevels = calculatePitchLevels(morae.size, pitchPosition)
            
            return buildString {
                append("<svg width='$width' height='$height' style='margin: 10px auto; display: block; background: rgba(0,0,0,0.02); border-radius: 6px; padding: 5px;' viewBox='0 0 $width $height'>")
                append("<defs>")
                append("<style>")
                append("""
                    .pitch-dot-high { 
                        fill: var(--high-color, #e74c3c); 
                        stroke: currentColor; 
                        stroke-width: 2; 
                        filter: drop-shadow(0 1px 2px rgba(0,0,0,0.3));
                    }
                    .pitch-dot-low { 
                        fill: var(--low-color, #7f8c8d); 
                        stroke: currentColor; 
                        stroke-width: 2; 
                        filter: drop-shadow(0 1px 2px rgba(0,0,0,0.3));
                    }
                    .pitch-line { 
                        stroke: var(--connector-color, #bdc3c7); 
                        stroke-width: 3; 
                        stroke-linecap: round;
                    }
                    .mora-text { 
                        font-family: "Noto Sans JP", sans-serif; 
                        font-size: 13px; 
                        text-anchor: middle; 
                        fill: var(--text-color, currentColor);
                        font-weight: 500;
                    }
                """.trimIndent())
                append("</style>")
                append("</defs>")
                
                // Draw connecting lines first (behind dots)
                for (i in 0 until morae.size - 1) {
                    val x1 = i * 30 + 20
                    val x2 = (i + 1) * 30 + 20
                    val y1 = if (pitchLevels[i]) 15 else 30
                    val y2 = if (pitchLevels[i + 1]) 15 else 30
                    
                    append("<line x1='$x1' y1='$y1' x2='$x2' y2='$y2' class='pitch-line'/>")
                }
                
                // Draw dots and mora labels
                for (i in morae.indices) {
                    val x = i * 30 + 20
                    val y = if (pitchLevels[i]) 15 else 30
                    val dotClass = if (pitchLevels[i]) "pitch-dot-high" else "pitch-dot-low"
                    
                    // Draw dot with larger radius for better visibility
                    append("<circle cx='$x' cy='$y' r='5' class='$dotClass'/>")
                    
                    // Draw mora text below
                    append("<text x='$x' y='48' class='mora-text'>${morae[i]}</text>")
                }
                
                append("</svg>")
            }
        }
        
        /**
         * Generate dark mode compatible modern bar graph (always enabled)
         */
        private fun generateModernBarGraphDarkMode(morae: List<String>, pitchPosition: Int): String {
            if (morae.isEmpty()) return ""
            
            val width = morae.size * 28 + 20
            val height = 50
            val pitchLevels = calculatePitchLevels(morae.size, pitchPosition)
            
            return buildString {
                append("<svg width='$width' height='$height' style='margin: 10px auto; display: block; background: rgba(0,0,0,0.02); border-radius: 6px; padding: 5px;' viewBox='0 0 $width $height'>")
                append("<defs>")
                append("<linearGradient id='highGradDark' x1='0%' y1='0%' x2='0%' y2='100%'>")
                append("<stop offset='0%' style='stop-color:var(--high-color, #ff6b6b);stop-opacity:1' />")
                append("<stop offset='100%' style='stop-color:var(--high-color, #e74c3c);stop-opacity:0.8' />")
                append("</linearGradient>")
                append("<linearGradient id='lowGradDark' x1='0%' y1='0%' x2='0%' y2='100%'>")
                append("<stop offset='0%' style='stop-color:var(--low-color, #a0a0a0);stop-opacity:1' />")
                append("<stop offset='100%' style='stop-color:var(--low-color, #7f8c8d);stop-opacity:0.8' />")
                append("</linearGradient>")
                append("</defs>")
                
                for (i in morae.indices) {
                    val x = i * 28 + 10
                    val barHeight = if (pitchLevels[i]) 22 else 14
                    val y = 28 - barHeight
                    val fill = if (pitchLevels[i]) "url(#highGradDark)" else "url(#lowGradDark)"
                    
                    append("<rect x='$x' y='$y' width='18' height='$barHeight' fill='$fill' rx='3' stroke='currentColor' stroke-width='0.5' opacity='0.9'/>")
                    append("<text x='${x + 9}' y='44' style='font-size: 11px; text-anchor: middle; fill: var(--text-color, currentColor); font-weight: 500;'>${morae[i]}</text>")
                }
                
                append("</svg>")
            }
        }
        
        /**
         * Generate dark mode compatible minimalist graph (always enabled)
         */
        private fun generateMinimalistGraphDarkMode(morae: List<String>, pitchPosition: Int): String {
            if (morae.isEmpty()) return ""
            
            val height = 35
            val pitchLevels = calculatePitchLevels(morae.size, pitchPosition)
            
            return buildString {
                append("<div style='")
                append("display: flex; ")
                append("align-items: center; ")
                append("justify-content: center; ")
                append("height: ${height}px; ")
                append("margin: 10px 0; ")
                append("padding: 8px; ")
                append("background: rgba(0,0,0,0.02); ")
                append("border-radius: 6px; ")
                append("font-family: \"Noto Sans JP\", monospace;")
                append("'>")
                
                for (i in morae.indices) {
                    val isHigh = pitchLevels[i]
                    val symbolColor = if (isHigh) "var(--high-color, #e74c3c)" else "var(--low-color, #7f8c8d)"
                    val symbol = if (isHigh) "●" else "○"
                    
                    append("<span style='display: flex; flex-direction: column; align-items: center; margin: 0 3px;'>")
                    append("<span style='color: $symbolColor; font-size: 16px; line-height: 1; text-shadow: 0 1px 2px rgba(0,0,0,0.2);'>$symbol</span>")
                    append("<span style='font-size: 11px; color: var(--text-color, currentColor); margin-top: 3px; font-weight: 500;'>${morae[i]}</span>")
                    append("</span>")
                    
                    // Add connector with better visibility
                    if (i < morae.size - 1) {
                        val connector = if (isHigh == pitchLevels[i + 1]) "─" else if (isHigh) "＼" else "／"
                        val connectorColor = "var(--connector-color, #bdc3c7)"
                        append("<span style='color: $connectorColor; align-self: ${if (isHigh) "start" else "end"}; margin: 0 -1px; font-size: 14px;'>$connector</span>")
                    }
                }
                
                append("</div>")
            }
        }
        
        /**
         * Generate visual reading with dark mode compatible pitch drop markers (always enabled)
         */
        private fun generateVisualReadingDarkMode(morae: List<String>, pitchPosition: Int): String {
            if (pitchPosition == 0) {
                return morae.joinToString("")
            }
            
            return buildString {
                for (i in morae.indices) {
                    append(morae[i])
                    if (i + 1 == pitchPosition) {
                        append("<span style='color: var(--drop-color); font-weight: bold; font-size: 1.3em; text-shadow: 0 0 2px currentColor;'>↓</span>")
                    }
                }
            }
        }
        
        /**
         * Parse pitch position from various formats
         */
        private fun parsePitchPosition(pitchAccent: String): Int {
            return when {
                pitchAccent.matches(Regex("^\\d+$")) -> pitchAccent.toIntOrNull() ?: 0
                pitchAccent.contains("↓") -> {
                    val beforeDrop = pitchAccent.substringBefore("↓")
                    convertToMorae(beforeDrop).size
                }
                else -> {
                    Log.w(TAG, "Unknown pitch accent format: $pitchAccent")
                    0
                }
            }
        }
        
        /**
         * Get pattern type name in Japanese
         */
        private fun getPatternTypeName(pitchPosition: Int, moraeCount: Int): String {
            return when (pitchPosition) {
                0 -> "平板型"        // Heiban (flat)
                1 -> "頭高型"        // Atamadaka (initial high)
                moraeCount -> "尾高型" // Odaka (final high)
                else -> "中高型"     // Nakadaka (middle high)
            }
        }
        
        /**
         * Convert Japanese text to mora units
         */
        private fun convertToMorae(text: String): List<String> {
            val morae = mutableListOf<String>()
            var i = 0
            
            while (i < text.length) {
                if (i + 1 < text.length) {
                    val nextChar = text[i + 1]
                    if (isSmallKana(nextChar)) {
                        morae.add(text.substring(i, i + 2))
                        i += 2
                        continue
                    }
                }
                morae.add(text[i].toString())
                i++
            }
            
            return morae
        }
        
        /**
         * Check if character is small kana
         */
        private fun isSmallKana(char: Char): Boolean {
            return char in setOf('ゃ', 'ゅ', 'ょ', 'っ', 'ャ', 'ュ', 'ョ', 'ッ', 'ぁ', 'ぃ', 'ぅ', 'ぇ', 'ぉ', 'ァ', 'ィ', 'ゥ', 'ェ', 'ォ')
        }
        
        /**
         * Calculate pitch levels for visual display
         */
        private fun calculatePitchLevels(moraeCount: Int, pitchPosition: Int): List<Boolean> {
            val levels = mutableListOf<Boolean>()
            
            for (i in 0 until moraeCount) {
                when {
                    pitchPosition == 0 -> levels.add(i > 0)  // Heiban: low first, then high
                    pitchPosition == 1 -> levels.add(i == 0) // Atamadaka: high first, then low
                    i < pitchPosition -> levels.add(i > 0)   // Before drop: low first, then high
                    else -> levels.add(false)                // After drop: low
                }
            }
            
            return levels
        }
    }
}
