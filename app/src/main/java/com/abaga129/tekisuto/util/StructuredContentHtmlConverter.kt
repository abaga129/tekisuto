package com.abaga129.tekisuto.util

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Utility class for converting dictionary structured content format to HTML
 */
class StructuredContentHtmlConverter {
    companion object {
        private const val TAG = "StructuredContentHtml"

        /**
         * Convert structured content to HTML
         * @param contentObject The structured content JSON object with "type" and "content" fields
         * @return HTML string representation of the structured content
         */
        fun convertToHtml(contentObject: JSONObject): String {
            return try {
                Log.d(TAG, "Converting structured content object: ${contentObject.toString().take(100)}...")
                
                // Check if this is a proper structured content object
                if (contentObject.has("type") && contentObject.getString("type") == "structured-content") {
                    val content = contentObject.opt("content")
                    
                    val html = when (content) {
                        is JSONArray -> jsonArrayToHtml(content)
                        is JSONObject -> jsonObjectToHtml(content)
                        is String -> content
                        null -> {
                            Log.w(TAG, "Structured content object has null content field!")
                            "No content available"
                        }
                        else -> {
                            Log.w(TAG, "Invalid content format: ${content.javaClass.simpleName}")
                            "Invalid content format"
                        }
                    }
                    
                    // Log the generated HTML for debugging
                    Log.d(TAG, "Generated HTML (first 100 chars): ${html.take(100)}...")
                    
                    // Return HTML wrapped in a root element to ensure proper structure
                    "<div class='dictionary-content'>$html</div>"
                } else {
                    // Not a proper structured content object
                    Log.w(TAG, "Not a valid structured content object - missing type field or type is not 'structured-content'")
                    
                    // Try to extract any useful information we can
                    val extractedHtml = StringBuilder("<div class='extracted-content'>")
                    
                    // If it has a content field, try to use that
                    if (contentObject.has("content")) {
                        val content = contentObject.get("content")
                        if (content is JSONArray) {
                            extractedHtml.append(jsonArrayToHtml(content))
                        } else if (content is JSONObject) {
                            extractedHtml.append(jsonObjectToHtml(content))
                        } else if (content is String) {
                            extractedHtml.append("<p>").append(content).append("</p>")
                        }
                    }
                    
                    // If it has a text field, use that
                    if (contentObject.has("text")) {
                        extractedHtml.append("<p>").append(contentObject.getString("text")).append("</p>")
                    }
                    
                    extractedHtml.append("</div>")
                    return extractedHtml.toString()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error converting structured content to HTML: ${e.message}", e)
                "<p>Error parsing content: ${e.message}</p>"
            }
        }

        /**
         * Convert a structured content JSON array to HTML
         */
        fun jsonArrayToHtml(jsonArray: JSONArray): String {
            val html = StringBuilder()
            
            for (i in 0 until jsonArray.length()) {
                try {
                    val item = jsonArray.get(i)
                    
                    when (item) {
                        is String -> html.append(item)
                        is JSONObject -> html.append(jsonObjectToHtml(item))
                        is JSONArray -> html.append(jsonArrayToHtml(item))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing array item at index $i: ${e.message}")
                }
            }
            
            return html.toString()
        }

        /**
         * Convert a structured content JSON object to HTML
         */
        fun jsonObjectToHtml(jsonObject: JSONObject): String {
            try {
                // Get the tag name
                if (!jsonObject.has("tag")) {
                    return extractTextFromObject(jsonObject)
                }
                
                val tag = jsonObject.getString("tag")
                val html = StringBuilder()
                
                // Process based on tag type
                when (tag) {
                    // Core structural elements
                    "div", "span", "p" -> {
                        html.append("<$tag")
                        appendHtmlAttributes(html, jsonObject)
                        html.append(">")
                        
                        // Process content
                        if (jsonObject.has("content")) {
                            val content = jsonObject.get("content")
                            when (content) {
                                is String -> html.append(content)
                                is JSONArray -> html.append(jsonArrayToHtml(content))
                                is JSONObject -> html.append(jsonObjectToHtml(content))
                            }
                        }
                        
                        html.append("</$tag>")
                    }
                    
                    // List elements
                    "ol", "ul" -> {
                        html.append("<$tag")
                        appendHtmlAttributes(html, jsonObject)
                        html.append(">")
                        
                        // Process list items
                        if (jsonObject.has("content")) {
                            val content = jsonObject.get("content")
                            if (content is JSONArray) {
                                html.append(jsonArrayToHtml(content))
                            } else if (content is JSONObject) {
                                html.append(jsonObjectToHtml(content))
                            } else if (content is String) {
                                html.append("<li>").append(content).append("</li>")
                            }
                        }
                        
                        html.append("</$tag>")
                    }
                    
                    "li" -> {
                        html.append("<li")
                        appendHtmlAttributes(html, jsonObject)
                        html.append(">")
                        
                        // Process content
                        if (jsonObject.has("content")) {
                            val content = jsonObject.get("content")
                            when (content) {
                                is String -> html.append(content)
                                is JSONArray -> html.append(jsonArrayToHtml(content))
                                is JSONObject -> html.append(jsonObjectToHtml(content))
                            }
                        }
                        
                        html.append("</li>")
                    }
                    
                    // Interactive elements
                    "details" -> {
                        html.append("<details")
                        appendHtmlAttributes(html, jsonObject)
                        html.append(">")
                        
                        // Process content
                        if (jsonObject.has("content")) {
                            val content = jsonObject.get("content")
                            when (content) {
                                is String -> html.append(content)
                                is JSONArray -> html.append(jsonArrayToHtml(content))
                                is JSONObject -> html.append(jsonObjectToHtml(content))
                            }
                        }
                        
                        html.append("</details>")
                    }
                    
                    "summary" -> {
                        html.append("<summary")
                        appendHtmlAttributes(html, jsonObject)
                        html.append(">")
                        
                        // Process content
                        if (jsonObject.has("content")) {
                            val content = jsonObject.get("content")
                            when (content) {
                                is String -> html.append(content)
                                is JSONArray -> html.append(jsonArrayToHtml(content))
                                is JSONObject -> html.append(jsonObjectToHtml(content))
                            }
                        }
                        
                        html.append("</summary>")
                    }
                    
                    // Links and references
                    "a" -> {
                        html.append("<a")
                        
                        // Add href attribute if present
                        if (jsonObject.has("href")) {
                            html.append(" href=\"").append(jsonObject.getString("href")).append("\"")
                        }
                        
                        appendHtmlAttributes(html, jsonObject)
                        html.append(">")
                        
                        // Process content
                        if (jsonObject.has("content")) {
                            val content = jsonObject.get("content")
                            when (content) {
                                is String -> html.append(content)
                                is JSONArray -> html.append(jsonArrayToHtml(content))
                                is JSONObject -> html.append(jsonObjectToHtml(content))
                            }
                        }
                        
                        html.append("</a>")
                    }
                    
                    // Japanese-specific elements
                    "ruby" -> {
                        html.append("<ruby")
                        appendHtmlAttributes(html, jsonObject)
                        html.append(">")
                        
                        // Process content
                        if (jsonObject.has("content")) {
                            val content = jsonObject.get("content")
                            when (content) {
                                is String -> html.append(content)
                                is JSONArray -> {
                                    // Process ruby content specially
                                    for (j in 0 until content.length()) {
                                        try {
                                            val item = content.get(j)
                                            if (item is String) {
                                                html.append(item)
                                            } else if (item is JSONObject && item.has("tag") && item.getString("tag") == "rt") {
                                                // Process ruby text
                                                html.append("<rt>")
                                                if (item.has("content")) {
                                                    val rtContent = item.get("content")
                                                    if (rtContent is String) {
                                                        html.append(rtContent)
                                                    } else if (rtContent is JSONArray) {
                                                        html.append(jsonArrayToHtml(rtContent))
                                                    } else if (rtContent is JSONObject) {
                                                        html.append(jsonObjectToHtml(rtContent))
                                                    }
                                                }
                                                html.append("</rt>")
                                            } else {
                                                html.append(jsonObjectToHtml(item as JSONObject))
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error processing ruby content at index $j: ${e.message}")
                                        }
                                    }
                                }
                                is JSONObject -> html.append(jsonObjectToHtml(content))
                            }
                        }
                        
                        html.append("</ruby>")
                    }
                    
                    "rt" -> {
                        html.append("<rt")
                        appendHtmlAttributes(html, jsonObject)
                        html.append(">")
                        
                        // Process content
                        if (jsonObject.has("content")) {
                            val content = jsonObject.get("content")
                            when (content) {
                                is String -> html.append(content)
                                is JSONArray -> html.append(jsonArrayToHtml(content))
                                is JSONObject -> html.append(jsonObjectToHtml(content))
                            }
                        }
                        
                        html.append("</rt>")
                    }
                    
                    // Images
                    "img" -> {
                        html.append("<img")
                        
                        // Add src attribute if we have a path
                        if (jsonObject.has("path")) {
                            val path = jsonObject.getString("path")
                            html.append(" src=\"").append(path).append("\"")
                        }
                        
                        // Add alt/title attribute if present
                        if (jsonObject.has("title")) {
                            html.append(" alt=\"").append(jsonObject.getString("title")).append("\"")
                            html.append(" title=\"").append(jsonObject.getString("title")).append("\"")
                        }
                        
                        // Add width and height if present
                        if (jsonObject.has("width")) {
                            html.append(" width=\"").append(jsonObject.get("width")).append("\"")
                        }
                        if (jsonObject.has("height")) {
                            html.append(" height=\"").append(jsonObject.get("height")).append("\"")
                        }
                        
                        appendHtmlAttributes(html, jsonObject)
                        html.append(">")
                    }
                    
                    // Fallback for any other HTML tag
                    else -> {
                        // Use div as a fallback for unknown tags
                        html.append("<div")
                        appendHtmlAttributes(html, jsonObject)
                        html.append(">")
                        
                        // Process content
                        if (jsonObject.has("content")) {
                            val content = jsonObject.get("content")
                            when (content) {
                                is String -> html.append(content)
                                is JSONArray -> html.append(jsonArrayToHtml(content))
                                is JSONObject -> html.append(jsonObjectToHtml(content))
                            }
                        }
                        
                        html.append("</div>")
                    }
                }
                
                return html.toString()
            } catch (e: Exception) {
                Log.e(TAG, "Error converting JSON object to HTML: ${e.message}")
                return "Error: ${e.message}"
            }
        }

        /**
         * Extract any text content from a JSON object
         */
        private fun extractTextFromObject(jsonObject: JSONObject): String {
            val sb = StringBuilder()
            
            try {
                // If it has a 'content' field, try to extract text from it
                if (jsonObject.has("content")) {
                    val content = jsonObject.get("content")
                    when (content) {
                        is String -> sb.append(content)
                        is JSONArray -> sb.append(jsonArrayToHtml(content))
                        is JSONObject -> sb.append(jsonObjectToHtml(content))
                    }
                }
                
                // If it has a 'text' field, append that
                if (jsonObject.has("text")) {
                    if (sb.isNotEmpty()) sb.append(" ")
                    sb.append(jsonObject.getString("text"))
                }
                
                // If we have title, use that as well
                if (jsonObject.has("title")) {
                    if (sb.isNotEmpty()) sb.append(" (")
                    sb.append(jsonObject.getString("title"))
                    if (sb.isNotEmpty() && !sb.endsWith(")")) sb.append(")")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting text from object: ${e.message}")
            }
            
            return sb.toString()
        }

        /**
         * Append HTML attributes from the JSON object to the HTML string
         */
        private fun appendHtmlAttributes(html: StringBuilder, jsonObject: JSONObject) {
            try {
                // Add class
                if (jsonObject.has("data") && jsonObject.getJSONObject("data").has("content")) {
                    html.append(" class=\"").append(jsonObject.getJSONObject("data").getString("content")).append("\"")
                }
                
                // Add language if present
                if (jsonObject.has("lang")) {
                    html.append(" lang=\"").append(jsonObject.getString("lang")).append("\"")
                }
                
                // Add title if present
                if (jsonObject.has("title")) {
                    html.append(" title=\"").append(jsonObject.getString("title")).append("\"")
                }
                
                // Add style if present
                if (jsonObject.has("style")) {
                    val styleObj = jsonObject.getJSONObject("style")
                    if (styleObj.length() > 0) {
                        html.append(" style=\"")
                        val keys = styleObj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val value = styleObj.get(key)
                            
                            // Convert camelCase to kebab-case for CSS
                            val cssKey = key.replace(Regex("([a-z])([A-Z])")) {
                                "${it.groupValues[1]}-${it.groupValues[2].lowercase()}"
                            }
                            
                            html.append("$cssKey: $value;")
                        }
                        html.append("\"")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error appending HTML attributes: ${e.message}")
            }
        }

        /**
         * Convert string array definitions to HTML
         */
        fun convertStringArrayToHtml(definitionsArray: JSONArray): String {
            val html = StringBuilder()
            
            try {
                // If there's only one item, don't use a numbered list
                if (definitionsArray.length() == 1) {
                    val def = definitionsArray.optString(0, "")
                    if (def.isNotEmpty()) {
                        html.append("<p>").append(def).append("</p>")
                    }
                } else {
                    // For multiple items, use an ordered list
                    html.append("<ol>")
                    for (i in 0 until definitionsArray.length()) {
                        val def = definitionsArray.optString(i, "")
                        if (def.isNotEmpty()) {
                            html.append("<li>").append(def).append("</li>")
                        }
                    }
                    html.append("</ol>")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error converting string array to HTML: ${e.message}")
                html.append("<p>Error: ${e.message}</p>")
            }
            
            return html.toString()
        }
    }
}