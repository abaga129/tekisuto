package com.abaga129.tekisuto.model.yomitan

import android.util.Log
import com.abaga129.tekisuto.util.StructuredContentHtmlConverter

/**
 * Parser for Yomitan dictionary content
 * Handles different formats of dictionary entries
 */
object YomitanContentParser {
    private const val TAG = "YomitanContentParser"

    /**
     * Parse definition content from Yomitan dictionaries
     * Handles both simple arrays and structured content formats
     *
     * @param definitionsRaw The raw definition data from JSON
     * @return Formatted definition string
     */
    fun parseDefinitions(definitionsRaw: Any?): Pair<String, Boolean> {
        var definition = ""
        var isHtmlContent = false

        try {
            when (definitionsRaw) {
                is List<*> -> {
                    // Common format: List of strings or structured objects
                    definition = parseDefinitionsList(definitionsRaw)
                }
                is String -> {
                    // Simple string definition
                    definition = definitionsRaw
                }
                is Map<*, *> -> {
                    // Single structured content object
                    definition = parseStructuredContentMap(definitionsRaw)
                }
                else -> {
                    // Unknown format
                    Log.w(TAG, "Unknown definition format: ${definitionsRaw?.javaClass?.simpleName}")
                    definition = definitionsRaw?.toString() ?: ""
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing definitions", e)
            definition = definitionsRaw?.toString() ?: ""
        }

        // Check if content contains HTML markers
        isHtmlContent = definition.contains("<") && definition.contains(">")

        // Log if definition is empty for debugging
        if (definition.isEmpty() && definitionsRaw != null) {
            Log.w(TAG, "Empty definition after parsing. Raw type: ${definitionsRaw.javaClass.simpleName}")
        }

        return Pair(definition, isHtmlContent)
    }

    /**
     * Parse a list of definition items
     */
    private fun parseDefinitionsList(definitions: List<*>): String {
        val sb = StringBuilder()

        definitions.forEachIndexed { index, def ->
            when (def) {
                is String -> {
                    // Simple string definition
                    sb.append(def)
                }
                is Map<*, *> -> {
                    // Structured content object
                    sb.append(parseStructuredContentMap(def))
                }
                is List<*> -> {
                    // Nested list - sometimes definitions have nested structures
                    sb.append(parseDefinitionsList(def))
                }
                else -> {
                    // Try to get string representation for unknown types
                    val text = def?.toString() ?: ""
                    if (text.isNotEmpty() && text != "null") {
                        sb.append(text)
                    }
                }
            }

            // Add newline between definitions
            if (index < definitions.size - 1) {
                sb.append("\n")
            }
        }

        return sb.toString()
    }

    /**
     * Parse a structured content map object
     * Handles different formats of structured content
     */
    private fun parseStructuredContentMap(contentMap: Map<*, *>): String {
        // Check for different common formats
        
        // Format 1: Dictionary has a "type" field with "structured-content"
        if (contentMap["type"] == "structured-content") {
            val content = contentMap["content"]
            return if (content != null) {
                StructuredContentHtmlConverter.convertToHtml(content)
            } else {
                contentMap.toString()
            }
        }
        
        // Format 2: Object with direct "content" field
        val content = contentMap["content"]
        if (content != null) {
            return StructuredContentHtmlConverter.convertToHtml(content)
        }
        
        // Format 3: Object with "tag" field (direct tag structure)
        if (contentMap["tag"] != null) {
            return StructuredContentHtmlConverter.convertToHtml(contentMap)
        }
        
        // Format 4: Object with "text" field (simple text wrapper)
        val text = contentMap["text"]
        if (text is String) {
            return text
        }
        
        // Last resort: convert the entire map to string
        return contentMap.toString()
    }
}