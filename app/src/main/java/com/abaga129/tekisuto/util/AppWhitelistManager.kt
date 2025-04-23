package com.abaga129.tekisuto.util

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Manager for handling app whitelist functionality
 * Stores apps as package names in a Set stored in SharedPreferences
 */
class AppWhitelistManager(private val context: Context) {
    companion object {
        private const val TAG = "AppWhitelistManager"
    }

    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val gson = Gson()
    
    init {
        Log.d(TAG, "AppWhitelistManager initialized")
    }
    
    /**
     * Data class to represent an app entry
     */
    data class AppEntry(
        val packageName: String,
        val appName: String
    )
    
    /**
     * Check if app whitelist feature is enabled
     */
    fun isWhitelistEnabled(): Boolean {
        return prefs.getBoolean(PreferenceKeys.ENABLE_APP_WHITELIST, false)
    }
    
    /**
     * Check if a package is in the whitelist
     */
    fun isAppWhitelisted(packageName: String): Boolean {
        // If whitelist is disabled, consider all apps "whitelisted"
        if (!isWhitelistEnabled()) {
            return true
        }
        
        val whitelistedApps = getWhitelistedApps()
        return whitelistedApps.any { it.packageName == packageName }
    }
    
    /**
     * Get all whitelisted apps
     */
    fun getWhitelistedApps(): List<AppEntry> {
        Log.d(TAG, "getWhitelistedApps: Retrieving whitelisted apps")
        val json = prefs.getString(PreferenceKeys.APP_WHITELIST, "[]")
        Log.d(TAG, "getWhitelistedApps: Raw JSON: $json")
        val type = object : TypeToken<List<AppEntry>>() {}.type
        val result = try {
            gson.fromJson<List<AppEntry>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "getWhitelistedApps: Error parsing JSON", e)
            emptyList()
        }
        Log.d(TAG, "getWhitelistedApps: Found ${result.size} whitelisted apps")
        return result
    }
    
    /**
     * Add an app to the whitelist
     */
    fun addAppToWhitelist(packageName: String) {
        val currentApps = getWhitelistedApps().toMutableList()
        
        // Check if the app is already in the whitelist
        if (currentApps.any { it.packageName == packageName }) {
            return
        }
        
        // Get app name from package manager
        val appName = try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName // Fallback to package name if we can't get the app name
        }
        
        // Add the new app
        currentApps.add(AppEntry(packageName, appName))
        
        // Save the updated list
        val json = gson.toJson(currentApps)
        prefs.edit().putString(PreferenceKeys.APP_WHITELIST, json).apply()
    }
    
    /**
     * Remove an app from the whitelist
     */
    fun removeAppFromWhitelist(packageName: String) {
        val currentApps = getWhitelistedApps().toMutableList()
        currentApps.removeAll { it.packageName == packageName }
        
        // Save the updated list
        val json = gson.toJson(currentApps)
        prefs.edit().putString(PreferenceKeys.APP_WHITELIST, json).apply()
    }
    
    /**
     * Set the complete whitelist
     */
    fun setWhitelistedApps(apps: List<AppEntry>) {
        val json = gson.toJson(apps)
        prefs.edit().putString(PreferenceKeys.APP_WHITELIST, json).apply()
    }
    
    /**
     * Get all installed apps that can be added to the whitelist
     */
    fun getInstalledApps(): List<AppEntry> {
        Log.d(TAG, "getInstalledApps: Retrieving installed apps")
        val pm = context.packageManager
        
        try {
            // Try a different approach using LauncherApps service first
            val result = mutableListOf<AppEntry>()
            
            try {
                Log.d(TAG, "getInstalledApps: Trying alternative approach with getInstalledPackages")
                // Try using getInstalledPackages instead of getInstalledApplications
                val packages = pm.getInstalledPackages(0)
                Log.d(TAG, "getInstalledApps: Found ${packages.size} total packages")
                
                for (packageInfo in packages) {
                    try {
                        // Skip system apps and our own app
                        val appInfo = packageInfo.applicationInfo
                        if (((appInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM) != 0 || 
                            packageInfo.packageName == context.packageName) {
                            continue
                        }
                        
                        // Check if the app has a launcher intent (can be opened)
                        val launchIntent = pm.getLaunchIntentForPackage(packageInfo.packageName)
                        if (launchIntent == null) {
                            // Skip apps that can't be launched
                            continue
                        }
                        
                        val appName = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: packageInfo.packageName
                        Log.d(TAG, "Adding app: $appName (${packageInfo.packageName})")
                        result.add(AppEntry(packageInfo.packageName, appName))
                    } catch (e: Exception) {
                        Log.w(TAG, "Error processing package ${packageInfo.packageName}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error using getInstalledPackages", e)
                
                // Fall back to original approach
                Log.d(TAG, "getInstalledApps: Falling back to getInstalledApplications")
                val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                Log.d(TAG, "getInstalledApps: Found ${installedApps.size} total installed apps")
                
                // Filter non-system apps
                val filteredApps = installedApps.filter { appInfo -> 
                    // Filter out system apps and our own app
                    (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && 
                    appInfo.packageName != context.packageName
                }
                Log.d(TAG, "getInstalledApps: After filtering, ${filteredApps.size} non-system apps found")
                
                // Create app entries
                filteredApps.forEach { appInfo ->
                    try {
                        // Check if the app has a launcher intent (can be opened)
                        val launchIntent = pm.getLaunchIntentForPackage(appInfo.packageName)
                        if (launchIntent == null) {
                            // Skip apps that can't be launched
                            return@forEach
                        }
                        
                        val appName = pm.getApplicationLabel(appInfo).toString()
                        Log.d(TAG, "Adding app: $appName (${appInfo.packageName})")
                        result.add(AppEntry(appInfo.packageName, appName))
                    } catch (e: Exception) {
                        Log.w(TAG, "Error processing app ${appInfo.packageName}", e)
                    }
                }
            }
            
            // Log findings after filtering
            Log.d(TAG, "getInstalledApps: Found ${result.size} launchable non-system apps")
            
            // Sort the results
            val sortedResult = result.sortedBy { it.appName.lowercase() }
            
            Log.d(TAG, "getInstalledApps: Returning ${sortedResult.size} app entries")
            if (sortedResult.isNotEmpty()) {
                Log.d(TAG, "getInstalledApps: First app: ${sortedResult.first().appName} (${sortedResult.first().packageName})")
                // Log a few more apps for debugging
                if (sortedResult.size >= 3) {
                    Log.d(TAG, "Sample apps: ${sortedResult[0].appName}, ${sortedResult[1].appName}, ${sortedResult[2].appName}")
                }
            }
            
            return sortedResult
        } catch (e: Exception) {
            Log.e(TAG, "getInstalledApps: Error getting installed apps", e)
            return emptyList()
        }
    }
}