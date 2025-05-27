package com.abaga129.tekisuto.util

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager

/**
 * Utility class to test and debug pitch accent export settings
 */
object PitchAccentSettingsTest {
    private const val TAG = "PitchAccentSettings"
    
    /**
     * Log all current pitch accent settings for debugging
     */
    fun logCurrentSettings(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        
        Log.d(TAG, "=== Pitch Accent Export Settings ===")
        Log.d(TAG, "Pitch Accent Export: Always Enabled")
        Log.d(TAG, "Format: ${prefs.getString("pitch_export_format", "HTML_FORMATTED")}")
        Log.d(TAG, "Include Pattern Type: ${prefs.getBoolean("pitch_include_pattern_type", true)}")
        Log.d(TAG, "Include Visual Graph: ${prefs.getBoolean("pitch_include_visual_graph", true)}")
        Log.d(TAG, "Include Reading: ${prefs.getBoolean("pitch_include_reading", true)}")
        Log.d(TAG, "Graph Style: ${prefs.getString("pitch_graph_style", "DOTS_AND_LINES")}")
        Log.d(TAG, "Dark Mode Compatible: Always Enabled")
        Log.d(TAG, "=====================================")
    }
    
    /**
     * Test pitch accent generation with current settings
     */
    fun testPitchAccentGeneration(context: Context): String {
        val testWords = listOf(
            Pair("かれー", "0"),   // Heiban (flat)
            Pair("あたま", "1"),   // Atamadaka (initial high)
            Pair("こころ", "2"),   // Nakadaka (middle high)
            Pair("さくら", "3")    // Odaka (final high)
        )
        
        val results = StringBuilder()
        results.append("=== Pitch Accent Test Results ===\n")
        
        testWords.forEach { (reading, pitchAccent) ->
            val formatted = PitchAccentExportHelper.generatePitchAccentForExport(
                context, reading, pitchAccent
            )
            results.append("$reading [$pitchAccent]: $formatted\n")
        }
        
        results.append("================================")
        Log.d(TAG, results.toString())
        return results.toString()
    }
    
    /**
     * Generate example HTML for testing in browser with dark mode support (always enabled)
     */
    fun generateTestHtml(context: Context): String {
        val testWords = listOf(
            Triple("かれー", "0", "curry (heiban)"),
            Triple("あたま", "1", "head (atamadaka)"),
            Triple("こころ", "2", "heart (nakadaka)"),
            Triple("さくら", "3", "cherry blossom (odaka)")
        )
        
        val html = StringBuilder()
        html.append("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Pitch Accent Export Test</title>
                <style>
                    body { 
                        font-family: 'Noto Sans JP', sans-serif; 
                        margin: 20px; 
                        background: var(--bg-color, #ffffff);
                        color: var(--text-color, #333333);
                        transition: background-color 0.3s, color 0.3s;
                    }
                    .test-item { 
                        margin: 20px 0; 
                        padding: 20px; 
                        border: 1px solid var(--border-color, #ddd); 
                        border-radius: 8px; 
                        background: var(--card-bg, #fafafa);
                        box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                    }
                    .word-info { 
                        color: var(--info-color, #666); 
                        font-size: 14px; 
                        margin-bottom: 12px; 
                        font-weight: 500;
                    }
                    .theme-toggle {
                        position: fixed;
                        top: 20px;
                        right: 20px;
                        padding: 10px 15px;
                        background: var(--button-bg, #007bff);
                        color: white;
                        border: none;
                        border-radius: 5px;
                        cursor: pointer;
                        font-size: 14px;
                    }
                    
                    /* Light theme (default) */
                    :root {
                        --bg-color: #ffffff;
                        --text-color: #333333;
                        --border-color: #e0e0e0;
                        --card-bg: #fafafa;
                        --info-color: #666666;
                        --button-bg: #007bff;
                    }
                    
                    /* Dark theme */
                    [data-theme="dark"] {
                        --bg-color: #1a1a1a;
                        --text-color: #f0f0f0;
                        --border-color: #404040;
                        --card-bg: #2a2a2a;
                        --info-color: #cccccc;
                        --button-bg: #0056b3;
                    }
                </style>
            </head>
            <body>
                <button class="theme-toggle" onclick="toggleTheme()">🌙 Toggle Dark Mode</button>
                <h1>Pitch Accent Export Test</h1>
                <p>Testing different pitch accent patterns with current settings. Use the toggle button to test dark mode compatibility (always enabled).</p>
        """.trimIndent())
        
        testWords.forEach { (reading, pitchAccent, meaning) ->
            val formatted = PitchAccentExportHelper.generatePitchAccentForExport(
                context, reading, pitchAccent
            ) ?: "Error generating"
            
            html.append("""
                <div class="test-item">
                    <div class="word-info">Word: $reading | Pattern: $pitchAccent | Meaning: $meaning</div>
                    $formatted
                </div>
            """.trimIndent())
        }
        
        html.append("""
                <script>
                    function toggleTheme() {
                        const body = document.body;
                        const currentTheme = body.getAttribute('data-theme');
                        const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
                        body.setAttribute('data-theme', newTheme);
                        
                        const button = document.querySelector('.theme-toggle');
                        button.textContent = newTheme === 'dark' ? '☀️ Toggle Light Mode' : '🌙 Toggle Dark Mode';
                    }
                </script>
            </body>
            </html>
        """.trimIndent())
        return html.toString()
    }
}
