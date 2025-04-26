package com.abaga129.tekisuto.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Entity representing a user profile.
 * Profiles allow users to have different settings and dictionaries per profile.
 */
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val isDefault: Boolean = false,
    val createdDate: Date = Date(),
    val lastUsedDate: Date = Date(),
    
    // OCR settings
    val ocrService: String = "mlkit",
    val ocrLanguage: String = "latin",
    val translateOcrText: Boolean = true,
    val translateTargetLanguage: String = "en",
    // Deprecated: Kept for database compatibility
    val cloudOcrApiKey: String = "", // Cloud OCR service has been removed
    
    // Screenshot settings
    val enableLongPressCapture: Boolean = true,
    val longPressDuration: Int = 500,
    
    // Audio settings
    val enableAudio: Boolean = true,
    val azureSpeechKey: String = "",
    val azureSpeechRegion: String = "eastus",
    val voiceSelection: String = "",  // Format: "languageCode:voiceId" (e.g., "en:en-US-GuyNeural")
    
    // AnkiDroid settings
    val ankiDeckId: Long = 0,
    val ankiModelId: Long = 0,
    val ankiFieldWord: Int = -1,
    val ankiFieldReading: Int = -1,
    val ankiFieldDefinition: Int = -1,
    val ankiFieldScreenshot: Int = -1,
    val ankiFieldContext: Int = -1,
    val ankiFieldPartOfSpeech: Int = -1,
    val ankiFieldTranslation: Int = -1,
    val ankiFieldAudio: Int = -1
)

/**
 * DAO for profile operations
 */
@androidx.room.Dao
interface ProfileDao {
    @androidx.room.Insert
    suspend fun insert(profile: ProfileEntity): Long
    
    @androidx.room.Query("SELECT * FROM profiles ORDER BY lastUsedDate DESC")
    suspend fun getAllProfiles(): List<ProfileEntity>
    
