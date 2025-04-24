package com.abaga129.tekisuto.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Base activity that implements edge-to-edge support for all activities.
 * Provides common functionality for handling system insets across different Android versions.
 * Enhanced to better support foldable devices and different screen aspect ratios.
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
    
    /**
     * Apply insets to a nested scroll view for better edge-to-edge experience.
     * This method is particularly useful for activities with scrollable content that should
     * extend edge-to-edge while still respecting system bars.
     */
    protected fun applyInsetsToScrollView(scrollViewId: Int, contentId: Int) {
        val scrollView = findViewById<View>(scrollViewId)
        val content = findViewById<View>(contentId)
        
        if (scrollView != null && content != null) {
            ViewCompat.setOnApplyWindowInsetsListener(scrollView) { _, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                
                // Apply padding to the content rather than the scroll view itself
                // This allows the content to scroll all the way to the edges while respecting insets
                if (content is ViewGroup) {
                    content.updatePadding(
                        left = insets.left,
                        top = insets.top,
                        right = insets.right,
                        bottom = insets.bottom
                    )
                }
                
                // Return the original insets so that children can still use them if needed
                windowInsets
            }
        }
    }
    
    /**
     * Apply insets specifically for foldable devices or unusual aspect ratios.
     * Handles the main container padding while preserving scrollability.
     */
    protected fun applyAdaptiveInsets(rootViewId: Int, contentViewId: Int) {
        val rootView = findViewById<View>(rootViewId)
        val contentView = findViewById<View>(contentViewId)
        
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val foldInsets = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
            
            // Apply combined insets (system bars and any foldable display cutouts)
            val combinedLeftInset = maxOf(insets.left, foldInsets.left)
            val combinedTopInset = maxOf(insets.top, foldInsets.top)
            val combinedRightInset = maxOf(insets.right, foldInsets.right)
            val combinedBottomInset = maxOf(insets.bottom, foldInsets.bottom)
            
            contentView.updatePadding(
                left = combinedLeftInset,
                top = combinedTopInset,
                right = combinedRightInset,
                bottom = combinedBottomInset
            )
            
            windowInsets
        }
    }
}