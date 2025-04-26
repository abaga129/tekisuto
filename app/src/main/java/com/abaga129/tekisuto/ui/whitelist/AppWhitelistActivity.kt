package com.abaga129.tekisuto.ui.whitelist

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import com.abaga129.tekisuto.ui.BaseEdgeToEdgeActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.abaga129.tekisuto.R
import com.abaga129.tekisuto.util.AppWhitelistManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Activity that allows the user to select which apps should show the floating button
 */
class AppWhitelistActivity : BaseEdgeToEdgeActivity(), CoroutineScope {
    companion object {
        private const val TAG = "AppWhitelistActivity"
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppWhitelistAdapter
    private lateinit var whitelistManager: AppWhitelistManager
    private var emptyView: TextView? = null
    
    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job
        
    private var whitelistedApps = listOf<AppWhitelistManager.AppEntry>()
    private var installedApps = listOf<AppWhitelistManager.AppEntry>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Starting activity initialization")
        setContentView(R.layout.activity_app_whitelist)
        
        // Apply edge-to-edge insets
        applyInsetsToView(R.id.root_layout)
        
        // Set up toolbar
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // Initialize whitelist manager
        whitelistManager = AppWhitelistManager(this)
        Log.d(TAG, "onCreate: WhitelistManager initialized")
        
        // Set up RecyclerView
        recyclerView = findViewById(R.id.app_list)
        recyclerView.layoutManager = LinearLayoutManager(this)
        Log.d(TAG, "onCreate: RecyclerView configured")
        
        // Add empty view to display messages when no apps are loaded
        emptyView = findViewById(R.id.empty_view)
        if (emptyView == null) {
            Log.e(TAG, "onCreate: empty_view not found in layout!")
            // Create a TextView programmatically if it doesn't exist in the layout
            emptyView = TextView(this).apply {
                text = "Loading apps..."
                textSize = 18f
                gravity = android.view.Gravity.CENTER
                setPadding(16, 48, 16, 0)
            }
            
            // Add it to the layout
            val constraintLayout = findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.root_layout)
            constraintLayout?.addView(emptyView)
        }
        
        // Check for necessary permissions
        val packageManager = packageManager
        val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        Log.d(TAG, "onCreate: Checking app permissions")
        packageInfo.requestedPermissions?.forEach { permission ->
            val granted = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "Permission $permission: ${if (granted) "GRANTED" else "DENIED"}")
        }
        
        // Load apps
        Log.d(TAG, "onCreate: Starting to load apps")
        loadApps()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.app_whitelist_menu, menu)
        
        // Set up search
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                adapter.filter(newText ?: "")
                return true
            }
        })
        
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun loadApps() {
        Log.d(TAG, "loadApps: Starting to load apps")
        
        // Show loading indicator
        findViewById<View>(R.id.progressBar).visibility = View.VISIBLE
        
        // Initialize adapter with empty lists first
        Log.d(TAG, "loadApps: Initializing adapter with empty lists")
        adapter = AppWhitelistAdapter(emptyList(), emptySet())
        recyclerView.adapter = adapter
        
        // Set empty view message
        emptyView?.text = "Loading apps..."
        emptyView?.visibility = View.VISIBLE
        
        // Use a dummy list for debugging if necessary
        val useDummyData = false
        
        launch {
            try {
                Log.d(TAG, "loadApps: Started coroutine to load apps")
                
                // Get whitelisted apps
                Log.d(TAG, "loadApps: Getting whitelisted apps")
                whitelistedApps = whitelistManager.getWhitelistedApps()
                Log.d(TAG, "loadApps: Found ${whitelistedApps.size} whitelisted apps")
                
                if (useDummyData) {
                    // For debugging: use dummy data when real data can't be loaded
                    Log.d(TAG, "loadApps: Using dummy data for testing")
                    installedApps = listOf(
                        AppWhitelistManager.AppEntry("com.example.app1", "Test App 1"),
                        AppWhitelistManager.AppEntry("com.example.app2", "Test App 2"),
                        AppWhitelistManager.AppEntry("com.example.app3", "Test App 3")
                    )
                } else {
                    // Get installed apps in background
                    Log.d(TAG, "loadApps: Getting installed apps")
                    try {
                        installedApps = withContext(Dispatchers.IO) {
                            whitelistManager.getInstalledApps()
                        }
                        Log.d(TAG, "loadApps: Found ${installedApps.size} installed apps")
                    } catch (e: Exception) {
                        Log.e(TAG, "loadApps: Error getting installed apps", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@AppWhitelistActivity,
                                "Error loading apps: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        installedApps = emptyList()
                    }
                }
                
                if (installedApps.isEmpty()) {
                    Log.w(TAG, "loadApps: No installed apps found!")
                    withContext(Dispatchers.Main) {
                        emptyView?.text = "No apps found. Please check app permissions."
                        emptyView?.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                    }
                    return@launch
                }
                
                // Create adapter with both lists
                Log.d(TAG, "loadApps: Creating new adapter with loaded apps")
                val selectedPackages = whitelistedApps.map { it.packageName }.toSet()
                
                withContext(Dispatchers.Main) {
                    adapter = AppWhitelistAdapter(installedApps, selectedPackages)
                    Log.d(TAG, "loadApps: Adapter created with ${installedApps.size} apps, ${selectedPackages.size} selected")
                    
                    // Handle app selection
                    adapter.setOnAppSelectionListener(object : AppWhitelistAdapter.OnAppSelectionListener {
                        override fun onAppSelected(appEntry: AppWhitelistManager.AppEntry, isSelected: Boolean) {
                            Log.d(TAG, "onAppSelected: ${appEntry.appName} - ${if (isSelected) "selected" else "unselected"}")
                            if (isSelected) {
                                whitelistManager.addAppToWhitelist(appEntry.packageName)
                            } else {
                                whitelistManager.removeAppFromWhitelist(appEntry.packageName)
                            }
                        }
                    })
                    
                    // Set adapter to recyclerView on the main thread
                    Log.d(TAG, "loadApps: Setting adapter to RecyclerView")
                    recyclerView.adapter = adapter
                    recyclerView.visibility = View.VISIBLE
                    emptyView?.visibility = View.GONE
                    adapter.notifyDataSetChanged()
                    Log.d(TAG, "loadApps: Adapter set and data notified")
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadApps: Error in main app loading process", e)
                withContext(Dispatchers.Main) {
                    emptyView?.text = "Error loading apps: ${e.message}"
                    emptyView?.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                    Toast.makeText(
                        this@AppWhitelistActivity,
                        "Error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                // Hide loading indicator
                Log.d(TAG, "loadApps: Hiding loading indicator")
                withContext(Dispatchers.Main) {
                    findViewById<View>(R.id.progressBar).visibility = View.GONE
                }
            }
        }
    }
}