package com.abaga129.tekisuto.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import kotlin.math.max

/**
 * A layout that arranges its children in multiple rows if there isn't enough horizontal space
 */
class FlowLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    private var lineHeight = 0
    private val horizontalSpacing = 4 // Space between items horizontally
    private val verticalSpacing = 4 // Space between rows

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        var height = 0
        
        var lineWidth = 0
        lineHeight = 0
        
        val childCount = childCount
        
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility != GONE) {
                measureChild(child, widthMeasureSpec, heightMeasureSpec)
                
                val childWidth = child.measuredWidth
                val childHeight = child.measuredHeight
                
                if (lineWidth + childWidth > width) {
                    // Need to wrap to next line
                    height += lineHeight + verticalSpacing
                    lineWidth = childWidth
                    lineHeight = childHeight
                } else {
                    lineWidth += childWidth + horizontalSpacing
                    lineHeight = max(lineHeight, childHeight)
                }
            }
        }
        
        // Add the last line's height
        height += lineHeight
        
        // Account for padding
        height += paddingTop + paddingBottom
        
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) 
                MeasureSpec.getSize(heightMeasureSpec) 
            else 
                height
        )
    }
    
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val width = right - left - paddingRight
        
        var xPos = paddingLeft
        var yPos = paddingTop
        
        lineHeight = 0
        
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility != GONE) {
                val childWidth = child.measuredWidth
                val childHeight = child.measuredHeight
                
                if (xPos + childWidth > width) {
                    // Move to next line
                    xPos = paddingLeft
                    yPos += lineHeight + verticalSpacing
                    lineHeight = 0
                }
                
                child.layout(xPos, yPos, xPos + childWidth, yPos + childHeight)
                
                xPos += childWidth + horizontalSpacing
                lineHeight = max(lineHeight, childHeight)
            }
        }
    }
}