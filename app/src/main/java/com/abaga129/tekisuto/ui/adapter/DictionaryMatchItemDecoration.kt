package com.abaga129.tekisuto.ui.adapter

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * Item decoration for dictionary match items in the RecyclerView
 */
class DictionaryMatchItemDecoration(private val spacing: Int) : RecyclerView.ItemDecoration() {
    
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        
        // Apply spacing to all items except the last one
        if (position != parent.adapter?.itemCount?.minus(1)) {
            outRect.bottom = spacing
        }
        
        // Apply spacing to all sides
        outRect.left = spacing
        outRect.right = spacing
        
        // Add top spacing only to the first item
        if (position == 0) {
            outRect.top = spacing
        }
    }
}