package com.abaga129.tekisuto

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import androidx.emoji2.text.EmojiCompat
import androidx.emoji2.bundled.BundledEmojiCompatConfig
import androidx.multidex.MultiDex

/**
 * Application class for Tekisuto to handle MultiDex support and global initialization
 */
class TekisutoApplication : Application() {
    
    companion object {
        private const val TAG = "TekisutoApp"
    }
    
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Initialize MultiDex to handle large app with many methods
        MultiDex.install(this)
    }
    
    override fun onCreate() {
        super.onCreate()
        // Initialize emoji support
        initEmojiCompat()
        
        // Request larger heap for dictionary imports
        requestLargeHeap()
        
        // Log available memory for debugging
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryClass = activityManager.memoryClass
        val largeMemoryClass = activityManager.largeMemoryClass
        android.util.Log.d(TAG, "Memory available: $memoryClass MB, Large heap: $largeMemoryClass MB")
        
        // Initialize memory trimming
        setupLowMemoryHandling()
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
            android.util.Log.d(TAG, "EmojiCompat initialized with bundled emoji font")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error initializing EmojiCompat: ${e.message}")
        }
    }
    
    /**
     * Request large heap for the application
     * This allows more memory for dictionary imports
     */
    private fun requestLargeHeap() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // For Android 9+ (API 28+), we can use per-app memory limits
            try {
                val vmClass = Class.forName("dalvik.system.VMRuntime")
                val getRuntime = vmClass.getDeclaredMethod("getRuntime")
                val runtime = getRuntime.invoke(null)
                
                // Try to set a higher target utilization (0.75 means use 75% of max heap)
                val setTargetUtilization = vmClass.getDeclaredMethod("setTargetUtilization", Float::class.java)
                setTargetUtilization.invoke(runtime, 0.75f)
                
                android.util.Log.d(TAG, "Requested larger heap for dictionary imports")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to request larger heap: ${e.message}")
            }
        }
    }
    
    /**
     * Setup handlers for low memory conditions
     */
    private fun setupLowMemoryHandling() {
        // Register for trim memory callbacks
        registerComponentCallbacks(object : android.content.ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                when (level) {
                    android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
                    android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                        android.util.Log.w(TAG, "Critical memory condition: $level - releasing caches")
                        System.gc()
                    }
                    android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
                    android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                        android.util.Log.w(TAG, "Low memory condition: $level - releasing non-critical caches")
                    }
                }
            }
            
            override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
                // Not needed for memory management
            }
            
            override fun onLowMemory() {
                android.util.Log.w(TAG, "onLowMemory called - releasing all caches")
                System.gc()
            }
        })
    }
}