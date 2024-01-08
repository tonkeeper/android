package uikit.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.core.view.setPadding
import com.google.android.material.tabs.TabLayout
import uikit.R
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.setPaddingHorizontal

class TabLayoutEx @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = com.google.android.material.R.style.Widget_Design_TabLayout,
) : TabLayout(context, attrs, defStyle) {

    private enum class DisplayMode {
        NONE, CENTER, START_AND_SCROLLABLE
    }

    private val offsetHorizontal = context.getDimensionPixelSize(R.dimen.offsetMedium)
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
                setPaddingHorizontal(offsetHorizontal)
            }
        }


    private var slidingTabIndicator: LinearLayout? = null

    init {
        tabMode = MODE_SCROLLABLE
        tabRippleColor = context.getColorStateList(R.color.backgroundContentTint)
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