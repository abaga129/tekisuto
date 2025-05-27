package com.abaga129.tekisuto.ui.ocr

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.preference.PreferenceManager
import com.abaga129.tekisuto.R
import com.abaga129.tekisuto.database.DictionaryEntryEntity
import com.abaga129.tekisuto.ui.anki.AnkiDroidConfigActivity
import com.abaga129.tekisuto.util.AnkiDroidHelper
import com.abaga129.tekisuto.util.PitchAccentExportHelper
import com.abaga129.tekisuto.util.SpeechService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages exporting dictionary entries to AnkiDroid with enhanced pitch accent support
 */
class AnkiExportManager(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val ankiDroidHelper: AnkiDroidHelper,
    private val speechService: SpeechService,
    private val audioManager: AudioManager
) {
    private val TAG = "AnkiExportManager"
    
    /**
     * Export a dictionary entry to AnkiDroid with enhanced formatting
     * 
     * @param entry The dictionary entry to export
     * @param ocrText The full OCR text for context
     * @param screenshotPath Path to the screenshot image
     * @param translatedText Translated text if available
     * @param ocrLanguage Detected OCR language
     */
    fun exportToAnki(
        entry: DictionaryEntryEntity,
        ocrText: String?,
        screenshotPath: String?,
        translatedText: String?,
        ocrLanguage: String?
    ) {
        if (!ankiDroidHelper.isAnkiDroidAvailable()) {
            Toast.makeText(context, R.string.anki_status_unavailable, Toast.LENGTH_SHORT).show()
            return
        }

        if (ankiDroidHelper.getSavedDeckId() == 0L || ankiDroidHelper.getSavedModelId() == 0L) {
            Toast.makeText(context, R.string.anki_configuration_not_set, Toast.LENGTH_SHORT).show()
            // Open Anki configuration activity
            val intent = Intent(context, AnkiDroidConfigActivity::class.java)
            context.startActivity(intent)
            return
        }

        lifecycleScope.launch {
            try {
                // Determine language from the entry
                val language = audioManager.determineLanguage(entry, ocrLanguage)

                // Check if we need to generate audio
                var audioFilePath: String? = null
                val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
                val audioEnabled = sharedPrefs.getBoolean("enable_audio", true)

                // Only generate audio if it's enabled
                if (audioEnabled) {
                    // Get audio file (from cache or generate new)
                    val audioFile = audioManager.getAudioFile(entry.term, language)
                    
                    // If we have audio, save it for export
                    if (audioFile != null) {
                        val exportFile = speechService.saveAudioForExport(
                            audioFile, entry.term, language
                        )
                        audioFilePath = exportFile?.absolutePath
                    }
                }

                // Try to get pitch accent for the word if it's Japanese with enhanced formatting
                // Pitch accent export is now always enabled when data is available
                var pitchAccent: String? = null
                if (language == "ja" || language == "jpn" || language == "japanese") {
                    try {
                        val dictionaryRepository = ankiDroidHelper.getDictionaryRepository()
                        val pitchAccentEntity = withContext(Dispatchers.IO) {
                            dictionaryRepository.getPitchAccentForWordAndReading(entry.term, entry.reading)
                        }
                        
                        // Enhanced pitch accent formatting using PitchAccentExportHelper
                        // Always generate enhanced pitch accent when data is available
                        if (pitchAccentEntity != null) {
                            pitchAccent = PitchAccentExportHelper.generatePitchAccentForExport(
                                context,
                                entry.reading,
                                pitchAccentEntity.pitchAccent
                            )
                            
                            Log.d(TAG, "Generated enhanced pitch accent for ${entry.term}: $pitchAccent")
                        } else {
                            Log.d(TAG, "No pitch accent found for ${entry.term}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error getting pitch accent", e)
                    }
                }

                val success = withContext(Dispatchers.IO) {
                    ankiDroidHelper.addNoteToAnkiDroid(
                        word = entry.term,
                        reading = entry.reading,
                        definition = entry.definition,
                        partOfSpeech = entry.partOfSpeech,
                        context = ocrText ?: "",
                        screenshotPath = screenshotPath,
                        translation = translatedText ?: "",
                        audioPath = audioFilePath,
                        pitchAccent = pitchAccent
                    )
                }

                if (success) {
                    // Show success message with pitch accent info if applicable
                    val message = if (pitchAccent != null) {
                        context.getString(R.string.export_success) + " (pitch accent included)"
                    } else {
                        context.getString(R.string.export_success)
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, R.string.export_failed, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error exporting to AnkiDroid", e)
                Toast.makeText(
                    context,
                    context.getString(R.string.export_error, e.message),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    

}