    @androidx.room.Query("SELECT * FROM profiles WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultProfile(): ProfileEntity?
    
    @androidx.room.Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getProfileById(id: Long): ProfileEntity?
    
    @androidx.room.Query("UPDATE profiles SET name = :name WHERE id = :id")
    suspend fun updateName(id: Long, name: String)
    
    @androidx.room.Query("UPDATE profiles SET isDefault = 0")
    suspend fun clearDefaultStatus()
    
    @androidx.room.Query("UPDATE profiles SET isDefault = 1 WHERE id = :id")
    suspend fun setAsDefault(id: Long)
    
    @androidx.room.Query("UPDATE profiles SET lastUsedDate = :date WHERE id = :id")
    suspend fun updateLastUsedDate(id: Long, date: Date)
    
    @androidx.room.Query("DELETE FROM profiles WHERE id = :id")
    suspend fun deleteProfile(id: Long)
    
    @androidx.room.Query("SELECT COUNT(*) FROM profiles")
    suspend fun getProfileCount(): Int
    
    // Update OCR settings
    @androidx.room.Query("UPDATE profiles SET ocrService = :ocrService WHERE id = :id")
    suspend fun updateOcrService(id: Long, ocrService: String)
    
    // Deprecated: Kept for database compatibility
    @androidx.room.Query("UPDATE profiles SET cloudOcrApiKey = :apiKey WHERE id = :id")
    suspend fun updateCloudOcrApiKey(id: Long, apiKey: String)
    
    @androidx.room.Query("UPDATE profiles SET ocrLanguage = :ocrLanguage WHERE id = :id")
    suspend fun updateOcrLanguage(id: Long, ocrLanguage: String)
    
    @androidx.room.Query("UPDATE profiles SET translateOcrText = :translate WHERE id = :id")
    suspend fun updateTranslateOcrText(id: Long, translate: Boolean)
    
    @androidx.room.Query("UPDATE profiles SET translateTargetLanguage = :language WHERE id = :id")
    suspend fun updateTranslateTargetLanguage(id: Long, language: String)
    
    // Update screenshot settings
    @androidx.room.Query("UPDATE profiles SET enableLongPressCapture = :enable WHERE id = :id")
    suspend fun updateEnableLongPressCapture(id: Long, enable: Boolean)
    
    @androidx.room.Query("UPDATE profiles SET longPressDuration = :duration WHERE id = :id")
    suspend fun updateLongPressDuration(id: Long, duration: Int)
    
    // Update audio settings
    @androidx.room.Query("UPDATE profiles SET enableAudio = :enable WHERE id = :id")
    suspend fun updateEnableAudio(id: Long, enable: Boolean)
    
    @androidx.room.Query("UPDATE profiles SET azureSpeechKey = :key WHERE id = :id")
    suspend fun updateAzureSpeechKey(id: Long, key: String)
    
    @androidx.room.Query("UPDATE profiles SET azureSpeechRegion = :region WHERE id = :id")
    suspend fun updateAzureSpeechRegion(id: Long, region: String)
    
    @androidx.room.Query("UPDATE profiles SET voiceSelection = :selection WHERE id = :id")
    suspend fun updateVoiceSelection(id: Long, selection: String)
    
    // Update AnkiDroid settings
    @androidx.room.Query("UPDATE profiles SET ankiDeckId = :deckId, ankiModelId = :modelId WHERE id = :id")
    suspend fun updateAnkiDeckAndModel(id: Long, deckId: Long, modelId: Long)
    
    @androidx.room.Query("UPDATE profiles SET ankiFieldWord = :field WHERE id = :id")
    suspend fun updateAnkiFieldWord(id: Long, field: Int)
    
    @androidx.room.Query("UPDATE profiles SET ankiFieldReading = :field WHERE id = :id")
    suspend fun updateAnkiFieldReading(id: Long, field: Int)
    
    @androidx.room.Query("UPDATE profiles SET ankiFieldDefinition = :field WHERE id = :id")
    suspend fun updateAnkiFieldDefinition(id: Long, field: Int)
    
    @androidx.room.Query("UPDATE profiles SET ankiFieldScreenshot = :field WHERE id = :id")
    suspend fun updateAnkiFieldScreenshot(id: Long, field: Int)
    
    @androidx.room.Query("UPDATE profiles SET ankiFieldContext = :field WHERE id = :id")
    suspend fun updateAnkiFieldContext(id: Long, field: Int)
    
    @androidx.room.Query("UPDATE profiles SET ankiFieldPartOfSpeech = :field WHERE id = :id")
    suspend fun updateAnkiFieldPartOfSpeech(id: Long, field: Int)
    
    @androidx.room.Query("UPDATE profiles SET ankiFieldTranslation = :field WHERE id = :id")
    suspend fun updateAnkiFieldTranslation(id: Long, field: Int)
    
    @androidx.room.Query("UPDATE profiles SET ankiFieldAudio = :field WHERE id = :id")
    suspend fun updateAnkiFieldAudio(id: Long, field: Int)
    
    @androidx.room.Query("""UPDATE profiles SET 
        ankiDeckId = :deckId, 
        ankiModelId = :modelId, 
        ankiFieldWord = :fieldWord, 
        ankiFieldReading = :fieldReading, 
        ankiFieldDefinition = :fieldDefinition, 
        ankiFieldScreenshot = :fieldScreenshot, 
        ankiFieldContext = :fieldContext, 
        ankiFieldPartOfSpeech = :fieldPartOfSpeech, 
        ankiFieldTranslation = :fieldTranslation, 
        ankiFieldAudio = :fieldAudio 
        WHERE id = :id""")
    suspend fun updateAnkiConfiguration(
        id: Long, 
        deckId: Long, 
        modelId: Long, 
        fieldWord: Int, 
        fieldReading: Int, 
        fieldDefinition: Int, 
        fieldScreenshot: Int, 
        fieldContext: Int, 
        fieldPartOfSpeech: Int, 
        fieldTranslation: Int, 
        fieldAudio: Int
    )
    
    // Update all profile settings at once
    @androidx.room.Update
    suspend fun updateProfile(profile: ProfileEntity)
}