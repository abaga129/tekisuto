package com.abaga129.tekisuto.database

import android.annotation.SuppressLint
import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 3 to 4 - adds isHtmlContent column to dictionary_entries table
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add isHtmlContent column with default value false
        database.execSQL("ALTER TABLE dictionary_entries ADD COLUMN isHtmlContent INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Migration from version 4 to 5 - adds exported_words table
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create exported_words table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS exported_words (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                word TEXT NOT NULL,
                dictionaryId INTEGER NOT NULL,
                ankiDeckId INTEGER NOT NULL,
                ankiNoteId INTEGER,
                dateAdded INTEGER NOT NULL
            )
        """)

        // Create index on word column
        database.execSQL("CREATE INDEX IF NOT EXISTS index_exported_words_word ON exported_words (word)")
    }
}

/**
 * Migration from version 5 to 6 - adds frequency column to dictionary_entries table
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add frequency column to dictionary_entries table (nullable INTEGER)
        database.execSQL("ALTER TABLE dictionary_entries ADD COLUMN frequency INTEGER")
    }
}

/**
 * Migration from version 6 to 7 - adds profiles table
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    @SuppressLint("DirectSystemCurrentTimeMillisUsage")
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

/**
 * Migration from version 13 to 14 - adds word_pitch_accents table and pitch accent field to profiles
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create the word_pitch_accents table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS word_pitch_accents (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                dictionaryId INTEGER NOT NULL,
                word TEXT NOT NULL,
                reading TEXT NOT NULL,
                pitchAccent TEXT NOT NULL,
                FOREIGN KEY(dictionaryId) REFERENCES dictionary_metadata(id) ON DELETE CASCADE
            )
        """)

        // Create indices for faster lookups
        database.execSQL("CREATE INDEX IF NOT EXISTS index_word_pitch_accents_dictionaryId ON word_pitch_accents(dictionaryId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_word_pitch_accents_word ON word_pitch_accents(word)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_word_pitch_accents_reading ON word_pitch_accents(reading)")
        
        try {
            // Check if the ankiFieldPitchAccent column already exists
            database.execSQL("SELECT ankiFieldPitchAccent FROM profiles LIMIT 1")
            Log.d("Migration", "ankiFieldPitchAccent column already exists")
        } catch (e: Exception) {
            // If the column doesn't exist, add it
            Log.d("Migration", "Adding ankiFieldPitchAccent column to profiles table")
            try {
                database.execSQL("""
                    ALTER TABLE profiles ADD COLUMN ankiFieldPitchAccent INTEGER NOT NULL DEFAULT -1
                """)
            } catch (e2: Exception) {
                // If we get an error trying to add the column, it might be because it was already added
                // by a concurrent process or a previously interrupted migration
                Log.e("Migration", "Error adding ankiFieldPitchAccent column: ${e2.message}")
                // Continue with migration regardless of this error
            }
        }
    }
}

/**
 * Migration from version 14 to 15 - ensures word_pitch_accents table has the correct indices
 */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Make sure ankiFieldPitchAccent column exists in profiles table
        try {
            database.execSQL("SELECT ankiFieldPitchAccent FROM profiles LIMIT 1")
            Log.d("Migration", "ankiFieldPitchAccent column already exists")
        } catch (e: Exception) {
            Log.d("Migration", "Adding ankiFieldPitchAccent column to profiles table")
            try {
                database.execSQL("""
                    ALTER TABLE profiles ADD COLUMN ankiFieldPitchAccent INTEGER NOT NULL DEFAULT -1
                """)
            } catch (e2: Exception) {
                // If we get an error trying to add the column, it might be because it was already added
                // by a concurrent process or a previously interrupted migration
                Log.e("Migration", "Error adding ankiFieldPitchAccent column: ${e2.message}")
                // Continue with migration regardless of this error
            }
        }
        
        // Handle the word_pitch_accents table
        try {
            // Backup existing data
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS word_pitch_accents_backup (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    dictionaryId INTEGER NOT NULL,
                    word TEXT NOT NULL,
                    reading TEXT NOT NULL,
                    pitchAccent TEXT NOT NULL
                )
            """)
            
            database.execSQL("""
                INSERT INTO word_pitch_accents_backup 
                SELECT id, dictionaryId, word, reading, pitchAccent 
                FROM word_pitch_accents
            """)
            
            // Drop existing table and recreate with correct schema
            database.execSQL("DROP TABLE IF EXISTS word_pitch_accents")
            
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS word_pitch_accents (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    dictionaryId INTEGER NOT NULL,
                    word TEXT NOT NULL,
                    reading TEXT NOT NULL,
                    pitchAccent TEXT NOT NULL,
                    FOREIGN KEY(dictionaryId) REFERENCES dictionary_metadata(id) ON DELETE CASCADE
                )
            """)
            
            // Add all required indices
            database.execSQL("CREATE INDEX IF NOT EXISTS index_word_pitch_accents_dictionaryId ON word_pitch_accents(dictionaryId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_word_pitch_accents_word ON word_pitch_accents(word)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_word_pitch_accents_reading ON word_pitch_accents(reading)")
            
            // Restore data from backup
            database.execSQL("""
                INSERT INTO word_pitch_accents 
                SELECT id, dictionaryId, word, reading, pitchAccent 
                FROM word_pitch_accents_backup
            """)
            
            // Drop backup table
            database.execSQL("DROP TABLE IF EXISTS word_pitch_accents_backup")
            
            Log.d("Migration", "Successfully recreated word_pitch_accents table with correct indices")
            
        } catch (e: Exception) {
            Log.e("Migration", "Error fixing word_pitch_accents table: ${e.message}", e)
            
            // If there was an error, make sure to create the table from scratch
            database.execSQL("DROP TABLE IF EXISTS word_pitch_accents")
            
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS word_pitch_accents (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    dictionaryId INTEGER NOT NULL,
                    word TEXT NOT NULL,
                    reading TEXT NOT NULL,
                    pitchAccent TEXT NOT NULL,
                    FOREIGN KEY(dictionaryId) REFERENCES dictionary_metadata(id) ON DELETE CASCADE
                )
            """)
            
            // Add all required indices
            database.execSQL("CREATE INDEX IF NOT EXISTS index_word_pitch_accents_dictionaryId ON word_pitch_accents(dictionaryId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_word_pitch_accents_word ON word_pitch_accents(word)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_word_pitch_accents_reading ON word_pitch_accents(reading)")
        }
    }
}

/**
 * Migration from version 15 to 16 - adds reading and displayValue columns to word_frequencies table
 */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(database: SupportSQLiteDatabase) {
        try {
            // Add reading column to word_frequencies table (nullable TEXT)
            database.execSQL("""
                ALTER TABLE word_frequencies ADD COLUMN reading TEXT
            """)
            
            // Add displayValue column to word_frequencies table (nullable TEXT)
            database.execSQL("""
                ALTER TABLE word_frequencies ADD COLUMN displayValue TEXT
            """)
            
            // Create index for reading column
            database.execSQL("CREATE INDEX IF NOT EXISTS index_word_frequencies_reading ON word_frequencies(reading)")
            
            // Create composite index for word+reading searches
            database.execSQL("CREATE INDEX IF NOT EXISTS index_word_frequencies_word_reading ON word_frequencies(word, reading)")
            
            Log.d("Migration", "Successfully added reading and displayValue columns to word_frequencies table")
            
        } catch (e: Exception) {
            Log.e("Migration", "Error adding columns to word_frequencies table: ${e.message}", e)
            throw e
        }
    }
}
