package com.abaga129.tekisuto.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.abaga129.tekisuto.database.DictionaryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import java.util.zip.ZipEntry
import android.database.sqlite.SQLiteDatabase

/**
 * Utility for importing words from Anki .apkg files
 * 
 * .apkg files are zip files containing a SQLite database (collection.anki2) and media files
 */
class AnkiPackageImporter(
    private val context: Context,
    private val dictionaryRepository: DictionaryRepository = DictionaryRepository(context)
) {

    companion object {
        private const val TAG = "AnkiPackageImporter"
        private const val ANKI_DB_NAME_LEGACY = "collection.anki2"
        private const val ANKI_DB_NAME_NEW = "collection.anki21"
        private const val TEMP_EXTRACT_DIR = "anki_extract"
    }
    
    /**
     * Import words from an Anki package (.apkg) file
     * 
     * @param uri URI of the .apkg file to import
     * @return Pair<List<String>, List<String>> - first is field names, second is deck names
     */
    suspend fun extractFieldsAndDecks(uri: Uri): Pair<List<String>, List<String>> = withContext(Dispatchers.IO) {
        // Create a temporary directory for extraction
        val extractDir = File(context.cacheDir, TEMP_EXTRACT_DIR)
        if (extractDir.exists()) {
            extractDir.deleteRecursively()
        }
        extractDir.mkdirs()
        
        // Extract the .apkg file
        val dbFile = extractApkgFile(uri, extractDir)
        if (dbFile == null || !dbFile.exists()) {
            Log.e(TAG, "Failed to extract Anki database file")
            return@withContext Pair(emptyList(), emptyList())
        }
        
        try {
            // Open the SQLite database
            val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            
            // Get available fields
            val fields = getAvailableFields(db)
            
            // Get available decks
            val decks = getAvailableDecks(db)
            
            // Close the database
            db.close()
            
            Pair(fields, decks)
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting fields from Anki package", e)
            Pair(emptyList(), emptyList())
        }
    }
    
    /**
     * Import words from an Anki package (.apkg) file
     * 
     * @param uri URI of the .apkg file to import
     * @param fieldIndex The index of the field to import
     * @param deckName Optional deck name to filter by (null for all decks)
     * @return Number of words imported
     */
    suspend fun importWords(
        uri: Uri, 
        fieldIndex: Int,
        deckName: String? = null
    ): Int = withContext(Dispatchers.IO) {
        // Create a temporary directory for extraction
        val extractDir = File(context.cacheDir, TEMP_EXTRACT_DIR)
        if (extractDir.exists()) {
            extractDir.deleteRecursively()
        }
        extractDir.mkdirs()
        
        // Extract the .apkg file
        val dbFile = extractApkgFile(uri, extractDir)
        if (dbFile == null || !dbFile.exists()) {
            Log.e(TAG, "Failed to extract Anki database file")
            return@withContext 0
        }
        
        try {
            // Open the SQLite database
            val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            
            // First check if this is a compatible Anki database
            val compatibilityCheck = db.rawQuery("SELECT models FROM col", null)
            if (compatibilityCheck.moveToFirst()) {
                val modelsJson = compatibilityCheck.getString(0)
                if (modelsJson.contains("please update to the latest anki version")) {
                    Log.e(TAG, "Incompatible Anki database detected. Contains update message.")
                    compatibilityCheck.close()
                    db.close()
                    extractDir.deleteRecursively()
                    throw IllegalStateException(
                        "This Anki package requires a newer version of Anki to open.\n\n" +
                        "Please re-export your deck using Anki 2.1.x with the legacy export option enabled."
                    )
                }
            }
            compatibilityCheck.close()
            
            // Get the words from the field
            val words = getWordsFromField(db, fieldIndex, deckName)
            Log.d(TAG, "Extracted ${words.size} words from field $fieldIndex")
            
            // Close the database
            db.close()
            
            if (words.isEmpty()) {
                Log.w(TAG, "No words extracted. Field index may be invalid or deck empty.")
                extractDir.deleteRecursively()
                throw IllegalStateException(
                    "No words found in the selected field. The field might be empty or the " +
                    "package format may be incompatible.\n\nTry a different field or deck."
                )
            }
            
            // Import the words into the app database
            val importCount = dictionaryRepository.importExportedWords(
                words = words,
                dictionaryId = 0, // 0 means "any dictionary"
                ankiDeckId = -1   // -1 means "imported from .apkg"
            )
            
            // Clean up the temporary directory
            extractDir.deleteRecursively()
            
            importCount
        } catch (e: Exception) {
            Log.e(TAG, "Error importing words from Anki package: ${e.message}", e)
            // Clean up the temporary directory
            extractDir.deleteRecursively()
            throw e // Rethrow so the activity can show a proper error message
        }
    }
    
    /**
     * Extract the .apkg file to a temporary directory
     * 
     * @param uri URI of the .apkg file
     * @param extractDir Directory to extract to
     * @return File object for the extracted collection.anki2 or collection.anki21 file, or null if extraction failed
     */
    private fun extractApkgFile(uri: Uri, extractDir: File): File? {
        try {
            // Open the .apkg file as a ZIP
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            
            // Check if this looks like a ZIP file (APKGs are ZIP files)
            val headerBytes = ByteArray(4)
            val bytesRead = inputStream.read(headerBytes)
            
            if (bytesRead < 4 || 
                headerBytes[0] != 0x50.toByte() || // 'P'
                headerBytes[1] != 0x4B.toByte() || // 'K'
                headerBytes[2] != 0x03.toByte() || // '\003'
                headerBytes[3] != 0x04.toByte()) { // '\004'
                
                Log.e(TAG, "File does not appear to be a valid ZIP file (missing PK header)")
                inputStream.close()
                throw IllegalStateException("The selected file is not a valid Anki package (.apkg).")
            }
            
            // Reset the stream and open as a ZIP
            inputStream.close()
            val newInputStream = context.contentResolver.openInputStream(uri) ?: return null
            val zipInputStream = ZipInputStream(newInputStream)
            
            var dbFile: File? = null
            var foundAnkiDb = false
            var entry: ZipEntry? = zipInputStream.nextEntry
            
            // First, try to find collection.anki21 (newer format)
            while (entry != null) {
                // Get the entry name
                val entryName = entry.name
                
                if (entryName == ANKI_DB_NAME_NEW) {
                    foundAnkiDb = true
                    // Extract the Anki database file
                    val outFile = File(extractDir, ANKI_DB_NAME_NEW)
                    val outStream = FileOutputStream(outFile)
                    
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    while (zipInputStream.read(buffer).also { bytesRead = it } != -1) {
                        outStream.write(buffer, 0, bytesRead)
                    }
                    
                    outStream.close()
                    dbFile = outFile
                    Log.d(TAG, "Extracted Anki 2.1 database to ${outFile.absolutePath}")
                    break // Found the newer format, no need to continue
                }
                
                zipInputStream.closeEntry()
                entry = zipInputStream.nextEntry
            }
            
            // If we didn't find collection.anki21, close and reopen to look for collection.anki2
            if (!foundAnkiDb) {
                zipInputStream.close()
                newInputStream.close()
                
                // Reopen the zip file
                val legacyInputStream = context.contentResolver.openInputStream(uri) ?: return null
                val legacyZipStream = ZipInputStream(legacyInputStream)
                
                entry = legacyZipStream.nextEntry
                while (entry != null) {
                    val entryName = entry.name
                    
                    if (entryName == ANKI_DB_NAME_LEGACY) {
                        foundAnkiDb = true
                        // Extract the Anki database file
                        val outFile = File(extractDir, ANKI_DB_NAME_LEGACY)
                        val outStream = FileOutputStream(outFile)
                        
                        val buffer = ByteArray(4096)
                        var bytesRead: Int
                        while (legacyZipStream.read(buffer).also { bytesRead = it } != -1) {
                            outStream.write(buffer, 0, bytesRead)
                        }
                        
                        outStream.close()
                        dbFile = outFile
                        Log.d(TAG, "Extracted Anki 2.0 database to ${outFile.absolutePath}")
                    }
                    
                    legacyZipStream.closeEntry()
                    entry = legacyZipStream.nextEntry
                }
                
                legacyZipStream.close()
                legacyInputStream.close()
            }
            
            // If we didn't find either database file, this isn't a valid .apkg file
            if (!foundAnkiDb) {
                Log.e(TAG, "No Anki database file found in the ZIP archive")
                throw IllegalStateException(
                    "The selected file doesn't appear to be a valid Anki package. " +
                    "It doesn't contain a collection.anki21 or collection.anki2 database file."
                )
            }
            
            return dbFile
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting APKG file", e)
            
            // Rethrow with more specific message if it appears to be a file format issue
            if (e is IllegalStateException) {
                throw e
            } else if (e.message?.contains("zip") == true || 
                       e.message?.contains("CRC") == true ||
                       e.message?.contains("corrupted") == true) {
                throw IllegalStateException(
                    "The file appears to be corrupted or not a valid Anki package. " +
                    "Please try exporting it again from Anki."
                )
            }
            
            return null
        }
    }
    
    /**
     * Get available fields from the Anki database
     * 
     * @param db SQLiteDatabase instance
     * @return List of field names
     */
    private fun getAvailableFields(db: SQLiteDatabase): List<String> {
        val fields = mutableListOf<String>()
        
        // First, try direct parsing of the models JSON from col table
        // This approach works best for Anki 2.1 packages
        val directFields = parseAnki21ModelsJson(db)
        if (directFields.isNotEmpty()) {
            return directFields
        }
        
        try {
            // First, check for the Anki 2.1 collection schema
            var anki21Format = false
            try {
                val schemaQuery = db.rawQuery("SELECT ver FROM col", null)
                if (schemaQuery.moveToFirst()) {
                    val schemaVersion = schemaQuery.getInt(0)
                    Log.d(TAG, "Schema version: $schemaVersion")
                    anki21Format = schemaVersion >= 11
                }
                schemaQuery.close()
            } catch (e: Exception) {
                Log.w(TAG, "Could not determine schema version", e)
            }
            
            // Try direct approach using notes table first - this is the most reliable
            try {
                // Get a sample note to see its fields
                val notesQuery = db.rawQuery("SELECT flds FROM notes LIMIT 1", null)
                if (notesQuery.moveToFirst()) {
                    val fieldsString = notesQuery.getString(0)
                    Log.d(TAG, "Sample note fields string: $fieldsString")
                    
                    // Get the actual field names from models/notetypes
                    val modelNames = mutableListOf<String>()
                    
                    // First try with 'mid' field in notes to get the model
                    try {
                        val midCursor = db.rawQuery("SELECT DISTINCT mid FROM notes LIMIT 5", null)
                        val modelIds = mutableListOf<Long>()
                        while (midCursor.moveToNext()) {
                            modelIds.add(midCursor.getLong(0))
                        }
                        midCursor.close()
                        
                        Log.d(TAG, "Found ${modelIds.size} model IDs in notes table")
                        
                        // For each model ID, try to get field names
                        for (mid in modelIds) {
                            try {
                                // Try with notetypes table (Anki 2.1.20+)
                                val noteTypeQuery = db.rawQuery("SELECT flds FROM notetypes WHERE id = ?", arrayOf(mid.toString()))
                                if (noteTypeQuery.moveToFirst()) {
                                    val flds = noteTypeQuery.getString(0)
                                    val fieldsSplit = flds.split("\u001F")
                                    modelNames.addAll(fieldsSplit)
                                    Log.d(TAG, "From notetypes: Found ${fieldsSplit.size} fields for model $mid")
                                }
                                noteTypeQuery.close()
                            } catch (e: Exception) {
                                Log.w(TAG, "Error querying notetypes for model $mid", e)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error getting model IDs from notes", e)
                    }
                    
                    // If we found field names from models, use those
                    if (modelNames.isNotEmpty()) {
                        fields.addAll(modelNames.filter { it.isNotEmpty() }.distinct())
                        Log.d(TAG, "Found ${fields.size} fields from model definition")
                    } else {
                        // Fallback: Create generic field names based on count of fields in notes
                        val fieldsSeparator = fieldsString.count { it == '\u001F' }
                        val fieldCount = fieldsSeparator + 1
                        Log.d(TAG, "Creating generic field names for $fieldCount fields")
                        
                        for (i in 0 until fieldCount) {
                            fields.add("Field ${i+1}")
                        }
                    }
                    
                    // If we found fields this way, return them
                    if (fields.isNotEmpty()) {
                        Log.d(TAG, "Successfully found ${fields.size} fields using notes: $fields")
                        return fields
                    }
                }
                notesQuery.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error getting fields from notes", e)
                // Continue with other methods
            }
            
            // Try direct table approach for newer Anki versions (2.1+)
            if (anki21Format) {
                try {
                    // Check if we have a dedicated notetypes table (Anki 2.1.20+)
                    val tableCheck = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='notetypes'", null)
                    val hasNoteTypesTable = tableCheck.count > 0
                    tableCheck.close()
                    
                    if (hasNoteTypesTable) {
                        // Use the notetypes table which has a dedicated field for field names
                        val noteTypesQuery = db.rawQuery("SELECT name, flds FROM notetypes", null)
                        while (noteTypesQuery.moveToNext()) {
                            val modelName = noteTypesQuery.getString(0)
                            val modelFields = noteTypesQuery.getString(1)
                            Log.d(TAG, "Model: $modelName, Fields: $modelFields")
                            
                            // Fields are stored as JSON array in newer Anki versions
                            val fieldNames = modelFields.split("\u001F")
                            for (fieldName in fieldNames) {
                                if (fieldName.isNotEmpty() && !fields.contains(fieldName)) {
                                    Log.d(TAG, "Found field name (notetypes table): $fieldName")
                                    fields.add(fieldName)
                                }
                            }
                        }
                        noteTypesQuery.close()
                        
                        // If we found fields this way, return them
                        if (fields.isNotEmpty()) {
                            Log.d(TAG, "Successfully found ${fields.size} fields using notetypes table")
                            return fields
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error querying notetypes table", e)
                    // Continue with fallback method
                }
            }
            
            // Query the models table (legacy approach for Anki 2.0 or when notetypes table fails)
            val cursor = db.rawQuery("SELECT models FROM col", null)
            
            if (cursor.moveToFirst()) {
                // The models column contains a JSON string of all note types
                val modelsJson = cursor.getString(0)
                
                // Check for incompatibility message first
                if (modelsJson.contains("please update to the latest anki version")) {
                    Log.w(TAG, "Found Anki version update message in database")
                    cursor.close()
                    throw IllegalStateException(
                        "This Anki package requires a newer version of Anki to open.\n\n" +
                        "Please re-export your deck using Anki 2.1.x with the legacy export option enabled."
                    )
                }
                
                // Log the JSON for debugging (limited length to avoid huge logs)
                val jsonPreview = if (modelsJson.length > 300) modelsJson.take(300) + "..." else modelsJson
                Log.d(TAG, "Models JSON: $jsonPreview")
                
                // Advanced approach trying to find the model IDs used in cards
                try {
                    // First get used model IDs from notes
                    val midsQuery = db.rawQuery("SELECT DISTINCT mid FROM notes", null)
                    val mids = mutableListOf<Long>()
                    while (midsQuery.moveToNext()) {
                        mids.add(midsQuery.getLong(0))
                    }
                    midsQuery.close()
                    
                    Log.d(TAG, "Found ${mids.size} model IDs in use: $mids")
                    
                    // Try another direct database approach first with notes (v1)
                    val noteFields = extractFieldsFromNotes(db)
                    if (noteFields.isNotEmpty()) {
                        fields.addAll(noteFields)
                        Log.d(TAG, "Found ${noteFields.size} fields from note samples: $noteFields")
                        return fields
                    }

                    // Now for each model ID, try to find it in the models JSON and extract fields
                    for (mid in mids) {
                        val midStr = mid.toString()
                        // Extract the model section for this mid
                        val modelRegex = """"$midStr"\s*:\s*\{[^}]*"flds"\s*:\s*\[([^\]]*)\\]""".toRegex()
                        val modelMatch = modelRegex.find(modelsJson)
                        
                        if (modelMatch != null) {
                            val fldsSection = modelMatch.groupValues[1]
                            // Extract field names from the flds section
                            val fieldNameRegex = """"name"\s*:\s*"([^"]+)"""".toRegex()
                            val fieldMatches = fieldNameRegex.findAll(fldsSection)
                            
                            for (fieldMatch in fieldMatches) {
                                val fieldName = fieldMatch.groupValues[1]
                                if (!fields.contains(fieldName)) {
                                    Log.d(TAG, "Found field name for model $mid: $fieldName")
                                    fields.add(fieldName)
                                }
                            }
                        }
                    }
                    
                    // Try more direct field extraction from models column directly
                    try {
                        val fieldsJsonRegex = """"flds":\s*\[(.*?)]""".toRegex(RegexOption.DOT_MATCHES_ALL)
                        val fieldsMatches = fieldsJsonRegex.findAll(modelsJson)
                        
                        for (fieldsMatch in fieldsMatches) {
                            val fldsJson = fieldsMatch.groupValues[1]
                            // Extract field names from the flds JSON
                            val nameRegex = """"name"\s*:\s*"([^"]+)"""".toRegex()
                            val nameMatches = nameRegex.findAll(fldsJson)
                            
                            for (nameMatch in nameMatches) {
                                val fieldName = nameMatch.groupValues[1]
                                if (!fields.contains(fieldName)) {
                                    Log.d(TAG, "Found field name from direct JSON: $fieldName")
                                    fields.add(fieldName)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error extracting fields directly from models JSON", e)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error getting fields from model IDs", e)
                    // Fall through to regex approaches
                }
                
                // If we haven't found fields yet, try regex patterns
                if (fields.isEmpty()) {
                    // Try several regex patterns to extract field names from different Anki versions
                    
                    // Pattern 1: Anki 2.0 style - Extract fields from flds array in each model
                    var matchCount = 0
                    val anki20Regex = """"flds":\s*\[\s*(\{[^}]*"name":\s*"([^"]+)"[^}]*\}(?:,\s*\{[^}]*"name":\s*"([^"]+)"[^}]*\})*)\]""".toRegex()
                    val matches = anki20Regex.findAll(modelsJson)
                    
                    for (match in matches) {
                        matchCount++
                        
                        // Extract field names from the match
                        val fieldJsonObjects = match.groupValues[1]
                        Log.d(TAG, "Field JSON objects: $fieldJsonObjects")
                        
                        val fieldNameRegex = """"name":\s*"([^"]+)"""".toRegex()
                        val fieldNameMatches = fieldNameRegex.findAll(fieldJsonObjects)
                        
                        for (fieldNameMatch in fieldNameMatches) {
                            val fieldName = fieldNameMatch.groupValues[1]
                            Log.d(TAG, "Found field name (pattern 1): $fieldName")
                            if (!fields.contains(fieldName)) {
                                fields.add(fieldName)
                            }
                        }
                    }
                    
                    // Pattern 2: Anki 2.1 alternative format - with array of strings
                    if (matchCount == 0) {
                        val anki21FieldsRegex = """"flds":\s*\[\s*"([^"]+)"(?:\s*,\s*"([^"]+)")*\s*\]""".toRegex()
                        val anki21Matches = anki21FieldsRegex.findAll(modelsJson)
                        
                        for (match in anki21Matches) {
                            matchCount++
                            // The regex captures each field in a different group
                            for (i in 1 until match.groupValues.size) {
                                val fieldName = match.groupValues[i]
                                if (fieldName.isNotEmpty() && !fields.contains(fieldName)) {
                                    Log.d(TAG, "Found field name (pattern 2): $fieldName")
                                    fields.add(fieldName)
                                }
                            }
                        }
                    }
                    
                    // Pattern 3: Try a more flexible regex just to find all name fields for any structure
                    if (matchCount == 0) {
                        val nameRegex = """"name"\s*:\s*"([^"]+)"""".toRegex() 
                        val nameMatches = nameRegex.findAll(modelsJson)
                        
                        // Names could be fields, models, templates - need to filter
                        val potentialFields = mutableListOf<String>()
                        for (match in nameMatches) {
                            val name = match.groupValues[1]
                            potentialFields.add(name)
                        }
                        
                        // Filter out unlikely field names (typically field names are capitalized words)
                        val filteredFields = potentialFields.filter { 
                            it.isNotEmpty() && 
                            it.length < 30 && // Not too long
                            !it.contains("{{") && // Not a template syntax
                            !it.contains("card") && // Not likely a card template name
                            !it.equals("Basic", ignoreCase = true) && // Not a note type name
                            !it.equals("Cloze", ignoreCase = true) // Not a note type name
                        }.distinct()
                        
                        Log.d(TAG, "Found ${filteredFields.size} potential field names using name regex")
                        fields.addAll(filteredFields)
                        matchCount = filteredFields.size
                    }
                }
                
                // If no matches were found with the regexes, try common field names as fallback
                if (fields.isEmpty()) {
                    Log.w(TAG, "No field matches found with regex. Using common field names as fallback.")
                    
                    // Add common field names as a last resort
                    val commonFields = listOf("Front", "Back", "Word", "Definition", "Reading", "Notes", "Example")
                    for (commonField in commonFields) {
                        if (!fields.contains(commonField)) {
                            fields.add(commonField)
                        }
                    }
                    
                    // Also look for simple quoted field names anywhere in the JSON
                    val simpleFieldRegex = """"(Front|Back|Word|Definition|Reading|Notes|Example|Image|Header|Footer)"""".toRegex()
                    val simpleMatches = simpleFieldRegex.findAll(modelsJson)
                    
                    for (simpleMatch in simpleMatches) {
                        val fieldName = simpleMatch.groupValues[1]
                        if (!fields.contains(fieldName)) {
                            Log.d(TAG, "Found field name using simple regex: $fieldName")
                            fields.add(fieldName)
                        }
                    }
                }
            }
            
            cursor.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available fields", e)
        }
        
        return fields
    }
    
    /**
     * Get available decks from the Anki database
     * 
     * @param db SQLiteDatabase instance
     * @return List of deck names
     */
    private fun getAvailableDecks(db: SQLiteDatabase): List<String> {
        val decks = mutableListOf<String>()
        
        try {
            // Query the decks table
            val cursor = db.rawQuery("SELECT decks FROM col", null)
            
            if (cursor.moveToFirst()) {
                // The decks column contains a JSON string of all decks
                val decksJson = cursor.getString(0)
                
                // Parse the JSON to extract deck names
                val regex = """"name":\s*"([^"]+)"""".toRegex()
                val matches = regex.findAll(decksJson)
                
                for (match in matches) {
                    val deckName = match.groupValues[1]
                    decks.add(deckName)
                }
            }
            
            cursor.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available decks", e)
        }
        
        return decks
    }
    
    /**
     * Get words from a specific field in the Anki database
     * 
     * @param db SQLiteDatabase instance
     * @param fieldIndex The index of the field to extract words from
     * @param deckName Optional deck name to filter by (null for all decks)
     * @return List of words extracted from the field
     */
    private fun getWordsFromField(db: SQLiteDatabase, fieldIndex: Int, deckName: String?): List<String> {
        val words = mutableListOf<String>()
        
        // First, check if this is a compatible Anki database
        try {
            // Check for the compatibility error message in models
            val modelsCheck = db.rawQuery("SELECT models FROM col", null)
            if (modelsCheck.moveToFirst()) {
                val modelsJson = modelsCheck.getString(0)
                if (modelsJson.contains("please update to the latest anki version")) {
                    Log.e(TAG, "Detected Anki version incompatibility message in database during word import")
                    modelsCheck.close()
                    return emptyList() // Return empty list instead of importing the error message
                }
            }
            modelsCheck.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Anki compatibility", e)
            // Continue with import attempt despite error
        }
        
        try {
            // Check if this is an Anki 2.1 database by looking for schema version
            var schemaVersion = -1
            var isAnki21 = false
            try {
                val schemaQuery = db.rawQuery("SELECT ver FROM col", null)
                if (schemaQuery.moveToFirst()) {
                    schemaVersion = schemaQuery.getInt(0)
                    isAnki21 = schemaVersion >= 11
                    Log.d(TAG, "Anki schema version: $schemaVersion, isAnki21: $isAnki21")
                }
                schemaQuery.close()
            } catch (e: Exception) {
                Log.w(TAG, "Could not determine Anki schema version", e)
            }
            
            // First try to get the actual field name for better logging
            var fieldName = "Field $fieldIndex"
            try {
                val fields = parseAnki21ModelsJson(db)
                if (fieldIndex < fields.size) {
                    fieldName = fields[fieldIndex]
                    Log.d(TAG, "Field index $fieldIndex corresponds to field name: $fieldName")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not get field name for index $fieldIndex", e)
            }
            
            var deckId: Long? = null
            
            // Build the query to get notes
            val query = if (deckName != null && deckName.isNotEmpty()) {
                // Try to get deck ID for the given deck name from decks JSON first (Anki 2.1)
                try {
                    val decksQuery = db.rawQuery("SELECT decks FROM col", null)
                    if (decksQuery.moveToFirst()) {
                        val decksJson = decksQuery.getString(0)
                        Log.d(TAG, "Decks JSON: ${decksJson.take(200)}...")
                        
                        // Find the deck ID for the given name
                        val deckRegex = """"(\d+)":\s*\{.*?"name":\s*"$deckName"""".toRegex(RegexOption.DOT_MATCHES_ALL)
                        val deckMatch = deckRegex.find(decksJson)
                        
                        if (deckMatch != null) {
                            deckId = deckMatch.groupValues[1].toLongOrNull()
                            Log.d(TAG, "Found deck ID $deckId for deck name $deckName")
                        }
                    }
                    decksQuery.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing decks JSON", e)
                }
                
                // If we couldn't get deck ID from JSON, try the legacy approach
                if (deckId == null) {
                    try {
                        val deckIdQuery = "SELECT id FROM decks WHERE name = ?"
                        val deckIdCursor = db.rawQuery(deckIdQuery, arrayOf(deckName))
                        if (deckIdCursor.moveToFirst()) {
                            deckId = deckIdCursor.getLong(0)
                            Log.d(TAG, "Found deck ID $deckId for deck name $deckName using legacy query")
                        }
                        deckIdCursor.close()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error getting deck ID with legacy query", e)
                    }
                }
                
                if (deckId == null) {
                    Log.w(TAG, "Could not find deck with name: $deckName")
                    return emptyList()
                }
                
                // Query notes in the specific deck
                "SELECT flds FROM notes WHERE id IN (SELECT nid FROM cards WHERE did = $deckId)"
            } else {
                // Query all notes
                "SELECT flds FROM notes"
            }
            
            // Execute the query
            Log.d(TAG, "Executing query: $query")
            val cursor = db.rawQuery(query, null)
            
            // Get the count of notes
            val noteCount = cursor.count
            Log.d(TAG, "Found $noteCount notes in query result")
            
            // If we have no notes, check if the notes table exists
            if (noteCount == 0) {
                val tableCheck = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='notes'", null)
                val hasNotesTable = tableCheck.count > 0
                tableCheck.close()
                
                if (!hasNotesTable) {
                    Log.e(TAG, "Notes table not found in Anki database - may be incompatible format")
                    cursor.close()
                    return emptyList()
                }
            }
            
            // Process the results
            var processedCount = 0
            while (cursor.moveToNext()) {
                processedCount++
                val fieldsString = cursor.getString(0)
                
                // Check for the Anki update message in note content
                if (fieldsString.contains("update to the latest Anki version") || 
                    fieldsString.contains("import the .colpkg/.apkg file")) {
                    Log.e(TAG, "Found Anki update message in note content. This is likely an incompatible file.")
                    throw IllegalStateException(
                        "This Anki package appears to be from a newer version of Anki.\n\n" +
                        "Please re-export your deck using Anki 2.1.x with the legacy export option enabled."
                    )
                }
                
                // Log a sample of the field string
                if (processedCount <= 3) {
                    Log.d(TAG, "Note #$processedCount fields string: ${fieldsString.take(100)}")
                }
                
                // Fields are separated by the Unit Separator character (0x1F)
                val fields = fieldsString.split("\u001F")
                Log.d(TAG, "Found ${fields.size} fields in note #$processedCount")
                
                // Get the requested field if it exists
                if (fieldIndex >= 0 && fieldIndex < fields.size) {
                    val fieldValue = fields[fieldIndex]
                    
                    // Check for update message again in this specific field
                    if (fieldValue.contains("update to the latest Anki version") || 
                        fieldValue.contains("import the .colpkg/.apkg file")) {
                        Log.e(TAG, "Found Anki update message in field value.")
                        throw IllegalStateException(
                            "This Anki package appears to be from a newer version of Anki.\n\n" +
                            "Please re-export your deck using Anki 2.1.x with the legacy export option enabled."
                        )
                    }
                    
                    // Log the field value
                    Log.d(TAG, "Field '$fieldName' value: ${fieldValue.take(50)}")
                    
                    // Remove HTML tags and clean up
                    val cleanedValue = cleanHtmlTags(fieldValue).trim()
                    
                    // Skip empty values or values that look like error messages
                    if (cleanedValue.isNotEmpty() && 
                        !cleanedValue.contains("update", ignoreCase = true) &&
                        !cleanedValue.contains("import", ignoreCase = true) &&
                        !cleanedValue.contains("apkg", ignoreCase = true) &&
                        !cleanedValue.contains("colpkg", ignoreCase = true) &&
                        cleanedValue.length < 50) { // Skip suspiciously long "words"
                        
                        Log.d(TAG, "Added word: $cleanedValue")
                        words.add(cleanedValue.lowercase())
                    } else if (cleanedValue.length >= 50) {
                        Log.w(TAG, "Skipping suspiciously long 'word': ${cleanedValue.take(30)}...")
                    }
                } else {
                    Log.w(TAG, "Field index $fieldIndex out of bounds for note with ${fields.size} fields")
                }
                
                // Log progress for large imports
                if (processedCount % 100 == 0) {
                    Log.d(TAG, "Processed $processedCount out of $noteCount notes")
                }
            }
            
            cursor.close()
            Log.d(TAG, "Extracted ${words.size} words from field index $fieldIndex")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting words from field", e)
        }
        
        return words
    }
    
    /**
     * Remove HTML tags from a string
     * 
     * @param html HTML string
     * @return String without HTML tags
     */
    private fun cleanHtmlTags(html: String): String {
        return html.replace(Regex("<[^>]*>"), "")
    }
    
    /**
     * Parse Anki 2.1 models JSON directly
     * 
     * This is a direct approach to extract field names from the models JSON in Anki 2.1 packages
     * 
     * @param db SQLiteDatabase instance
     * @return List of field names
     */
    private fun parseAnki21ModelsJson(db: SQLiteDatabase): List<String> {
        val fields = mutableListOf<String>()
        
        try {
            val colQuery = db.rawQuery("SELECT models FROM col", null)
            if (!colQuery.moveToFirst()) {
                colQuery.close()
                return emptyList()
            }
            
            val modelsJson = colQuery.getString(0)
            colQuery.close()
            
            // Log the first part of the JSON for debugging
            Log.d(TAG, "Models JSON: ${modelsJson.take(300)}...")
            
            // Find model IDs - they are the top-level keys in the JSON object
            val modelIdRegex = """"(\d+)":\s*\{""".toRegex()
            val modelIds = modelIdRegex.findAll(modelsJson).map { it.groupValues[1] }.toList()
            
            Log.d(TAG, "Found ${modelIds.size} model IDs in JSON")
            
            for (modelId in modelIds) {
                // Extract the model definition for this ID
                val modelRegex = """"$modelId":\s*\{.*?"flds":\s*\[(.*?)\]""".toRegex(RegexOption.DOT_MATCHES_ALL)
                val modelMatch = modelRegex.find(modelsJson)
                
                if (modelMatch != null) {
                    val fldsSection = modelMatch.groupValues[1]
                    Log.d(TAG, "Found fields section for model $modelId: ${fldsSection.take(100)}...")
                    
                    // Extract field names from the flds array
                    val fieldNameRegex = """"name":\s*"([^"]+)"""".toRegex()
                    val fieldMatches = fieldNameRegex.findAll(fldsSection)
                    
                    val modelFields = mutableListOf<String>()
                    for (fieldMatch in fieldMatches) {
                        val fieldName = fieldMatch.groupValues[1]
                        modelFields.add(fieldName)
                    }
                    
                    if (modelFields.isNotEmpty()) {
                        Log.d(TAG, "Extracted ${modelFields.size} fields from model $modelId: $modelFields")
                        fields.addAll(modelFields)
                    }
                }
            }
            
            // If no fields found yet, try an alternative approach for problematic JSON
            if (fields.isEmpty()) {
                // Look for all "name" fields in the context of field definitions
                val fldNameRegex = """"flds":\s*\[.*?"name":\s*"([^"]+)"""".toRegex(RegexOption.DOT_MATCHES_ALL)
                val fldMatches = fldNameRegex.findAll(modelsJson)
                
                for (match in fldMatches) {
                    val fieldName = match.groupValues[1]
                    if (!fields.contains(fieldName)) {
                        fields.add(fieldName)
                        Log.d(TAG, "Found field using fallback regex: $fieldName")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Anki 2.1 models JSON", e)
        }
        
        Log.d(TAG, "Total fields extracted: ${fields.size}")
        return fields.distinct()
    }
    
    /**
     * Attempt to extract field names by looking at actual notes in the database
     * 
     * @param db SQLiteDatabase instance
     * @return List of field names
     */
    private fun extractFieldsFromNotes(db: SQLiteDatabase): List<String> {
        val extractedFields = mutableListOf<String>()
        
        try {
            // First check if we have a models table with separate field definitions
            var hasModelsTable = false
            try {
                val modelsCheck = db.rawQuery("SELECT EXISTS (SELECT 1 FROM sqlite_master WHERE type='table' AND name='models')", null)
                if (modelsCheck.moveToFirst()) {
                    hasModelsTable = modelsCheck.getInt(0) == 1
                }
                modelsCheck.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error checking for models table", e)
            }
            
            if (hasModelsTable) {
                Log.d(TAG, "Found models table, trying to extract field names directly")
                
                try {
                    // Query models table for field names
                    val modelQuery = db.rawQuery("SELECT flds FROM models", null)
                    while (modelQuery.moveToNext()) {
                        val flds = modelQuery.getString(0)
                        // Fields are stored as unit separator-delimited string
                        val fields = flds.split("\u001F")
                        
                        for (field in fields) {
                            if (field.isNotEmpty() && !extractedFields.contains(field)) {
                                extractedFields.add(field)
                                Log.d(TAG, "Found field directly from models table: $field")
                            }
                        }
                    }
                    modelQuery.close()
                    
                    if (extractedFields.isNotEmpty()) {
                        return extractedFields
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error extracting fields from models table", e)
                }
            }
            
            // If we couldn't get fields from models table, try another approach:
            // Get model IDs used in actual notes, then find sample notes for each model
            val midToNotesMap = mutableMapOf<Long, List<String>>()
            
            // Get distinct model IDs used in notes
            val midsQuery = db.rawQuery("SELECT DISTINCT mid FROM notes LIMIT 10", null)
            val mids = mutableListOf<Long>()
            while (midsQuery.moveToNext()) {
                mids.add(midsQuery.getLong(0))
            }
            midsQuery.close()
            
            // For each model ID, get a sample note
            for (mid in mids) {
                val notesQuery = db.rawQuery("SELECT flds FROM notes WHERE mid = ? LIMIT 5", arrayOf(mid.toString()))
                val noteFields = mutableListOf<String>()
                
                while (notesQuery.moveToNext()) {
                    val fieldsString = notesQuery.getString(0)
                    // Split the fields string by unit separator
                    noteFields.add(fieldsString)
                }
                notesQuery.close()
                
                if (noteFields.isNotEmpty()) {
                    midToNotesMap[mid] = noteFields
                }
            }
            
            // Now try to get field names from the col table
            val colQuery = db.rawQuery("SELECT models FROM col", null)
            if (colQuery.moveToFirst()) {
                val modelsJson = colQuery.getString(0)
                
                // For each model ID, extract field names from the JSON
                for (mid in mids) {
                    // First try to find the model in the JSON directly
                    val modelPattern = """"$mid"\s*:\s*\{[^}]*"flds"\s*:\s*\[(.*?)\]""".toRegex(RegexOption.DOT_MATCHES_ALL)
                    val modelMatch = modelPattern.find(modelsJson)
                    
                    if (modelMatch != null) {
                        val fldsSection = modelMatch.groupValues[1]
                        // Extract field names from the flds section
                        val fieldNameRegex = """"name"\s*:\s*"([^"]+)"""".toRegex()
                        val fieldMatches = fieldNameRegex.findAll(fldsSection)
                        
                        val fieldNamesForModel = mutableListOf<String>()
                        for (fieldMatch in fieldMatches) {
                            val fieldName = fieldMatch.groupValues[1]
                            fieldNamesForModel.add(fieldName)
                        }
                        
                        if (fieldNamesForModel.isNotEmpty()) {
                            // Found field names for this model
                            extractedFields.addAll(fieldNamesForModel.filter { !extractedFields.contains(it) })
                            Log.d(TAG, "Found ${fieldNamesForModel.size} fields for model $mid: $fieldNamesForModel")
                        }
                    }
                }
            }
            colQuery.close()
            
            // If we still don't have field names, try inferring them from the content
            if (extractedFields.isEmpty() && midToNotesMap.isNotEmpty()) {
                // Take the first model's notes
                val sampleNotes = midToNotesMap.values.first()
                if (sampleNotes.isNotEmpty()) {
                    // Calculate the number of fields based on unit separator count
                    val fieldSeparatorCount = sampleNotes[0].count { it == '\u001F' }
                    val fieldCount = fieldSeparatorCount + 1
                    
                    // Create generic field names
                    for (i in 1..fieldCount) {
                        extractedFields.add("Field $i")
                    }
                    
                    Log.d(TAG, "Created $fieldCount generic field names based on sample note")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting fields from notes", e)
        }
        
        return extractedFields
    }
}