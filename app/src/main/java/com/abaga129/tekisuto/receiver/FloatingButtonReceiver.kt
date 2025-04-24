package com.abaga129.tekisuto.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.abaga129.tekisuto.service.AccessibilityOcrService

/**
 * BroadcastReceiver for handling the floating button visibility
 * This is a standalone implementation to support Android 15's requirement
 * for explicitly declaring broadcast receivers.
 */
class FloatingButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("FloatingButtonReceiver", "Received action: ${intent.action}")
        
        if (intent.action == AccessibilityOcrService.ACTION_SHOW_FLOATING_BUTTON) {
            Log.d("FloatingButtonReceiver", "Show floating button action received")
            
            // Get the service instance and show the button
            val serviceInstance = AccessibilityOcrService.getInstance()
            if (serviceInstance != null) {
                Log.d("FloatingButtonReceiver", "Service instance found, showing button")
                serviceInstance.showFloatingButton()
            } else {
                Log.w("FloatingButtonReceiver", "Service instance not available")
            }
        }
    }
}