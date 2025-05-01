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
        ProfileDictionaryEntity::class,
        WordFrequencyEntity::class
    ], 
    version = 13
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao
    abstract fun dictionaryMetadataDao(): DictionaryMetadataDao
    abstract fun exportedWordsDao(): ExportedWordsDao
    abstract fun profileDao(): ProfileDao
    abstract fun profileDictionaryDao(): ProfileDictionaryDao
    abstract fun wordFrequencyDao(): WordFrequencyDao
    
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
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
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

/**
 * Migration from version 10 to 11 - adds word_frequencies table and migrates frequency data
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create the word_frequencies table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS word_frequencies (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                dictionaryId INTEGER NOT NULL,
                word TEXT NOT NULL,
                frequency INTEGER NOT NULL,
                FOREIGN KEY(dictionaryId) REFERENCES dictionary_metadata(id) ON DELETE CASCADE
            )
        """)
        
        // Create indices for faster lookups
        database.execSQL("CREATE INDEX IF NOT EXISTS index_word_frequencies_dictionaryId ON word_frequencies(dictionaryId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_word_frequencies_word ON word_frequencies(word)")
        
        // Migrate data from dictionary_entries to word_frequencies
        database.execSQL("""
            INSERT INTO word_frequencies (dictionaryId, word, frequency)
            SELECT dictionaryId, term, frequency 
            FROM dictionary_entries 
            WHERE frequency IS NOT NULL
        """)
    }
}

/**
 * Migration from version 11 to 12 - removes frequency column from dictionary_entries
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // SQLite doesn't support DROP COLUMN directly, so we need to:
        // 1. Create a new table without the frequency column
        // 2. Copy data from the old table to the new table
        // 3. Drop the old table
        // 4. Rename the new table to the original name
        
        // Create a new table without the frequency column
        database.execSQL("""
            CREATE TABLE dictionary_entries_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                dictionaryId INTEGER NOT NULL,
                term TEXT NOT NULL,
                reading TEXT NOT NULL,
                definition TEXT NOT NULL,
                partOfSpeech TEXT NOT NULL,
                tags TEXT NOT NULL,
                isHtmlContent INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(dictionaryId) REFERENCES dictionary_metadata(id) ON DELETE CASCADE
            )
        """)
        
        // Copy data from the old table to the new table (excluding frequency)
        database.execSQL("""
            INSERT INTO dictionary_entries_new (id, dictionaryId, term, reading, definition, partOfSpeech, tags, isHtmlContent)
            SELECT id, dictionaryId, term, reading, definition, partOfSpeech, tags, isHtmlContent
            FROM dictionary_entries
        """)
        
        // Create indices on the new table
        database.execSQL("CREATE INDEX IF NOT EXISTS index_dictionary_entries_new_dictionaryId ON dictionary_entries_new(dictionaryId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_dictionary_entries_new_term ON dictionary_entries_new(term)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_dictionary_entries_new_reading ON dictionary_entries_new(reading)")
        
        // Drop the old table
        database.execSQL("DROP TABLE dictionary_entries")
        
        // Rename the new table to the original name
        database.execSQL("ALTER TABLE dictionary_entries_new RENAME TO dictionary_entries")
    }
}

/**
 * Migration from version 12 to 13 - ensures word_frequencies table is correctly set up
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // This migration exists to handle the case where we need to ensure
        // all queries against the database will work without referencing
        // the old frequency column that was removed in migration 11 to 12.
        // No actual schema changes are needed.
        
        try {
            // Verify word_frequencies table exists
            database.execSQL("SELECT count(*) FROM word_frequencies LIMIT 1")
        } catch (e: Exception) {
            // If the table doesn't exist, create it
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS word_frequencies (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    dictionaryId INTEGER NOT NULL,
                    word TEXT NOT NULL,
                    frequency INTEGER NOT NULL,
                    FOREIGN KEY(dictionaryId) REFERENCES dictionary_metadata(id) ON DELETE CASCADE
                )
            """)
            
            // Create indices for faster lookups
            database.execSQL("CREATE INDEX IF NOT EXISTS index_word_frequencies_dictionaryId ON word_frequencies(dictionaryId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_word_frequencies_word ON word_frequencies(word)")
        }
        
        // Ensure dictionary_entries doesn't have a frequency column
        try {
            database.execSQL("SELECT frequency FROM dictionary_entries LIMIT 1")
            
            // If we got here, the column still exists, so we need to remove it
            // Create a new table without the frequency column
            database.execSQL("""
                CREATE TABLE dictionary_entries_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    dictionaryId INTEGER NOT NULL,
                    term TEXT NOT NULL,
                    reading TEXT NOT NULL,
                    definition TEXT NOT NULL,
                    partOfSpeech TEXT NOT NULL,
                    tags TEXT NOT NULL,
                    isHtmlContent INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY(dictionaryId) REFERENCES dictionary_metadata(id) ON DELETE CASCADE
                )
            """)
            
            // Copy data from the old table to the new table (excluding frequency)
            database.execSQL("""
                INSERT INTO dictionary_entries_new (id, dictionaryId, term, reading, definition, partOfSpeech, tags, isHtmlContent)
                SELECT id, dictionaryId, term, reading, definition, partOfSpeech, tags, isHtmlContent
                FROM dictionary_entries
            """)
            
            // Create indices on the new table
            database.execSQL("CREATE INDEX IF NOT EXISTS index_dictionary_entries_new_dictionaryId ON dictionary_entries_new(dictionaryId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_dictionary_entries_new_term ON dictionary_entries_new(term)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_dictionary_entries_new_reading ON dictionary_entries_new(reading)")
            
            // Drop the old table
            database.execSQL("DROP TABLE dictionary_entries")
            
            // Rename the new table to the original name
            database.execSQL("ALTER TABLE dictionary_entries_new RENAME TO dictionary_entries")
        } catch (e: Exception) {
            // If the query fails, the frequency column doesn't exist, which is good
        }
    }
}