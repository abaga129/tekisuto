package com.abaga129.tekisuto.ui

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Base activity that implements edge-to-edge support for all activities.
 * Provides common functionality for handling system insets across different Android versions.
 */
open class BaseEdgeToEdgeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable edge-to-edge before calling setContentView
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
    }

    /**
     * Apply insets to the specified view. Call this after setContentView()
     * to properly handle insets for your main content.
     */
    protected fun applyInsetsToView(viewId: Int) {
        val view = findViewById<View>(viewId)
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }
    
    /**
     * Apply insets to only the top and bottom of the specified view.
     * Useful for scroll views where you want content to scroll under the side insets.
     */
    protected fun applyTopBottomInsetsToView(viewId: Int) {
        val view = findViewById<View>(viewId)
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(view.paddingLeft, insets.top, view.paddingRight, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }
}