package com.abaga129.tekisuto.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database for Tekisuto
 */
@Database(
    entities = [
        DictionaryEntryEntity::class, 
        DictionaryMetadataEntity::class, 
        ExportedWordEntity::class,
        ProfileEntity::class,
        ProfileDictionaryEntity::class
    ], 
    version = 10
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao
    abstract fun dictionaryMetadataDao(): DictionaryMetadataDao
    abstract fun exportedWordsDao(): ExportedWordsDao
    abstract fun profileDao(): ProfileDao
    abstract fun profileDictionaryDao(): ProfileDictionaryDao
    
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
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
            .fallbackToDestructiveMigration()
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()
        }
    }
}

/**
 * Migration from version 6 to 7 - adds profiles table
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create profiles table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS profiles (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                isDefault INTEGER NOT NULL DEFAULT 0,
                createdDate INTEGER NOT NULL,
                lastUsedDate INTEGER NOT NULL
            )
        """)
        
        // Create a default profile
        database.execSQL(
            "INSERT INTO profiles (name, isDefault, createdDate, lastUsedDate) " +
            "VALUES ('Default', 1, ${System.currentTimeMillis()}, ${System.currentTimeMillis()})"
        )
    }
}

/**
 * Migration from version 7 to 8 - adds profile_dictionaries table
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create profile_dictionaries table for the many-to-many relationship
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS profile_dictionaries (
                profileId INTEGER NOT NULL,
                dictionaryId INTEGER NOT NULL,
                PRIMARY KEY(profileId, dictionaryId),
                FOREIGN KEY(profileId) REFERENCES profiles(id) ON DELETE CASCADE,
                FOREIGN KEY(dictionaryId) REFERENCES dictionary_metadata(id) ON DELETE CASCADE
            )
        """)
        
        // Create indices for faster lookups
        database.execSQL("CREATE INDEX IF NOT EXISTS index_profile_dictionaries_profileId ON profile_dictionaries(profileId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_profile_dictionaries_dictionaryId ON profile_dictionaries(dictionaryId)")
        
        // Add all existing dictionaries to the default profile (if any exist)
        database.execSQL("""
            INSERT INTO profile_dictionaries (profileId, dictionaryId)
            SELECT p.id, d.id
            FROM profiles p, dictionary_metadata d
            WHERE p.isDefault = 1
        """)
    }
}

/**
 * Migration from version 8 to 9 - adds AnkiDroid configuration fields to profiles table
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add AnkiDroid configuration columns to profiles table
        database.execSQL("""
            ALTER TABLE profiles ADD COLUMN ankiDeckId INTEGER NOT NULL DEFAULT 0
        """)
        database.execSQL("""
            ALTER TABLE profiles ADD COLUMN ankiModelId INTEGER NOT NULL DEFAULT 0
        """)
        database.execSQL("""
            ALTER TABLE profiles ADD COLUMN ankiFieldWord INTEGER NOT NULL DEFAULT -1
        """)
        database.execSQL("""
            ALTER TABLE profiles ADD COLUMN ankiFieldReading INTEGER NOT NULL DEFAULT -1
        """)
        database.execSQL("""
            ALTER TABLE profiles ADD COLUMN ankiFieldDefinition INTEGER NOT NULL DEFAULT -1
        """)
        database.execSQL("""
            ALTER TABLE profiles ADD COLUMN ankiFieldScreenshot INTEGER NOT NULL DEFAULT -1
        """)
        database.execSQL("""
            ALTER TABLE profiles ADD COLUMN ankiFieldContext INTEGER NOT NULL DEFAULT -1
        """)
        database.execSQL("""
            ALTER TABLE profiles ADD COLUMN ankiFieldPartOfSpeech INTEGER NOT NULL DEFAULT -1
        """)
        database.execSQL("""
            ALTER TABLE profiles ADD COLUMN ankiFieldTranslation INTEGER NOT NULL DEFAULT -1
        """)
        database.execSQL("""
            ALTER TABLE profiles ADD COLUMN ankiFieldAudio INTEGER NOT NULL DEFAULT -1
        """)
    }
}

/**
 * Migration from version 9 to 10 - adds ocrService and cloudOcrApiKey fields to profiles table
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add ocrService column to profiles table with default value of 'mlkit'
        database.execSQL("""
            ALTER TABLE profiles ADD COLUMN ocrService TEXT NOT NULL DEFAULT 'mlkit'
        """)
        
        // Add cloudOcrApiKey column to profiles table with default empty string
        database.execSQL("""
            ALTER TABLE profiles ADD COLUMN cloudOcrApiKey TEXT NOT NULL DEFAULT ''
        """)
    }
}