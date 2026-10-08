package com.naseeb.salestracker.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.HorizontalScrollView

/**
 * Custom HorizontalScrollView for the data records table that prevents parent ViewPager2
 * from intercepting horizontal swipe gestures, preventing accidental page transitions
 * while navigating table columns.
 */
class TableHorizontalScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // Disallow parent ViewPager2 from intercepting horizontal touch gestures inside the table
        parent?.requestDisallowInterceptTouchEvent(true)
        return super.dispatchTouchEvent(ev)
    }
}
