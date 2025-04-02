package com.abaga129.tekisuto.util

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.ichi2.anki.api.AddContentApi
import java.io.ByteArrayOutputStream

/**
 * Helper class for AnkiDroid integration
 */
class AnkiDroidHelper(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(ANKI_PREFS, Context.MODE_PRIVATE)
    private val api = AddContentApi(context)

    companion object {
        private const val TAG = "AnkiDroidHelper"
        const val ANKI_PREFS = "anki_prefs"
        const val PREF_DECK_ID = "deck_id"
        const val PREF_MODEL_ID = "model_id"
        const val PREF_FIELD_WORD = "field_word"
        const val PREF_FIELD_READING = "field_reading"
        const val PREF_FIELD_DEFINITION = "field_definition"
        const val PREF_FIELD_SCREENSHOT = "field_screenshot"
        const val PREF_FIELD_CONTEXT = "field_context"
        const val PREF_FIELD_PART_OF_SPEECH = "field_part_of_speech"
        const val PREF_FIELD_TRANSLATION = "field_translation"
        const val DEFAULT_FIELD_VALUE = "0"
    }

    /**
     * Check if AnkiDroid is installed and API is available
     */
    fun isAnkiDroidAvailable(): Boolean {
//        return api.isApiAvailable(context)
        return AddContentApi.getAnkiDroidPackageName(context) != null
    }

    /**
     * Get available decks from AnkiDroid
     * @return Map of deck IDs to deck names
     */
    fun getAvailableDecks(): Map<Long, String> {
        return try {
            api.deckList
        } catch (e: Exception) {
            Log.e(TAG, "Error getting deck list", e)
            emptyMap()
        }
    }

    /**
     * Get available models (note types) from AnkiDroid
     * @return Map of model IDs to model names
     */
    fun getAvailableModels(): Map<Long, String> {
        return try {
            api.modelList
        } catch (e: Exception) {
            Log.e(TAG, "Error getting model list", e)
            emptyMap()
        }
    }

    /**
     * Get fields for a given model
     * @param modelId The ID of the model
     * @return Array of field names
     */
    fun getFieldsForModel(modelId: Long): Array<String> {
        return try {
            api.getFieldList(modelId)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting fields for model", e)
            emptyArray()
        }
    }

    /**
     * Save AnkiDroid configuration
     */
    fun saveConfiguration(
        deckId: Long,
        modelId: Long,
        fieldWord: Int,
        fieldReading: Int,
        fieldDefinition: Int,
        fieldScreenshot: Int,
        fieldContext: Int,
        fieldPartOfSpeech: Int,
        fieldTranslation: Int
    ) {
        prefs.edit().apply {
            putLong(PREF_DECK_ID, deckId)
            putLong(PREF_MODEL_ID, modelId)
            putInt(PREF_FIELD_WORD, fieldWord)
            putInt(PREF_FIELD_READING, fieldReading)
            putInt(PREF_FIELD_DEFINITION, fieldDefinition)
            putInt(PREF_FIELD_SCREENSHOT, fieldScreenshot)
            putInt(PREF_FIELD_CONTEXT, fieldContext)
            putInt(PREF_FIELD_PART_OF_SPEECH, fieldPartOfSpeech)
            putInt(PREF_FIELD_TRANSLATION, fieldTranslation)
            apply()
        }
    }

    /**
     * Get saved deck ID
     */
    fun getSavedDeckId(): Long {
        return prefs.getLong(PREF_DECK_ID, 0)
    }

    /**
     * Get saved model ID
     */
    fun getSavedModelId(): Long {
        return prefs.getLong(PREF_MODEL_ID, 0)
    }

    /**
     * Get saved field mappings
     */
    fun getSavedFieldMappings(): FieldMappings {
        return FieldMappings(
            word = prefs.getInt(PREF_FIELD_WORD, 0),
            reading = prefs.getInt(PREF_FIELD_READING, 0),
            definition = prefs.getInt(PREF_FIELD_DEFINITION, 0),
            screenshot = prefs.getInt(PREF_FIELD_SCREENSHOT, 0),
            context = prefs.getInt(PREF_FIELD_CONTEXT, 0),
            partOfSpeech = prefs.getInt(PREF_FIELD_PART_OF_SPEECH, 0),
            translation = prefs.getInt(PREF_FIELD_TRANSLATION, 0)
        )
    }

    /**
     * Add note to AnkiDroid
     */
    fun addNoteToAnkiDroid(
        word: String,
        reading: String,
        definition: String,
        partOfSpeech: String,
        context: String,
        screenshotPath: String?,
        translation: String = ""
    ): Boolean {
        if (!isAnkiDroidAvailable()) {
            Log.e(TAG, "AnkiDroid not available")
            return false
        }

        val deckId = getSavedDeckId()
        val modelId = getSavedModelId()
        val fieldMappings = getSavedFieldMappings()

        if (deckId == 0L || modelId == 0L) {
            Log.e(TAG, "AnkiDroid configuration not set")
            return false
        }

        try {
            // Try using the API first
            return addNoteWithApi(
                word, reading, definition, partOfSpeech, context, screenshotPath, translation,
                deckId, modelId, fieldMappings
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error using AnkiDroid API, falling back to Intent method", e)
            // Fallback to using Intent if API fails
            return addNoteWithIntent(
                word, reading, definition, partOfSpeech, context, translation,
                getDeckName(deckId), getModelName(modelId), fieldMappings
            )
        }
    }
    
    /**
     * Add note using the AnkiDroid API
     */
    private fun addNoteWithApi(
        word: String,
        reading: String,
        definition: String,
        partOfSpeech: String,
        context: String,
        screenshotPath: String?,
        translation: String,
        deckId: Long,
        modelId: Long,
        fieldMappings: FieldMappings
    ): Boolean {
        val fields = getFieldsForModel(modelId)
        if (fields.isEmpty()) {
            Log.e(TAG, "No fields found for model")
            return false
        }

        // Create a map to hold the field values
        val fieldValues = HashMap<String, String>()
        
        // Map fields only when the mapping is valid
        try {
            // Word field (required)
            if (fieldMappings.word >= 0 && fieldMappings.word < fields.size) {
                fieldValues[fields[fieldMappings.word]] = word
            } else {
                // Word field is required, if not available, use the first field
                fieldValues[fields[0]] = word
            }
            
            // Reading field (optional)
            if (fieldMappings.reading >= 0 && fieldMappings.reading < fields.size && reading.isNotBlank()) {
                fieldValues[fields[fieldMappings.reading]] = reading
            }
            
            // Definition field (required)
            if (fieldMappings.definition >= 0 && fieldMappings.definition < fields.size) {
                fieldValues[fields[fieldMappings.definition]] = definition
            } else if (fields.size > 1) {
                // Definition field is required, if not available, use the second field
                fieldValues[fields[1]] = definition
            }
            
            // Part of speech field (optional)
            if (fieldMappings.partOfSpeech >= 0 && fieldMappings.partOfSpeech < fields.size && partOfSpeech.isNotBlank()) {
                fieldValues[fields[fieldMappings.partOfSpeech]] = partOfSpeech
            }
            
            // Context field (optional)
            if (fieldMappings.context >= 0 && fieldMappings.context < fields.size && context.isNotBlank()) {
                fieldValues[fields[fieldMappings.context]] = context
            }
            
            // Translation field (optional)
            if (fieldMappings.translation >= 0 && fieldMappings.translation < fields.size && translation.isNotBlank()) {
                fieldValues[fields[fieldMappings.translation]] = translation
            }
            
            // Screenshot field (optional)
            if (fieldMappings.screenshot >= 0 && fieldMappings.screenshot < fields.size && screenshotPath != null) {
                val screenshot = loadAndEncodeScreenshot(screenshotPath)
                if (screenshot.isNotBlank()) {
                    fieldValues[fields[fieldMappings.screenshot]] = screenshot
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error mapping fields", e)
            return false
        }

        // Create the note
        return try {
            // Convert field values map to array of values in the correct order
            val fieldsArray = Array(fields.size) { i ->
                fieldValues[fields[i]] ?: ""
            }
            
            // Call the API with the correct parameter types
            val noteId = api.addNote(modelId, deckId, fieldsArray, setOf<String>())
            noteId > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error adding note to AnkiDroid", e)
            throw e
        }
    }
    
    /**
     * Add note using the AnkiDroid Intent API (fallback method)
     */
    private fun addNoteWithIntent(
        word: String,
        reading: String,
        definition: String,
        partOfSpeech: String,
        context: String,
        translation: String,
        deckName: String,
        modelName: String,
        fieldMappings: FieldMappings
    ): Boolean {
        try {
            val intent = Intent("com.ichi2.anki.ACTION_ADD_NOTE")
            intent.putExtra("modelName", modelName)
            intent.putExtra("deckName", deckName)
            
            // Get the maximum field index to determine array size (0-based, so add 1)
            val maxFieldIndex = maxOf(
                fieldMappings.word.takeIf { it >= 0 } ?: 0,
                fieldMappings.reading.takeIf { it >= 0 } ?: 0,
                fieldMappings.definition.takeIf { it >= 0 } ?: 0,
                fieldMappings.partOfSpeech.takeIf { it >= 0 } ?: 0,
                fieldMappings.context.takeIf { it >= 0 } ?: 0,
                fieldMappings.translation.takeIf { it >= 0 } ?: 0
            ) + 1
            
            // Prepare field values - ensure array is large enough
            val fields = Array(maxOf(7, maxFieldIndex)) { "" }
            
            // Add field values only for valid indices
            if (fieldMappings.word >= 0) {
                fields[fieldMappings.word] = word
            } else {
                // Word field is required - use first field
                fields[0] = word
            }
            
            if (fieldMappings.reading >= 0 && reading.isNotBlank()) {
                fields[fieldMappings.reading] = reading
            }
            
            if (fieldMappings.definition >= 0) {
                fields[fieldMappings.definition] = definition
            } else if (fields.size > 1) {
                // Definition field is required - use second field
                fields[1] = definition
            }
            
            if (fieldMappings.partOfSpeech >= 0 && partOfSpeech.isNotBlank()) {
                fields[fieldMappings.partOfSpeech] = partOfSpeech
            }
            
            if (fieldMappings.context >= 0 && context.isNotBlank()) {
                fields[fieldMappings.context] = context
            }
            
            if (fieldMappings.translation >= 0 && translation.isNotBlank()) {
                fields[fieldMappings.translation] = translation
            }
            
            intent.putExtra("fields", fields)
            intent.putExtra("tags", arrayOf("tekisuto"))
            
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            this.context.startActivity(intent)
            
            // We can't know for sure if the note was added when using Intent
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error adding note with intent", e)
            return false
        }
    }
    
    /**
     * Get deck name from ID
     */
    private fun getDeckName(deckId: Long): String {
        return try {
            val decks = api.deckList
            decks[deckId] ?: "Default"
        } catch (e: Exception) {
            "Default"
        }
    }
    
    /**
     * Get model name from ID
     */
    private fun getModelName(modelId: Long): String {
        return try {
            val models = api.modelList
            models[modelId] ?: "Basic"
        } catch (e: Exception) {
            "Basic"
        }
    }

    /**
     * Load and encode screenshot as HTML img tag with base64 data
     */
    private fun loadAndEncodeScreenshot(screenshotPath: String): String {
        return try {
            val bitmap = BitmapFactory.decodeFile(screenshotPath)
            if (bitmap != null) {
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
                val byteArray = outputStream.toByteArray()
                val encoded = Base64.encodeToString(byteArray, Base64.DEFAULT)
                "<img src=\"data:image/jpeg;base64,$encoded\" />"
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding screenshot", e)
            ""
        }
    }

    /**
     * Launch AnkiDroid app
     */
    fun launchAnkiDroid() {
        val intent = context.packageManager.getLaunchIntentForPackage("com.ichi2.anki")
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}

/**
 * Field mappings for AnkiDroid note
 */
data class FieldMappings(
    val word: Int = 0,
    val reading: Int = 0,
    val definition: Int = 0, 
    val screenshot: Int = 0,
    val context: Int = 0,
    val partOfSpeech: Int = 0,
    val translation: Int = 0
)