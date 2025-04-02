package com.abaga129.tekisuto

import android.app.ActivityManager
import android.app.Application
import android.content.Context
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
        // Log available memory for debugging
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryClass = activityManager.memoryClass
        val largeMemoryClass = activityManager.largeMemoryClass
        android.util.Log.d("TekisutoApp", "Memory available: $memoryClass MB, Large heap: $largeMemoryClass MB")
    }
}