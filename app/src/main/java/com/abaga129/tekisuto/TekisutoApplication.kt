package com.abaga129.tekisuto

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import androidx.emoji2.text.EmojiCompat
import androidx.emoji2.bundled.BundledEmojiCompatConfig
import androidx.multidex.MultiDex

/**
 * Application class for Tekisuto to handle MultiDex support and global initialization
 */
class TekisutoApplication : Application() {
    
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Initialize MultiDex to handle large app with many methods
        MultiDex.install(this)
    }
    
    override fun onCreate() {
        super.onCreate()
        // Initialize emoji support
        initEmojiCompat()
        
        // Log available memory for debugging
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryClass = activityManager.memoryClass
        val largeMemoryClass = activityManager.largeMemoryClass
        android.util.Log.d("TekisutoApp", "Memory available: $memoryClass MB, Large heap: $largeMemoryClass MB")
    }
    
    /**
     * Initialize EmojiCompat for proper emoji display
     */
    private fun initEmojiCompat() {
        try {
            // Use the bundled emoji font configuration
            val config = BundledEmojiCompatConfig(this)
                .setReplaceAll(true)
                
            EmojiCompat.init(config)
            android.util.Log.d("TekisutoApp", "EmojiCompat initialized with bundled emoji font")
        } catch (e: Exception) {
            android.util.Log.e("TekisutoApp", "Error initializing EmojiCompat: ${e.message}")
        }
    }
}