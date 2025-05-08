package com.abaga129.tekisuto.database

import android.content.Context
import android.util.Log

/**
 * Repository for exported word operations
 */
class ExportedWordRepository(context: Context) : BaseRepository(context) {

    companion object {
        @Volatile
        private var INSTANCE: ExportedWordRepository? = null

        fun getInstance(context: Context): ExportedWordRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = ExportedWordRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    private val exportedWordsDao = database.exportedWordsDao()

    /**
     * Adds a word to the exported words table
     */
    suspend fun addExportedWord(word: String, dictionaryId: Long, ankiDeckId: Long, ankiNoteId: Long? = null) {
        try {
            val exportedWord = ExportedWordEntity(
                word = word.trim().lowercase(),
                dictionaryId = dictionaryId,
                ankiDeckId = ankiDeckId,
                ankiNoteId = ankiNoteId
            )
            exportedWordsDao.insert(exportedWord)
            Log.d(TAG, "Added exported word: $word")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding exported word", e)
            throw e
        }
    }

    /**
     * Checks if a word has been exported to AnkiDroid
     */
    suspend fun isWordExported(word: String): Boolean {
        return try {
            exportedWordsDao.isWordExported(word.trim().lowercase())
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if word is exported", e)
            false
        }
    }

    /**
     * Gets all exported words
     */
    suspend fun getAllExportedWords(): List<ExportedWordEntity> {
        return try {
            exportedWordsDao.getAllExportedWords()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all exported words", e)
            emptyList()
        }
    }

    /**
     * Imports a list of words as exported words (e.g., from Anki)
     *
     * @param words List of words to import
     * @param dictionaryId Dictionary ID (use 0 for "any dictionary")
     * @param ankiDeckId Anki deck ID (use -1 for words imported from .apkg)
     * @return Number of words imported
     */
    suspend fun importExportedWords(words: List<String>, dictionaryId: Long, ankiDeckId: Long): Int {
        try {
            if (words.isEmpty()) {
                Log.w(TAG, "importExportedWords called with empty list")
                return 0
            }

            // Create ExportedWordEntity objects for each word
            val exportedWords = words.map { word ->
                ExportedWordEntity(
                    word = word.trim().lowercase(),
                    dictionaryId = dictionaryId,
                    ankiDeckId = ankiDeckId
                )
            }

            // Insert into database
            val result = exportedWordsDao.insertAll(exportedWords)

            Log.d(TAG, "Successfully imported ${exportedWords.size} exported words")
            return exportedWords.size
        } catch (e: Exception) {
            Log.e(TAG, "Error importing exported words: ${e.message}", e)
            throw e
        }
    }
}
