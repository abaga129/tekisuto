package com.abaga129.tekisuto.database

import android.content.Context
import android.util.Log
import java.util.Date

/**
 * Base repository class with common functionality for all repositories
 */
abstract class BaseRepository(context: Context) {
    protected val database: AppDatabase = AppDatabase.createDatabase(context)
    
    protected val TAG = this.javaClass.simpleName

    /**
     * Normalize Japanese characters for better matching
     * - Convert full-width to half-width where appropriate
     * - Normalize variations of similar characters
     */
    protected fun normalizeJapaneseCharacters(input: String): String {
        var result = input

        // Replace full-width alphabetic characters with half-width
        val fullWidthAlphabeticStart = 0xFF21 // Ａ
        val fullWidthAlphabeticEnd = 0xFF5A // ｚ
        val asciiOffset = 0xFF21 - 'A'.code

        val sb = StringBuilder()
        for (c in result) {
            val code = c.code
            if (code in fullWidthAlphabeticStart..fullWidthAlphabeticEnd) {
                sb.append((code - asciiOffset).toChar())
            } else {
                sb.append(c)
            }
        }
        result = sb.toString()

        // Other specific normalizations
        val replacements = mapOf(
            '〜' to '～',
            'ー' to '－',
            '－' to '-',
            '，' to ',',
            '　' to ' '
        )

        for ((fullWidth, halfWidth) in replacements) {
            result = result.replace(fullWidth, halfWidth)
        }

        return result
    }
}
