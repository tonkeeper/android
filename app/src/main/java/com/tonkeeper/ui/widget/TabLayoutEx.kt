package com.tonkeeper.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.doOnNextLayout
import androidx.core.view.setPadding
import androidx.core.view.size
import com.google.android.material.tabs.TabLayout
import com.tonkeeper.R
import com.tonkeeper.extensions.getDimension

class TabLayoutEx @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = com.google.android.material.R.style.Widget_Design_TabLayout,
) : TabLayout(context, attrs, defStyle) {

    private enum class DisplayMode {
        NONE, CENTER, START_AND_SCROLLABLE
    }

    private val offsetHorizontal = context.getDimension(R.dimen.offset)
    private var displayMode = DisplayMode.NONE
        set(value) {
            if (field == value) {
                return
            }
            field = value
            if (value == DisplayMode.CENTER) {
                tabGravity = GRAVITY_CENTER
                tabMode = MODE_FIXED
                setPadding(0)
            } else if (value == DisplayMode.START_AND_SCROLLABLE) {
                tabGravity = GRAVITY_START
                tabMode = MODE_SCROLLABLE
                setPadding(offsetHorizontal.toInt(), 0, offsetHorizontal.toInt(), 0)
            }
        }


    private var slidingTabIndicator: LinearLayout? = null
    init {
        tabMode = MODE_SCROLLABLE
        tabRippleColor = context.getColorStateList(R.color.divider)
        isTabIndicatorFullWidth = false
        tabIndicatorAnimationMode = INDICATOR_ANIMATION_MODE_ELASTIC
        setSelectedTabIndicator(R.drawable.bg_tab_indicator)

        slidingTabIndicator = getChildAt(0) as LinearLayout
        applyTabModeAndGravity()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        applyTabModeAndGravity()
    }

    private fun applyTabModeAndGravity() {
        val slidingTabIndicator = slidingTabIndicator ?: return
        if (slidingTabIndicator.childCount == 0) {
            return
        }

        displayMode = if (slidingTabIndicator.measuredWidth > measuredWidth) {
            DisplayMode.START_AND_SCROLLABLE
        } else {
            DisplayMode.CENTER
        }
    }

}