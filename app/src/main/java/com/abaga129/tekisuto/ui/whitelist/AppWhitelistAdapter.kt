package com.abaga129.tekisuto.ui.whitelist

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.abaga129.tekisuto.R
import com.abaga129.tekisuto.util.AppWhitelistManager

/**
 * Adapter for displaying and selecting apps for the whitelist
 */
class AppWhitelistAdapter(
    private val allApps: List<AppWhitelistManager.AppEntry>,
    private val selectedPackages: Set<String>
) : RecyclerView.Adapter<AppWhitelistAdapter.AppViewHolder>() {
    companion object {
        private const val TAG = "AppWhitelistAdapter"
    }

    // List to hold filtered apps
    private var filteredApps = allApps
    
    init {
        Log.d(TAG, "Adapter initialized with ${allApps.size} apps, ${selectedPackages.size} selected")
    }
    
    // Listener for app selection events
    private var selectionListener: OnAppSelectionListener? = null
    
    // Interface for selection callbacks
    interface OnAppSelectionListener {
        fun onAppSelected(appEntry: AppWhitelistManager.AppEntry, isSelected: Boolean)
    }
    
    // Set the selection listener
    fun setOnAppSelectionListener(listener: OnAppSelectionListener) {
        selectionListener = listener
        Log.d(TAG, "Selection listener set")
    }
    
    // Filter the app list based on search query
    fun filter(query: String) {
        Log.d(TAG, "Filtering apps with query: $query")
        filteredApps = if (query.isEmpty()) {
            allApps
        } else {
            allApps.filter { 
                it.appName.contains(query, ignoreCase = true) || 
                it.packageName.contains(query, ignoreCase = true)
            }
        }
        Log.d(TAG, "Filter result: ${filteredApps.size} apps matched")
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        Log.d(TAG, "onCreateViewHolder called")
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        Log.d(TAG, "onBindViewHolder at position $position")
        if (position >= filteredApps.size) {
            Log.e(TAG, "Position $position is out of bounds for filteredApps size ${filteredApps.size}")
            return
        }
        
        val app = filteredApps[position]
        holder.bind(app, selectedPackages.contains(app.packageName))
    }

    override fun getItemCount(): Int {
        val count = filteredApps.size
        Log.d(TAG, "getItemCount: $count")
        return count
    }

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val appName: TextView = itemView.findViewById(R.id.app_name)
        private val packageName: TextView = itemView.findViewById(R.id.package_name)
        private val appIcon: ImageView = itemView.findViewById(R.id.app_icon)
        private val checkbox: CheckBox = itemView.findViewById(R.id.checkbox)
        
        fun bind(app: AppWhitelistManager.AppEntry, isSelected: Boolean) {
            Log.d(TAG, "Binding app: ${app.appName} (${app.packageName}), selected: $isSelected")
            
            appName.text = app.appName
            packageName.text = app.packageName
            
            // Try to load app icon
            try {
                val packageManager = itemView.context.packageManager
                val appInfo = packageManager.getApplicationInfo(app.packageName, 0)
                appIcon.setImageDrawable(packageManager.getApplicationIcon(appInfo))
                Log.d(TAG, "Loaded icon for ${app.appName}")
            } catch (e: Exception) {
                // If we can't load the icon, use a default one
                Log.w(TAG, "Failed to load icon for ${app.appName}: ${e.message}")
                appIcon.setImageResource(R.drawable.ic_app_default)
            }
            
            // Set checkbox state without triggering listener
            checkbox.setOnCheckedChangeListener(null)
            checkbox.isChecked = isSelected
            
            // Set up click listeners
            val clickListener = View.OnClickListener {
                val newState = !checkbox.isChecked
                Log.d(TAG, "Item clicked: ${app.appName}, new state: $newState")
                checkbox.isChecked = newState
                selectionListener?.onAppSelected(app, newState)
            }
            
            // Make the whole item clickable
            itemView.setOnClickListener(clickListener)
            
            // Also set listener on checkbox
            checkbox.setOnCheckedChangeListener { _, isChecked ->
                Log.d(TAG, "Checkbox changed: ${app.appName}, checked: $isChecked")
                selectionListener?.onAppSelected(app, isChecked)
            }
        }
    }
}