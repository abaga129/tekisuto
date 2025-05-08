package com.abaga129.tekisuto.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO for dictionary operations
 */
@Dao
interface DictionaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<DictionaryEntryEntity>): List<Long>

    @Query("SELECT e.* FROM dictionary_entries e " +
            "JOIN dictionary_metadata m ON e.dictionaryId = m.id " +
            "LEFT JOIN word_frequencies wf ON e.term = wf.word AND e.dictionaryId = wf.dictionaryId " +
            "WHERE LOWER(e.term) LIKE LOWER(:query) OR LOWER(e.reading) LIKE LOWER(:query) OR LOWER(e.definition) LIKE LOWER(:query) " +
            "ORDER BY " +
            "CASE " +
            "  WHEN LOWER(e.term) = LOWER(:exactQuery) THEN 0 " + // Exact term match gets highest priority
            "  WHEN LOWER(e.term) LIKE LOWER(:exactQuery) || '%' THEN 1 " + // Term starts with query
            "  WHEN LOWER(e.reading) = LOWER(:exactQuery) THEN 2 " + // Exact reading match
            "  WHEN LOWER(e.reading) LIKE LOWER(:exactQuery) || '%' THEN 3 " + // Reading starts with query
            "  ELSE 4 " + // Everything else (matches in definition, etc.)
            "END, " +
            "LENGTH(e.term), " + // Prioritize shorter terms (exact matches like "be" before "beautiful")
            "CASE WHEN wf.frequency IS NOT NULL THEN 0 ELSE 1 END, " + // Prioritize entries with frequency data
            "CASE WHEN wf.frequency IS NOT NULL THEN wf.frequency ELSE 999999 END, " + // Sort by frequency (lower numbers first)
            "m.priority DESC, e.id DESC")
    suspend fun searchEntriesOrderedByPriority(query: String, exactQuery: String = query): List<DictionaryEntryEntity>

    @Query("SELECT e.* FROM dictionary_entries e " +
            "JOIN dictionary_metadata m ON e.dictionaryId = m.id " +
            "JOIN profile_dictionaries pd ON e.dictionaryId = pd.dictionaryId " +
            "LEFT JOIN word_frequencies wf ON e.term = wf.word AND e.dictionaryId = wf.dictionaryId " +
            "WHERE (LOWER(e.term) LIKE LOWER(:query) OR LOWER(e.reading) LIKE LOWER(:query) OR LOWER(e.definition) LIKE LOWER(:query)) " +
            "AND pd.profileId = :profileId " +
            "ORDER BY " +
            "CASE " +
            "  WHEN LOWER(e.term) = LOWER(:exactQuery) THEN 0 " + // Exact term match gets highest priority
            "  WHEN LOWER(e.term) LIKE LOWER(:exactQuery) || '%' THEN 1 " + // Term starts with query
            "  WHEN LOWER(e.reading) = LOWER(:exactQuery) THEN 2 " + // Exact reading match
            "  WHEN LOWER(e.reading) LIKE LOWER(:exactQuery) || '%' THEN 3 " + // Reading starts with query
            "  ELSE 4 " + // Everything else (matches in definition, etc.)
            "END, " +
            "LENGTH(e.term), " + // Prioritize shorter terms (exact matches like "be" before "beautiful")
            "CASE WHEN wf.frequency IS NOT NULL THEN 0 ELSE 1 END, " + // Prioritize entries with frequency data
            "CASE WHEN wf.frequency IS NOT NULL THEN wf.frequency ELSE 999999 END, " + // Sort by frequency (lower numbers first)
            "m.priority DESC, e.id DESC")
    suspend fun searchEntriesForProfile(query: String, exactQuery: String = query, profileId: Long): List<DictionaryEntryEntity>

    @Query("SELECT e.* FROM dictionary_entries e " +
            "JOIN dictionary_metadata m ON e.dictionaryId = m.id " +
            "LEFT JOIN word_frequencies wf ON e.term = wf.word AND e.dictionaryId = wf.dictionaryId " +
            "WHERE e.term LIKE :query OR e.reading LIKE :query " +  /* Using index without LOWER for better performance */
            "ORDER BY " +
            "CASE " +
            "  WHEN e.term = :exactQuery THEN 0 " + // Exact term match gets highest priority
            "  WHEN e.term LIKE :exactQuery || '%' THEN 1 " + // Term starts with query
            "  WHEN e.reading = :exactQuery THEN 2 " + // Exact reading match
            "  WHEN e.reading LIKE :exactQuery || '%' THEN 3 " + // Reading starts with query
            "  ELSE 4 " + // Everything else
            "END, " +
            "LENGTH(e.term), " + // Prioritize shorter terms (exact matches like "be" before "beautiful")
            "CASE WHEN wf.frequency IS NOT NULL THEN 0 ELSE 1 END, " + // Prioritize entries with frequency data
            "CASE WHEN wf.frequency IS NOT NULL THEN wf.frequency ELSE 999999 END, " + // Sort by frequency (lower is better)
            "m.priority DESC, e.id DESC LIMIT 50")
    suspend fun fastSearchEntries(query: String, exactQuery: String = query): List<DictionaryEntryEntity>

    @Query("SELECT e.* FROM dictionary_entries e " +
            "JOIN profile_dictionaries pd ON e.dictionaryId = pd.dictionaryId " +
            "JOIN dictionary_metadata m ON e.dictionaryId = m.id " +
            "LEFT JOIN word_frequencies wf ON e.term = wf.word AND e.dictionaryId = wf.dictionaryId " +
            "WHERE (e.term LIKE :query OR e.reading LIKE :query) " +  /* Using index without LOWER for better performance */
            "AND pd.profileId = :profileId " +
            "ORDER BY " +
            "CASE " +
            "  WHEN e.term = :exactQuery THEN 0 " + // Exact term match gets highest priority
            "  WHEN e.term LIKE :exactQuery || '%' THEN 1 " + // Term starts with query
            "  WHEN e.reading = :exactQuery THEN 2 " + // Exact reading match
            "  WHEN e.reading LIKE :exactQuery || '%' THEN 3 " + // Reading starts with query
            "  ELSE 4 " + // Everything else
            "END, " +
            "LENGTH(e.term), " + // Prioritize shorter terms (exact matches like "be" before "beautiful")
            "CASE WHEN wf.frequency IS NOT NULL THEN 0 ELSE 1 END, " + // Prioritize entries with frequency data
            "CASE WHEN wf.frequency IS NOT NULL THEN wf.frequency ELSE 999999 END, " + // Sort by frequency (lower is better)
            "m.priority DESC, e.id DESC LIMIT 50")
    suspend fun fastSearchEntriesForProfile(query: String, exactQuery: String = query, profileId: Long): List<DictionaryEntryEntity>

    /* Bulk search for multiple terms at once */
    @Query("SELECT * FROM dictionary_entries WHERE term IN (:terms) LIMIT 100")
    suspend fun bulkSearchByExactTerms(terms: List<String>): List<DictionaryEntryEntity>

    /* Bulk search for multiple terms at once, limited to a specific profile */
    @Query("SELECT e.* FROM dictionary_entries e " +
            "JOIN profile_dictionaries pd ON e.dictionaryId = pd.dictionaryId " +
            "WHERE e.term IN (:terms) AND pd.profileId = :profileId LIMIT 100")
    suspend fun bulkSearchByExactTermsForProfile(terms: List<String>, profileId: Long): List<DictionaryEntryEntity>

    @Query("SELECT * FROM dictionary_entries WHERE LOWER(term) = LOWER(:term) " +
            "ORDER BY id DESC LIMIT 1")
    suspend fun getEntryByTerm(term: String): DictionaryEntryEntity?

    @Query("SELECT COUNT(*) FROM dictionary_entries")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM dictionary_entries WHERE dictionaryId = :dictionaryId")
    suspend fun getCountByDictionaryId(dictionaryId: Long): Int

    @Query("DELETE FROM dictionary_entries")
    suspend fun deleteAll()

    @Query("DELETE FROM dictionary_entries WHERE dictionaryId = :dictionaryId")
    suspend fun deleteEntriesByDictionaryId(dictionaryId: Long)

    @Query("SELECT * FROM dictionary_entries ORDER BY id DESC LIMIT :limit")
    suspend fun getRecentEntries(limit: Int): List<DictionaryEntryEntity>

    @Query("SELECT * FROM dictionary_entries WHERE dictionaryId IN (:dictionaryIds) ORDER BY id DESC LIMIT :limit")
    suspend fun getRecentEntriesFromDictionaries(limit: Int, dictionaryIds: List<Long>): List<DictionaryEntryEntity>
}