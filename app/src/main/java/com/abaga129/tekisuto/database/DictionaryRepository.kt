package com.abaga129.tekisuto.database

import android.content.Context
import android.util.Log

private const val TAG = "DictionaryRepository"

/**
 * Dictionary repository to handle database operations
 * Acts as a facade for the specialized repositories
 */
class DictionaryRepository(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: DictionaryRepository? = null

        fun getInstance(context: Context): DictionaryRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = DictionaryRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    // Initialize specialized repositories
    private val dictionaryMetadataRepo = DictionaryMetadataRepository.getInstance(context)
    private val dictionaryEntryRepo = DictionaryEntryRepository.getInstance(context)
    private val exportedWordRepo = ExportedWordRepository.getInstance(context)
    private val profileDictionaryRepo = ProfileDictionaryRepository.getInstance(context)
    private val wordFrequencyRepo = WordFrequencyRepository.getInstance(context)

    init {
        // For debugging only
        Log.d(TAG, "DictionaryRepository initialized")
    }

    // DictionaryMetadataRepository delegates
    suspend fun saveDictionaryMetadata(metadata: DictionaryMetadataEntity): Long =
        dictionaryMetadataRepo.saveDictionaryMetadata(metadata)

    suspend fun getAllDictionaries(): List<DictionaryMetadataEntity> =
        dictionaryMetadataRepo.getAllDictionaries()

    suspend fun deleteDictionary(dictionaryId: Long) =
        dictionaryMetadataRepo.deleteDictionary(dictionaryId)

    suspend fun updateDictionaryPriority(dictionaryId: Long, newPriority: Int) =
        dictionaryMetadataRepo.updateDictionaryPriority(dictionaryId, newPriority)

    suspend fun getDictionaryMetadata(dictionaryId: Long): DictionaryMetadataEntity? =
        dictionaryMetadataRepo.getDictionaryMetadata(dictionaryId)

    // DictionaryEntryRepository delegates
    suspend fun importDictionaryEntries(entries: List<DictionaryEntryEntity>): Int =
        dictionaryEntryRepo.importDictionaryEntries(entries)

    suspend fun searchDictionary(
        query: String,
        profileId: Long? = null,
        fastSearch: Boolean = false
    ): List<DictionaryEntryEntity> =
        dictionaryEntryRepo.searchDictionary(query, profileId, fastSearch)

    suspend fun bulkSearchByExactTerms(terms: List<String>, profileId: Long? = null): List<DictionaryEntryEntity> =
        dictionaryEntryRepo.bulkSearchByExactTerms(terms, profileId)

    suspend fun getDictionaryEntryCount(): Int =
        dictionaryEntryRepo.getDictionaryEntryCount()

    suspend fun getDictionaryEntryCount(dictionaryId: Long): Int =
        dictionaryEntryRepo.getDictionaryEntryCount(dictionaryId)

    suspend fun getRecentEntries(limit: Int): List<DictionaryEntryEntity> =
        dictionaryEntryRepo.getRecentEntries(limit)

    suspend fun getRecentEntriesFromDictionaries(limit: Int, dictionaryIds: List<Long>): List<DictionaryEntryEntity> =
        dictionaryEntryRepo.getRecentEntriesFromDictionaries(limit, dictionaryIds)

    // ExportedWordRepository delegates
    suspend fun addExportedWord(word: String, dictionaryId: Long, ankiDeckId: Long, ankiNoteId: Long? = null) =
        exportedWordRepo.addExportedWord(word, dictionaryId, ankiDeckId, ankiNoteId)

    suspend fun isWordExported(word: String): Boolean =
        exportedWordRepo.isWordExported(word)

    suspend fun getAllExportedWords(): List<ExportedWordEntity> =
        exportedWordRepo.getAllExportedWords()

    suspend fun importExportedWords(words: List<String>, dictionaryId: Long, ankiDeckId: Long): Int =
        exportedWordRepo.importExportedWords(words, dictionaryId, ankiDeckId)

    // ProfileDictionaryRepository delegates
    suspend fun getDictionariesForProfile(profileId: Long): List<DictionaryMetadataEntity> =
        profileDictionaryRepo.getDictionariesForProfile(profileId)

    suspend fun addDictionaryToProfile(profileId: Long, dictionaryId: Long) =
        profileDictionaryRepo.addDictionaryToProfile(profileId, dictionaryId)

    suspend fun removeDictionaryFromProfile(profileId: Long, dictionaryId: Long) =
        profileDictionaryRepo.removeDictionaryFromProfile(profileId, dictionaryId)

    suspend fun isDictionaryInProfile(profileId: Long, dictionaryId: Long): Boolean =
        profileDictionaryRepo.isDictionaryInProfile(profileId, dictionaryId)

    // WordFrequencyRepository delegates
    suspend fun getWordFrequencies(dictionaryId: Long): List<WordFrequencyEntity> =
        wordFrequencyRepo.getWordFrequencies(dictionaryId)

    suspend fun getFrequencyForWord(word: String): WordFrequencyEntity? =
        wordFrequencyRepo.getFrequencyForWord(word)

    suspend fun getFrequencyForWordAndReading(word: String, reading: String? = null): WordFrequencyEntity? =
        wordFrequencyRepo.getFrequencyForWordAndReading(word, reading)

    suspend fun getFrequencyForReading(reading: String): WordFrequencyEntity? =
        wordFrequencyRepo.getFrequencyForReading(reading)

    suspend fun getFrequencyForWordInDictionary(word: String, dictionaryId: Long): WordFrequencyEntity? =
        wordFrequencyRepo.getFrequencyForWordInDictionary(word, dictionaryId)

    suspend fun getWordFrequencyCount(): Int =
        wordFrequencyRepo.getWordFrequencyCount()

    suspend fun getWordFrequencyCount(dictionaryId: Long): Int =
        wordFrequencyRepo.getWordFrequencyCount(dictionaryId)

    suspend fun importWordFrequencies(entries: List<WordFrequencyEntity>): Int =
        wordFrequencyRepo.importWordFrequencies(entries)
        
    // WordPitchAccentRepository delegates
    private val wordPitchAccentRepo = WordPitchAccentRepository.getInstance(context)

    suspend fun getPitchAccentForWordAndReading(word: String, reading: String): WordPitchAccentEntity? =
        wordPitchAccentRepo.getPitchAccentForWordAndReading(word, reading)

    suspend fun importWordPitchAccents(entries: List<WordPitchAccentEntity>): Int =
        wordPitchAccentRepo.importWordPitchAccents(entries)

    /**
     * Clears all dictionary entries and metadata
     */
    suspend fun clearAllDictionaries() {
        try {
            dictionaryEntryRepo.clearAllDictionaryEntries()
            dictionaryMetadataRepo.clearAllDictionaryMetadata()
            Log.d(TAG, "All dictionaries cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing all dictionaries", e)
            throw e
        }
    }
}
