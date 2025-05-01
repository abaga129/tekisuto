package com.abaga129.tekisuto.util

import android.util.Log

/**
 * Utility class to convert Yomitan structured content to HTML
 */
object StructuredContentHtmlConverter {
    private const val TAG = "StructContentConverter"
    
    /**
     * Converts structured content from Yomitan dictionaries to HTML
     * 
     * Structured content is an array of objects that define formatting, text, etc.
     * Each object has a type and content field, or it can be in a more complex nested structure
     * 
     * @param content The structured content array or object
     * @return HTML string representation of the structured content
     */
    fun convertToHtml(content: Any?): String {
        val sb = StringBuilder()
        
        try {
            when (content) {
                is List<*> -> processContent(content, sb)
                is Map<*, *> -> processStructuredItem(content, sb)
                is String -> sb.append(escapeHtml(content))
                else -> sb.append(content.toString())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error converting structured content to HTML", e)
            // Return original content as string if conversion fails
            return content.toString()
        }
        
        return sb.toString()
    }
    
    /**
     * Process the content list recursively
     */
    private fun processContent(content: List<*>, sb: StringBuilder) {
        for (item in content) {
            when (item) {
                is String -> {
                    // Plain text
                    sb.append(escapeHtml(item))
                }
                is Map<*, *> -> {
                    // Structured content object
                    processStructuredItem(item, sb)
                }
                is List<*> -> {
                    // Nested list
                    processContent(item, sb)
                }
                else -> {
                    // Try to get string representation
                    val text = item?.toString() ?: ""
                    if (text.isNotEmpty() && text != "null") {
                        sb.append(escapeHtml(text))
                    }
                }
            }
        }
    }
    
    /**
     * Process a structured content item
     */
    private fun processStructuredItem(item: Map<*, *>, sb: StringBuilder) {
        // Check if this item has a tag (newer format) or type (older format)
        val type = item["type"] as? String
        val tag = item["tag"] as? String
        
        if (tag != null) {
            // New format - tag-based structure
            processTagBasedItem(item, sb)
            return
        }
        
        if (type == null) {
            // If no type or tag, try to find content
            val content = item["content"]
            when (content) {
                is List<*> -> processContent(content, sb)
                is Map<*, *> -> processStructuredItem(content, sb)
                is String -> sb.append(escapeHtml(content))
                else -> {
                    // Try to get text value if present
                    val text = item["text"] as? String
                    if (text != null) {
                        sb.append(escapeHtml(text))
                    } else {
                        // Last resort: use toString()
                        sb.append(item.toString())
                    }
                }
            }
            return
        }
        
        // Standard type-based structure
        when (type) {
            "text" -> {
                val value = item["text"] as? String ?: return
                sb.append(escapeHtml(value))
            }
            "kanji" -> {
                val value = item["text"] as? String ?: return
                sb.append("<span class=\"kanji\">").append(escapeHtml(value)).append("</span>")
            }
            "kana" -> {
                val value = item["text"] as? String ?: return
                sb.append("<span class=\"kana\">").append(escapeHtml(value)).append("</span>")
            }
            "italic" -> {
                val childContent = item["content"] as? List<*> ?: return
                sb.append("<i>")
                processContent(childContent, sb)
                sb.append("</i>")
            }
            "bold" -> {
                val childContent = item["content"] as? List<*> ?: return
                sb.append("<b>")
                processContent(childContent, sb)
                sb.append("</b>")
            }
            "heading" -> {
                val childContent = item["content"] as? List<*> ?: return
                sb.append("<h3>")
                processContent(childContent, sb)
                sb.append("</h3>")
            }
            "link" -> {
                val url = item["url"] as? String ?: return
                val childContent = item["content"] as? List<*> ?: return
                sb.append("<a href=\"").append(url).append("\">")
                processContent(childContent, sb)
                sb.append("</a>")
            }
            "ruby" -> {
                val childContent = item["content"] as? List<*> ?: return
                val ruby = item["ruby"] as? String ?: return
                sb.append("<ruby>")
                processContent(childContent, sb)
                sb.append("<rt>").append(escapeHtml(ruby)).append("</rt></ruby>")
            }
            "image" -> {
                val url = item["url"] as? String ?: return
                sb.append("<img src=\"").append(url).append("\" alt=\"Image\" />")
            }
            "br" -> {
                sb.append("<br/>")
            }
            "hr" -> {
                sb.append("<hr/>")
            }
            "div", "span" -> {
                val childContent = item["content"] as? List<*> ?: return
                val classes = item["class"] as? String
                
                sb.append("<").append(type)
                if (!classes.isNullOrEmpty()) {
                    sb.append(" class=\"").append(classes).append("\"")
                }
                sb.append(">")
                
                processContent(childContent, sb)
                
                sb.append("</").append(type).append(">")
            }
            "structured-content" -> {
                // This is the root of structured content
                val content = item["content"]
                when (content) {
                    is Map<*, *> -> processTagBasedItem(content, sb)
                    is List<*> -> processContent(content, sb)
                    is String -> sb.append(escapeHtml(content))
                    else -> {
                        val text = content?.toString() ?: ""
                        if (text.isNotEmpty() && text != "null") {
                            sb.append(escapeHtml(text))
                        }
                    }
                }
            }
            else -> {
                // Default handling for unknown types
                val childContent = item["content"]
                when (childContent) {
                    is List<*> -> processContent(childContent, sb)
                    is Map<*, *> -> processStructuredItem(childContent, sb)
                    is String -> sb.append(escapeHtml(childContent))
                    else -> {
                        // Try to get text value if present
                        val text = item["text"] as? String
                        if (text != null) {
                            sb.append(escapeHtml(text))
                        } else {
                            // Last resort: use toString()
                            val itemText = item.toString()
                            if (itemText != "{}" && itemText != "null") {
                                sb.append(itemText)
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Process a tag-based structured content item (newer format)
     */
    private fun processTagBasedItem(item: Map<*, *>, sb: StringBuilder) {
        val tag = item["tag"] as? String ?: return
        
        // Handle attributes
        val styleMap = item["style"] as? Map<*, *>
        val dataMap = item["data"] as? Map<*, *>
        val lang = item["lang"] as? String
        val href = item["href"] as? String
        
        // Begin tag with attributes
        sb.append("<").append(tag)
        
        // Add style attributes if present
        if (styleMap != null && styleMap.isNotEmpty()) {
            sb.append(" style=\"")
            styleMap.entries.forEachIndexed { index, entry ->
                if (index > 0) sb.append("; ")
                sb.append(entry.key).append(": ").append(entry.value)
            }
            sb.append("\"")
        }
        
        // Add data attributes if present
        if (dataMap != null && dataMap.isNotEmpty()) {
            dataMap.entries.forEach { entry ->
                sb.append(" data-").append(entry.key).append("=\"").append(entry.value).append("\"")
            }
        }
        
        // Add language attribute if present
        if (!lang.isNullOrEmpty()) {
            sb.append(" lang=\"").append(lang).append("\"")
        }
        
        // Add href for anchor tags
        if (tag == "a" && !href.isNullOrEmpty()) {
            sb.append(" href=\"").append(href).append("\"")
        }
        
        // Process special attributes for specific tags
        when (tag) {
            "img" -> {
                val src = item["src"] as? String ?: item["path"] as? String
                val alt = item["alt"] as? String ?: item["title"] as? String ?: "Image"
                val width = item["width"]
                val height = item["height"]
                
                if (src != null) {
                    sb.append(" src=\"").append(src).append("\"")
                }
                sb.append(" alt=\"").append(alt).append("\"")
                
                if (width != null) {
                    sb.append(" width=\"").append(width).append("\"")
                }
                if (height != null) {
                    sb.append(" height=\"").append(height).append("\"")
                }
            }
        }
        
        // Close opening tag
        sb.append(">")
        
        // Process content
        val content = item["content"]
        when (content) {
            is List<*> -> processContent(content, sb)
            is Map<*, *> -> processStructuredItem(content, sb)
            is String -> sb.append(escapeHtml(content))
            else -> {
                // If content is null or unrecognized, check for text property
                val text = item["text"] as? String
                if (text != null) {
                    sb.append(escapeHtml(text))
                }
            }
        }
        
        // Handle void elements that don't need closing tags
        val voidElements = setOf("img", "br", "hr", "input", "meta", "link")
        if (!voidElements.contains(tag)) {
            sb.append("</").append(tag).append(">")
        }
    }
    
    /**
     * Escape HTML special characters
     */
    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}