package com.abaga129.tekisuto.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.abaga129.tekisuto.database.DictionaryEntryEntity
import com.abaga129.tekisuto.database.DictionaryMetadataEntity
import com.abaga129.tekisuto.database.ExportedWordEntity

/**
 * Room database for Tekisuto
 */
@Database(
    entities = [
        DictionaryEntryEntity::class,
        DictionaryMetadataEntity::class,
        ExportedWordEntity::class,
        ProfileEntity::class,
        ProfileDictionaryEntity::class,
        WordFrequencyEntity::class,
        WordPitchAccentEntity::class
    ], 
    version = 15
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao
    abstract fun dictionaryMetadataDao(): DictionaryMetadataDao
    abstract fun exportedWordsDao(): ExportedWordsDao
    abstract fun profileDao(): ProfileDao
    abstract fun profileDictionaryDao(): ProfileDictionaryDao
    abstract fun wordFrequencyDao(): WordFrequencyDao
    abstract fun wordPitchAccentDao(): WordPitchAccentDao
    
    companion object {
        /**
         * Create the database with all migrations
         */
        fun createDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "tekisuto_dictionary${com.abaga129.tekisuto.BuildConfig.DB_NAME_SUFFIX}.db"
            )
            .addMigrations(
                MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, 
                MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, 
                MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15
            )
            .fallbackToDestructiveMigration()
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()
        }
    }
}

