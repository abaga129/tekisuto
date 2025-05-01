package com.abaga129.tekisuto.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.abaga129.tekisuto.database.DictionaryRepository

private const val TAG = "MainViewModel"
internal const val BATCH_SIZE = 100 // Process 100 entries at a time - reduced to save memory

class MainViewModel : ViewModel() {

    private val _isAccessibilityServiceEnabled = MutableLiveData<Boolean>()
    val isAccessibilityServiceEnabled: LiveData<Boolean> = _isAccessibilityServiceEnabled

    fun checkAccessibilityServiceStatus(context: Context, serviceClass: Class<*>) {
        val serviceClassName = serviceClass.name
        val packageName = context.packageName
        val componentName = "$packageName/$serviceClassName"
        
        try {
            // Get the enabled accessibility services string from settings
            val enabledServicesStr = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            
            Log.d(TAG, "Current enabled services: $enabledServicesStr")
            Log.d(TAG, "Looking for service: $componentName")
            
            // The format in the settings string is "packagename/serviceclassname"
            // We need to check if our service is in this list
            val isEnabled = if (enabledServicesStr.isEmpty()) {
                false
            } else {
                // Split the string into individual service components
                val enabledServices = enabledServicesStr.split(":")
                
                // Check if our service is in the list
                enabledServices.any { service ->
                    service.equals(componentName, ignoreCase = true) ||
                    service.contains(serviceClassName) ||
                    (service.contains(packageName) && service.contains(".AccessibilityOcrService"))
                }
            }
            
            // Update the LiveData with our finding
            _isAccessibilityServiceEnabled.value = isEnabled
            
            Log.d(TAG, "Accessibility service is ${if (isEnabled) "ENABLED" else "DISABLED"}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking accessibility service status", e)
            _isAccessibilityServiceEnabled.value = false
        }
    }

    /**
     * Imports a Yomitan dictionary from the given URI
     * @param context Android context
     * @param uri URI of the dictionary file (zip)
     * @return Pair<Boolean, Long> where first is success status and second is dictionaryId if successful
     */
    suspend fun importYomitanDictionary(context: Context, uri: Uri): Pair<Boolean, Long> {
        return ParserViewModel.getInstance().importYomitanDictionary(context, uri)
    }
}
