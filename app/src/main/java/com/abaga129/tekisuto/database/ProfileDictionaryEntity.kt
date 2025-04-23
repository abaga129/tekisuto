package com.abaga129.tekisuto.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Entity that represents the many-to-many relationship between profiles and dictionaries.
 * This allows each profile to have its own set of dictionaries.
 */
@Entity(
    tableName = "profile_dictionaries",
    primaryKeys = ["profileId", "dictionaryId"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = DictionaryMetadataEntity::class,
            parentColumns = ["id"],
            childColumns = ["dictionaryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("profileId"),
        Index("dictionaryId")
    ]
)
data class ProfileDictionaryEntity(
    val profileId: Long,
    val dictionaryId: Long
)

/**
 * DAO for profile dictionary operations
 */
@androidx.room.Dao
interface ProfileDictionaryDao {
    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ProfileDictionaryEntity): Long
    
    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ProfileDictionaryEntity>)
    
    @androidx.room.Query("SELECT * FROM profile_dictionaries WHERE profileId = :profileId")
    suspend fun getDictionariesForProfile(profileId: Long): List<ProfileDictionaryEntity>
    
    @androidx.room.Query("SELECT * FROM profile_dictionaries WHERE dictionaryId = :dictionaryId")
    suspend fun getProfilesForDictionary(dictionaryId: Long): List<ProfileDictionaryEntity>
    
    @androidx.room.Query("SELECT d.* FROM dictionary_metadata d " +
                       "INNER JOIN profile_dictionaries pd ON d.id = pd.dictionaryId " +
                       "WHERE pd.profileId = :profileId " +
                       "ORDER BY d.priority DESC")
    suspend fun getDictionaryMetadataForProfile(profileId: Long): List<DictionaryMetadataEntity>
    
    @androidx.room.Query("DELETE FROM profile_dictionaries WHERE profileId = :profileId AND dictionaryId = :dictionaryId")
    suspend fun remove(profileId: Long, dictionaryId: Long)
    
    @androidx.room.Query("DELETE FROM profile_dictionaries WHERE profileId = :profileId")
    suspend fun removeAllForProfile(profileId: Long)
    
    @androidx.room.Query("DELETE FROM profile_dictionaries WHERE dictionaryId = :dictionaryId")
    suspend fun removeAllForDictionary(dictionaryId: Long)
    
    @androidx.room.Query("SELECT EXISTS(SELECT 1 FROM profile_dictionaries WHERE profileId = :profileId AND dictionaryId = :dictionaryId)")
    suspend fun isDictionaryInProfile(profileId: Long, dictionaryId: Long): Boolean
}